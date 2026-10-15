package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.network.RouteViewPacket;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public record RouteViewTransfer(List<RouteViewPacket.Node> nodes, List<RouteViewPacket.Edge> edges,
                                List<UUID> removedNodes, List<UUID> removedEdges, long base) {
    public RouteViewTransfer {
        nodes = List.copyOf(nodes); edges = List.copyOf(edges);
        removedNodes = List.copyOf(removedNodes); removedEdges = List.copyOf(removedEdges);
    }
    public static RouteViewTransfer between(List<RouteViewPacket.Node> beforeNodes, List<RouteViewPacket.Edge> beforeEdges,
                                           List<RouteViewPacket.Node> nodes, List<RouteViewPacket.Edge> edges, long base) {
        var previousNodes = index(beforeNodes, RouteViewPacket.Node::id);
        var previousEdges = index(beforeEdges, RouteViewPacket.Edge::id);
        var updates = nodes.stream().filter(node -> !node.equals(previousNodes.remove(node.id()))).toList();
        var links = edges.stream().filter(edge -> !edge.equals(previousEdges.remove(edge.id()))).toList();
        return new RouteViewTransfer(updates, links, List.copyOf(previousNodes.keySet()), List.copyOf(previousEdges.keySet()), base);
    }

    private static <T> Map<UUID, T> index(List<T> values, Function<T, UUID> id) {
        var result = new LinkedHashMap<UUID, T>();
        for (var value : values) result.put(id.apply(value), value);
        return result;
    }

    public int parts() {
        int size = Math.max(Math.max(nodes.size(), edges.size()), Math.max(removedNodes.size(), removedEdges.size()));
        return Math.max(1, (size + RouteSettings.VIEW_BATCH_SIZE - 1) / RouteSettings.VIEW_BATCH_SIZE);
    }

    public RouteViewPacket packet(ResourceLocation dimension, long snapshot, int part, @Nullable UUID selected) {
        return new RouteViewPacket(dimension, snapshot, part, part == parts() - 1, selected,
                slice(nodes, part), slice(edges, part), base, slice(removedNodes, part), slice(removedEdges, part));
    }

    private static <T> List<T> slice(List<T> values, int part) {
        int start = part * RouteSettings.VIEW_BATCH_SIZE;
        return values.subList(Math.min(start, values.size()), Math.min(start + RouteSettings.VIEW_BATCH_SIZE, values.size()));
    }

    public static final class Receiver {
        private Map<UUID, RouteViewPacket.Node> nodes = Map.of(), stagedNodes;
        private Map<UUID, RouteViewPacket.Edge> edges = Map.of(), stagedEdges;
        private long committed = -1, incoming = -1, base = -1;
        private int nextPart;
        private boolean needsFull;

        public List<RouteViewPacket.Node> nodes() { return List.copyOf(nodes.values()); }
        public List<RouteViewPacket.Edge> edges() { return List.copyOf(edges.values()); }
        public long snapshot() { return committed; }
        public boolean needsFull() { return needsFull; }

        public boolean accept(RouteViewPacket packet) {
            if (packet.snapshot() <= committed || packet.snapshot() < incoming) return false;
            if (packet.part() == 0) {
                if (packet.baseSnapshot() != -1 && packet.baseSnapshot() != committed) { needsFull = true; return false; }
                incoming = packet.snapshot(); base = packet.baseSnapshot(); nextPart = 0;
                stagedNodes = base == -1 ? new LinkedHashMap<>() : new LinkedHashMap<>(nodes);
                stagedEdges = base == -1 ? new LinkedHashMap<>() : new LinkedHashMap<>(edges);
            }
            if (stagedNodes == null || packet.snapshot() != incoming || packet.baseSnapshot() != base || packet.part() != nextPart) return false;
            for (var id : packet.removedNodes()) stagedNodes.remove(id);
            for (var id : packet.removedEdges()) stagedEdges.remove(id);
            for (var node : packet.nodes()) stagedNodes.put(node.id(), node);
            for (var edge : packet.edges()) stagedEdges.put(edge.id(), edge);
            if (stagedNodes.size() > RouteSettings.MAX_VIEW_NODES * (packet.last() ? 1 : 2)
                    || stagedEdges.size() > RouteSettings.MAX_VIEW_EDGES * (packet.last() ? 1 : 2)) {
                stagedNodes = null; stagedEdges = null; needsFull = true; return false;
            }
            nextPart++;
            if (!packet.last()) return false;
            nodes = stagedNodes; edges = stagedEdges; stagedNodes = null; stagedEdges = null;
            committed = incoming; needsFull = false;
            return true;
        }
    }
}
