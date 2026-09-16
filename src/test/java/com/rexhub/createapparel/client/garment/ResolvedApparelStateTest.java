package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.garment.GarmentSlot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResolvedApparelStateTest {
    @Test
    void eachSlotResolvesOnceUntilResourceGenerationChanges() {
        AtomicInteger resolutions = new AtomicInteger();
        var garment = new ClientGarmentAssets.ResolvedGarment(null, null, List.of());
        var state = new ResolvedApparelState(slot -> {
            resolutions.incrementAndGet();
            return slot == GarmentSlot.TOP ? Optional.of(garment) : Optional.empty();
        });
        for (int frame = 0; frame < 20; frame++) {
            for (var slot : GarmentSlot.values()) state.garment(1, slot);
            assertSame(garment, state.garment(1, GarmentSlot.TOP).orElseThrow());
        }
        assertEquals(GarmentSlot.values().length, resolutions.get());
        for (var slot : GarmentSlot.values()) state.garment(2, slot);
        assertEquals(2 * GarmentSlot.values().length, resolutions.get());
    }

    @Test
    void playerAndPreviewSnapshotsDoNotShareResolvedState() {
        var world = new ClientGarmentAssets.ResolvedGarment(null, null, List.of());
        var preview = new ClientGarmentAssets.ResolvedGarment(null, null, List.of());
        var worldState = new ResolvedApparelState(slot -> Optional.of(world));
        var previewState = new ResolvedApparelState(slot -> Optional.of(preview));
        assertSame(world, worldState.garment(1, GarmentSlot.TOP).orElseThrow());
        assertSame(preview, previewState.garment(1, GarmentSlot.TOP).orElseThrow());
        assertSame(world, worldState.garment(1, GarmentSlot.TOP).orElseThrow());
    }
}
