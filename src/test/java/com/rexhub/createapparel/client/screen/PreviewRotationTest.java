package com.rexhub.createapparel.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewRotationTest {
    @Test
    void startsAtFrontAndPausesLongerAtFrontThanBack() {
        var rotation = new PreviewRotation();
        rotation.reset(12345);
        assertEquals(0, rotation.angle(12345));
        assertEquals(0, rotation.angle(15344));
        assertEquals(90, rotation.angle(16345), 0.001);
        assertEquals(180, rotation.angle(17345));
        assertEquals(180, rotation.angle(18344));
        assertEquals(270, rotation.angle(19345), 0.001);
        assertEquals(0, rotation.angle(20345));
        assertEquals(0, rotation.angle(23344));
    }

    @Test
    void draggingPausesAutomaticMotionAndReleaseReturnsByShortestPath() {
        var rotation = new PreviewRotation();
        rotation.reset(0);
        rotation.startDrag(4000);
        assertEquals(90, rotation.angle(9000), 0.001);
        rotation.drag(140);
        assertEquals(-60, rotation.angle(9000), 0.001);
        assertTrue(rotation.release(9000));
        assertEquals(-30, rotation.angle(9300), 0.001);
        assertEquals(0, rotation.angle(9600));
        assertEquals(0, rotation.angle(12599));
        assertEquals(90, rotation.angle(13600), 0.001);
        assertFalse(rotation.release(13600));
    }

    @Test
    void dragCanInterruptReturnAndResetRestoresInitialPause() {
        var rotation = new PreviewRotation();
        rotation.reset(0);
        rotation.startDrag(0);
        rotation.drag(60);
        rotation.release(1000);
        rotation.startDrag(1300);
        assertEquals(45, rotation.angle(5000), 0.001);
        rotation.reset(5000);
        assertFalse(rotation.isDragging());
        assertEquals(0, rotation.angle(7999));
    }
}
