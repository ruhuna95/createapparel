package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.block.WardrobeBlock;
import com.rexhub.createapparel.init.ModBlockEntities;
import com.rexhub.createapparel.menu.WardrobeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class WardrobeBlockEntity extends BlockEntity implements MenuProvider {
    private static final float ANIMATION_STEP = 0.1F;

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            WardrobeBlock.setOpen(level, pos, true);
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.75F, 1.0F);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            WardrobeBlock.setOpen(level, pos, false);
            level.playSound(null, pos, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.75F, 1.0F);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int oldCount, int newCount) {
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof WardrobeMenu menu
                    && menu.wardrobePos().equals(worldPosition);
        }
    };

    private float previousOpenness;
    private float openness;

    public WardrobeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WARDROBE.get(), pos, state);
        openness = state.getValue(WardrobeBlock.OPEN) ? 1.0F : 0.0F;
        previousOpenness = openness;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, WardrobeBlockEntity blockEntity) {
        blockEntity.previousOpenness = blockEntity.openness;
        float target = state.getValue(WardrobeBlock.OPEN) ? 1.0F : 0.0F;
        blockEntity.openness = Mth.clamp(
                blockEntity.openness + Mth.sign(target - blockEntity.openness) * ANIMATION_STEP,
                0.0F,
                1.0F
        );
    }

    public float getOpenness(float partialTick) {
        float progress = Mth.lerp(partialTick, previousOpenness, openness);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    public void startOpen(Player player) {
        if (!isRemoved() && !player.isSpectator() && level != null) {
            openersCounter.incrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    public void stopOpen(Player player) {
        if (!isRemoved() && !player.isSpectator() && level != null) {
            openersCounter.decrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    public void recheckOpeners() {
        if (!isRemoved() && level != null) {
            openersCounter.recheckOpeners(level, worldPosition, getBlockState());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.createapparel.wardrobe");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new WardrobeMenu(containerId, playerInventory, this);
    }

    public AABB getRenderBoundingBox() {
        return new AABB(
                worldPosition.getX(),
                worldPosition.getY(),
                worldPosition.getZ(),
                worldPosition.getX() + 1.0D,
                worldPosition.getY() + 2.0D,
                worldPosition.getZ() + 1.0D
        );
    }
}
