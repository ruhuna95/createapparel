package com.rexhub.createapparel.client.screen;

import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PatternEditSessionTest {
    @Test
    void unchangedOrRestoredSelectionDoesNotSend() {
        var initial = pattern("t_shirt", Optional.empty());
        assertTrue(new PatternEditSession(initial).close(pattern("t_shirt", Optional.empty())).isEmpty());
    }

    @Test
    void changedGarmentIsSentOnlyOnceAcrossCloseAndRemoved() {
        var session = new PatternEditSession(pattern("t_shirt", Optional.empty()));
        var changed = pattern("sweater", Optional.empty());
        assertEquals(Optional.of(changed), session.close(changed));
        assertTrue(session.close(changed).isEmpty());
    }

    @Test
    void savedDesignChangeIsDetected() {
        var session = new PatternEditSession(pattern("t_shirt", Optional.empty()));
        var changed = new PatternData(ResourceLocation.parse("createapparel:t_shirt"), "stripes",
                Optional.empty(), Optional.empty());
        assertEquals(Optional.of(changed), session.close(changed));
    }

    @Test
    void invalidOrEmptySelectionNeverSends() {
        assertTrue(new PatternEditSession(null).close(null).isEmpty());
        assertTrue(new PatternEditSession(pattern("t_shirt", Optional.empty())).close(null).isEmpty());
    }

    @Test
    void unconfiguredPatternCanSaveTheDisplayedDefaultChoice() {
        var selected = pattern("t_shirt", Optional.empty());
        assertEquals(Optional.of(selected), new PatternEditSession(null).close(selected));
    }

    private static PatternData pattern(String garment, Optional<FabricColor> secondary) {
        return new PatternData(ResourceLocation.parse("createapparel:" + garment), "plain", secondary, Optional.empty());
    }
}
