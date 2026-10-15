package com.rexhub.createapparel.network;

import com.rexhub.createapparel.citizen.route.SmartPhoneRouteEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import java.util.UUID;

public record RouteEdgeActionPacket(UUID edgeId, InteractionHand hand, boolean control) {
    public RouteEdgeActionPacket(UUID edgeId, InteractionHand hand) { this(edgeId, hand, false); }
    public void encode(FriendlyByteBuf buffer) { buffer.writeUUID(edgeId); buffer.writeEnum(hand); buffer.writeBoolean(control); }
    public static RouteEdgeActionPacket decode(FriendlyByteBuf buffer) { return new RouteEdgeActionPacket(buffer.readUUID(), buffer.readEnum(InteractionHand.class), buffer.readBoolean()); }
    public void handle(ServerPlayer player) { SmartPhoneRouteEditor.deleteEdge(player, edgeId, hand, control); }
}
