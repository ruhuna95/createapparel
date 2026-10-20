package com.rexhub.createapparel.citizen.population;

import java.util.*;

public final class PopulationRequest {
    private record Step(UUID gauge, int depth, @javax.annotation.Nullable Step parent) { }
    private final UUID id = UUID.randomUUID();
    private final UUID requester;
    private final long createdAt;
    private long retryAt;
    private long lastAttemptAt = Long.MIN_VALUE;
    private final Set<UUID> visited = new LinkedHashSet<>();
    public PopulationRequest(UUID requester, long time) { this.requester = requester; createdAt = time; }
    public UUID id() { return id; }
    public UUID requester() { return requester; }
    public long createdAt() { return createdAt; }
    public long retryAt() { return retryAt; }
    public void retryAt(long time) { retryAt = time; }
    public long lastAttemptAt() { return lastAttemptAt; }
    public void attempted(long now, int retryTicks) { defer(now, now + retryTicks); }
    public void defer(long now, long until) { lastAttemptAt = now; retryAt(until); }
    public static Comparator<PopulationRequest> schedulingOrder(java.util.function.ToDoubleFunction<PopulationRequest> ratio) {
        return Comparator.comparingLong(PopulationRequest::lastAttemptAt).thenComparingLong(PopulationRequest::createdAt).thenComparingDouble(ratio);
    }
    public Set<UUID> visited() { return Collections.unmodifiableSet(visited); }

    public List<UUID> findSupply(CitizenPopulationSavedData data, int maxHops, int maxNodes,
                                java.util.function.Predicate<CitizenGauge> donor) {
        return findSupply(data, maxHops, maxNodes, donor, false);
    }
    public List<UUID> findSupply(CitizenPopulationSavedData data, int maxHops, int maxNodes,
                                java.util.function.Predicate<CitizenGauge> donor, boolean preferExisting) {
        visited.clear(); visited.add(requester);
        List<UUID> sourceFallback = List.of();
        var queue = new ArrayDeque<Step>(); queue.add(new Step(requester, 0, null));
        while (!queue.isEmpty() && visited.size() <= maxNodes) {
            var step = queue.removeFirst();
            if (step.depth >= maxHops) continue;
            for (var id : data.linked(step.gauge)) {
                if (visited.size() >= maxNodes || !visited.add(id)) continue;
                var gauge = data.gauge(id);
                if (gauge == null) continue;
                var next = new Step(id, step.depth + 1, step);
                if (donor.test(gauge)) {
                    if (!preferExisting || !gauge.influx()) return path(next);
                    if (sourceFallback.isEmpty()) sourceFallback = path(next);
                }
                if (!gauge.source()) queue.addLast(next);
            }
        }
        return sourceFallback;
    }
    private static List<UUID> path(Step end) {
        var path = new ArrayList<UUID>();
        for (var step = end; step != null; step = step.parent) path.add(step.gauge);
        return List.copyOf(path);
    }
    public static boolean improves(CitizenGauge donor, int population, CitizenGauge requester, int requestedPopulation) {
        if (requester.source() || requester.target() == 0 || donor.outflow()) return false;
        if (donor.influx()) return true;
        if (population <= 0) return false;
        if (donor.target() == 0) return true;
        long difference = (long) population * requester.target() - (long) requestedPopulation * donor.target();
        return difference > 0 && Math.abs(difference - requester.target() - donor.target()) < difference;
    }
}
