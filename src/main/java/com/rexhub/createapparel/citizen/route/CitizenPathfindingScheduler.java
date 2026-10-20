package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class CitizenPathfindingScheduler {
    public enum Priority { PLAYER, SAFETY, TRANSFER, ROUTE, AUTONOMY }
    private record Request(Citizen citizen, Priority priority, BooleanSupplier valid, Runnable action, long submittedAt) { }
    private static final Map<ServerLevel, CitizenPathfindingScheduler> LEVELS = new IdentityHashMap<>();
    private final LinkedHashMap<UUID, Request> pending = new LinkedHashMap<>();
    private final EnumMap<Priority, LinkedHashMap<UUID, Request>> queues = new EnumMap<>(Priority.class);
    private final ServerLevel level;
    private int lastExecuted;
    private long lastTick = Long.MIN_VALUE;
    private long lastNanos;

    private CitizenPathfindingScheduler(ServerLevel level) {
        this.level = level; for (var priority : Priority.values()) queues.put(priority, new LinkedHashMap<>());
    }
    public static CitizenPathfindingScheduler get(ServerLevel level) { return LEVELS.computeIfAbsent(level, CitizenPathfindingScheduler::new); }
    public static void unload(ServerLevel level) { LEVELS.remove(level); }
    public static void clear() { LEVELS.clear(); }
    public boolean pending(Citizen citizen) { return pending.containsKey(citizen.getUUID()); }
    public int queuedCount() { return pending.size(); }
    public int lastExecuted() { return lastExecuted; }
    public long lastNanos() { return lastNanos; }

    public boolean submit(Citizen citizen, Priority priority, BooleanSupplier valid, Runnable action) {
        if (citizen.level() != level || citizen.isRemoved()) return false;
        var previous = pending.get(citizen.getUUID());
        if (previous != null && priority.ordinal() >= previous.priority.ordinal()) return false;
        if (previous != null) cancel(citizen);
        var request = new Request(citizen, priority, valid, action, previous == null ? level.getGameTime() : previous.submittedAt);
        pending.put(citizen.getUUID(), request); queues.get(priority).put(citizen.getUUID(), request);
        return true;
    }

    public void cancel(Citizen citizen) {
        var request = pending.remove(citizen.getUUID()); if (request != null) queues.get(request.priority).remove(citizen.getUUID());
    }
    public long oldestWaitTicks() { return pending.isEmpty() ? 0 : level.getGameTime() - pending.values().iterator().next().submittedAt; }
    private Request next(boolean fairness) {
        if (fairness && !pending.isEmpty()) return pending.values().iterator().next();
        for (var queue : queues.values()) if (!queue.isEmpty()) return queue.values().iterator().next();
        return null;
    }

    public void tick() {
        if (lastTick == level.getGameTime()) return;
        lastTick = level.getGameTime();
        CitizenRouteManager.get(level).planner().tick();
        long started = System.nanoTime();
        lastExecuted = 0;
        var oldest = pending.isEmpty() ? null : pending.values().iterator().next();
        boolean aged = oldest != null && level.getGameTime() - oldest.submittedAt >= 40;
        for (int checked = 0, inspections = Math.min(64, pending.size()); checked < inspections; checked++) {
            if (lastExecuted >= ModConfigs.COMMON.maxNewPathRequestsPerTick.get()) break;
            var request = next(checked == 0 && (aged && ModConfigs.COMMON.maxNewPathRequestsPerTick.get() > 1 || level.getGameTime() % 4 == 0));
            if (request == null) break;
            var citizen = request.citizen;
            if (pending.get(citizen.getUUID()) != request) continue;
            if (citizen.isRemoved() || !citizen.isAlive() || citizen.isNoAi() || citizen.level() != level || !request.valid.getAsBoolean()) {
                cancel(citizen);
                continue;
            }
            if (request.priority == Priority.AUTONOMY
                    && CitizenActivityScheduler.get(level).state(citizen) == CitizenActivityScheduler.State.THROTTLED
                    && !(aged && request == oldest)) {
                var queue = queues.get(request.priority); queue.remove(citizen.getUUID()); queue.put(citizen.getUUID(), request); continue;
            }
            cancel(citizen);
            lastExecuted++;
            request.action.run();
        }
        CitizenRouteManager.get(level).planner().finishTick();
        lastNanos = System.nanoTime() - started + CitizenRouteManager.get(level).planner().lastStatistics().nanos();
    }
}
