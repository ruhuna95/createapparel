package com.rexhub.createapparel.item;

import com.rexhub.createapparel.block.ClothingRackBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public final class ClothingRackItem extends BlockItem {
    public ClothingRackItem(Block block, Properties properties) { super(block, properties); }

    @Nullable
    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        if (context instanceof RackPlacementContext) return context;
        var clicked = context.replacingClickedOnBlock() ? context.getClickedPos()
                : context.getClickedPos().relative(context.getClickedFace().getOpposite());
        var state = context.getLevel().getBlockState(clicked);
        var face = context.getClickedFace();
        if (state.getBlock() instanceof ClothingRackBlock && ClothingRackBlock.extendsFrom(state, face,
                context.getPlayer() != null && context.getPlayer().isShiftKeyDown())) {
            var target = ClothingRackBlock.lowerPos(clicked, state).relative(face);
            return context.getLevel().getBlockState(target).canBeReplaced(context)
                    ? new RackPlacementContext(BlockPlaceContext.at(context, target, face)) : null;
        }
        if (face.getAxis().isHorizontal()) {
            var target = context.getClickedPos().below();
            if (target.getY() < context.getLevel().getMinBuildHeight()
                    || !context.getLevel().getBlockState(target).canBeReplaced(context)) return null;
            return new RackPlacementContext(BlockPlaceContext.at(context, target, face));
        }
        return context;
    }

    private static final class RackPlacementContext extends BlockPlaceContext {
        private RackPlacementContext(BlockPlaceContext context) { super(context); }
    }
}
