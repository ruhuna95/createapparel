package com.rexhub.createapparel.block;

import com.rexhub.createapparel.block.entity.ClothingRackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class ClothingRackBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<RackMountType> MOUNT = EnumProperty.create("mount", RackMountType.class);
    public static final BooleanProperty LEFT_CONNECTED = BooleanProperty.create("left_connected");
    public static final BooleanProperty RIGHT_CONNECTED = BooleanProperty.create("right_connected");
    public static final BooleanProperty WALL_ANCHORED = BooleanProperty.create("wall_anchored");
    public static final BooleanProperty ISOLATED = BooleanProperty.create("isolated");
    private final java.util.Map<BlockState, VoxelShape> shapes = new java.util.HashMap<>();

    public ClothingRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(MOUNT, RackMountType.FLOOR)
                .setValue(LEFT_CONNECTED, false).setValue(RIGHT_CONNECTED, false).setValue(WALL_ANCHORED, false)
                .setValue(ISOLATED, false));
        for (var state : stateDefinition.getPossibleStates()) shapes.put(state, createShape(state));
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientBlockExtensions> consumer) {
        consumer.accept(new com.rexhub.createapparel.client.particle.DisplayFurnitureClientExtensions());
    }

    public static BlockPos lowerPos(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    public static Direction leftDirection(BlockState state) {
        return state.getValue(MOUNT) == RackMountType.WALL ? state.getValue(FACING) : state.getValue(FACING).getClockWise();
    }

    public static boolean extendsFrom(BlockState state, Direction face, boolean sneaking) {
        return face.getAxis().isHorizontal() && (state.getValue(MOUNT) == RackMountType.WALL
                ? face == state.getValue(FACING) : !sneaking && face.getAxis() != state.getValue(FACING).getAxis());
    }

    public static boolean connected(LevelReader level, BlockPos lower, BlockState state, boolean left) {
        if (state.getValue(MOUNT) == RackMountType.FLOOR && state.getValue(ISOLATED)) return false;
        Direction direction = leftDirection(state);
        BlockPos neighborPos = lower.relative(left ? direction : direction.getOpposite());
        if (!chunkAvailable(level, neighborPos)) return state.getValue(left ? LEFT_CONNECTED : RIGHT_CONNECTED);
        BlockState neighbor = level.getBlockState(neighborPos);
        return neighbor.is(state.getBlock()) && neighbor.getValue(HALF) == DoubleBlockHalf.LOWER
                && neighbor.getValue(FACING) == state.getValue(FACING) && neighbor.getValue(MOUNT) == state.getValue(MOUNT)
                && (state.getValue(MOUNT) != RackMountType.FLOOR || !neighbor.getValue(ISOLATED));
    }

    private static boolean chunkAvailable(LevelReader level, BlockPos pos) {
        return level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(pos.getX()),
                net.minecraft.core.SectionPos.blockToSectionCoord(pos.getZ()),
                net.minecraft.world.level.chunk.ChunkStatus.FULL, false) != null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        if (face == Direction.DOWN) return null;
        BlockState state = defaultBlockState();
        BlockPos clicked = pos.relative(face.getOpposite());
        BlockState clickedState = level.getBlockState(clicked);
        boolean sneaking = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        if (clickedState.is(this) && extendsFrom(clickedState, face, sneaking)) {
            state = state.setValue(FACING, clickedState.getValue(FACING)).setValue(MOUNT, clickedState.getValue(MOUNT));
        } else if (face.getAxis().isHorizontal()) {
            state = state.setValue(FACING, face).setValue(MOUNT, RackMountType.WALL);
        } else {
            state = state.setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(ISOLATED, sneaking);
        }
        if (!state.canSurvive(level, pos)) return null;
        return state.setValue(LEFT_CONNECTED, connected(level, pos, state, true))
                .setValue(RIGHT_CONNECTED, connected(level, pos, state, false))
                .setValue(WALL_ANCHORED, state.getValue(MOUNT) == RackMountType.WALL && wallAnchored(level, pos, state));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        scheduleConnections(level, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos lower = lowerPos(pos, state);
        if (state.getValue(MOUNT) == RackMountType.FLOOR) {
            BlockPos floor = lower.below();
            return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
        }
        if (hasWallAnchorOrUnloadedSupport(level, lower, state)) return true;
        Direction backward = state.getValue(FACING).getOpposite();
        BlockPos cursor = lower;
        BlockState cursorState = state;
        while (true) {
            cursor = cursor.relative(backward);
            if (!chunkAvailable(level, cursor)) return cursorState.getValue(RIGHT_CONNECTED);
            BlockState neighbor = level.getBlockState(cursor);
            if (!neighbor.is(this) || neighbor.getValue(HALF) != DoubleBlockHalf.LOWER
                    || neighbor.getValue(FACING) != state.getValue(FACING) || neighbor.getValue(MOUNT) != RackMountType.WALL) return false;
            BlockState upper = level.getBlockState(cursor.above());
            if (!upper.is(this) || upper.getValue(HALF) != DoubleBlockHalf.UPPER
                    || upper.getValue(FACING) != state.getValue(FACING) || upper.getValue(MOUNT) != RackMountType.WALL) return false;
            if (hasWallAnchorOrUnloadedSupport(level, cursor, neighbor)) return true;
            cursorState = neighbor;
        }
    }

    public static boolean wallAnchored(LevelReader level, BlockPos lower, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = lower.above().relative(facing.getOpposite());
        return chunkAvailable(level, wall) ? level.getBlockState(wall).isFaceSturdy(level, wall, facing)
                : state.getValue(WALL_ANCHORED);
    }

    private static boolean hasWallAnchorOrUnloadedSupport(LevelReader level, BlockPos lower, BlockState state) {
        return !chunkAvailable(level, lower.above().relative(state.getValue(FACING).getOpposite()))
                || wallAnchored(level, lower, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        if (direction == (lower ? Direction.UP : Direction.DOWN)) {
            if (!neighbor.is(this) || neighbor.getValue(HALF) == state.getValue(HALF)
                    || neighbor.getValue(FACING) != state.getValue(FACING) || neighbor.getValue(MOUNT) != state.getValue(MOUNT))
                return Blocks.AIR.defaultBlockState();
        }
        if ((direction == Direction.DOWN && lower || direction == state.getValue(FACING).getOpposite())
                && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        if (!level.isClientSide()) level.scheduleTick(lowerPos(pos, state), this, 1);
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(lowerPos(pos, state), this, 1);
    }

    public static void scheduleConnections(Level level, BlockPos lower, BlockState state) {
        if (level.isClientSide) return;
        level.scheduleTick(lower, state.getBlock(), 1);
        Direction left = leftDirection(state);
        for (Direction direction : new Direction[]{left, left.getOpposite()}) {
            BlockPos neighborPos = lower.relative(direction);
            if (chunkAvailable(level, neighborPos) && level.getBlockState(neighborPos).is(state.getBlock()))
                level.scheduleTick(neighborPos, state.getBlock(), 1);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) return;
        BlockState upper = level.getBlockState(pos.above());
        if (!upper.is(this) || upper.getValue(HALF) != DoubleBlockHalf.UPPER
                || upper.getValue(FACING) != state.getValue(FACING) || upper.getValue(MOUNT) != state.getValue(MOUNT)
                || !state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        if (level.getBlockEntity(pos) instanceof ClothingRackBlockEntity rack) rack.refreshConnections();
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lower = pos.below();
            BlockState lowerState = level.getBlockState(lower);
            if (lowerState.is(this) && lowerState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, lower, Block.getId(lowerState));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ClothingRackBlockEntity rack) rack.dropContents();
            scheduleConnections(level, lowerPos(pos, state), state);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos lower = lowerPos(pos, state);
        if (!(level.getBlockEntity(lower) instanceof ClothingRackBlockEntity rack)) return InteractionResult.PASS;
        int slot = slotAtHit(state, pos, hit);
        if (slot < 0 || !rack.isSlotUsable(slot)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var held = player.getItemInHand(hand);
        boolean changed = held.isEmpty() ? rack.take(slot, player)
                : rack.insert(slot, held, player.getAbilities().instabuild);
        return changed ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    public static int slotAtHit(BlockState state, BlockPos pos, BlockHitResult hit) {
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER || !hit.getBlockPos().equals(pos)) return -1;
        var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        for (int slot = 0; slot < 4; slot++) {
            if (!state.getValue(MOUNT).isSlotUsable(state.getValue(LEFT_CONNECTED), state.getValue(RIGHT_CONNECTED), slot)) continue;
            var shape = HangerGeometry.rackHanger(state.getValue(MOUNT), slot, state.getValue(FACING));
            if (shape instanceof FurnitureShape furniture && furniture.contains(local, 0.001)) return slot;
        }
        return -1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new ClothingRackBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    private static VoxelShape createShape(BlockState state) {
        boolean upper = state.getValue(HALF) == DoubleBlockHalf.UPPER;
        VoxelShape shape = Shapes.empty();
        if (state.getValue(MOUNT) == RackMountType.WALL) {
            if (upper) {
                shape = box(7, 13, 0, 9, 15, state.getValue(WALL_ANCHORED) ? 15 : 16);
                if (state.getValue(WALL_ANCHORED)) shape = FurnitureShape.combine(shape, box(6, 12, 15, 10, 16, 16));
            }
        } else {
            shape = upper ? box(0, 13, 7, 16, 15, 9) : box(0, 0, 6, 16, 2, 10);
            if (!state.getValue(LEFT_CONNECTED)) shape = FurnitureShape.combine(shape, box(13, 0, 7, 15, upper ? 15 : 16, 9));
            if (!state.getValue(RIGHT_CONNECTED)) shape = FurnitureShape.combine(shape, box(1, 0, 7, 3, upper ? 15 : 16, 9));
            if (!upper) {
                if (!state.getValue(LEFT_CONNECTED)) shape = FurnitureShape.combine(shape, box(12, 0, 2, 16, 2, 14));
                if (!state.getValue(RIGHT_CONNECTED)) shape = FurnitureShape.combine(shape, box(0, 0, 2, 4, 2, 14));
            }
        }
        if (upper) for (int slot = 0; slot < 4; slot++) {
            if (state.getValue(MOUNT).isSlotUsable(state.getValue(LEFT_CONNECTED), state.getValue(RIGHT_CONNECTED), slot))
                shape = FurnitureShape.combine(shape, HangerGeometry.rackHanger(state.getValue(MOUNT), slot));
        }
        return HangerGeometry.orient(shape, state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(MOUNT) == RackMountType.WALL && state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? Shapes.empty() : getShape(state, level, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, MOUNT, LEFT_CONNECTED, RIGHT_CONNECTED, WALL_ANCHORED, ISOLATED);
    }
}
