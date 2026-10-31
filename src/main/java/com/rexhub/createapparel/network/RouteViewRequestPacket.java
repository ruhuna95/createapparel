package com.rexhub.createapparel.network;

import com.rexhub.createapparel.citizen.route.RouteViewSynchronization;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record RouteViewRequestPacket(int radius) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<RouteViewRequestPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "route_view_request_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, RouteViewRequestPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), RouteViewRequestPacket::decode);

    @Override
    public Type<RouteViewRequestPacket> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(radius); }
    public static RouteViewRequestPacket decode(FriendlyByteBuf buffer) { return new RouteViewRequestPacket(buffer.readVarInt()); }
    public void handle(ServerPlayer player) { if (radius >= 8 && radius <= 128) RouteViewSynchronization.request(player, radius); }
}
