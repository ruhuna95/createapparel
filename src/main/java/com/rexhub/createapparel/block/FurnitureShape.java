package com.rexhub.createapparel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;

public final class FurnitureShape extends ArrayVoxelShape {
    private final List<OrientedBox> parts;

    private FurnitureShape(List<OrientedBox> parts, Grid grid) {
        super(grid.cells, grid.xs, grid.ys, grid.zs);
        this.parts = List.copyOf(parts);
    }

    public static VoxelShape of(List<OrientedBox> parts) {
        return parts.isEmpty() ? Shapes.empty() : new FurnitureShape(parts, Grid.of(parts));
    }

    public static VoxelShape combine(VoxelShape... shapes) {
        var parts = new ArrayList<OrientedBox>();
        for (var shape : shapes) {
            if (shape instanceof FurnitureShape furniture) parts.addAll(furniture.parts);
            else for (var box : shape.toAabbs()) parts.add(OrientedBox.axisAligned(box));
        }
        return of(parts);
    }

    public VoxelShape turnY(int turns, double extent) {
        return of(parts.stream().map(part -> part.turnY(turns, extent)).toList());
    }

    public boolean contains(Vec3 point, double tolerance) {
        return parts.stream().anyMatch(part -> part.contains(point, tolerance));
    }

    public int partCount() { return parts.size(); }

    public List<OrientedBox> parts() { return parts; }

    @Override
    public VoxelShape move(double x, double y, double z) {
        var offset = new Vec3(x, y, z);
        return new FurnitureShape(parts.stream().map(part -> part.move(offset)).toList(),
                new Grid((BitSetDiscreteVoxelShape) shape, shifted(Direction.Axis.X, x),
                        shifted(Direction.Axis.Y, y), shifted(Direction.Axis.Z, z)));
    }

    private double[] shifted(Direction.Axis axis, double offset) {
        return Arrays.stream(getCoords(axis).toDoubleArray()).map(value -> value + offset).toArray();
    }

    @Override
    public VoxelShape optimize() { return this; }

    @Override
    public void forAllEdges(Shapes.DoubleLineConsumer consumer) {
        for (var part : parts) part.edges(consumer);
    }

    @Override
    public void forAllBoxes(Shapes.DoubleLineConsumer consumer) {
        for (var part : parts) {
            var box = part.bounds();
            consumer.consume(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }
    }

    @Nullable
    @Override
    public BlockHitResult clip(Vec3 start, Vec3 end, BlockPos pos) {
        var offset = Vec3.atLowerCornerOf(pos);
        var localStart = start.subtract(offset);
        var localEnd = end.subtract(offset);
        var direction = localEnd.subtract(localStart);
        if (direction.lengthSqr() < 1.0E-7) return null;
        var inside = localStart.add(direction.scale(0.001));
        if (contains(inside, 0)) return new BlockHitResult(inside.add(offset),
                Direction.getNearest(direction.x, direction.y, direction.z).getOpposite(), pos, true);
        BlockHitResult hit = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (var part : parts) {
            var point = part.clip(localStart, localEnd);
            if (point.isEmpty()) continue;
            double distance = point.get().distanceToSqr(localStart);
            if (distance < nearest) {
                nearest = distance;
                hit = new BlockHitResult(point.get().add(offset), part.hitDirection(point.get()), pos, false);
            }
        }
        return hit;
    }

    @Override
    public double collide(Direction.Axis axis, AABB box, double distance) {
        if (Math.abs(distance) < 1.0E-7) return 0;
        for (var part : parts) {
            distance = part.collide(axis, box, distance);
            if (Math.abs(distance) < 1.0E-7) return 0;
        }
        return distance;
    }

    private record Grid(BitSetDiscreteVoxelShape cells, double[] xs, double[] ys, double[] zs) {
        static Grid of(List<OrientedBox> parts) {
            var boxes = parts.stream().map(OrientedBox::bounds).toList();
            double[] xs = coordinates(boxes, Direction.Axis.X), ys = coordinates(boxes, Direction.Axis.Y);
            double[] zs = coordinates(boxes, Direction.Axis.Z);
            var cells = new BitSetDiscreteVoxelShape(xs.length - 1, ys.length - 1, zs.length - 1);
            for (var box : boxes) {
                int x1 = Arrays.binarySearch(xs, box.minX), x2 = Arrays.binarySearch(xs, box.maxX);
                int y1 = Arrays.binarySearch(ys, box.minY), y2 = Arrays.binarySearch(ys, box.maxY);
                int z1 = Arrays.binarySearch(zs, box.minZ), z2 = Arrays.binarySearch(zs, box.maxZ);
                for (int x = x1; x < x2; x++) for (int y = y1; y < y2; y++) for (int z = z1; z < z2; z++) cells.fill(x, y, z);
            }
            return new Grid(cells, xs, ys, zs);
        }

        private static double[] coordinates(List<AABB> boxes, Direction.Axis axis) {
            return boxes.stream().flatMapToDouble(box -> DoubleStream.of(box.min(axis), box.max(axis))).distinct().sorted().toArray();
        }
    }
}
