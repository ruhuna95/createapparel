package com.rexhub.createapparel;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class TestArms {
    private TestArms() { }

    public static ArmBlockEntity create(Level level) {
        var block = AllBlocks.MECHANICAL_ARM.get();
        var arm = (ArmBlockEntity) java.util.Objects.requireNonNull(block.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()));
        arm.setLevel(level);
        return arm;
    }
}
