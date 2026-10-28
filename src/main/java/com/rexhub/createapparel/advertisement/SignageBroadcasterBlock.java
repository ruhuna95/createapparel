package com.rexhub.createapparel.advertisement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SignageBroadcasterBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.EntityBlock {
    public SignageBroadcasterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignageBroadcasterBlockEntity(pos, state);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof SignageBroadcasterBlockEntity broadcaster) broadcaster.initialize(player);
    }
    @Override @SuppressWarnings("deprecation") public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && !state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof SignageBroadcasterBlockEntity broadcaster)
            net.minecraft.world.Containers.dropContents(level, pos, broadcaster.photos());
        super.onRemove(state, level, pos, next, moving);
    }
    @Override @SuppressWarnings("deprecation") public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override @SuppressWarnings("deprecation") public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override @SuppressWarnings("deprecation") public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getAbilities().mayBuild || !level.mayInteract(player, pos)
                || !(level.getBlockEntity(pos) instanceof SignageBroadcasterBlockEntity broadcaster) || !broadcaster.mayEdit(player)) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer server) AdvertisementControlMenu.open(server, pos, hand);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
