package com.rexhub.createapparel.mirror;

import com.rexhub.createapparel.block.MirrorFurnitureBlock;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class MirrorMovementChecks {
    private MirrorMovementChecks() { }

    public static void register() {
        BlockMovementChecks.registerBrittleCheck(state -> state.getBlock() instanceof MirrorFurnitureBlock
                ? CheckResult.SUCCESS : CheckResult.PASS);
        BlockMovementChecks.registerAttachedCheck((state, level, pos, direction) -> {
            if (!(state.getBlock() instanceof MirrorFurnitureBlock furniture)) return CheckResult.PASS;
            boolean attached = switch (furniture.kind()) {
                case PANEL -> direction == state.getValue(MirrorFurnitureBlock.FACING).getOpposite();
                case TABLE -> direction == Direction.DOWN;
                case FULL_LENGTH -> direction == Direction.DOWN || direction == Direction.UP
                        && state.getValue(MirrorFurnitureBlock.HALF) == DoubleBlockHalf.LOWER;
            };
            return CheckResult.of(attached);
        });
    }
}
