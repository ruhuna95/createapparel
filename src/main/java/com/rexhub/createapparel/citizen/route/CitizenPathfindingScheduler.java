package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class CitizenPathfindingScheduler {
    public enum Priority { SAFETY, PLAYER, ROUTE, AUTONOMY }
    private record Request(Citizen citizen, Priority priority, BooleanSupplier valid, Runnable action, long sequence, long submittedAt) { }
    private static final Map<ServerLevel, CitizenPathfindingScheduler> LEVELS = new IdentityHashMap<>();
    private final LinkedHashMap<UUID, Request> pending = new LinkedHashMap<>();
    private final ServerLevel level;
    private long sequence;
    private int lastExecuted;
    private long lastTick = Long.MIN_VALUE;
    private long lastNanos;

    private CitizenPathfindingScheduler(ServerLevel level) { this.level = level; }
    public static CitizenPathfindingScheduler get(ServerLevel level) { return LEVELS.computeIfAbsent(level, CitizenPathfindingScheduler::new); }
    public static void unload(ServerLevel level) { LEVELS.remove(level); }
    public static void clear() { LEVELS.clear(); }
    public boolean pending(Citizen citizen) { return pending.containsKey(citizen.getUUID()); }
    public int queuedCount() { return pending.size(); }
    public int lastExecuted() { return lastExecuted; }
    public long lastNanos() { return lastNanos; }

    public boolean submit(Citizen citizen, Priority priority, BooleanSupplier valid, Runnable action) {
        if (pending(citizen) || citizen.level() != level || citizen.isRemoved()) return false;
        pending.put(citizen.getUUID(), new Request(citizen, priority, valid, action, sequence++, level.getGameTime()));
        return true;
    }

    public void cancel(Citizen citizen) { pending.remove(citizen.getUUID()); }

    public void tick() {
        if (lastTick == level.getGameTime()) return;
        lastTick = level.getGameTime();
        CitizenRouteManager.get(level).planner().tick();
        long started = System.nanoTime();
        lastExecuted = 0;
        var batch = new ArrayList<>(pending.values());
        batch.sort(Comparator.comparing(Request::priority).thenComparingLong(Request::sequence));
        var oldest = pending.isEmpty() ? null : pending.values().iterator().next();
        boolean aged = oldest != null && level.getGameTime() - oldest.submittedAt >= 40;
        if (oldest != null && (aged || level.getGameTime() % 4 == 0)) {
            batch.remove(oldest);
            batch.add(0, oldest);
        }
        for (var request : batch) {
            if (lastExecuted >= ModConfigs.COMMON.maxNewPathRequestsPerTick.get()) break;
            var citizen = request.citizen;
            if (pending.get(citizen.getUUID()) != request) continue;
            if (citizen.isRemoved() || !citizen.isAlive() || citizen.isNoAi() || citizen.level() != level || !request.valid.getAsBoolean()) {
                pending.remove(citizen.getUUID());
                continue;
            }
            if (request.priority == Priority.AUTONOMY
                    && CitizenActivityScheduler.get(level).state(citizen) == CitizenActivityScheduler.State.THROTTLED
                    && !(aged && request == oldest)) continue;
            pending.remove(citizen.getUUID());
            lastExecuted++;
            request.action.run();
        }
        CitizenRouteManager.get(level).planner().finishTick();
        lastNanos = System.nanoTime() - started + CitizenRouteManager.get(level).planner().lastStatistics().nanos();
    }
}
