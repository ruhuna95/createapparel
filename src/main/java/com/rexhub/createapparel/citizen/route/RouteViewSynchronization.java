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
        long version = -1;
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
    }

    private RouteViewSynchronization() { }
    public static void logout(UUID id) { VIEWS.remove(id); }
    public static void clear() { VIEWS.clear(); }
    public static void request(ServerPlayer player, int radius) {
        if (!RouteMarkerPermissions.holdingPhone(player)) return;
        var view = VIEWS.computeIfAbsent(player.getUUID(), ignored -> new View());
        if (view.radius != radius) { view.radius = radius; view.version = -1; }
    }

    public static void tick(ServerLevel level) {
        for (var player : level.players()) {
            if (!RouteMarkerPermissions.holdingPhone(player)) {
                if (VIEWS.remove(player.getUUID()) != null) ModNetwork.sendRouteView(player,
                        new RouteViewPacket(level.dimension().location(), ++sequence, 0, true, null, List.of(), List.of()));
                continue;
            }
            var manager = CitizenRouteManager.get(level);
            var view = VIEWS.computeIfAbsent(player.getUUID(), ignored -> new View());
            var selected = SmartPhoneRouteEditor.selected(player);
            if (level.getGameTime() >= view.nextRefresh) {
                boolean changed = view.version != manager.data().version() || view.loaded != manager.loadRevision()
                        || view.runtime != manager.runtime().revision() || view.maintenance != manager.maintenanceRevision()
                        || !view.dimension.equals(level.dimension().location().toString()) || view.center.distSqr(player.blockPosition()) >= 16
                        || !Objects.equals(selected, view.selected) || conditionsChanged(view, manager);
                if (changed && view.part >= view.parts) rebuild(view, player, manager, selected);
                view.nextRefresh = level.getGameTime() + 5;
            }
            if (view.part < view.parts) {
                int start = view.part * RouteSettings.VIEW_BATCH_SIZE;
                ModNetwork.sendRouteView(player, new RouteViewPacket(level.dimension().location(), view.snapshot, view.part,
                        view.part == view.parts - 1, view.selected,
                        slice(view.nodes, start), slice(view.edges, start)));
                view.part++;
            }
        }
    }

    private static <T> List<T> slice(List<T> values, int start) {
        return values.subList(Math.min(start, values.size()), Math.min(start + RouteSettings.VIEW_BATCH_SIZE, values.size()));
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

    private static void rebuild(View view, ServerPlayer player, CitizenRouteManager manager, UUID selected) {
        var nearby = manager.nearby(player.position(), view.radius + 4).stream().limit(RouteSettings.MAX_VIEW_NODES).toList();
        view.nodes = nearby.stream().map(node -> {
            int signal = node.type() == RouteMarkerType.CROSSING ? node.condition().signalStrength(player.serverLevel()) : -1;
            return new RouteViewPacket.Node(node.id(), node.pos(), node.type(), node.horizontalRadius(), node.verticalTolerance(),
                    node.type() != RouteMarkerType.GATEWAY || manager.gatewayEnabled(node), node.type() == RouteMarkerType.CROSSING && node.condition().configured() ? node.condition().pos() : null,
                    node.condition().active(signal), signal > 0);
        }).toList();
        var edges = new LinkedHashMap<UUID, RouteViewPacket.Edge>();
        for (var node : nearby) {
            for (var edge : manager.data().connected(node.id())) {
                if (edges.size() >= RouteSettings.MAX_VIEW_EDGES) break;
                var a = manager.data().marker(edge.a()); var b = manager.data().marker(edge.b());
                if (a != null && b != null) edges.putIfAbsent(edge.id(),
                        new RouteViewPacket.Edge(edge.id(), a.pos(), b.pos(), edge.conditional(), manager.crossingOpen(edge),
                                !manager.loaded(a) || !manager.loaded(b) ? RouteViewPacket.Health.UNLOADED
                                        : manager.runtime().knownFailed(edge.id(), player.level().getGameTime()) ? RouteViewPacket.Health.BLOCKED : RouteViewPacket.Health.NORMAL));
            }
        }
        var liveEndpoints = new HashSet<Endpoints>();
        for (var edge : edges.values()) liveEndpoints.add(Endpoints.of(edge));
        for (var removed : manager.removedEdges()) if (edges.size() < RouteSettings.MAX_VIEW_EDGES && !liveEndpoints.contains(Endpoints.of(removed))
                && Math.min(removed.a().distSqr(player.blockPosition()), removed.b().distSqr(player.blockPosition())) <= (double) view.radius * view.radius)
            edges.putIfAbsent(removed.id(), removed);
        view.edges = List.copyOf(edges.values());
        view.center = player.blockPosition();
        view.dimension = player.level().dimension().location().toString();
        view.version = manager.data().version(); view.loaded = manager.loadRevision(); view.selected = selected;
        view.runtime = manager.runtime().revision(); view.maintenance = manager.maintenanceRevision();
        view.snapshot = ++sequence;
        view.part = view.conditionCursor = view.nodeConditionCursor = 0;
        view.parts = Math.max(1, (Math.max(view.nodes.size(), view.edges.size()) + RouteSettings.VIEW_BATCH_SIZE - 1) / RouteSettings.VIEW_BATCH_SIZE);
    }
}
