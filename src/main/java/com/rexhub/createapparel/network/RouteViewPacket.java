package com.rexhub.createapparel.network;

import com.rexhub.createapparel.citizen.route.RouteMarkerType;
import com.rexhub.createapparel.citizen.route.RouteSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import javax.annotation.Nullable;
import java.util.*;

public record RouteViewPacket(ResourceLocation dimension, long snapshot, int part, boolean last,
                              @Nullable UUID selected, List<Node> nodes, List<Edge> edges,
                              long baseSnapshot, List<UUID> removedNodes, List<UUID> removedEdges) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<RouteViewPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "route_view_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, RouteViewPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), RouteViewPacket::decode);

    @Override
    public Type<RouteViewPacket> type() {
        return TYPE;
    }

    public record Node(UUID id, BlockPos pos, RouteMarkerType type, double radius, double tolerance, boolean enabled, @Nullable BlockPos controlPos, boolean on, boolean powered) {
        public Node(UUID id, BlockPos pos, RouteMarkerType type, double radius, double tolerance, boolean enabled, @Nullable BlockPos controlPos) { this(id, pos, type, radius, tolerance, enabled, controlPos, false, false); }
        public Node(UUID id, BlockPos pos, RouteMarkerType type, double radius, double tolerance, boolean enabled) { this(id, pos, type, radius, tolerance, enabled, null); }
    }
    public enum Health { NORMAL, UNLOADED, BLOCKED, REMOVED }
    public record Edge(UUID id, BlockPos a, BlockPos b, boolean conditional, boolean open, Health health) {
        public Edge(UUID id, BlockPos a, BlockPos b, boolean conditional, boolean open) { this(id, a, b, conditional, open, Health.NORMAL); }
    }
    public RouteViewPacket {
        nodes = List.copyOf(nodes); edges = List.copyOf(edges);
        removedNodes = List.copyOf(removedNodes); removedEdges = List.copyOf(removedEdges);
    }
    public RouteViewPacket(ResourceLocation dimension, long snapshot, int part, boolean last, @Nullable UUID selected, List<Node> nodes, List<Edge> edges) {
        this(dimension, snapshot, part, last, selected, nodes, edges, -1, List.of(), List.of());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension); buffer.writeLong(snapshot); buffer.writeVarInt(part); buffer.writeBoolean(last);
        buffer.writeBoolean(selected != null); if (selected != null) buffer.writeUUID(selected);
        buffer.writeVarInt(nodes.size());
        for (var node : nodes) {
            buffer.writeUUID(node.id); buffer.writeBlockPos(node.pos); buffer.writeEnum(node.type);
            buffer.writeDouble(node.radius); buffer.writeDouble(node.tolerance); buffer.writeBoolean(node.enabled);
            buffer.writeBoolean(node.controlPos != null); if (node.controlPos != null) buffer.writeBlockPos(node.controlPos);
            buffer.writeBoolean(node.on); buffer.writeBoolean(node.powered);
        }
        buffer.writeVarInt(edges.size());
        for (var edge : edges) {
            buffer.writeUUID(edge.id); buffer.writeBlockPos(edge.a); buffer.writeBlockPos(edge.b);
            buffer.writeBoolean(edge.conditional); buffer.writeBoolean(edge.open);
            buffer.writeEnum(edge.health);
        }
        buffer.writeLong(baseSnapshot);
        buffer.writeVarInt(removedNodes.size()); for (var id : removedNodes) buffer.writeUUID(id);
        buffer.writeVarInt(removedEdges.size()); for (var id : removedEdges) buffer.writeUUID(id);
    }

    public static RouteViewPacket decode(FriendlyByteBuf buffer) {
        var dimension = buffer.readResourceLocation(); long snapshot = buffer.readLong(); int part = buffer.readVarInt(); boolean last = buffer.readBoolean();
        UUID selected = buffer.readBoolean() ? buffer.readUUID() : null;
        int size = bounded(buffer.readVarInt()); var nodes = new ArrayList<Node>(size);
        for (int i = 0; i < size; i++) nodes.add(new Node(buffer.readUUID(), buffer.readBlockPos(), buffer.readEnum(RouteMarkerType.class),
                buffer.readDouble(), buffer.readDouble(), buffer.readBoolean(), buffer.readBoolean() ? buffer.readBlockPos() : null, buffer.readBoolean(), buffer.readBoolean()));
        size = bounded(buffer.readVarInt()); var edges = new ArrayList<Edge>(size);
        for (int i = 0; i < size; i++) edges.add(new Edge(buffer.readUUID(), buffer.readBlockPos(), buffer.readBlockPos(), buffer.readBoolean(), buffer.readBoolean(), buffer.readEnum(Health.class)));
        if (part < 0 || part > RouteSettings.MAX_VIEW_EDGES / RouteSettings.VIEW_BATCH_SIZE) throw new IllegalArgumentException("Invalid route view part");
        long base = buffer.readLong();
        size = bounded(buffer.readVarInt()); var removedNodes = new ArrayList<UUID>(size);
        for (int i = 0; i < size; i++) removedNodes.add(buffer.readUUID());
        size = bounded(buffer.readVarInt()); var removedEdges = new ArrayList<UUID>(size);
        for (int i = 0; i < size; i++) removedEdges.add(buffer.readUUID());
        return new RouteViewPacket(dimension, snapshot, part, last, selected, nodes, edges, base, removedNodes, removedEdges);
    }

    private static int bounded(int size) { if (size < 0 || size > RouteSettings.VIEW_BATCH_SIZE) throw new IllegalArgumentException("Route view batch too large"); return size; }
    public void handleClient() { com.rexhub.createapparel.client.citizen.ClientRouteView.accept(this); }
}
