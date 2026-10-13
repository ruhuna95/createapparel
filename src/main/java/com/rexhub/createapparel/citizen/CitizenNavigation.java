package com.rexhub.createapparel.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.Set;

public final class CitizenNavigation extends GroundPathNavigation {
    public CitizenNavigation(Mob mob, Level level) { super(mob, level); }

    @Nullable
    @Override
    public Path createPath(BlockPos target, int accuracy) {
        if (!hasLoadedRegion(level, target, 0)) return null;
        return super.createPath(target, accuracy);
    }

    @Nullable
    @Override
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        var center = offsetUpward ? mob.blockPosition().above() : mob.blockPosition();
        int extent = (int) (followRange + regionOffset);
        if (!hasLoadedRegion(level, center, extent)) return null;
        return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
    }

    public static boolean hasLoadedRegion(Level level, BlockPos center, int extent) {
        for (int x = Math.floorDiv(center.getX() - extent, 16); x <= Math.floorDiv(center.getX() + extent, 16); x++) {
            for (int z = Math.floorDiv(center.getZ() - extent, 16); z <= Math.floorDiv(center.getZ() + extent, 16); z++) {
                if (!level.getChunkSource().hasChunk(x, z)) return false;
            }
        }
        return true;
    }
}
