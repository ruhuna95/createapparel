package com.rexhub.createapparel.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public final class HangerGeometry {
    public static final Vec3 MODEL_ANCHOR = new Vec3(0.5, 0.6875, 0.6875);
    public static final float JOINT_CLEARANCE_PIXELS = 0.02F;
    public static final float WALL_RECESS_PIXELS = 2;
    public static final double DISPLAY_ANCHOR_Y = (10 + JOINT_CLEARANCE_PIXELS) / 16.0;
    public static final Vec3 WALL_ANCHOR = new Vec3(MODEL_ANCHOR.x, DISPLAY_ANCHOR_Y, MODEL_ANCHOR.z + WALL_RECESS_PIXELS / 16.0);

    public record Part(float x1, float y1, float z1, float x2, float y2, float z2, String texture, float angle) {
        OrientedBox box() {
            return OrientedBox.pixels(x1, y1, z1, x2, y2, z2)
                    .rotateZ(new Vec3(8 / 16.0, 13 / 16.0, 11 / 16.0), angle);
        }
    }

    public static final List<Part> PARTS = List.of(
            new Part(3, 10, 10.5F, 13, 11, 11.5F, "wood", 0),
            new Part(8, 13, 10.5F - JOINT_CLEARANCE_PIXELS, 14, 14, 11.5F + JOINT_CLEARANCE_PIXELS, "wood", -22.5F),
            new Part(2, 13, 10.5F - JOINT_CLEARANCE_PIXELS, 8, 14, 11.5F + JOINT_CLEARANCE_PIXELS, "wood", 22.5F),
            new Part(6.5F, 13.5F, 10.5F - 2 * JOINT_CLEARANCE_PIXELS, 7.5F, 16, 11.5F + 2 * JOINT_CLEARANCE_PIXELS, "metal", 0),
            new Part(6.5F, 16, 10.5F - 2 * JOINT_CLEARANCE_PIXELS, 9.5F, 16.5F, 11.5F + 2 * JOINT_CLEARANCE_PIXELS, "metal", 0),
            new Part(8.5F, 14.5F, 10.5F - 2 * JOINT_CLEARANCE_PIXELS, 9.5F, 16, 11.5F + 2 * JOINT_CLEARANCE_PIXELS, "metal", 0));
    public static final Part WALL_HOOK = new Part(7.5F, 14, PARTS.get(3).z2(), 8.5F, 15, 15, "metal", 0);
    private static final VoxelShape MODEL = createModelShape();
    private static final VoxelShape WALL_HANGER = MODEL.move(0, WALL_ANCHOR.y - MODEL_ANCHOR.y, WALL_ANCHOR.z - MODEL_ANCHOR.z);
    private static final VoxelShape[][] RACK_HANGERS = createRackShapes();
    private static final VoxelShape[][][] ORIENTED_RACK_HANGERS = createOrientedRackShapes();

    private HangerGeometry() { }

    public static Vec3 rackAnchor(RackMountType mount, int slot) {
        return mount == RackMountType.WALL ? new Vec3(0.5, DISPLAY_ANCHOR_Y, (2 + 4 * slot) / 16.0)
                : new Vec3((14 - 4 * slot) / 16.0, DISPLAY_ANCHOR_Y, 0.5);
    }

    public static VoxelShape wallHanger() { return WALL_HANGER; }

    public static VoxelShape rackHanger(RackMountType mount, int slot) {
        return RACK_HANGERS[mount.ordinal()][slot];
    }

    public static VoxelShape rackHanger(RackMountType mount, int slot, Direction facing) {
        return ORIENTED_RACK_HANGERS[mount.ordinal()][slot][facing.get2DDataValue()];
    }

    private static VoxelShape[][][] createOrientedRackShapes() {
        var shapes = new VoxelShape[RackMountType.values().length][4][4];
        for (var mount : RackMountType.values()) for (int slot = 0; slot < 4; slot++)
            for (Direction facing : Direction.Plane.HORIZONTAL)
                shapes[mount.ordinal()][slot][facing.get2DDataValue()] = orient(rackHanger(mount, slot), facing);
        return shapes;
    }

    private static VoxelShape[][] createRackShapes() {
        var shapes = new VoxelShape[RackMountType.values().length][4];
        VoxelShape centered = MODEL.move(-MODEL_ANCHOR.x, -MODEL_ANCHOR.y, -MODEL_ANCHOR.z);
        for (var mount : RackMountType.values()) {
            var shape = mount == RackMountType.FLOOR ? rotate(centered, 3, 0) : centered;
            for (int slot = 0; slot < 4; slot++) {
                Vec3 anchor = rackAnchor(mount, slot);
                shapes[mount.ordinal()][slot] = shape.move(anchor.x, anchor.y, anchor.z);
            }
        }
        return shapes;
    }

    public static VoxelShape orient(VoxelShape shape, Direction facing) {
        return rotate(shape, switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("Non-horizontal hanger");
        }, 1);
    }

    private static VoxelShape rotate(VoxelShape shape, int turns, double extent) {
        if (shape.isEmpty()) return Shapes.empty();
        var furniture = shape instanceof FurnitureShape typed ? typed : (FurnitureShape) FurnitureShape.combine(shape);
        return furniture.turnY(turns, extent);
    }

    private static VoxelShape createModelShape() {
        var bounds = PARTS.stream().map(part -> part.box().bounds()).reduce(net.minecraft.world.phys.AABB::minmax).orElseThrow();
        return FurnitureShape.of(List.of(OrientedBox.axisAligned(bounds)));
    }
}
