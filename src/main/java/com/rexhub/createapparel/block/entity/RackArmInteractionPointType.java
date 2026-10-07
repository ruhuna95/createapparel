package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.block.ClothingRackBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

public final class RackArmInteractionPointType extends ArmInteractionPointType {
    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof ClothingRackBlock && state.getValue(ClothingRackBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new ArmInteractionPoint(this, level, pos, state) {
            @Override
            protected Vec3 getInteractionPositionVector() {
                return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(cachedState.getValue(ClothingRackBlock.FACING).getNormal()).scale(0.4));
            }

            @Override
            protected Direction getInteractionDirection() { return cachedState.getValue(ClothingRackBlock.FACING).getOpposite(); }
        };
    }
}
