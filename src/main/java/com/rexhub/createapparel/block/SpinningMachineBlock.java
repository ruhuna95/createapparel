package com.rexhub.createapparel.block;

import com.rexhub.createapparel.block.entity.SpinningMachineBlockEntity;
import com.rexhub.createapparel.init.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SpinningMachineBlock extends HorizontalShaftMachineBlock
        implements IBE<SpinningMachineBlockEntity> {
    public static final double STRESS_IMPACT = 4.0;

    public SpinningMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return onBlockEntityUse(level, pos, blockEntity -> blockEntity.interact(player, hand));
    }

    @Override
    public Class<SpinningMachineBlockEntity> getBlockEntityClass() {
        return SpinningMachineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpinningMachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SPINNING_MACHINE.get();
    }
}
