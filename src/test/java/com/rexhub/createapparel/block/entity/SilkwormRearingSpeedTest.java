package com.rexhub.createapparel.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilkwormRearingSpeedTest {
    @Test
    void standardRecipeMatchesTheProductionTimeTargets() {
        float[] speeds = {16, 32, 64, 128, 256};
        float[] seconds = {120, 90, 60, 45, 40};
        for (int i = 0; i < speeds.length; i++) {
            assertEquals(seconds[i], 1200 / SilkwormRearingSpeed.progressPerTick(speeds[i]) / 20, 0.001);
            assertEquals(SilkwormRearingSpeed.progressPerTick(speeds[i]),
                    SilkwormRearingSpeed.progressPerTick(-speeds[i]));
        }
    }

    @Test
    void intermediateSpeedsAreMonotonicAndHighRpmIsCapped() {
        float previous = 0;
        for (int rpm = 1; rpm < 2048; rpm++) {
            float rate = SilkwormRearingSpeed.progressPerTick(rpm);
            assertTrue(rate >= previous && rate <= 1.5F);
            previous = rate;
        }
        assertEquals(0, SilkwormRearingSpeed.progressPerTick(0.99F));
        assertEquals(0, SilkwormRearingSpeed.progressPerTick(Float.NaN));
        assertEquals(0, SilkwormRearingSpeed.progressPerTick(Float.POSITIVE_INFINITY));
    }
}
