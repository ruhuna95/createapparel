package com.rexhub.createapparel.apparel;

import com.rexhub.createapparel.garment.GarmentSlot;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApparelBodyModeTest {
    private static final Set<GarmentSlot> EXPECTED_AUTOMATIC_TRIGGER_SLOTS = EnumSet.of(
            GarmentSlot.INNER_TOP,
            GarmentSlot.TOP,
            GarmentSlot.OUTER,
            GarmentSlot.INNER_BOTTOM,
            GarmentSlot.BOTTOM,
            GarmentSlot.LEGWEAR,
            GarmentSlot.FEET
    );

    @Test
    void limitsAutomaticTriggerToBodyGarmentSlots() {
        for (GarmentSlot slot : GarmentSlot.values()) {
            assertEquals(
                    EXPECTED_AUTOMATIC_TRIGGER_SLOTS.contains(slot),
                    ApparelBodyMode.isAutomaticTriggerSlot(slot),
                    slot.getSerializedName()
            );
        }
    }
}
