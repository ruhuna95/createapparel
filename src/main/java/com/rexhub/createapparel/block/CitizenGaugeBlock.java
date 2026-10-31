package com.rexhub.createapparel.block;

import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.citizen.population.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;

public final class CitizenGaugeBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, IWrenchable {
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return com.rexhub.createapparel.block.BlockInteractions.itemResult(interact(state, level, pos, player, hand, hit));
    }

    public static final com.mojang.serialization.MapCodec<CitizenGaugeBlock> CODEC = simpleCodec(CitizenGaugeBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends CitizenGaugeBlock> codec() {
        return CODEC;
    }

    public CitizenGaugeBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.WALL)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, FACE); }
    @Override @javax.annotation.Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel().getBlockState(context.getClickedPos()).is(this)) return context.getLevel().getBlockState(context.getClickedPos());
        if (!context.getClickedFace().getAxis().isHorizontal()) return null;
        var state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }
    @Override @SuppressWarnings("deprecation") public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().getItem() instanceof com.rexhub.createapparel.item.CitizenGaugeItem
                && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof CitizenGaugeBlockEntity entity
                && !entity.hasPanel(targetedSlot(context.getClickedPos(), state, context.getClickLocation()));
    }
    public static int targetedSlot(BlockPos pos, BlockState state, Vec3 hit) { return FactoryPanelBlock.getTargetedSlot(pos, state, hit).ordinal(); }
    public static Vec3 slotCenter(BlockState state, int index) {
        var slot = FactoryPanelBlock.PanelSlot.values()[index];
        var point = new Vec3(.25 + slot.xOffset * .5, 1 / 16.0, .25 + slot.yOffset * .5);
        point = VecHelper.rotateCentered(point, 180, Direction.Axis.Y);
        point = VecHelper.rotateCentered(point, 90, Direction.Axis.X);
        return VecHelper.rotateCentered(point, net.minecraft.util.Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(state), Direction.Axis.Y);
    }
    @Override @SuppressWarnings("deprecation") public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return FactoryPanelBlock.canAttachLenient(level, pos, state.getValue(FACING).getOpposite());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CitizenGaugeBlockEntity(pos, state); }
    @Override @SuppressWarnings("deprecation") public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override @SuppressWarnings("deprecation") public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = Shapes.empty();
        if (level.getBlockEntity(pos) instanceof CitizenGaugeBlockEntity entity) for (int slot = 0; slot < 4; slot++) if (entity.hasPanel(slot)) {
            var center = slotCenter(state, slot);
            var face = state.getValue(FACING);
            shape = Shapes.or(shape, Shapes.create(new AABB(center, center).inflate(face.getAxis() == Direction.Axis.X ? 1 / 16.0 : .25, .25, face.getAxis() == Direction.Axis.Z ? 1 / 16.0 : .25)));
        }
        return shape;
    }
    @Override @SuppressWarnings("deprecation") public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return context instanceof EntityCollisionContext entity && entity.getEntity() == null ? getShape(state, level, pos, context) : Shapes.empty();
    }
     @SuppressWarnings("deprecation") private InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return GaugeInteractions.use(level, pos, player, hand, hit);
    }
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) { return InteractionResult.PASS; }
    @Override public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!(context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!GaugeInteractions.protectedEdit(player, context.getClickedPos())) return InteractionResult.FAIL;
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof CitizenGaugeBlockEntity entity) {
            int slot = targetedSlot(context.getClickedPos(), state, context.getClickLocation());
            if (entity.hasPanel(slot)) {
                var stack = entity.panelItem(slot); entity.removePanel(slot);
                if (!player.isCreative()) player.getInventory().placeItemBackInInventory(stack);
                if (entity.panelCount() == 0) context.getLevel().destroyBlock(context.getClickedPos(), false);
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean harvest, net.minecraft.world.level.material.FluidState fluid) {
        if (level.getBlockEntity(pos) instanceof CitizenGaugeBlockEntity entity && entity.panelCount() > 1) {
            int slot = targetedSlot(pos, state, player.pick(player.blockInteractionRange() + 1, 1, false).getLocation());
            if (entity.hasPanel(slot)) {
                var stack = entity.panelItem(slot); entity.removePanel(slot);
                if (!level.isClientSide && !player.isCreative()) popResource(level, pos, stack);
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, harvest, fluid);
    }
    @Override @SuppressWarnings("deprecation") public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var entity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        builder.withDynamicDrop(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createapparel", "gauge_panels"), consumer -> {
            if (entity instanceof CitizenGaugeBlockEntity gauge)
                for (int slot = 0; slot < 4; slot++) if (gauge.hasPanel(slot)) consumer.accept(gauge.panelItem(slot));
        });
        return super.getDrops(state, builder);
    }
    @Override @SuppressWarnings("deprecation") public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server) {
            if (moving) CitizenPopulationManager.get(server).suspendAt(pos);
            else CitizenPopulationManager.get(server).removeAt(pos);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
