package com.rexhub.createapparel.network;

import com.rexhub.createapparel.fashion.RetailInspection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.UUID;

public record RetailInspectionPacket(ResourceLocation dimension, BlockPos pos, UUID registerId, RetailInspection.Status status) {
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
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.rexhub.createapparel.client.fashion.ClientRetailView.accept(this));
    }
}
