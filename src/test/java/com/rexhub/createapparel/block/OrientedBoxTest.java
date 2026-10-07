package com.rexhub.createapparel.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class OrientedBoxTest {
    private static OrientedBox shoulder() {
        return OrientedBox.pixels(3, 13, 10, 8, 14, 11).rotateZ(new Vec3(0.5, 13 / 16.0, 11 / 16.0), 22.5);
    }

    @Test
    void rotatedBoxRejectsBoundingBoxCornersAndSelectsActualShoulder() {
        var shoulder = shoulder();
        var empty = new Vec3(0.4, 0.72, 0.65);
        assertTrue(shoulder.bounds().contains(empty));
        assertFalse(shoulder.contains(empty, 0));
        assertTrue(shoulder.clip(new Vec3(0.4, 0.72, 0), new Vec3(0.4, 0.72, 1)).isEmpty());
        assertTrue(shoulder.contains(new Vec3(0.4, 0.8, 0.65), 0));
        assertEquals(0.625, shoulder.clip(new Vec3(0.4, 0.8, 0), new Vec3(0.4, 0.8, 1)).orElseThrow().z, 1.0E-9);
    }

    @Test
    void movementCollisionUsesRotatedVolumeRatherThanEnclosingBox() {
        assertEquals(1, shoulder().collide(Direction.Axis.Z, new AABB(0.399, 0.719, 0, 0.401, 0.721, 0.02), 1), 1.0E-9);
        assertEquals(0.605, shoulder().collide(Direction.Axis.Z, new AABB(0.399, 0.799, 0, 0.401, 0.801, 0.02), 1), 1.0E-9);
        assertEquals(-0.3125, shoulder().collide(Direction.Axis.Z, new AABB(0.399, 0.799, 1, 0.401, 0.801, 1.02), -1), 1.0E-9);
    }

    @Test
    void verticalAndHorizontalMovementStopsAtTheSlantedFace() {
        double angle = Math.toRadians(22.5);
        double vertical = 13 / 16.0 + (0.399 - 0.5) * Math.tan(angle) - 0.42;
        assertEquals(vertical, shoulder().collide(Direction.Axis.Y, new AABB(0.399, 0.4, 0.64, 0.401, 0.42, 0.66), 1), 1.0E-9);
        double horizontal = 0.5 + ((0.799 - 13 / 16.0) * Math.cos(angle) - 1 / 16.0) / Math.sin(angle) - 0.02;
        assertEquals(horizontal, shoulder().collide(Direction.Axis.X, new AABB(0, 0.799, 0.64, 0.02, 0.801, 0.66), 1), 1.0E-9);
    }

    @Test
    void axisAlignedCollisionMatchesVanillaOffsetsAndAllowsMovingAway() {
        var box = OrientedBox.pixels(4, 4, 4, 12, 12, 12);
        for (var axis : Direction.Axis.values()) {
            Vec3 step = switch (axis) {
                case X -> new Vec3(1, 0, 0);
                case Y -> new Vec3(0, 1, 0);
                case Z -> new Vec3(0, 0, 1);
            };
            var start = new AABB(0.4, 0.4, 0.4, 0.6, 0.6, 0.6).move(step.scale(-0.6));
            assertEquals(0.25, box.collide(axis, start, 1), 1.0E-9);
            var touching = start.move(step.scale(0.25));
            assertEquals(0, box.collide(axis, touching, 1), 1.0E-9);
            assertEquals(-1, box.collide(axis, touching, -1), 1.0E-9);
            var reverse = start.move(step.scale(1.2));
            assertEquals(-0.25, box.collide(axis, reverse, -1), 1.0E-9);
        }
    }

    @Test
    void outlinesHaveTwelveRealEdgesIncludingDiagonals() {
        int[] count = {0}, diagonals = {0};
        shoulder().edges((x1, y1, z1, x2, y2, z2) -> {
            count[0]++;
            if (Math.abs(x1 - x2) > 1.0E-7 && Math.abs(y1 - y2) > 1.0E-7) diagonals[0]++;
        });
        assertEquals(12, count[0]);
        assertEquals(8, diagonals[0]);
    }

    @Test
    void quarterTurnsAndTranslationsPreserveRayPickingAndVolume() {
        var shape = shoulder();
        Vec3 start = new Vec3(0.4, 0.8, 0), end = new Vec3(0.4, 0.8, 1);
        for (int turn = 0; turn < 4; turn++) {
            var moved = shape.move(new Vec3(5, 2, -3));
            assertTrue(shape.clip(start, end).isPresent());
            assertTrue(moved.clip(start.add(5, 2, -3), end.add(5, 2, -3)).isPresent());
            assertEquals(5 / 4096.0, moved.volume(), 1.0E-9);
            shape = shape.turnY(1, 1);
            start = new Vec3(1 - start.z, start.y, start.x);
            end = new Vec3(1 - end.z, end.y, end.x);
        }
    }

    @Test
    void particleSamplingStaysInsideRotatedBox() {
        var shape = shoulder();
        var random = new Random(1);
        for (int index = 0; index < 1000; index++) assertTrue(shape.contains(shape.sample(random), 1.0E-9));
    }
}
