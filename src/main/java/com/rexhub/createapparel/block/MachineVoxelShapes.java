package com.rexhub.createapparel.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

final class MachineVoxelShapes {
    private static final Map<Direction, VoxelShape> SPINNING_MACHINE = horizontalShapes(Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
            Block.box(1.0D, 2.0D, 2.0D, 2.0D, 12.0D, 14.0D),
            Block.box(0.0D, 2.0D, 0.0D, 2.0D, 12.0D, 2.0D),
            Block.box(0.0D, 2.0D, 14.0D, 2.0D, 12.0D, 16.0D),
            Block.box(14.0D, 2.0D, 2.0D, 15.0D, 12.0D, 14.0D),
            Block.box(14.0D, 2.0D, 0.0D, 16.0D, 12.0D, 2.0D),
            Block.box(14.0D, 2.0D, 14.0D, 16.0D, 12.0D, 16.0D),
            Block.box(3.0D, 2.0D, 2.0D, 4.0D, 14.0D, 14.0D),
            Block.box(12.0D, 2.0D, 2.0D, 13.0D, 14.0D, 14.0D),
            Block.box(2.0D, 7.0D, 7.0D, 14.0D, 9.0D, 9.0D)
    ));
    private static final Map<Direction, VoxelShape> LOOM = horizontalShapes(Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 2.0D, 23.0D, 4.0D),
            Block.box(14.0D, 0.0D, 0.0D, 16.0D, 23.0D, 4.0D),
            Block.box(14.0D, 0.0D, 14.0D, 16.0D, 12.0D, 16.0D),
            Block.box(0.0D, 0.0D, 14.0D, 2.0D, 12.0D, 16.0D),
            Block.box(1.0D, 2.0D, 4.0D, 2.0D, 12.0D, 14.0D),
            Block.box(14.0D, 2.0D, 4.0D, 15.0D, 12.0D, 14.0D),
            Block.box(0.0D, 0.0D, 4.0D, 16.0D, 2.0D, 14.0D),
            Block.box(2.0D, 20.0D, 1.0D, 14.0D, 22.0D, 3.0D),
            Block.box(2.0D, 8.0D, 14.0D, 14.0D, 10.0D, 16.0D),
            Block.box(2.0D, 10.0D, 1.0D, 14.0D, 12.0D, 3.0D),
            Block.box(2.0D, 0.0D, 0.0D, 14.0D, 2.0D, 4.0D),
            Block.box(2.0D, 3.0D, 10.0D, 3.0D, 18.0D, 12.0D),
            Block.box(13.0D, 3.0D, 10.0D, 14.0D, 18.0D, 12.0D),
            Block.box(3.0D, 14.0D, 10.0D, 13.0D, 18.0D, 12.0D),
            Block.box(3.0D, 3.0D, 10.0D, 13.0D, 7.0D, 12.0D)
    ));
    private static final Map<Direction, VoxelShape> SEWING_MACHINE = horizontalShapes(Shapes.or(
            Block.box(2.0D, 0.0D, 0.0D, 14.0D, 1.0D, 16.0D),
            Block.box(5.0D, 1.0D, 1.0D, 11.0D, 13.0D, 5.0D),
            Block.box(6.0D, 10.0D, 5.0D, 10.0D, 13.0D, 9.0D),
            Block.box(5.5D, 8.0D, 9.0D, 10.5D, 14.0D, 14.0D),
            Block.box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 1.0D),
            Block.box(7.0D, 5.0D, 10.5D, 9.0D, 17.0D, 12.5D)
    ));

    private MachineVoxelShapes() {
    }

    static VoxelShape spinningMachine(Direction facing) {
        return SPINNING_MACHINE.get(facing);
    }

    static VoxelShape loom(Direction facing) {
        return LOOM.get(facing);
    }

    static VoxelShape sewingMachine(Direction facing) {
        return SEWING_MACHINE.get(facing);
    }

    private static Map<Direction, VoxelShape> horizontalShapes(VoxelShape southShape) {
        EnumMap<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        VoxelShape current = southShape.optimize();
        for (Direction direction : new Direction[]{
                Direction.SOUTH,
                Direction.WEST,
                Direction.NORTH,
                Direction.EAST
        }) {
            shapes.put(direction, current);
            current = rotateClockwise(current);
        }
        return Map.copyOf(shapes);
    }

    private static VoxelShape rotateClockwise(VoxelShape shape) {
        VoxelShape[] rotated = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> rotated[0] = Shapes.or(
                rotated[0],
                Shapes.box(1.0D - maxZ, minY, minX, 1.0D - minZ, maxY, maxX)
        ));
        return rotated[0].optimize();
    }
}
