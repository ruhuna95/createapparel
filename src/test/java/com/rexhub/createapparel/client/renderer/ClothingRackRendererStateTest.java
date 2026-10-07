package com.rexhub.createapparel.client.renderer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClothingRackRendererStateTest {
    @Test
    void rendererSnapshotsStateOnceAndReusesUsabilityWithoutConnectionLookups() throws IOException {
        var source = Files.readString(Path.of("src/main/java/com/rexhub/createapparel/client/renderer/ClothingRackRenderer.java"));
        assertEquals(1, source.split("rack\\.getBlockState\\(\\)", -1).length - 1);
        for (var forbidden : new String[]{"rack.isSlotUsable", "connected(", "getChunk(", "level.getBlockState(", "wallAnchored("})
            assertFalse(source.contains(forbidden), "Rendering must not query world connections: " + forbidden);
        assertTrue(source.contains("mount.isSlotUsable(leftConnected, rightConnected, slot)"));
        assertEquals(3, source.split("usable\\[slot\\]", -1).length - 1,
                "One usability calculation, reused by hanger and garment loops");
    }
}
