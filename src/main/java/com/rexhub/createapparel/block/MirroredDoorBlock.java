package com.rexhub.createapparel.block;

import com.rexhub.createapparel.block.entity.MirrorBlockEntity;
import com.rexhub.createapparel.mirror.MirrorGeometry;
import com.rexhub.createapparel.mirror.MirrorSurfaceProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MirroredDoorBlock extends DoorBlock implements EntityBlock, MirrorSurfaceProvider {
    public MirroredDoorBlock(Properties properties) { super(BlockSetType.IRON, properties); }
    public MirroredDoorBlock(Properties properties, BlockSetType type) { super(type, properties); }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new MirrorBlockEntity(pos, state) : null;
    }

    @Override public List<MirrorGeometry> mirrorGeometry(BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return List.of();
        Direction facing = state.getValue(FACING);
        if (state.getValue(OPEN)) {
            boolean right = state.getValue(HINGE) == DoorHingeSide.RIGHT;
            facing = right ? facing.getCounterClockWise() : facing.getClockWise();
        }
        var front = MirrorGeometry.north(2 / 16.0, 2 / 16.0, 14 / 16.0, 30 / 16.0, 13 / 16.0 - 0.0005);
        var back = MirrorGeometry.north(2 / 16.0, 2 / 16.0, 14 / 16.0, 30 / 16.0, 1.0005).reverse();
        var orientation = MirrorFurnitureBlock.orientation(facing);
        return List.of(front.transform(orientation), back.transform(orientation));
    }
}
