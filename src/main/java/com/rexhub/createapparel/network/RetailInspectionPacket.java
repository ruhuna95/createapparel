package com.rexhub.createapparel.network;

import com.rexhub.createapparel.fashion.RetailInspection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;

import java.util.UUID;

public record RetailInspectionPacket(ResourceLocation dimension, BlockPos pos, UUID registerId, RetailInspection.Status status) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<RetailInspectionPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "retail_inspection_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, RetailInspectionPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), RetailInspectionPacket::decode);

    @Override
    public Type<RetailInspectionPacket> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeBlockPos(pos);
        buffer.writeUUID(registerId);
        buffer.writeBoolean(status.cashier());
        buffer.writeVarInt(status.storageCount());
        buffer.writeBoolean(status.missing());
        buffer.writeBoolean(status.checking());
    }
    public static RetailInspectionPacket decode(FriendlyByteBuf buffer) {
        return new RetailInspectionPacket(buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readUUID(),
                new RetailInspection.Status(buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean()));
    }
    public void handleClient() {
        com.rexhub.createapparel.client.fashion.ClientRetailView.accept(this);
    }
}
