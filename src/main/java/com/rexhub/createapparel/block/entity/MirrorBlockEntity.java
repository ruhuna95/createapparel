package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.init.ModMirrors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class MirrorBlockEntity extends BlockEntity {
    public MirrorBlockEntity(BlockPos pos, BlockState state) { super(ModMirrors.BLOCK_ENTITY.get(), pos, state); }

    public AABB getRenderBoundingBox() { return new AABB(worldPosition).expandTowards(0, 1, 0); }
}
