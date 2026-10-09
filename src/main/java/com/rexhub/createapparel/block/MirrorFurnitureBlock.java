package com.rexhub.createapparel.block;

import com.rexhub.createapparel.block.entity.MirrorBlockEntity;
import com.rexhub.createapparel.mirror.MirrorGeometry;
import com.rexhub.createapparel.mirror.MirrorFurnitureGeometry;
import com.rexhub.createapparel.mirror.MirrorSurfaceProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MirrorFurnitureBlock extends BaseEntityBlock implements MirrorSurfaceProvider {
    public enum Kind { PANEL, FULL_LENGTH, TABLE }
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private final Kind kind;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final Map<Direction, List<MirrorGeometry>> surfaces = new EnumMap<>(Direction.class);

    public MirrorFurnitureBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
        VoxelShape shape = switch (kind) {
            case PANEL -> Block.box(0, 0, 15, 16, 16, 16);
            case TABLE -> Block.box(3, 0, 7, 13, 11, 13);
            case FULL_LENGTH -> Block.box(1, 0, 7, 15, 16, 16);
        };
        MirrorGeometry geometry = switch (kind) {
            case PANEL -> MirrorGeometry.north(0, 0, 1, 1, 15 / 16.0 - 0.0005);
            case TABLE -> MirrorFurnitureGeometry.tableSurface();
            case FULL_LENGTH -> MirrorFurnitureGeometry.fullSurface();
        };
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            shapes.put(facing, HangerGeometry.orient(shape, facing));
            surfaces.put(facing, List.of(geometry.transform(orientation(facing))));
        }
    }

    public Kind kind() { return kind; }

    public static Matrix4f orientation(Direction facing) {
        return new Matrix4f().translation(0.5F, 0, 0.5F)
                .rotateY((float) Math.toRadians(180 - facing.toYRot())).translate(-0.5F, 0, -0.5F);
    }

    @Nullable @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (kind == Kind.FULL_LENGTH && (context.getClickedPos().getY() >= context.getLevel().getMaxBuildHeight() - 1
                || !context.getLevel().getBlockState(context.getClickedPos().above()).canBeReplaced(context))) return null;
        Direction facing = kind == Kind.PANEL ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        if (!facing.getAxis().isHorizontal()) return null;
        var state = defaultBlockState().setValue(FACING, facing);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack stack) {
        if (kind == Kind.FULL_LENGTH) level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (kind == Kind.FULL_LENGTH && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            var below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        Direction face = kind == Kind.PANEL ? state.getValue(FACING) : Direction.UP;
        BlockPos support = pos.relative(face.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, face);
    }

    @Override @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (kind == Kind.FULL_LENGTH && direction == (state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != state.getValue(HALF)
                    ? state.setValue(FACING, neighbor.getValue(FACING)) : Blocks.AIR.defaultBlockState();
        }
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.getAbilities().instabuild && kind == Kind.FULL_LENGTH
                && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lower = pos.below();
            if (level.getBlockState(lower).is(this)) level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind == Kind.FULL_LENGTH && state.getValue(HALF) == DoubleBlockHalf.UPPER ? null : new MirrorBlockEntity(pos, state);
    }

    @Override public List<MirrorGeometry> mirrorGeometry(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? List.of() : surfaces.get(state.getValue(FACING));
    }
    @Override public boolean mergesSurfaces() { return kind == Kind.PANEL; }
    @Override @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shapes.get(state.getValue(FACING)); }
    @Override @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, HALF); }
}
