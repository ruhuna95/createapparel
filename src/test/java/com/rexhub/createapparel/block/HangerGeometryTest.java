package com.rexhub.createapparel.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HangerGeometryTest {
    @Test
    void oneSelectionBoxEnclosesEveryModelPart() {
        var shape = HangerGeometry.wallHanger();
        assertEquals(1, shape.toAabbs().size());
        assertEquals(1, ((FurnitureShape) shape).partCount());
        var bounds = shape.bounds().inflate(1.0E-7);
        for (var part : HangerGeometry.PARTS) {
            var partBounds = part.box().bounds().move(HangerGeometry.WALL_ANCHOR.subtract(HangerGeometry.MODEL_ANCHOR));
            assertTrue(bounds.contains(new Vec3(partBounds.minX, partBounds.minY, partBounds.minZ)));
            assertTrue(bounds.contains(new Vec3(partBounds.maxX, partBounds.maxY, partBounds.maxZ)));
        }
        int[] edges = {0};
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> edges[0]++);
        assertEquals(12, edges[0]);
    }

    @Test
    void wallAndRackUseIdenticalDisplayHeightWithoutChangingRackPlacement() {
        for (var mount : RackMountType.values()) for (int slot = 0; slot < 4; slot++) {
            assertEquals(HangerGeometry.WALL_ANCHOR.y, HangerGeometry.rackAnchor(mount, slot).y);
            assertEquals((10 + HangerGeometry.JOINT_CLEARANCE_PIXELS) / 16.0, HangerGeometry.rackAnchor(mount, slot).y);
        }
        assertEquals(HangerGeometry.MODEL_ANCHOR.x, HangerGeometry.WALL_ANCHOR.x);
        assertEquals(HangerGeometry.MODEL_ANCHOR.z + HangerGeometry.WALL_RECESS_PIXELS / 16.0, HangerGeometry.WALL_ANCHOR.z);
        assertEquals(HangerGeometry.rackHanger(RackMountType.WALL, 0).bounds().minY, HangerGeometry.wallHanger().bounds().minY);
        assertEquals(HangerGeometry.rackHanger(RackMountType.WALL, 0).bounds().maxY, HangerGeometry.wallHanger().bounds().maxY);
    }

    @Test
    void centeredWallHookFitsOpeningOfRecessedHangerAndKeepsTopClearance() {
        var hook = HangerGeometry.WALL_HOOK;
        var offset = HangerGeometry.WALL_ANCHOR.subtract(HangerGeometry.MODEL_ANCHOR).scale(16);
        var stem = HangerGeometry.PARTS.get(3);
        var cap = HangerGeometry.PARTS.get(4);
        var tip = HangerGeometry.PARTS.get(5);
        assertEquals(8, (hook.x1() + hook.x2()) / 2);
        assertEquals(HangerGeometry.WALL_ANCHOR.x * 16, (hook.x1() + hook.x2()) / 2);
        assertEquals(stem.x2(), hook.x1());
        assertEquals(tip.x1(), hook.x2());
        assertTrue(hook.z1() < stem.z1() + offset.z);
        assertTrue(hook.z2() > stem.z2() + offset.z);
        assertEquals(HangerGeometry.JOINT_CLEARANCE_PIXELS, cap.y1() + offset.y - hook.y2(), 1.0E-6);
        assertEquals(13 / 16.0, HangerGeometry.WALL_ANCHOR.z);
        assertEquals(0.6875, HangerGeometry.MODEL_ANCHOR.z);
    }

    @Test
    void hangerBodyDepthAndHookOpeningStayCenteredOnSharedAnchor() {
        var anchor = HangerGeometry.MODEL_ANCHOR;
        var parts = HangerGeometry.PARTS;
        for (var part : parts) assertEquals(anchor.z * 16, (part.z1() + part.z2()) * 0.5, 1.0E-6);
        assertEquals(anchor.x * 16, (parts.get(0).x1() + parts.get(0).x2()) * 0.5, 1.0E-6);
        assertEquals(anchor.x * 16, (parts.get(3).x2() + parts.get(5).x1()) * 0.5, 1.0E-6);
        assertTrue(parts.get(3).box().bounds().intersects(parts.get(2).box().bounds()));
    }

    @Test
    void everyRackSlotHasOneCenteredBoxInBothMountsAndFourFacings() {
        for (var mount : RackMountType.values()) for (int slot = 0; slot < 4; slot++) {
            var anchor = HangerGeometry.rackAnchor(mount, slot);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var shape = HangerGeometry.rackHanger(mount, slot, facing);
                assertEquals(1, shape.toAabbs().size());
                var expected = switch (facing) {
                    case NORTH -> anchor;
                    case EAST -> new Vec3(1 - anchor.z, anchor.y, anchor.x);
                    case SOUTH -> new Vec3(1 - anchor.x, anchor.y, 1 - anchor.z);
                    case WEST -> new Vec3(anchor.z, anchor.y, 1 - anchor.x);
                    default -> throw new IllegalArgumentException("Horizontal facing required");
                };
                var center = shape.bounds().getCenter();
                assertEquals(expected.x, center.x, 1.0E-7);
                assertEquals(expected.z, center.z, 1.0E-7);
                assertEquals(expected.y + 2.25 / 16.0, center.y, 1.0E-7);
            }
        }
    }

    @Test
    void compactHookOverlapsRackPoleWithoutMovingBodyOrSharingTopSurface() {
        for (var mount : RackMountType.values()) for (int slot = 0; slot < 4; slot++) {
            var anchor = HangerGeometry.rackAnchor(mount, slot);
            var pole = OrientedBox.pixels(mount == RackMountType.WALL ? 7 : 0, 13,
                    mount == RackMountType.WALL ? 0 : 7, mount == RackMountType.WALL ? 9 : 16, 15,
                    mount == RackMountType.WALL ? 16 : 9);
            for (int index : new int[]{3, 5}) {
                var part = HangerGeometry.PARTS.get(index);
                var box = part.box().move(HangerGeometry.MODEL_ANCHOR.scale(-1));
                if (mount == RackMountType.FLOOR) box = box.turnY(3, 0);
                box = box.move(anchor);
                for (int turns = 0; turns < 4; turns++)
                    assertTrue(box.turnY(turns, 1).bounds().intersects(pole.turnY(turns, 1).bounds()));
            }
            var cap = HangerGeometry.PARTS.get(4);
            assertEquals(3, cap.x2() - cap.x1());
            assertEquals(3, cap.y2() - HangerGeometry.PARTS.get(3).y1());
            assertEquals(HangerGeometry.JOINT_CLEARANCE_PIXELS / 16.0,
                    cap.y1() / 16.0 - HangerGeometry.MODEL_ANCHOR.y + anchor.y - 15 / 16.0, 1.0E-7);
        }
        assertEquals(10, HangerGeometry.PARTS.get(0).y1());
        assertEquals(11, HangerGeometry.PARTS.get(0).y2());
        assertEquals(new Vec3(0.5, 0.6875, 0.6875), HangerGeometry.MODEL_ANCHOR);
    }
}
