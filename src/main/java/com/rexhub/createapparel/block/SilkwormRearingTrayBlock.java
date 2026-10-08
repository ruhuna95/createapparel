package com.rexhub.createapparel.block;

import com.rexhub.createapparel.block.entity.SilkwormRearingTrayBlockEntity;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SilkwormRearingTrayBlock extends HorizontalShaftMachineBlock
        implements IBE<SilkwormRearingTrayBlockEntity> {
    public static final double STRESS_IMPACT = 4;
    private static final VoxelShape BODY_SHAPE = Shapes.or(
            Block.box(1, 12, 1, 15, 16, 15), Block.box(4, 4, 4, 12, 12, 12),
            Block.box(2, 0, 2, 4, 12, 4), Block.box(12, 0, 2, 14, 12, 4),
            Block.box(2, 0, 12, 4, 12, 14), Block.box(12, 0, 12, 14, 12, 14));
    private static final VoxelShape X_SHAPE = Shapes.or(BODY_SHAPE, Block.box(0, 6, 6, 16, 10, 10));
    private static final VoxelShape Z_SHAPE = Shapes.or(BODY_SHAPE, Block.box(6, 6, 0, 10, 10, 16));

    public SilkwormRearingTrayBlock(Properties properties) { super(properties); }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return onBlockEntityUse(level, pos, tray -> tray.interact(player, hand));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean moving) {
        withBlockEntityDo(level, pos, SilkwormRearingTrayBlockEntity::updateRedstone);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.getAbilities().instabuild) {
            withBlockEntityDo(level, pos, tray -> {
                if (tray.hasStoredContents()) Block.popResource(level, pos, tray.storedItem());
            });
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public Class<SilkwormRearingTrayBlockEntity> getBlockEntityClass() { return SilkwormRearingTrayBlockEntity.class; }

    @Override
    public BlockEntityType<? extends SilkwormRearingTrayBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SILKWORM_REARING_TRAY.get();
    }

    @Override
    protected VoxelShape getMachineShape(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis() == net.minecraft.core.Direction.Axis.Z
                ? X_SHAPE : Z_SHAPE;
    }
}
