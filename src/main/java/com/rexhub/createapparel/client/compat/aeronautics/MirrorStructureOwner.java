package com.rexhub.createapparel.client.compat.aeronautics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class MirrorStructureOwner {
    private MirrorStructureOwner() { }

    public static Object owner(Level level, BlockPos pos) {
        return ModList.get().isLoaded("sable") ? MirrorSubLevelOwner.owner(level, pos) : level;
    }
}
