package com.rexhub.createapparel.block;

import com.rexhub.createapparel.block.entity.LoomBlockEntity;
import com.rexhub.createapparel.init.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LoomBlock extends HorizontalShaftMachineBlock implements IBE<LoomBlockEntity> {
    public static final double STRESS_IMPACT = 8.0;
    public static final BooleanProperty THREAD_VISIBLE = BooleanProperty.create("working");

    public LoomBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(THREAD_VISIBLE, false));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(THREAD_VISIBLE);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public Class<LoomBlockEntity> getBlockEntityClass() {
        return LoomBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LoomBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LOOM.get();
    }

    @Override
    protected VoxelShape getMachineShape(BlockState state) {
        return MachineVoxelShapes.loom(state.getValue(HORIZONTAL_FACING));
    }
}
