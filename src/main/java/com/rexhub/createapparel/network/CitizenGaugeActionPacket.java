package com.rexhub.createapparel.network;

import com.rexhub.createapparel.menu.CitizenGaugeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import javax.annotation.Nullable;
import java.util.UUID;

public record CitizenGaugeActionPacket(int containerId, UUID gaugeId, long version, Action action, int range, int target, @Nullable UUID other, @Nullable String name) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<CitizenGaugeActionPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "citizen_gauge_action_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, CitizenGaugeActionPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), CitizenGaugeActionPacket::decode);

    @Override
    public Type<CitizenGaugeActionPacket> type() {
        return TYPE;
    }

    public enum Action { SETTINGS, UNLINK, CONNECT, RELOCATE, CANCEL, FLOW, RESYNC }
    public CitizenGaugeActionPacket(int containerId, UUID gaugeId, long version, Action action, int range, int target, @Nullable UUID other) {
        this(containerId, gaugeId, version, action, range, target, other, null);
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId); buffer.writeUUID(gaugeId); buffer.writeLong(version); buffer.writeEnum(action);
        buffer.writeVarInt(range); buffer.writeVarInt(target); buffer.writeNullable(other, (buf, value) -> buf.writeUUID(value));
        buffer.writeNullable(name, (output, value) -> output.writeUtf(value, com.rexhub.createapparel.citizen.population.CitizenGauge.MAX_NAME_LENGTH));
    }
    public static CitizenGaugeActionPacket decode(FriendlyByteBuf buffer) {
        return new CitizenGaugeActionPacket(buffer.readVarInt(), buffer.readUUID(), buffer.readLong(), buffer.readEnum(Action.class),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readNullable(buf -> buf.readUUID()),
                buffer.readNullable(input -> input.readUtf(com.rexhub.createapparel.citizen.population.CitizenGauge.MAX_NAME_LENGTH)));
    }
    public void handle(ServerPlayer player) {
        if (action == Action.RESYNC) {
            if (containerId >= 0) {
                if (player.containerMenu instanceof CitizenGaugeMenu menu && menu.containerId == containerId && menu.gaugeId().equals(gaugeId)) menu.resync(player);
            } else com.rexhub.createapparel.citizen.population.GaugeViewSynchronization.resync(player);
            return;
        }
        if (action == Action.CANCEL) { com.rexhub.createapparel.citizen.population.GaugeInteractions.cancel(player, gaugeId); return; }
        if (player.containerMenu instanceof CitizenGaugeMenu menu) menu.apply(player, this);
    }
}
