package com.rexhub.createapparel.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import java.util.UUID;

public record RouteMarkerInfoPacket(int containerId, UUID markerId, CompoundTag info) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<RouteMarkerInfoPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "route_marker_info_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, RouteMarkerInfoPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), RouteMarkerInfoPacket::decode);

    @Override
    public Type<RouteMarkerInfoPacket> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(containerId); buffer.writeUUID(markerId); buffer.writeNbt(info); }
    public static RouteMarkerInfoPacket decode(FriendlyByteBuf buffer) {
        int id = buffer.readVarInt(); UUID marker = buffer.readUUID(); var info = buffer.readNbt();
        return new RouteMarkerInfoPacket(id, marker, info == null ? new CompoundTag() : info);
    }
    public void handleClient() {
        {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof com.rexhub.createapparel.menu.RouteMarkerMenu menu
                    && menu.containerId == containerId && menu.markerId().equals(markerId)) menu.accept(info);
        }
    }
}
