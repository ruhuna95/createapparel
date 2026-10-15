package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class CitizenRoutePlanner {
    public record Plan(List<UUID> nodes, List<UUID> edges, boolean fallback, boolean deferred) {
        public Plan { nodes = List.copyOf(nodes); edges = List.copyOf(edges); }
        public static Plan vanilla() { return new Plan(List.of(), List.of(), true, false); }
    }
    private record CacheKey(UUID start, UUID target, UUID citizen, long graphVersion, long loadRevision, long runtimeRevision) { }
    private record Visit(UUID marker, double cost) { }
    private final CitizenRouteManager manager;
    private final Map<CacheKey, Plan> cache = new LinkedHashMap<>();
    private final Map<CacheKey, Integer> conditionStates = new HashMap<>();
    private final Map<CacheKey, Long> expiry = new HashMap<>();
    private long searchTick = Long.MIN_VALUE;
    private int searches;

    public CitizenRoutePlanner(CitizenRouteManager manager) { this.manager = manager; }
    public void clear() { cache.clear(); conditionStates.clear(); expiry.clear(); }

    public Plan plan(Citizen citizen, Vec3 destination) {
        return plan(citizen, destination, Set.of());
    }

    public Plan plan(Citizen citizen, Vec3 destination, Set<UUID> excludedMarkers) {
        var starts = candidates(citizen.position(), excludedMarkers);
        var targets = candidates(destination, excludedMarkers);
        if (starts.isEmpty() || targets.isEmpty()) return Plan.vanilla();
        long time = manager.level().getGameTime();
        if (searchTick != time) { searches = 0; searchTick = time; }
        if (searches >= 2) return new Plan(List.of(), List.of(), false, true);
        searches++;
        Plan best = Plan.vanilla();
        double cost = Double.POSITIVE_INFINITY;
        for (var start : starts) for (var target : targets) {
            var route = between(citizen, start.id(), target.id());
            if (route.fallback()) continue;
            double candidate = start.center().distanceTo(citizen.position()) + target.center().distanceTo(destination);
            for (int i = 0; i < route.edges().size(); i++) {
                var edge = manager.data().edge(route.edges().get(i));
                var a = manager.data().marker(route.nodes().get(i));
                var b = manager.data().marker(route.nodes().get(i + 1));
                candidate += edgeCost(edge, a, b);
            }
            if (candidate < cost) { cost = candidate; best = route; }
        }
        return best;
    }

    private List<RouteMarker> candidates(Vec3 point, Set<UUID> excludedMarkers) {
        var nearby = manager.nearby(point, RouteSettings.DISCOVERY_RADIUS).stream().filter(marker -> !excludedMarkers.contains(marker.id())).toList();
        if (nearby.isEmpty()) return List.of();
        double closest = nearby.get(0).center().distanceTo(point);
        return nearby.stream().filter(marker -> marker.center().distanceTo(point) <= closest + 4).limit(4).toList();
    }

    private int conditionState(Plan plan) {
        int hash = 1;
        for (var id : plan.edges()) {
            var edge = manager.data().edge(id);
            hash = 31 * hash + (edge != null && manager.crossingOpen(edge) ? 1 : 0);
        }
        return hash;
    }

    public Plan between(Citizen citizen, UUID start, UUID target) {
        var data = manager.data();
        var runtime = manager.runtime();
        var key = new CacheKey(start, target, citizen.getUUID(), data.version(), manager.loadRevision(), runtime.revision());
        var cached = cache.get(key);
        if (cached != null && expiry.getOrDefault(key, 0L) > manager.level().getGameTime() && cached.nodes().stream().allMatch(id -> manager.loaded(data.marker(id)))
                && cached.edges().stream().noneMatch(id -> runtime.blocked(citizen.getUUID(), id, manager.level().getGameTime()))
                && conditionStates.getOrDefault(key, 0) == conditionState(cached)) return cached;
        if (!manager.loaded(data.marker(start)) || !manager.loaded(data.marker(target))) return Plan.vanilla();
        var queue = new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::cost));
        var costs = new HashMap<UUID, Double>();
        var previous = new HashMap<UUID, UUID>();
        var via = new HashMap<UUID, UUID>();
        costs.put(start, 0.0);
        queue.add(new Visit(start, 0));
        int visited = 0;
        while (!queue.isEmpty() && visited++ < RouteSettings.MAX_PLANNER_NODES) {
            var visit = queue.remove();
            if (visit.cost > costs.getOrDefault(visit.marker, Double.POSITIVE_INFINITY)) continue;
            if (visit.marker.equals(target)) {
                var nodes = new ArrayList<UUID>();
                var edges = new ArrayList<UUID>();
                UUID node = target;
                nodes.add(node);
                while (!node.equals(start)) { edges.add(via.get(node)); node = previous.get(node); nodes.add(node); }
                Collections.reverse(nodes);
                Collections.reverse(edges);
                var plan = new Plan(nodes, edges, false, false);
                if (cache.size() >= RouteSettings.MAX_CACHED_ROUTES) { var oldest = cache.keySet().iterator().next(); cache.remove(oldest); conditionStates.remove(oldest); expiry.remove(oldest); }
                cache.put(key, plan);
                conditionStates.put(key, conditionState(plan));
                expiry.put(key, manager.level().getGameTime() + 40);
                return plan;
            }
            for (var edge : data.connected(visit.marker)) {
                var next = data.marker(edge.other(visit.marker));
                if (!manager.loaded(next) || runtime.blocked(citizen.getUUID(), edge.id(), manager.level().getGameTime())) continue;
                double cost = visit.cost + edgeCost(edge, data.marker(visit.marker), next);
                if (cost >= costs.getOrDefault(next.id(), Double.POSITIVE_INFINITY)) continue;
                costs.put(next.id(), cost);
                previous.put(next.id(), visit.marker);
                via.put(next.id(), edge.id());
                queue.add(new Visit(next.id(), cost));
            }
        }
        return Plan.vanilla();
    }

    private double edgeCost(RouteEdge edge, RouteMarker a, RouteMarker b) {
        return Math.max(0.5, a.center().distanceTo(b.center()))
                + (edge.conditional() ? ModConfigs.COMMON.crossingPenalty.get() * (manager.crossingOpen(edge) ? 1 : 2) : 0);
    }
}
