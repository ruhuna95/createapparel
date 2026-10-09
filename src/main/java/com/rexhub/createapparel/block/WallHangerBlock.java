package com.rexhub.createapparel.block;

import com.rexhub.createapparel.block.entity.WallHangerBlockEntity;
import com.rexhub.createapparel.garment.HangerType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class WallHangerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private final java.util.Map<Direction, VoxelShape> shapes = new java.util.EnumMap<>(Direction.class);
    private final HangerType hangerType;

    public WallHangerBlock(Properties properties) {
        this(properties, HangerType.NORMAL);
    }

    public WallHangerBlock(Properties properties, HangerType hangerType) {
        super(properties);
        this.hangerType = hangerType;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        var bounds = HangerGeometry.wallHanger().bounds().minmax(HangerGeometry.WALL_HOOK.box().bounds())
                .minmax(new net.minecraft.world.phys.AABB(6 / 16.0, 11 / 16.0, 15 / 16.0, 10 / 16.0, 1, 1));
        var shape = FurnitureShape.of(java.util.List.of(OrientedBox.axisAligned(bounds)));
        for (Direction facing : Direction.Plane.HORIZONTAL) shapes.put(facing, HangerGeometry.orient(shape, facing));
    }

    public HangerType hangerType() { return hangerType; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, java.util.List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(hangerType.tooltipKey()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientBlockExtensions> consumer) {
        consumer.accept(new com.rexhub.createapparel.client.particle.DisplayFurnitureClientExtensions());
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (!face.getAxis().isHorizontal()) return null;
        BlockState state = defaultBlockState().setValue(FACING, face);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof WallHangerBlockEntity hanger)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var held = player.getItemInHand(hand);
        boolean changed = held.isEmpty() ? hanger.take(player) : hanger.insert(held, player.getAbilities().instabuild);
        return changed ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof WallHangerBlockEntity hanger)
            hanger.dropContents();
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WallHangerBlockEntity(pos, state); }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
}
