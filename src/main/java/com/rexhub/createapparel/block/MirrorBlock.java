package com.rexhub.createapparel.block;

import com.rexhub.createapparel.block.entity.MirrorBlockEntity;
import com.rexhub.createapparel.mirror.MirrorGeometry;
import com.rexhub.createapparel.mirror.MirrorSurfaceProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.List;

public final class MirrorBlock extends BaseEntityBlock implements MirrorSurfaceProvider {
    public static final com.mojang.serialization.MapCodec<MirrorBlock> CODEC = simpleCodec(MirrorBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends MirrorBlock> codec() {
        return CODEC;
    }

    private static final List<MirrorGeometry> SURFACES = java.util.Arrays.stream(Direction.values()).map(direction -> {
        var rotation = new Matrix4f().translation(0.5F, 0.5F, 0.5F);
        switch (direction) {
            case SOUTH -> rotation.rotateY((float) Math.PI);
            case WEST -> rotation.rotateY((float) Math.PI / 2);
            case EAST -> rotation.rotateY(-(float) Math.PI / 2);
            case UP -> rotation.rotateX((float) Math.PI / 2);
            case DOWN -> rotation.rotateX(-(float) Math.PI / 2);
            default -> { }
        }
        return MirrorGeometry.north(-0.5, -0.5, 0.5, 0.5, -0.5005).transform(rotation);
    }).toList();

    public MirrorBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MirrorBlockEntity(pos, state); }
    @Override public List<MirrorGeometry> mirrorGeometry(BlockState state) { return SURFACES; }
    @Override public boolean mergesSurfaces() { return true; }
    public boolean isFaceVisible(BlockGetter level, BlockPos pos, int face) {
        return !level.getBlockState(pos.relative(Direction.from3DDataValue(face))).is(this);
    }
    @Override @SuppressWarnings("deprecation")
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        return adjacentState.is(this) || super.skipRendering(state, adjacentState, side);
    }
    @Override @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
}
