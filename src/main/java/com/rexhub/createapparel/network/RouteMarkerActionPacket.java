package com.rexhub.createapparel.network;

import com.rexhub.createapparel.menu.RouteMarkerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public record RouteMarkerActionPacket(int containerId, UUID markerId, long version, Action action,
                                      double radius, double tolerance, String value, BlockPos conditionPos,
                                      String blockId, String property, String expected, boolean inverted, int page, int sequence, int threshold) {
    public RouteMarkerActionPacket(int containerId, UUID markerId, long version, Action action,
                                   double radius, double tolerance, String value, BlockPos conditionPos,
                                   String blockId, String property, String expected, boolean inverted, int page, int sequence) {
        this(containerId, markerId, version, action, radius, tolerance, value, conditionPos, blockId, property, expected, inverted, page, sequence,
                com.rexhub.createapparel.citizen.route.RouteSettings.DEFAULT_SIGNAL_THRESHOLD);
    }
    public RouteMarkerActionPacket(int containerId, UUID markerId, long version, Action action,
                                   double radius, double tolerance, String value, BlockPos conditionPos,
                                   String blockId, String property, String expected, boolean inverted, int page) {
        this(containerId, markerId, version, action, radius, tolerance, value, conditionPos, blockId, property, expected, inverted, page, 0);
    }
    public enum Action { REGION, TYPE, DISTRICT, CONDITION, PAIR, UNPAIR, EDGE, DELETE, PAGE }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId); buffer.writeUUID(markerId); buffer.writeLong(version); buffer.writeEnum(action);
        buffer.writeDouble(radius); buffer.writeDouble(tolerance); buffer.writeUtf(value, 128); buffer.writeBlockPos(conditionPos);
        buffer.writeUtf(blockId, 128); buffer.writeUtf(property, 64); buffer.writeUtf(expected, 64); buffer.writeBoolean(inverted); buffer.writeVarInt(page); buffer.writeVarInt(sequence);
        buffer.writeVarInt(threshold);
    }

    public static RouteMarkerActionPacket decode(FriendlyByteBuf buffer) {
        return new RouteMarkerActionPacket(buffer.readVarInt(), buffer.readUUID(), buffer.readLong(), buffer.readEnum(Action.class),
                buffer.readDouble(), buffer.readDouble(), buffer.readUtf(128), buffer.readBlockPos(), buffer.readUtf(128), buffer.readUtf(64),
                buffer.readUtf(64), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(ServerPlayer player) { if (player.containerMenu instanceof RouteMarkerMenu menu) menu.apply(player, this); }
}
