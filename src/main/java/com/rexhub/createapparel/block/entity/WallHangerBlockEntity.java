package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.garment.HangerGarments;
import com.rexhub.createapparel.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class WallHangerBlockEntity extends BlockEntity {
    private ItemStack garment = ItemStack.EMPTY;

    public WallHangerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WALL_HANGER.get(), pos, state);
    }

    public ItemStack garment() { return garment.copy(); }

    public boolean insert(ItemStack held, boolean creative) {
        if (level == null || level.isClientSide || !garment.isEmpty() || !HangerGarments.accepts(held)) return false;
        garment = held.copyWithCount(1);
        if (!creative) held.shrink(1);
        sync();
        return true;
    }

    public boolean take(Player player) {
        if (level == null || level.isClientSide || garment.isEmpty()) return false;
        ItemStack returned = garment;
        garment = ItemStack.EMPTY;
        sync();
        if (!player.getInventory().add(returned)) player.drop(returned, false);
        return true;
    }

    public void dropContents() {
        if (level == null || level.isClientSide || garment.isEmpty()) return;
        ItemStack dropped = garment;
        garment = ItemStack.EMPTY;
        setChanged();
        Block.popResource(level, worldPosition, dropped);
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Garment", garment.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        garment = ItemStack.of(tag.getCompound("Garment"));
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.6, 1.5, 0.6);
    }
}
