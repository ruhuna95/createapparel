package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.fashion.PurchasedRegisterRef;
import com.rexhub.createapparel.photo.AdvertisingPhotoData;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;
import java.util.List;

public final class AdvertisementPanelBlock extends BaseEntityBlock {
    public enum Kind { POSTER, BILLBOARD, SIGNAGE }
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);
    private final Kind kind;
    public AdvertisementPanelBlock(Properties properties, Kind kind) {
        super(properties); this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }
    public Kind kind() { return kind; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }
    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (kind == Kind.POSTER && !context.getClickedFace().getAxis().isHorizontal()) return null;
        Direction facing = context.getClickedFace().getAxis().isHorizontal() ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        var clicked = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        if (kind != Kind.POSTER && clicked.is(this)) facing = clicked.getValue(FACING);
        var state = defaultBlockState().setValue(FACING, facing);
        if (kind == Kind.POSTER) {
            var other = context.getClickedPos().above();
            if (other.getY() >= context.getLevel().getMaxBuildHeight()
                    || !context.getLevel().hasChunk(other.getX() >> 4, other.getZ() >> 4) || !context.getLevel().getWorldBorder().isWithinBounds(other)
                    || !context.getLevel().getBlockState(other).canBeReplaced(context)
                    || !state.canSurvive(context.getLevel(), context.getClickedPos()) || !state.canSurvive(context.getLevel(), other)) return null;
        }
        return state;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!(level instanceof ServerLevel server) || !(placer instanceof Player player)
                || !(level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity panel)) return;
        panel.initialize(player, stack.hasTag() ? stack.getOrCreateTag() : new net.minecraft.nbt.CompoundTag());
        if (kind == Kind.POSTER) {
            var other = pos.above();
            level.setBlock(other, state.setValue(PART, 1), 3);
            var content = panel.content(); var id = panel.groupId();
            panel.group(id, pos, 1, 2, content);
            if (level.getBlockEntity(other) instanceof AdvertisementPanelBlockEntity follower) follower.group(id, pos, 1, 2, null);
        } else server.scheduleTick(pos, this, 1);
    }
    @Override @SuppressWarnings("deprecation") public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AdvertisementPanelBlockEntity(pos, state); }
    @Override @SuppressWarnings("deprecation") public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int thickness = kind == Kind.POSTER ? 1 : 2;
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(0, 0, 16 - thickness, 16, 16, 16);
            case SOUTH -> Block.box(0, 0, 0, 16, 16, thickness);
            case WEST -> Block.box(16 - thickness, 0, 0, 16, 16, 16);
            default -> Block.box(0, 0, 0, thickness, 16, 16);
        };
    }
    @Override @SuppressWarnings("deprecation") public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        if (kind != Kind.POSTER) return true;
        var wall = pos.relative(state.getValue(FACING).getOpposite());
        return level.hasChunk(wall.getX() >> 4, wall.getZ() >> 4)
                && (level.getBlockState(wall).isFaceSturdy(level, wall, state.getValue(FACING)) || DiodeBlock.isDiode(level.getBlockState(wall)));
    }
    @Override @SuppressWarnings("deprecation") public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (kind != Kind.POSTER && level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
        return kind == Kind.POSTER && direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
    @Override @SuppressWarnings("deprecation") public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity panel) PanelGroupManager.repair(level, panel);
    }
    @Override @SuppressWarnings("deprecation") public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind != Kind.POSTER && player.getItemInHand(hand).is(asItem())) return InteractionResult.PASS;
        if (kind == Kind.POSTER && !(player.isShiftKeyDown() && player.getItemInHand(hand).is(com.rexhub.createapparel.init.ModItems.SMART_PHONE.get())))
            return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity panel)) return InteractionResult.PASS;
        var controller = panel.controller(); if (controller == null || !controller.mayEdit(player)) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown() && player.getItemInHand(hand).is(com.rexhub.createapparel.init.ModItems.SMART_PHONE.get())) {
            var reference = com.rexhub.createapparel.fashion.RetailLinkSession.selected(player);
            if (reference != null && reference.resolve((ServerLevel) level) != null) {
                boolean unlink = reference.equals(controller.linkedRegister());
                controller.link(unlink ? null : reference);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.createapparel.register." + (unlink ? "unlinked" : "linked")), true);
            }
        } else if (player instanceof net.minecraft.server.level.ServerPlayer server) AdvertisementControlMenu.open(server, controller.getBlockPos(), hand);
        return InteractionResult.CONSUME;
    }
    public static ItemStack drop(AdvertisementPanelBlockEntity panel) {
        if (panel.kind() != Kind.POSTER && panel.getLevel() instanceof ServerLevel server) PanelGroupManager.repair(server, panel);
        var result = new ItemStack(panel.getBlockState().getBlock()); var controller = panel.controller();
        if (controller != null) {
            if (panel.kind() == Kind.SIGNAGE) {
                result.getOrCreateTag().putInt("AdvertisementDataVersion", 2);
                result.getOrCreateTag().putString("Channel", controller.channel());
                if (!controller.sceneLayouts().isEmpty()) result.getOrCreateTag().put("SceneLayouts", controller.content().getCompound("SceneLayouts"));
            }
            else if ((panel.kind() != Kind.BILLBOARD || (long) controller.width() * controller.height() == 1)
                    && (controller.localSnapshot().photo() != null || controller.localSnapshot().layoutOverride() != null))
                result.getOrCreateTag().put("Advertisement", controller.localSnapshot().save());
        }
        return result;
    }
    @Override @SuppressWarnings("deprecation") public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (kind == Kind.POSTER && state.getValue(PART) == 1) return List.of();
        var entity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        return entity instanceof AdvertisementPanelBlockEntity panel ? List.of(drop(panel)) : List.of(new ItemStack(this));
    }
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && kind == Kind.POSTER && state.getValue(PART) == 1) {
            var other = posterOther(level, pos, state);
            if (level.getBlockState(other).is(this)) level.destroyBlock(other, !player.isCreative(), player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
    @Override @SuppressWarnings("deprecation") public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity panel) {
            if (kind == Kind.POSTER) {
                var other = posterOther(level, pos, state);
                if (level.getBlockState(other).is(this)) {
                    if (state.getValue(PART) == 1) level.destroyBlock(other, true);
                    else level.setBlock(other, Blocks.AIR.defaultBlockState(), 35);
                }
            } else if (!moving) PanelGroupManager.removed(server, panel);
        }
        super.onRemove(state, level, pos, next, moving);
    }
    private static BlockPos posterOther(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity panel && panel.width() == 2 && panel.height() == 1)
            return pos.relative(state.getValue(PART) == 0 ? state.getValue(FACING).getCounterClockWise() : state.getValue(FACING).getClockWise());
        return state.getValue(PART) == 0 ? pos.above() : pos.below();
    }
}
