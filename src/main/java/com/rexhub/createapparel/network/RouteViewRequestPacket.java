package com.rexhub.createapparel.network;

import com.rexhub.createapparel.citizen.route.RouteViewSynchronization;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record RouteViewRequestPacket(int radius) {
    public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(radius); }
    public static RouteViewRequestPacket decode(FriendlyByteBuf buffer) { return new RouteViewRequestPacket(buffer.readVarInt()); }
    public void handle(ServerPlayer player) { if (radius >= 8 && radius <= 128) RouteViewSynchronization.request(player, radius); }
}
