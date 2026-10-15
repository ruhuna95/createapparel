package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.*;

public final class CitizenRoutePlanner {
    public record Plan(List<UUID> nodes, List<UUID> edges, boolean fallback, boolean deferred) {
        public Plan { nodes = List.copyOf(nodes); edges = List.copyOf(edges); }
        public static Plan vanilla() { return new Plan(List.of(), List.of(), true, false); }
        public static Plan pending() { return new Plan(List.of(), List.of(), false, true); }
    }
    public record Statistics(int searches, int visitedNodes, int scannedEdges, int queued, int active, long nanos) { }
    private record Endpoint(UUID id, double cost) { }
    private record Key(UUID citizen, List<Endpoint> starts, List<Endpoint> targets, long version, long loaded,
                       @Nullable UUID avoided, boolean openOnly) { }
    private record Visit(UUID marker, double cost) { }
    private record Cached(Plan plan, long expires, long runtime, long conditions, boolean delivered) { }
    private static final class Search {
        final Citizen citizen;
        final Key key;
        final PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::cost));
        final Map<UUID, Double> costs = new HashMap<>();
        final Map<UUID, UUID> previous = new HashMap<>(), via = new HashMap<>();
        final Map<UUID, Double> goals = new HashMap<>();
        boolean initialized;
        @Nullable UUID bestGoal;
        double bestCost = Double.POSITIVE_INFINITY;
        @Nullable Visit expanding;
        Iterator<RouteEdge> adjacent = Collections.emptyIterator();
        boolean finishing;
        @Nullable UUID tracing;
        final List<UUID> resultNodes = new ArrayList<>(), resultEdges = new ArrayList<>();
        Search(Citizen citizen, Key key) { this.citizen = citizen; this.key = key; }
    }
    private final CitizenRouteManager manager;
    private final Map<Key, Cached> cache = new LinkedHashMap<>();
    private final LinkedHashMap<Key, Search> pending = new LinkedHashMap<>();
    private final Map<UUID, Key> requests = new HashMap<>();
    private long budgetTick = Long.MIN_VALUE;
    private int searches, visitedNodes, scannedEdges, active;
    private long nanos;
    private Statistics lastStatistics = new Statistics(0, 0, 0, 0, 0, 0);

    public CitizenRoutePlanner(CitizenRouteManager manager) { this.manager = manager; }
    public void clear() { cache.clear(); pending.clear(); requests.clear(); active = 0; }
    public void cancel(Citizen citizen) {
        var key = requests.remove(citizen.getUUID());
        var search = key == null ? null : pending.remove(key);
        if (search != null && search.initialized) active--;
    }
    public Statistics statistics() { refreshBudget(); return new Statistics(searches, visitedNodes, scannedEdges, pending.size(), active, nanos); }
    public Statistics lastStatistics() { return lastStatistics; }
    public void finishTick() { lastStatistics = statistics(); }

    public Plan plan(Citizen citizen, Vec3 destination) { return plan(citizen, destination, Set.of()); }

    public Plan plan(Citizen citizen, Vec3 destination, Set<UUID> excludedMarkers) {
        return plan(citizen, destination, excludedMarkers, null);
    }

    public Plan plan(Citizen citizen, Vec3 destination, Set<UUID> excludedMarkers, @Nullable UUID avoided) {
        var starts = candidates(citizen.position(), excludedMarkers).stream()
                .map(node -> new Endpoint(node.id(), node.center().distanceTo(citizen.position()))).toList();
        var targets = candidates(destination, excludedMarkers).stream()
                .map(node -> new Endpoint(node.id(), node.center().distanceTo(destination))).toList();
        if (starts.isEmpty() || targets.isEmpty()) { cancel(citizen); return Plan.vanilla(); }
        return query(citizen, starts, targets, avoided, false);
    }

    private List<RouteMarker> candidates(Vec3 point, Set<UUID> excluded) {
        var nearby = manager.nearby(point, RouteSettings.DISCOVERY_RADIUS).stream().filter(node -> !excluded.contains(node.id())).toList();
        if (nearby.isEmpty()) return List.of();
        double closest = nearby.get(0).center().distanceTo(point);
        return nearby.stream().filter(node -> node.center().distanceTo(point) <= closest + 4).limit(4).toList();
    }

    public Plan between(Citizen citizen, UUID start, UUID target) {
        return query(citizen, List.of(new Endpoint(start, 0)), List.of(new Endpoint(target, 0)), null, false);
    }

    public Plan openDetour(Citizen citizen, UUID start, UUID target, UUID avoided) {
        return query(citizen, List.of(new Endpoint(start, 0)), List.of(new Endpoint(target, 0)), avoided, true);
    }

    private Plan query(Citizen citizen, List<Endpoint> starts, List<Endpoint> targets, @Nullable UUID avoided, boolean openOnly) {
        var key = new Key(citizen.getUUID(), starts, targets, manager.data().version(), manager.loadRevision(), avoided, openOnly);
        var previous = requests.get(citizen.getUUID());
        if (previous != null && equivalent(previous, key)) key = previous;
        else if (previous != null) cancel(citizen);
        var result = takeCached(key);
        if (result != null) return result;
        cache.remove(key);
        if (!pending.containsKey(key)) {
            if (pending.size() >= RouteSettings.MAX_PENDING_PLANS) return Plan.pending();
            pending.put(key, new Search(citizen, key));
            requests.put(citizen.getUUID(), key);
        }
        tick();
        result = takeCached(key);
        return result == null ? Plan.pending() : result;
    }

    @Nullable private Plan takeCached(Key key) {
        var cached = cache.get(key);
        if (cached == null || cached.expires <= manager.level().getGameTime()) return null;
        if (cached.delivered && (cached.runtime != manager.runtime().revision() || cached.conditions != manager.conditionRevision())) return null;
        if (!cached.delivered) cache.put(key, new Cached(cached.plan, manager.level().getGameTime() + 40, cached.runtime, cached.conditions, true));
        return cached.plan;
    }

    private static boolean equivalent(Key first, Key second) {
        if (first.version != second.version || first.loaded != second.loaded || first.openOnly != second.openOnly
                || !Objects.equals(first.avoided, second.avoided) || !first.targets.equals(second.targets)
                || first.starts.size() != second.starts.size()) return false;
        for (var start : first.starts) if (second.starts.stream().noneMatch(other -> other.id.equals(start.id) && Math.abs(other.cost - start.cost) <= 1)) return false;
        return true;
    }

    private void refreshBudget() {
        long time = manager.level().getGameTime();
        if (budgetTick != time) { budgetTick = time; searches = visitedNodes = scannedEdges = 0; nanos = 0; }
    }

    public void tick() {
        refreshBudget();
        long started = System.nanoTime();
        int slices = Math.min(64, Math.max(1, pending.size()) * 2);
        while (slices-- > 0 && !pending.isEmpty() && visitedNodes < RouteSettings.PLANNER_NODES_PER_TICK
                && scannedEdges < RouteSettings.PLANNER_EDGES_PER_TICK) {
            var entry = pending.entrySet().iterator().next();
            var key = entry.getKey(); var search = entry.getValue();
            pending.remove(key);
            boolean stale = key.version != manager.data().version() || key.loaded != manager.loadRevision()
                    || search.citizen.isRemoved() || !search.citizen.isAlive() || search.citizen.level() != manager.level();
            var result = stale ? Plan.vanilla() : advance(search);
            if (stale) {
                if (search.initialized) active--;
                requests.remove(key.citizen, key);
            } else if (result == null) pending.put(key, search);
            else {
                if (search.initialized) active--;
                requests.remove(key.citizen, key);
                if (cache.size() >= RouteSettings.MAX_CACHED_ROUTES) cache.remove(cache.keySet().iterator().next());
                cache.put(key, new Cached(result, manager.level().getGameTime() + RouteSettings.RETRY_INTERVAL * 2,
                        manager.runtime().revision(), manager.conditionRevision(), false));
            }
        }
        nanos += System.nanoTime() - started;
    }

    @Nullable private Plan advance(Search search) {
        if (!search.initialized) {
            if (searches >= RouteSettings.PLANNER_SEARCHES_PER_TICK || active >= RouteSettings.MAX_ACTIVE_PLANS) return null;
            searches++; active++; search.initialized = true;
            for (var start : search.key.starts) if (manager.loaded(manager.data().marker(start.id))) {
                search.costs.put(start.id, start.cost); search.queue.add(new Visit(start.id, start.cost));
            }
            for (var target : search.key.targets) if (manager.loaded(manager.data().marker(target.id))) search.goals.put(target.id, target.cost);
        }
        if (search.finishing) return finishSlice(search);
        int nodes = 0, edges = 0;
        while (nodes < 32 && edges < 128 && visitedNodes < RouteSettings.PLANNER_NODES_PER_TICK
                && scannedEdges < RouteSettings.PLANNER_EDGES_PER_TICK) {
            if (search.expanding == null) {
                if (search.queue.isEmpty() || search.queue.peek().cost >= search.bestCost) return finish(search);
                var visit = search.queue.remove(); visitedNodes++; nodes++;
                if (visit.cost > search.costs.getOrDefault(visit.marker, Double.POSITIVE_INFINITY)) continue;
                var terminal = search.goals.get(visit.marker);
                if (terminal != null && visit.cost + terminal < search.bestCost) {
                    search.bestCost = visit.cost + terminal; search.bestGoal = visit.marker;
                }
                search.expanding = visit;
                search.adjacent = manager.data().connections(visit.marker);
            }
            if (!search.adjacent.hasNext()) { search.expanding = null; continue; }
            var edge = search.adjacent.next(); scannedEdges++; edges++;
            var next = manager.data().marker(edge.other(search.expanding.marker));
            if (edge.id().equals(search.key.avoided) || !manager.loaded(next)
                    || manager.runtime().blocked(search.citizen.getUUID(), edge.id(), manager.level().getGameTime())
                    || search.key.openOnly && !manager.crossingOpen(edge)) continue;
            double cost = search.expanding.cost + edgeCost(edge, manager.data().marker(search.expanding.marker), next);
            if (cost >= search.costs.getOrDefault(next.id(), Double.POSITIVE_INFINITY)) continue;
            search.costs.put(next.id(), cost); search.previous.put(next.id(), search.expanding.marker); search.via.put(next.id(), edge.id());
            search.queue.add(new Visit(next.id(), cost));
        }
        return null;
    }

    @Nullable private Plan finish(Search search) {
        if (search.bestGoal == null) return Plan.vanilla();
        search.finishing = true; search.tracing = search.bestGoal;
        return finishSlice(search);
    }

    @Nullable private Plan finishSlice(Search search) {
        int count = 0;
        while (search.tracing != null && count++ < 32 && visitedNodes < RouteSettings.PLANNER_NODES_PER_TICK
                && scannedEdges < RouteSettings.PLANNER_EDGES_PER_TICK) {
            var node = search.tracing; visitedNodes++;
            if (!manager.loaded(manager.data().marker(node))) { restart(search); return null; }
            search.resultNodes.add(node);
            var previous = search.previous.get(node);
            if (previous != null) {
                var id = search.via.get(node); var edge = manager.data().edge(id); scannedEdges++;
                if (edge == null || manager.runtime().blocked(search.citizen.getUUID(), id, manager.level().getGameTime())
                        || search.key.openOnly && !manager.crossingOpen(edge)) { restart(search); return null; }
                search.resultEdges.add(id);
            }
            search.tracing = previous;
        }
        if (search.tracing != null) return null;
        Collections.reverse(search.resultNodes); Collections.reverse(search.resultEdges);
        return new Plan(search.resultNodes, search.resultEdges, false, false);
    }

    private void restart(Search search) {
        active--; search.initialized = search.finishing = false; search.tracing = search.bestGoal = null;
        search.bestCost = Double.POSITIVE_INFINITY; search.expanding = null; search.adjacent = Collections.emptyIterator();
        search.queue.clear(); search.costs.clear(); search.previous.clear(); search.via.clear(); search.goals.clear();
        search.resultNodes.clear(); search.resultEdges.clear();
    }

    public double cost(Plan plan) {
        double cost = 0;
        for (int i = 0; i < plan.edges.size(); i++) {
            var edge = manager.data().edge(plan.edges.get(i));
            if (edge == null) return Double.POSITIVE_INFINITY;
            cost += edgeCost(edge, manager.data().marker(plan.nodes.get(i)), manager.data().marker(plan.nodes.get(i + 1)));
        }
        return cost;
    }

    private double edgeCost(RouteEdge edge, RouteMarker a, RouteMarker b) {
        return Math.max(0.5, a.center().distanceTo(b.center()))
                + (edge.conditional() ? ModConfigs.COMMON.crossingPenalty.get() * (manager.crossingOpen(edge) ? 1 : 2) : 0);
    }
}
