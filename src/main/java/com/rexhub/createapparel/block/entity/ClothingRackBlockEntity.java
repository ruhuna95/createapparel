package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.block.ClothingRackBlock;
import com.rexhub.createapparel.garment.HangerGarments;
import com.rexhub.createapparel.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public final class ClothingRackBlockEntity extends BlockEntity {
    private final ItemStackHandler inventory = new ItemStackHandler(4) {
        @Override
        public int getSlotLimit(int slot) { return 1; }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !isRemoved() && level != null && !level.isClientSide && isSlotUsable(slot) && HangerGarments.accepts(stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (!stack.isEmpty() && (!isItemValid(slot, stack) || stack.getCount() > 1))
                throw new IllegalArgumentException("Invalid rack garment slot");
            super.setStackInSlot(slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) { sync(); }
    };
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> inventory);

    public ClothingRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLOTHING_RACK.get(), pos, state);
    }

    public IItemHandler inventory() { return inventory; }
    public ItemStack garment(int slot) { return inventory.getStackInSlot(slot).copy(); }

    public boolean isSlotUsable(int slot) {
        BlockState state = getBlockState();
        boolean left = level == null ? state.getValue(ClothingRackBlock.LEFT_CONNECTED)
                : ClothingRackBlock.connected(level, worldPosition, state, true);
        boolean right = level == null ? state.getValue(ClothingRackBlock.RIGHT_CONNECTED)
                : ClothingRackBlock.connected(level, worldPosition, state, false);
        return state.getValue(ClothingRackBlock.MOUNT).isSlotUsable(left, right, slot);
    }

    public int usableMask() {
        int mask = 0;
        for (int slot = 0; slot < 4; slot++) if (isSlotUsable(slot)) mask |= 1 << slot;
        return mask;
    }

    public boolean insert(int slot, ItemStack held, boolean creative) {
        if (level == null || level.isClientSide || slot < 0 || slot >= 4 || !HangerGarments.accepts(held)) return false;
        refreshConnections();
        if (!inventory.insertItem(slot, held.copyWithCount(1), false).isEmpty()) return false;
        if (!creative) held.shrink(1);
        return true;
    }

    public boolean take(int slot, Player player) {
        if (level == null || level.isClientSide || slot < 0 || slot >= 4) return false;
        refreshConnections();
        if (!isSlotUsable(slot)) return false;
        ItemStack returned = inventory.extractItem(slot, 1, false);
        if (returned.isEmpty()) return false;
        if (!player.getInventory().add(returned)) player.drop(returned, false);
        return true;
    }

    public void refreshConnections() {
        if (level == null || level.isClientSide || isRemoved() || !level.getBlockState(worldPosition).is(getBlockState().getBlock())) return;
        BlockState state = getBlockState();
        BlockState updated = state.setValue(ClothingRackBlock.LEFT_CONNECTED, ClothingRackBlock.connected(level, worldPosition, state, true))
                .setValue(ClothingRackBlock.RIGHT_CONNECTED, ClothingRackBlock.connected(level, worldPosition, state, false))
                .setValue(ClothingRackBlock.WALL_ANCHORED, state.getValue(ClothingRackBlock.MOUNT) == com.rexhub.createapparel.block.RackMountType.WALL
                        && ClothingRackBlock.wallAnchored(level, worldPosition, state));
        if (updated != state) {
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
        }
        BlockPos upper = worldPosition.above();
        BlockState upperState = level.getBlockState(upper);
        BlockState updatedUpper = updated.setValue(ClothingRackBlock.HALF, DoubleBlockHalf.UPPER);
        if (upperState.is(state.getBlock()) && upperState.getValue(ClothingRackBlock.HALF) == DoubleBlockHalf.UPPER
                && upperState != updatedUpper) level.setBlock(upper, updatedUpper, Block.UPDATE_CLIENTS);
        for (int slot = 0; slot < 4; slot++) if (!isSlotUsable(slot)) dropSlot(slot);
    }

    private void dropSlot(int slot) {
        ItemStack dropped = inventory.extractItem(slot, Integer.MAX_VALUE, false);
        if (!dropped.isEmpty() && level != null) Block.popResource(level, worldPosition, dropped);
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < 4; slot++) dropSlot(slot);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) ClothingRackBlock.scheduleConnections(level, worldPosition, getBlockState());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Garments", inventory.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag garments = tag.getCompound("Garments").copy();
        garments.putInt("Size", 4);
        inventory.deserializeNBT(garments);
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        return !isRemoved() && capability == ForgeCapabilities.ITEM_HANDLER ? itemCapability.cast() : super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCapability = LazyOptional.of(() -> inventory);
    }

    @Override
    public AABB getRenderBoundingBox() { return new AABB(worldPosition).expandTowards(0, 1, 0).inflate(0.8, 0.6, 0.8); }
}
