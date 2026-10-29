package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.*;

public final class CitizenRoutePlanner {
    public record Plan(List<UUID> nodes, List<UUID> edges, boolean fallback, boolean deferred, long version, long loaded) {
        public Plan { nodes = List.copyOf(nodes); edges = List.copyOf(edges); }
        public Plan(List<UUID> nodes, List<UUID> edges, boolean fallback, boolean deferred) { this(nodes, edges, fallback, deferred, -1, -1); }
        public static Plan vanilla() { return new Plan(List.of(), List.of(), true, false); }
        public static Plan pending() { return new Plan(List.of(), List.of(), false, true); }
    }
    public record Statistics(int searches, int visitedNodes, int scannedEdges, int queued, int active, long nanos) { }
    private record Endpoint(UUID id, double cost) { }
    private record Key(UUID citizen, List<Endpoint> starts, List<Endpoint> targets, long version, long loaded,
                       @Nullable UUID avoided, boolean openOnly, @Nullable Vec3 partialTarget, double partialLimit, boolean reverse,
                       boolean requireEdges) { }
    private record Visit(UUID marker, double cost) { }
    private record Crossing(UUID edge, boolean open) { }
    private record Cached(Plan plan, long expires, long runtime, long conditions, boolean delivered, boolean validated,
                          List<Crossing> crossings, int probe) { }
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
        @Nullable UUID bestPartial;
        double partialDistance;
        double partialCost = Double.POSITIVE_INFINITY;
        @Nullable Visit expanding;
        Iterator<RouteEdge> adjacent = Collections.emptyIterator();
        boolean finishing;
        @Nullable UUID tracing;
        final List<UUID> resultNodes = new ArrayList<>(), resultEdges = new ArrayList<>();
        final List<Crossing> resultCrossings = new ArrayList<>();
        @Nullable Cached validating;
        int validationCursor;
        long validationRuntime;
        Search(Citizen citizen, Key key) { this.citizen = citizen; this.key = key; partialDistance = key.partialLimit; }
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
        return plan(citizen, destination, excludedMarkers, avoided, Set.of());
    }

    public Plan plan(Citizen citizen, Vec3 destination, Set<UUID> excludedMarkers, @Nullable UUID avoided, Set<UUID> departedMarkers) {
        if (com.rexhub.createapparel.citizen.population.CitizenDiagnostics.debugDisableCitizenRoutePlanning) return Plan.pending();
        try (var diagnosticScope = com.rexhub.createapparel.citizen.population.CitizenSystemMetrics.measure(manager.level(), "routePlan")) {
            var starts = candidates(citizen.position(), excludedMarkers, departedMarkers).stream()
                    .map(node -> new Endpoint(node.id(), node.center().distanceTo(citizen.position()))).toList();
            var targets = candidates(destination, excludedMarkers, departedMarkers).stream()
                    .map(node -> new Endpoint(node.id(), node.center().distanceTo(destination))).toList();
            double limit = citizen.position().distanceTo(destination) - 1;
            if (!starts.isEmpty()) return query(citizen, starts, targets, avoided, false,
                    avoided == null ? destination : null, limit, false, true);
            if (!targets.isEmpty() && avoided == null) return query(citizen, targets, List.of(), null, false,
                    citizen.position(), limit, true, true);
            cancel(citizen); return Plan.vanilla();
        }
    }

    private List<RouteMarker> candidates(Vec3 point, Set<UUID> excluded, Set<UUID> departed) {
        var nearby = manager.nearby(point, RouteSettings.DISCOVERY_RADIUS).stream()
                .filter(node -> !excluded.contains(node.id()) && !departed.contains(node.id())).toList();
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
        return query(citizen, starts, targets, avoided, openOnly, null, 0, false, false);
    }

    private Plan query(Citizen citizen, List<Endpoint> starts, List<Endpoint> targets, @Nullable UUID avoided, boolean openOnly,
                       @Nullable Vec3 partialTarget, double partialLimit, boolean reverse, boolean requireEdges) {
        if (com.rexhub.createapparel.citizen.population.CitizenDiagnostics.debugDisableCitizenRoutePlanning) return Plan.pending();
        try (var diagnosticScope = com.rexhub.createapparel.citizen.population.CitizenSystemMetrics.measure(manager.level(), "routeQuery")) {
            refreshBudget();
            var key = new Key(citizen.getUUID(), starts, targets, manager.data().version(), manager.loadRevision(), avoided, openOnly,
                    partialTarget, partialLimit, reverse, requireEdges);
            var previous = requests.get(citizen.getUUID());
            if (previous != null && equivalent(previous, key)) key = previous;
            else if (previous != null) cancel(citizen);
            var result = takeCached(key);
            if (result != null) return result;
            if (!pending.containsKey(key)) {
                if (pending.size() >= RouteSettings.MAX_PENDING_PLANS) return Plan.pending();
                var search = new Search(citizen, key);
                var cached = cache.remove(key);
                if (cached != null && !cached.delivered && cached.expires > manager.level().getGameTime()) {
                    search.validating = cached;
                    search.validationRuntime = manager.runtime().revision(key.citizen);
                }
                pending.put(key, search);
                requests.put(citizen.getUUID(), key);
            }
            tick();
            result = takeCached(key);
            var completed = cache.get(key);
            if (result == null && completed != null && !completed.validated && !pending.containsKey(key)) {
                cache.remove(key);
                var validation = new Search(citizen, key);
                validation.validating = completed;
                validation.validationRuntime = manager.runtime().revision(key.citizen);
                pending.put(key, validation); requests.put(key.citizen, key);
                tick(); result = takeCached(key);
            }
            return result == null ? Plan.pending() : result;
        }
    }

    @Nullable private Plan takeCached(Key key) {
        var cached = cache.get(key);
        if (cached == null || cached.expires <= manager.level().getGameTime()) return null;
        long conditions = manager.conditionRevision();
        if (!cached.validated || key.version != manager.data().version() || key.loaded != manager.loadRevision()
                || cached.runtime != manager.runtime().revision(key.citizen)
                || (cached.delivered || cached.plan.fallback()) && cached.conditions != conditions) return null;
        if (!cached.crossings.isEmpty()) {
            int probe = cached.probe;
            while (probe < cached.crossings.size() && scannedEdges < RouteSettings.PLANNER_EDGES_PER_TICK) {
                scannedEdges++;
                var crossing = cached.crossings.get(probe++);
                var edge = manager.data().edge(crossing.edge);
                if (edge == null || manager.crossingOpen(edge) != crossing.open || key.openOnly && !crossing.open) { cache.remove(key); return null; }
            }
            if (probe < cached.crossings.size()) {
                cache.put(key, new Cached(cached.plan, cached.expires, cached.runtime, cached.conditions, cached.delivered, true, cached.crossings, probe));
                return Plan.pending();
            }
        }
        cache.put(key, new Cached(cached.plan, cached.delivered ? cached.expires : manager.level().getGameTime() + 40,
                cached.runtime, conditions, true, true, cached.crossings, 0));
        return cached.plan;
    }

    private static boolean equivalent(Key first, Key second) {
        if (first.version != second.version || first.loaded != second.loaded || first.openOnly != second.openOnly
                || first.reverse != second.reverse || first.requireEdges != second.requireEdges || Math.abs(first.partialLimit - second.partialLimit) > 1
                || (first.partialTarget == null) != (second.partialTarget == null)
                || first.partialTarget != null && first.partialTarget.distanceToSqr(second.partialTarget) > 1
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
        if (com.rexhub.createapparel.citizen.population.CitizenDiagnostics.debugDisableCitizenRoutePlanning) return;
        try (var diagnosticScope = com.rexhub.createapparel.citizen.population.CitizenSystemMetrics.measure(manager.level(), "routePlanner")) {
            refreshBudget();
            long started = com.rexhub.createapparel.citizen.population.CitizenSystemMetrics.profiling() ? System.nanoTime() : 0;
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
                            manager.runtime().revision(key.citizen), manager.conditionRevision(), false, search.validating != null,
                            search.validating == null ? List.copyOf(search.resultCrossings) : search.validating.crossings, 0));
                }
            }
            if (started != 0) nanos += System.nanoTime() - started;
        }
    }

    @Nullable private Plan advance(Search search) {
        if (search.validating != null) return validateSlice(search);
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
                if (terminal != null && (!search.key.requireEdges || search.previous.containsKey(visit.marker))
                        && visit.cost + terminal < search.bestCost) {
                    search.bestCost = visit.cost + terminal; search.bestGoal = visit.marker;
                }
                if (search.key.partialTarget != null && search.previous.containsKey(visit.marker)) {
                    double distance = manager.data().marker(visit.marker).center().distanceTo(search.key.partialTarget);
                    if (distance < search.partialDistance - 1.0E-6
                            || search.bestPartial != null && Math.abs(distance - search.partialDistance) <= 1.0E-6 && visit.cost < search.partialCost) {
                        search.bestPartial = visit.marker; search.partialDistance = distance; search.partialCost = visit.cost;
                    }
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
        var goal = search.bestGoal == null ? search.bestPartial : search.bestGoal;
        if (goal == null) return Plan.vanilla();
        search.finishing = true; search.tracing = goal;
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
                if (edge.conditional()) search.resultCrossings.add(new Crossing(id, manager.crossingOpen(edge)));
            }
            search.tracing = previous;
        }
        if (search.tracing != null) return null;
        if (!search.key.reverse) { Collections.reverse(search.resultNodes); Collections.reverse(search.resultEdges); }
        return new Plan(search.resultNodes, search.resultEdges, false, false, search.key.version, search.key.loaded);
    }

    @Nullable private Plan validateSlice(Search search) {
        var cached = Objects.requireNonNull(search.validating);
        if (cached.plan.fallback()) {
            if (cached.runtime == manager.runtime().revision(search.key.citizen) && cached.conditions == manager.conditionRevision()) return cached.plan;
            search.validating = null; return null;
        }
        long runtime = manager.runtime().revision(search.key.citizen);
        if (runtime != search.validationRuntime) {
            search.validationCursor = 0; search.validationRuntime = runtime;
        }
        int count = 0;
        while (search.validationCursor < cached.plan.nodes.size() && count++ < 32
                && visitedNodes < RouteSettings.PLANNER_NODES_PER_TICK && scannedEdges < RouteSettings.PLANNER_EDGES_PER_TICK) {
            int i = search.validationCursor++; visitedNodes++;
            var node = manager.data().marker(cached.plan.nodes.get(i));
            boolean valid = manager.loaded(node);
            if (i > 0) {
                scannedEdges++;
                var edge = manager.data().edge(cached.plan.edges.get(i - 1));
                valid &= edge != null && Objects.equals(edge.other(cached.plan.nodes.get(i - 1)), cached.plan.nodes.get(i))
                        && !edge.id().equals(search.key.avoided) && !manager.runtime().blocked(search.key.citizen, edge.id(), manager.level().getGameTime())
                        && (!search.key.openOnly || manager.crossingOpen(edge));
            }
            if (!valid) { search.validating = null; return null; }
        }
        return search.validationCursor == cached.plan.nodes.size() ? cached.plan : null;
    }

    private void restart(Search search) {
        active--; search.initialized = search.finishing = false; search.tracing = search.bestGoal = null;
        search.bestPartial = null; search.partialDistance = search.key.partialLimit; search.partialCost = Double.POSITIVE_INFINITY;
        search.bestCost = Double.POSITIVE_INFINITY; search.expanding = null; search.adjacent = Collections.emptyIterator();
        search.queue.clear(); search.costs.clear(); search.previous.clear(); search.via.clear(); search.goals.clear();
        search.resultNodes.clear(); search.resultEdges.clear(); search.resultCrossings.clear();
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
        return RouteEdgeCost.distance(a, b)
                + (edge.conditional() ? ModConfigs.COMMON.crossingPenalty.get() * (manager.crossingOpen(edge) ? 1 : 2) : 0);
    }
}
