package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.route.CrossingCondition;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrossingConditionTest {
    @Test void defaultAndLegacyConditionsUseThresholdOne() {
        var condition = condition(1, false);
        var saved = condition.save(); saved.remove("Threshold");
        assertEquals(1, CrossingCondition.UNSET.threshold());
        assertEquals(condition, CrossingCondition.load(saved));
    }

    @Test void thresholdsAreInclusiveAndInversionNeverOpensAnUnavailableControl() {
        for (int threshold = 1; threshold <= 15; threshold++) for (boolean inverted : new boolean[]{false, true}) {
            var condition = condition(threshold, inverted);
            for (int power = 0; power <= 15; power++) assertEquals(inverted != (power >= threshold), condition.active(power));
            assertFalse(condition.active(-1));
            assertFalse(condition.disconnected().active(15));
        }
    }

    @Test void invalidThresholdsAreRejectedAndCorruptSavesLoadClosed() {
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 16, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> condition(value, false));
            var saved = condition(1, true).save(); saved.putInt("Threshold", value);
            assertEquals(CrossingCondition.UNSET, CrossingCondition.load(saved));
        }
    }

    @Test void saveAndDisconnectionPreserveThresholdAndInversion() {
        var condition = condition(12, true);
        assertEquals(condition, CrossingCondition.load(condition.save()));
        assertEquals(12, condition.disconnected().threshold());
        assertTrue(condition.disconnected().inverted());
        assertFalse(condition.disconnected().configured());
    }

    private static CrossingCondition condition(int threshold, boolean inverted) {
        return new CrossingCondition(CrossingCondition.Mode.REDSTONE, BlockPos.ZERO, "", "", "", inverted, threshold);
    }
}
