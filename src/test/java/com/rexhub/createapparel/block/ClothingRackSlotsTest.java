package com.rexhub.createapparel.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClothingRackSlotsTest {
    @Test
    void floorPositionsDependOnlyOnNeighborConnections() {
        for (boolean left : new boolean[]{false, true}) for (boolean right : new boolean[]{false, true}) {
            assertEquals(left, RackMountType.FLOOR.isSlotUsable(left, right, 0));
            assertTrue(RackMountType.FLOOR.isSlotUsable(left, right, 1));
            assertTrue(RackMountType.FLOOR.isSlotUsable(left, right, 2));
            assertEquals(right, RackMountType.FLOOR.isSlotUsable(left, right, 3));
        }
    }

    @Test
    void wallPositionsAlwaysUsableButOutOfRangePositionsRejected() {
        for (boolean left : new boolean[]{false, true}) for (boolean right : new boolean[]{false, true}) {
            for (int slot = 0; slot < 4; slot++) assertTrue(RackMountType.WALL.isSlotUsable(left, right, slot));
            assertFalse(RackMountType.WALL.isSlotUsable(left, right, -1));
            assertFalse(RackMountType.WALL.isSlotUsable(left, right, 4));
        }
    }
}
