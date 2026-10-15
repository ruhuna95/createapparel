package com.rexhub.createapparel.block;

import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.citizen.route.CitizenRouteManager;
import com.rexhub.createapparel.citizen.route.RouteMarkerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.MethodsReturnNonnullByDefault;
import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CitizenRouteMarkerBlock extends BaseEntityBlock {
    private static final VoxelShape GUIDE = box(4, 0, 4, 12, 4, 12);
    private final RouteMarkerType type;

    public CitizenRouteMarkerBlock(Properties properties, RouteMarkerType type) { super(properties); this.type = type; }
    public RouteMarkerType type() { return type; }

    public static boolean supported(LevelReader level, BlockPos pos) {
        var below = pos.below();
        return level.getFluidState(below).isEmpty() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CitizenRouteMarkerBlockEntity(pos, state); }
    @Override @SuppressWarnings("deprecation") public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof net.minecraft.world.entity.player.Player player
                && !com.rexhub.createapparel.citizen.route.RouteMarkerPermissions.holdingPhone(player)) return Shapes.empty();
        return GUIDE;
    }
    @Override @SuppressWarnings("deprecation") public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override @SuppressWarnings("deprecation") public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return supported(level, pos); }
    @Override @SuppressWarnings("deprecation") public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType pathType) { return true; }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (next.getBlock() instanceof CitizenRouteMarkerBlock) return;
        if (level instanceof ServerLevel server) CitizenRouteManager.get(server).removeAt(pos);
        super.onRemove(state, level, pos, next, moving);
    }
}
