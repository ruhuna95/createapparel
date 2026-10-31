package com.rexhub.createapparel.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import java.util.UUID;

public record CitizenGaugeInfoPacket(int containerId, UUID gaugeId, CompoundTag info) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<CitizenGaugeInfoPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "citizen_gauge_info_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, CitizenGaugeInfoPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), CitizenGaugeInfoPacket::decode);

    @Override
    public Type<CitizenGaugeInfoPacket> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(containerId); buffer.writeUUID(gaugeId); buffer.writeNbt(info); }
    public static CitizenGaugeInfoPacket decode(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt(); var id = buffer.readUUID(); var tag = buffer.readNbt();
        return new CitizenGaugeInfoPacket(container, id, tag == null ? new CompoundTag() : tag);
    }
    public void handleClient() {
        com.rexhub.createapparel.client.citizen.ClientGaugeView.accept(this);
    }
}
