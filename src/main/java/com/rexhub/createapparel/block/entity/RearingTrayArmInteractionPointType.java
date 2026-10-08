package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.init.ModBlocks;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class RearingTrayArmInteractionPointType extends ArmInteractionPointType {
    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        return state.is(ModBlocks.SILKWORM_REARING_TRAY.get());
    }

    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new ArmInteractionPoint(this, level, pos, state) {
            @Override
            protected Vec3 getInteractionPositionVector() { return Vec3.atBottomCenterOf(pos).add(0, 1, 0); }
        };
    }
}
