package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.network.RouteViewPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class RouteViewSynchronization {
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static long sequence;
    private record SharedKey(ServerLevel level, BlockPos center, int radius, long region, long loaded, long runtime, long maintenance, long time) { }
    private record Snapshot(List<RouteViewPacket.Node> nodes, List<RouteViewPacket.Edge> edges) { }
    private static final Map<SharedKey, Snapshot> SHARED = new LinkedHashMap<>();
    private record DeltaKey(Snapshot before, Snapshot after) {
        @Override public boolean equals(Object other) { return other instanceof DeltaKey key && before == key.before && after == key.after; }
        @Override public int hashCode() { return 31 * System.identityHashCode(before) + System.identityHashCode(after); }
    }
    private static final Map<DeltaKey, RouteViewTransfer> SHARED_DELTAS = new LinkedHashMap<>();
    private static final Map<ServerLevel, CaptureBudget> BUDGETS = new IdentityHashMap<>();
    public record Statistics(int captures, int waiting) { }
    private static final class CaptureBudget {
        long tick = Long.MIN_VALUE;
        int captures;
        final Set<UUID> waiting = new LinkedHashSet<>();
        void refresh(long time) { if (tick != time) { tick = time; captures = 0; } }
        boolean reserve(UUID player) {
            waiting.add(player);
            if (captures >= 2 || !waiting.iterator().next().equals(player)) return false;
            waiting.remove(player); captures++; return true;
        }
    }
    private record Endpoints(long first, long second) {
        static Endpoints of(RouteViewPacket.Edge edge) {
            long a = edge.a().asLong(), b = edge.b().asLong();
            return new Endpoints(Math.min(a, b), Math.max(a, b));
        }
    }
    private static final class View {
        int radius = 64;
        BlockPos center = BlockPos.ZERO;
        String dimension = "";
        long region = -1;
        long loaded = -1;
        long runtime = -1, maintenance = -1;
        long snapshot;
        UUID selected;
        List<RouteViewPacket.Node> nodes = List.of();
        List<RouteViewPacket.Edge> edges = List.of();
        int part;
        int parts;
        int conditionCursor;
        int nodeConditionCursor;
        long nextRefresh;
        boolean full = true;
        RouteViewTransfer transfer;
        Snapshot contents;
    }

    private RouteViewSynchronization() { }
    public static void logout(UUID id) { VIEWS.remove(id); for (var budget : BUDGETS.values()) budget.waiting.remove(id); }
    public static void unload(ServerLevel level) { SHARED.keySet().removeIf(key -> key.level == level); SHARED_DELTAS.clear(); BUDGETS.remove(level); }
    public static void clear() { VIEWS.clear(); SHARED.clear(); SHARED_DELTAS.clear(); BUDGETS.clear(); }
    public static Statistics statistics(ServerLevel level) {
        var budget = budget(level); return new Statistics(budget.captures, budget.waiting.size());
    }
    private static CaptureBudget budget(ServerLevel level) {
        var budget = BUDGETS.computeIfAbsent(level, ignored -> new CaptureBudget());
        budget.refresh(level.getGameTime()); return budget;
    }
    public static void request(ServerPlayer player, int radius) {
        if (!RouteMarkerPermissions.holdingPhone(player)) return;
        var view = VIEWS.computeIfAbsent(player.getUUID(), ignored -> new View());
        view.radius = radius; view.region = -1; view.full = true;
    }

    public static void tick(ServerLevel level) {
        for (var player : level.players()) update(player, packet -> ModNetwork.sendRouteView(player, packet));
    }

    public static void update(ServerPlayer player, java.util.function.Consumer<RouteViewPacket> send) {
        var level = player.serverLevel();
        if (!RouteMarkerPermissions.holdingPhone(player)) {
            if (VIEWS.remove(player.getUUID()) != null) send.accept(
                    new RouteViewPacket(level.dimension().location(), ++sequence, 0, true, null, List.of(), List.of()));
            budget(level).waiting.remove(player.getUUID());
            return;
        }
        var manager = CitizenRouteManager.get(level);
        var view = VIEWS.computeIfAbsent(player.getUUID(), ignored -> new View());
        var selected = SmartPhoneRouteEditor.selected(player);
        if (level.getGameTime() >= view.nextRefresh) {
            boolean changed = view.region != manager.data().viewRevision(view.center, view.radius + 4) || view.loaded != manager.loadRevision()
                    || view.runtime != manager.runtime().viewRevision() || view.maintenance != manager.maintenanceRevision()
                    || !view.dimension.equals(level.dimension().location().toString()) || view.center.distSqr(player.blockPosition()) >= 16
                    || !Objects.equals(selected, view.selected) || conditionsChanged(view, manager);
            if (!view.dimension.equals(level.dimension().location().toString())) { view.full = true; view.part = view.parts; }
            boolean deferred = changed && view.part >= view.parts && !rebuild(view, player, manager, selected);
            if (!changed) budget(level).waiting.remove(player.getUUID());
            view.nextRefresh = level.getGameTime() + (deferred ? 1 : 5);
        }
        if (view.part < view.parts) {
            send.accept(view.transfer.packet(level.dimension().location(), view.snapshot, view.part, view.selected));
            view.part++;
        }
    }

    private static boolean conditionsChanged(View view, CitizenRouteManager manager) {
        if (view.part < view.parts) return false;
        for (int i = 0; i < Math.min(view.nodes.size(), 256); i++) {
            var previous = view.nodes.get(view.nodeConditionCursor);
            view.nodeConditionCursor = (view.nodeConditionCursor + 1) % view.nodes.size();
            if (previous.type() != RouteMarkerType.CROSSING) continue;
            var current = manager.data().marker(previous.id());
            if (current == null) return true;
            int signal = current.condition().signalStrength(manager.level());
            if (current.condition().active(signal) != previous.on() || (signal > 0) != previous.powered()) return true;
        }
        int checked = Math.min(view.edges.size(), 256);
        for (int i = 0; i < checked; i++) {
            var edge = view.edges.get(view.conditionCursor);
            view.conditionCursor = (view.conditionCursor + 1) % view.edges.size();
            if (!edge.conditional()) continue;
            var current = manager.data().edge(edge.id());
            if (current == null || manager.crossingOpen(current) != edge.open()) return true;
        }
        return false;
    }

    private static boolean rebuild(View view, ServerPlayer player, CitizenRouteManager manager, UUID selected) {
        var center = player.blockPosition();
        var key = new SharedKey(manager.level(), center, view.radius, manager.data().viewRevision(center, view.radius + 4),
                manager.loadRevision(), manager.runtime().viewRevision(), manager.maintenanceRevision(), manager.level().getGameTime());
        var shared = SHARED.get(key);
        if (shared == null) {
            if (!budget(manager.level()).reserve(player.getUUID())) return false;
            shared = capture(manager, center, view.radius);
            if (SHARED.size() >= 32) SHARED.remove(SHARED.keySet().iterator().next());
            SHARED.put(key, shared);
        }
        budget(manager.level()).waiting.remove(player.getUUID());
        if (view.full) view.transfer = new RouteViewTransfer(shared.nodes, shared.edges, List.of(), List.of(), -1);
        else {
            var deltaKey = new DeltaKey(view.contents, shared);
            var delta = SHARED_DELTAS.get(deltaKey);
            if (delta == null) {
                delta = RouteViewTransfer.between(view.nodes, view.edges, shared.nodes, shared.edges, -1);
                if (SHARED_DELTAS.size() >= 32) SHARED_DELTAS.remove(SHARED_DELTAS.keySet().iterator().next());
                SHARED_DELTAS.put(deltaKey, delta);
            }
            view.transfer = new RouteViewTransfer(delta.nodes(), delta.edges(), delta.removedNodes(), delta.removedEdges(), view.snapshot);
        }
        view.contents = shared;
        view.nodes = shared.nodes; view.edges = shared.edges;
        view.full = false;
        view.center = center;
        view.dimension = player.level().dimension().location().toString();
        view.region = key.region; view.loaded = manager.loadRevision(); view.selected = selected;
        view.runtime = manager.runtime().viewRevision(); view.maintenance = manager.maintenanceRevision();
        view.snapshot = ++sequence;
        view.part = view.conditionCursor = view.nodeConditionCursor = 0;
        view.parts = view.transfer.parts();
        return true;
    }

    private static Snapshot capture(CitizenRouteManager manager, BlockPos center, int radius) {
        var nearby = manager.nearby(net.minecraft.world.phys.Vec3.atCenterOf(center), radius + 4).stream().limit(RouteSettings.MAX_VIEW_NODES).toList();
        var nodes = nearby.stream().map(node -> {
            int signal = node.type() == RouteMarkerType.CROSSING ? node.condition().signalStrength(manager.level()) : -1;
            return new RouteViewPacket.Node(node.id(), node.pos(), node.type(), node.horizontalRadius(), node.verticalTolerance(),
                    node.type() != RouteMarkerType.GATEWAY || manager.gatewayEnabled(node), node.type() == RouteMarkerType.CROSSING && node.condition().configured() ? node.condition().pos() : null,
                    node.condition().active(signal), signal > 0);
        }).toList();
        var edges = new LinkedHashMap<UUID, RouteViewPacket.Edge>();
        for (var node : nearby) {
            var adjacent = manager.data().connections(node.id());
            while (edges.size() < RouteSettings.MAX_VIEW_EDGES && adjacent.hasNext()) {
                var edge = adjacent.next();
                var a = manager.data().marker(edge.a()); var b = manager.data().marker(edge.b());
                if (a != null && b != null) edges.putIfAbsent(edge.id(),
                        new RouteViewPacket.Edge(edge.id(), a.pos(), b.pos(), edge.conditional(), manager.crossingOpen(edge),
                                !manager.loaded(a) || !manager.loaded(b) ? RouteViewPacket.Health.UNLOADED
                                        : manager.runtime().knownFailed(edge.id(), manager.level().getGameTime()) ? RouteViewPacket.Health.BLOCKED : RouteViewPacket.Health.NORMAL));
            }
            if (edges.size() >= RouteSettings.MAX_VIEW_EDGES) break;
        }
        var liveEndpoints = new HashSet<Endpoints>();
        for (var edge : edges.values()) liveEndpoints.add(Endpoints.of(edge));
        for (var removed : manager.removedEdges()) if (edges.size() < RouteSettings.MAX_VIEW_EDGES && !liveEndpoints.contains(Endpoints.of(removed))
                && Math.min(removed.a().distSqr(center), removed.b().distSqr(center)) <= (double) radius * radius)
            edges.putIfAbsent(removed.id(), removed);
        return new Snapshot(nodes, List.copyOf(edges.values()));
    }
}
