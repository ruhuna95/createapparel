package com.rexhub.createapparel.citizen;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CitizenPathShortcutTest {
    private static final AABB BODY = new AABB(-0.3, 1, -0.3, 0.3, 2.8, 0.3);

    @Test void touchingTheFloorIsNotAnObstacle() {
        assertFalse(CitizenPathShortcut.hits(BODY, new Vec3(8, 0, 4), new AABB(-1, 0, -1, 10, 1, 6)));
    }

    @Test void fullBodyWidthCannotClipAWallCorner() {
        assertTrue(CitizenPathShortcut.hits(BODY, new Vec3(8, 0, 4), new AABB(4, 1, 2.2, 5, 3, 3.2)));
    }

    @Test void thinObstaclesBetweenEndpointsAreNotMissed() {
        assertTrue(CitizenPathShortcut.hits(BODY, new Vec3(8, 0, 4), new AABB(3.97, 1, 1, 4.03, 3, 3)));
    }

    @Test void aCeilingAboveTheBodyDoesNotBlockTheSegment() {
        assertFalse(CitizenPathShortcut.hits(BODY, new Vec3(8, 0, 4), new AABB(-1, 2.9, -1, 10, 4, 6)));
    }

    @Test void reverseTravelUsesTheSameSweptBody() {
        var delta = new Vec3(8, 0, 4);
        var obstacle = new AABB(4, 1, 2.2, 5, 3, 3.2);
        assertEquals(CitizenPathShortcut.hits(BODY, delta, obstacle),
                CitizenPathShortcut.hits(BODY.move(delta), delta.scale(-1), obstacle));
    }
}
