package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.block.CitizenGaugeBlock;
import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

public final class GaugeSurface {
    public record Target(BlockPos pos, int slot) { }
    private GaugeSurface() { }
    public static boolean loaded(Level level, BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) != null;
    }
    public static boolean compatible(BlockState from, BlockPos a, BlockState to, BlockPos b) {
        var facing = from.getValue(CitizenGaugeBlock.FACING); var diff = b.subtract(a);
        return facing == to.getValue(CitizenGaugeBlock.FACING) && a.distSqr(b) < 16 * 16
                && facing.getAxis().choose(diff.getX(), diff.getY(), diff.getZ()) == 0;
    }
    @Nullable public static Target relocationTarget(Level level, BlockPos from, BlockState state, BlockHitResult hit) {
        var facing = state.getValue(CitizenGaugeBlock.FACING);
        if (hit.getDirection() != facing || !loaded(level, hit.getBlockPos())) return null;
        var pos = level.getBlockState(hit.getBlockPos()).getBlock() instanceof CitizenGaugeBlock ? hit.getBlockPos() : hit.getBlockPos().relative(facing);
        if (!loaded(level, pos) || !loaded(level, pos.relative(facing.getOpposite())) || !level.isInWorldBounds(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !compatible(state, from, state, pos)) return null;
        var targetState = level.getBlockState(pos);
        int slot = CitizenGaugeBlock.targetedSlot(pos, state, hit.getLocation());
        if (targetState.getBlock() instanceof CitizenGaugeBlock) {
            if (!compatible(state, from, targetState, pos) || !(level.getBlockEntity(pos) instanceof CitizenGaugeBlockEntity entity) || entity.hasPanel(slot)) return null;
        } else if (!targetState.isAir()) return null;
        return state.canSurvive(level, pos) ? new Target(pos.immutable(), slot) : null;
    }
}
