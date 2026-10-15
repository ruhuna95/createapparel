package com.rexhub.createapparel.citizen.route;

import java.util.*;

public final class EdgeRuntimeState {
    private record Failure(Set<UUID> citizens, long expires, long version) { }
    private final Map<UUID, Map<UUID, Long>> local = new HashMap<>();
    private final Map<UUID, Failure> failures = new HashMap<>();
    private final Map<UUID, Long> shared = new HashMap<>();
    private long version = -1;
    private long revision;
    private long updatedAt = Long.MIN_VALUE;

    public long revision() { return revision; }

    public void update(long graphVersion, long time) {
        if (version != graphVersion) { clear(); version = graphVersion; }
        if (updatedAt == time) return;
        updatedAt = time;
        boolean expired = shared.values().removeIf(until -> until <= time);
        for (var blocked : local.values()) expired |= blocked.values().removeIf(until -> until <= time);
        local.values().removeIf(Map::isEmpty);
        failures.values().removeIf(failure -> failure.expires <= time);
        if (expired) revision++;
    }

    public boolean blocked(UUID citizen, UUID edge, long time) {
        return shared.getOrDefault(edge, 0L) > time || local.getOrDefault(citizen, Map.of()).getOrDefault(edge, 0L) > time;
    }

    public boolean knownFailed(UUID edge, long time) {
        var failure = failures.get(edge);
        return failure != null && failure.expires > time;
    }

    public void failed(UUID citizen, UUID edge, long time, long graphVersion) {
        update(graphVersion, time);
        local.computeIfAbsent(citizen, ignored -> new HashMap<>()).put(edge, time + RouteSettings.TEMPORARY_BLOCK_TICKS);
        var failure = failures.computeIfAbsent(edge, ignored -> new Failure(new HashSet<>(), time + RouteSettings.TEMPORARY_BLOCK_TICKS, graphVersion));
        failure.citizens.add(citizen);
        if (failure.citizens.size() >= 3) shared.put(edge, time + RouteSettings.TEMPORARY_BLOCK_TICKS);
        revision++;
    }

    public void blockLocal(UUID citizen, UUID edge, long time) {
        local.computeIfAbsent(citizen, ignored -> new HashMap<>()).put(edge, time + RouteSettings.TEMPORARY_BLOCK_TICKS);
        revision++;
    }

    public void succeeded(UUID citizen, UUID edge) {
        var blocked = local.get(citizen);
        if (blocked != null && blocked.remove(edge) != null) revision++;
        failures.remove(edge);
        if (shared.remove(edge) != null) revision++;
    }

    public void clearLocal(UUID citizen, UUID edge) {
        var blocked = local.get(citizen);
        if (blocked != null && blocked.remove(edge) != null) revision++;
    }

    public void clear() { local.clear(); failures.clear(); shared.clear(); revision++; }
}
