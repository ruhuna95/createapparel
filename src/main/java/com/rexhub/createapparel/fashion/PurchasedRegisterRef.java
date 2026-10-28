package com.rexhub.createapparel.fashion;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import javax.annotation.Nullable;
import java.util.UUID;

public record PurchasedRegisterRef(UUID registerId, ResourceLocation dimension, BlockPos lastKnownPos) {
    public PurchasedRegisterRef { lastKnownPos = lastKnownPos.immutable(); }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("RegisterId", registerId);
        tag.putString("Dimension", dimension.toString());
        tag.putLong("Pos", lastKnownPos.asLong());
        return tag;
    }
    @Nullable public static PurchasedRegisterRef load(CompoundTag tag) {
        var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        return tag.hasUUID("RegisterId") && dimension != null && tag.contains("Pos", CompoundTag.TAG_LONG)
                ? new PurchasedRegisterRef(tag.getUUID("RegisterId"), dimension, BlockPos.of(tag.getLong("Pos"))) : null;
    }
    @Nullable public CashRegisterBlockEntity resolve(ServerLevel level) {
        if (!level.dimension().location().equals(dimension) || !level.hasChunk(lastKnownPos.getX() >> 4, lastKnownPos.getZ() >> 4)) return null;
        return level.getBlockEntity(lastKnownPos) instanceof CashRegisterBlockEntity register
                && register.registerId().equals(registerId) ? register : null;
    }
}
