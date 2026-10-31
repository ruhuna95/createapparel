package com.rexhub.createapparel.block;

import com.rexhub.createapparel.block.entity.SewingMachineBlockEntity;
import com.rexhub.createapparel.init.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SewingMachineBlock extends HorizontalShaftMachineBlock
        implements IBE<SewingMachineBlockEntity> {
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return com.rexhub.createapparel.block.BlockInteractions.itemResult(interact(state, level, pos, player, hand, hit));
    }

    public static final double STRESS_IMPACT = 8.0;

    public SewingMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(HORIZONTAL_FACING).getOpposite();
    }


    @SuppressWarnings("deprecation")
    private InteractionResult interact(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return onBlockEntityUse(level, pos, blockEntity -> blockEntity.interact(player, hand));
    }

    @Override
    public Class<SewingMachineBlockEntity> getBlockEntityClass() {
        return SewingMachineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SewingMachineBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SEWING_MACHINE.get();
    }

    @Override
    protected VoxelShape getMachineShape(BlockState state) {
        return MachineVoxelShapes.sewingMachine(state.getValue(HORIZONTAL_FACING));
    }
}
