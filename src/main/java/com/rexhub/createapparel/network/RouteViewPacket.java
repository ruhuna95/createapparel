package com.rexhub.createapparel.network;

import com.rexhub.createapparel.citizen.route.RouteMarkerType;
import com.rexhub.createapparel.citizen.route.RouteSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import javax.annotation.Nullable;
import java.util.*;

public record RouteViewPacket(ResourceLocation dimension, long snapshot, int part, boolean last,
                              @Nullable UUID selected, List<Node> nodes, List<Edge> edges) {
    public record Node(UUID id, BlockPos pos, RouteMarkerType type, double radius, double tolerance, boolean enabled, @Nullable BlockPos controlPos, boolean on, boolean powered) {
        public Node(UUID id, BlockPos pos, RouteMarkerType type, double radius, double tolerance, boolean enabled, @Nullable BlockPos controlPos) { this(id, pos, type, radius, tolerance, enabled, controlPos, false, false); }
        public Node(UUID id, BlockPos pos, RouteMarkerType type, double radius, double tolerance, boolean enabled) { this(id, pos, type, radius, tolerance, enabled, null); }
    }
    public enum Health { NORMAL, UNLOADED, BLOCKED, REMOVED }
    public record Edge(UUID id, BlockPos a, BlockPos b, boolean conditional, boolean open, Health health) {
        public Edge(UUID id, BlockPos a, BlockPos b, boolean conditional, boolean open) { this(id, a, b, conditional, open, Health.NORMAL); }
    }
    public RouteViewPacket { nodes = List.copyOf(nodes); edges = List.copyOf(edges); }

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
        return new RouteViewPacket(dimension, snapshot, part, last, selected, nodes, edges);
    }

    private static int bounded(int size) { if (size < 0 || size > RouteSettings.VIEW_BATCH_SIZE) throw new IllegalArgumentException("Route view batch too large"); return size; }
    public void handleClient() { DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.rexhub.createapparel.client.citizen.ClientRouteView.accept(this)); }
}
