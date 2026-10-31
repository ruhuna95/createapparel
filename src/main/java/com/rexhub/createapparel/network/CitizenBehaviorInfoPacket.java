package com.rexhub.createapparel.network;

import com.rexhub.createapparel.client.citizen.ClientCitizenBehaviorInfo;
import com.rexhub.createapparel.citizen.population.CitizenGauge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;

import javax.annotation.Nullable;
import java.util.UUID;

public record CitizenBehaviorInfoPacket(int containerId, int entityId, @Nullable UUID targetUuid, Component display,
                                       @Nullable BlockPos destination, @Nullable UUID districtId, String districtName) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<CitizenBehaviorInfoPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "citizen_behavior_info_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, CitizenBehaviorInfoPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), CitizenBehaviorInfoPacket::decode);

    @Override
    public Type<CitizenBehaviorInfoPacket> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(entityId);
        buffer.writeBoolean(targetUuid != null);
        if (targetUuid != null) buffer.writeUUID(targetUuid);
        net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buffer, display);
        buffer.writeBoolean(destination != null);
        if (destination != null) buffer.writeBlockPos(destination);
        buffer.writeBoolean(districtId != null);
        if (districtId != null) buffer.writeUUID(districtId);
        buffer.writeUtf(districtName, CitizenGauge.MAX_NAME_LENGTH);
    }

    public static CitizenBehaviorInfoPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CitizenBehaviorInfoPacket(buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean() ? buffer.readUUID() : null, net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buffer),
                buffer.readBoolean() ? buffer.readBlockPos() : null,
                buffer.readBoolean() ? buffer.readUUID() : null,
                buffer.readUtf(CitizenGauge.MAX_NAME_LENGTH));
    }

    public void handleClient() {
        ClientCitizenBehaviorInfo.accept(this);
    }
}
