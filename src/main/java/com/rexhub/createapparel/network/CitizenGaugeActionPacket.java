package com.rexhub.createapparel.network;

import com.rexhub.createapparel.menu.CitizenGaugeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import javax.annotation.Nullable;
import java.util.UUID;

public record CitizenGaugeActionPacket(int containerId, UUID gaugeId, long version, Action action, int range, int target, @Nullable UUID other, @Nullable String name) {
    public enum Action { SETTINGS, UNLINK, CONNECT, RELOCATE, CANCEL, FLOW }
    public CitizenGaugeActionPacket(int containerId, UUID gaugeId, long version, Action action, int range, int target, @Nullable UUID other) {
        this(containerId, gaugeId, version, action, range, target, other, null);
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId); buffer.writeUUID(gaugeId); buffer.writeLong(version); buffer.writeEnum(action);
        buffer.writeVarInt(range); buffer.writeVarInt(target); buffer.writeNullable(other, FriendlyByteBuf::writeUUID);
        buffer.writeNullable(name, (output, value) -> output.writeUtf(value, com.rexhub.createapparel.citizen.population.CitizenGauge.MAX_NAME_LENGTH));
    }
    public static CitizenGaugeActionPacket decode(FriendlyByteBuf buffer) {
        return new CitizenGaugeActionPacket(buffer.readVarInt(), buffer.readUUID(), buffer.readLong(), buffer.readEnum(Action.class),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readNullable(FriendlyByteBuf::readUUID),
                buffer.readNullable(input -> input.readUtf(com.rexhub.createapparel.citizen.population.CitizenGauge.MAX_NAME_LENGTH)));
    }
    public void handle(ServerPlayer player) {
        if (action == Action.CANCEL) { com.rexhub.createapparel.citizen.population.GaugeInteractions.cancel(player, gaugeId); return; }
        if (player.containerMenu instanceof CitizenGaugeMenu menu) menu.apply(player, this);
    }
}
