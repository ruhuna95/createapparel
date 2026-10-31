package com.rexhub.createapparel.network;

import com.rexhub.createapparel.citizen.route.SmartPhoneRouteEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import java.util.UUID;

public record RouteEdgeActionPacket(UUID edgeId, InteractionHand hand, boolean control) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<RouteEdgeActionPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "route_edge_action_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, RouteEdgeActionPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), RouteEdgeActionPacket::decode);

    @Override
    public Type<RouteEdgeActionPacket> type() {
        return TYPE;
    }

    public RouteEdgeActionPacket(UUID edgeId, InteractionHand hand) { this(edgeId, hand, false); }
    public void encode(FriendlyByteBuf buffer) { buffer.writeUUID(edgeId); buffer.writeEnum(hand); buffer.writeBoolean(control); }
    public static RouteEdgeActionPacket decode(FriendlyByteBuf buffer) { return new RouteEdgeActionPacket(buffer.readUUID(), buffer.readEnum(InteractionHand.class), buffer.readBoolean()); }
    public void handle(ServerPlayer player) { SmartPhoneRouteEditor.deleteEdge(player, edgeId, hand, control); }
}
