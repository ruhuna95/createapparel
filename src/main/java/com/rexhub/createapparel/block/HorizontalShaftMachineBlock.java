package com.rexhub.createapparel.block;

import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

public abstract class HorizontalShaftMachineBlock extends HorizontalKineticBlock {
    protected HorizontalShaftMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        Direction.Axis preferredAxis = HorizontalAxisKineticBlock.getPreferredHorizontalAxis(context);
        Direction facing = state.getValue(HORIZONTAL_FACING);
        if (preferredAxis != null && facing.getAxis() == preferredAxis) {
            return state.setValue(HORIZONTAL_FACING, facing.getClockWise());
        }
        return state;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getClockWise().getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }
}
