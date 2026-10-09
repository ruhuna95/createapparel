package com.rexhub.createapparel.mirror;

import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public interface MirrorSurfaceProvider {
    List<MirrorGeometry> mirrorGeometry(BlockState state);
    default boolean mergesSurfaces() { return false; }
}
