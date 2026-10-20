package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

public final class CitizenActivityScheduler {
    public enum State { ACTIVE, THROTTLED }
    public enum Reason { PLAYER, GAUGE_TRANSFER, STUCKED, RETURN_HOME, INTERACTION }
    private static final Map<ServerLevel, CitizenActivityScheduler> LEVELS = new IdentityHashMap<>();
    private final Map<UUID, Citizen> citizens = new LinkedHashMap<>();
    private final LinkedHashSet<UUID> rotation = new LinkedHashSet<>();
    private final Set<UUID> active = new LinkedHashSet<>();
    private final Map<UUID, Long> forced = new HashMap<>();
    private final TreeMap<Long, Set<UUID>> expirations = new TreeMap<>();
    private final ServerLevel level;
    private long nextRotation;

    private CitizenActivityScheduler(ServerLevel level) { this.level = level; }
    public static CitizenActivityScheduler get(ServerLevel level) { return LEVELS.computeIfAbsent(level, CitizenActivityScheduler::new); }
    public static void unload(ServerLevel level) { LEVELS.remove(level); }
    public static void clear() { LEVELS.clear(); }

    public void register(Citizen citizen) {
        if (citizens.put(citizen.getUUID(), citizen) == null) rotation.add(citizen.getUUID());
        if (active.size() < ModConfigs.COMMON.maxActiveCitizensPerDimension.get()) active.add(citizen.getUUID());
    }

    public void remove(UUID id) { citizens.remove(id); rotation.remove(id); active.remove(id); removeForced(id); }
    private void removeForced(UUID id) {
        var until = forced.remove(id); if (until == null) return;
        var ids = expirations.get(until);
        if (ids != null) { ids.remove(id); if (ids.isEmpty()) expirations.remove(until); }
    }
    public int loadedCitizenCount() { return citizens.size(); }
    public int activeCount() { return (int) citizens.values().stream().filter(citizen -> state(citizen) == State.ACTIVE).count(); }

    public State state(Citizen citizen) {
        return active.contains(citizen.getUUID()) || forced.getOrDefault(citizen.getUUID(), 0L) > level.getGameTime() ? State.ACTIVE : State.THROTTLED;
    }

    public void forceActive(Citizen citizen, Reason reason, int ticks) {
        register(citizen);
        long until = level.getGameTime() + Math.max(1, ticks);
        if (until <= forced.getOrDefault(citizen.getUUID(), Long.MIN_VALUE)) return;
        removeForced(citizen.getUUID()); forced.put(citizen.getUUID(), until);
        expirations.computeIfAbsent(until, ignored -> new HashSet<>()).add(citizen.getUUID());
    }

    public void tick() {
        int budget = ModConfigs.COMMON.maxActiveCitizensPerDimension.get();
        for (int index = 0; index < 64 && !expirations.isEmpty() && expirations.firstKey() <= level.getGameTime(); index++) {
            var ids = expirations.firstEntry().getValue(); removeForced(ids.iterator().next());
        }
        while (active.size() > budget) active.remove(active.iterator().next());
        if (level.getGameTime() < nextRotation) return;
        nextRotation = level.getGameTime() + Math.max(1, ModConfigs.COMMON.activityRotationTicks.get() / budget);
        if (citizens.size() <= budget) { active.addAll(citizens.keySet()); return; }
        UUID outgoing = null;
        for (var id : rotation) if (active.contains(id)) { outgoing = id; break; }
        if (outgoing != null) { active.remove(outgoing); rotation.remove(outgoing); rotation.add(outgoing); }
        for (var id : rotation) {
            if (active.size() >= budget) break;
            active.add(id);
        }
    }
}
