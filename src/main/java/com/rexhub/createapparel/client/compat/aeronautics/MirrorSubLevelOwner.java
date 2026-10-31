package com.rexhub.createapparel.client.compat.aeronautics;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class MirrorSubLevelOwner {
    private record Owner(Level level, Object plot) { }
    private record Unresolved(Level level, BlockPos pos) { }

    private MirrorSubLevelOwner() { }

    public static Object owner(Level level, BlockPos pos) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return new Unresolved(level, pos.immutable());
        if (!container.inBounds(pos)) return level;
        var plot = container.getPlot(new ChunkPos(pos));
        return plot == null ? new Unresolved(level, pos.immutable()) : new Owner(level, plot);
    }
}
