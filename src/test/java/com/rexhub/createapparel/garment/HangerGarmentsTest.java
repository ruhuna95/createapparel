package com.rexhub.createapparel.garment;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangerGarmentsTest {
    @Test
    void acceptsOnlyTopAndOuterSlotsWithoutDependingOnIds() {
        for (var slot : GarmentSlot.values()) {
            var definition = new GarmentDefinition(ResourceLocation.fromNamespaceAndPath("test", "arbitrary"), slot,
                    ResourceLocation.fromNamespaceAndPath("test", "arbitrary_model"), "default", "default",
                    Set.of("plain"), Map.of());
            assertEquals(slot == GarmentSlot.TOP || slot == GarmentSlot.OUTER, HangerGarments.accepts(definition));
        }
    }

    @Test
    void acceptsAllowedTopEvenWhenDefaultSlotDiffers() {
        var definition = new GarmentDefinition(ResourceLocation.fromNamespaceAndPath("test", "multi_slot"), GarmentSlot.INNER_TOP,
                Set.of(GarmentSlot.INNER_TOP, GarmentSlot.TOP), ResourceLocation.fromNamespaceAndPath("test", "model"),
                "default", Map.of(), "default", Set.of("plain"), Map.of(), Map.of());
        assertTrue(HangerGarments.accepts(definition));
    }
}
