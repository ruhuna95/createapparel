package com.rexhub.createapparel.garment;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MetalAccessoryDataTest {
    @Test
    void allTwelveAppearancesRoundTripWithoutFabricOrStyleData() {
        Set<MetalAccessoryData> appearances = new HashSet<>();
        for (var metal : MetalAccessoryData.MetalColor.values()) {
            for (var gem : MetalAccessoryData.Gem.values()) {
                var data = new MetalAccessoryData(metal, gem);
                var tag = data.serializeNBT();
                assertEquals(Set.of("metal_color", "gem"), tag.getAllKeys());
                assertEquals(data, MetalAccessoryData.fromNBT(tag));
                assertTrue(appearances.add(data));
                assertEquals(metal.tintRgb(), data.primaryTintRgb());
                assertEquals(gem.tintRgb(), data.secondaryTintRgb().orElseThrow());
                assertTrue(data.accentTintRgb().isEmpty());
            }
        }
        assertEquals(12, appearances.size());
    }

    @Test
    void invalidAppearanceIsRejectedWithoutChangingSavedNbt() {
        for (String key : Set.of("metal_color", "gem")) {
            CompoundTag tag = MetalAccessoryData.DEFAULT.serializeNBT();
            tag.putString(key, "unknown");
            CompoundTag before = tag.copy();
            assertThrows(IllegalArgumentException.class, () -> MetalAccessoryData.fromNBT(tag));
            assertEquals(before, tag);
            tag.remove(key);
            assertThrows(IllegalArgumentException.class, () -> MetalAccessoryData.fromNBT(tag));
        }
    }
}
