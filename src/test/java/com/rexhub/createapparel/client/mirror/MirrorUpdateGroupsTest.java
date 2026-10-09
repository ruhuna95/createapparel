package com.rexhub.createapparel.client.mirror;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MirrorUpdateGroupsTest {
    @Test void hugeConnectedGroupsKeepEverySurfaceButUseBoundedMemoryCohorts() {
        var entries = java.util.stream.IntStream.range(0, 100).mapToObj(i -> entry("plane" + i, "world", i, 0, i)).toList();
        var groups = MirrorUpdateGroups.build(entries);
        assertEquals(1, groups.size());
        var cohorts = MirrorUpdateGroups.bounded(groups.get(0), 16, 8L * 1024 * 1024, ignored -> 2048L * 1152);
        assertTrue(cohorts.stream().allMatch(cohort -> cohort.size() <= 3));
        assertEquals(groups.get(0), cohorts.stream().flatMap(List::stream).toList());
        var byCount = MirrorUpdateGroups.bounded(groups.get(0), 16, 8L * 1024 * 1024, ignored -> 256L * 128);
        assertTrue(byCount.stream().allMatch(cohort -> cohort.size() <= 16));
        assertEquals(100, byCount.stream().mapToInt(List::size).sum());
    }

    @Test void tenIndependentReflectionPlanesInOneGroupStillUseFourCaptureSlots() {
        var entries = java.util.stream.IntStream.range(0, 10).mapToObj(i -> entry("plane" + i, "world", i, 0, i)).toList();
        var group = MirrorUpdateGroups.build(entries).get(0);
        int captured = 0;
        int frames = 0;
        while (captured < group.size()) {
            var budget = new MirrorRenderPolicy.CaptureBudget(4, 50_000_000);
            while (captured < group.size() && budget.canStart(0, 1_000_000)) {
                budget.start();
                captured++;
            }
            assertTrue(budget.started() <= 4);
            frames++;
        }
        assertEquals(3, frames);
        assertEquals(10, captured);
    }
    private static MirrorUpdateGroups.Entry<String> entry(String key, Object owner, int x, int y, int z) {
        return new MirrorUpdateGroups.Entry<>(key, owner, Set.of(new BlockPos(x, y, z)), true);
    }

    @Test void topDownDiagonalBlocksSynchronizeWithoutCombiningTheirReflectionPlanes() {
        var entries = List.of(entry("first_north", "world", 1, 0, 0), entry("second_north", "world", 0, 0, 1),
                entry("first_west", "world", 1, 0, 0), entry("second_east", "world", 0, 0, 1));
        var groups = MirrorUpdateGroups.build(entries);
        assertEquals(1, groups.size());
        assertEquals(List.of("first_north", "second_east", "first_west", "second_north"), groups.get(0));
    }

    @Test void ownersGapsAndIndependentFurnitureKeepSeparateSchedules() {
        var first = entry("first", "world", 0, 0, 0);
        assertEquals(2, MirrorUpdateGroups.build(List.of(first, entry("gap", "world", 2, 0, 2))).size());
        assertEquals(2, MirrorUpdateGroups.build(List.of(first, entry("ship", "ship", 1, 0, 1))).size());
        var furniture = new MirrorUpdateGroups.Entry<>("table", "world", Set.of(BlockPos.ZERO), false);
        assertEquals(2, MirrorUpdateGroups.build(List.of(first, furniture)).size());
    }

    @Test void connectedChainsAndMergedFacesFormOneBatchInPriorityOrder() {
        var merged = new MirrorUpdateGroups.Entry<>("merged", "world", Set.of(BlockPos.ZERO, new BlockPos(3, 0, 0)), true);
        var entries = List.of(entry("priority", "world", 5, 0, 2), entry("bridge", "world", 4, 0, 1), merged);
        assertEquals(1, MirrorUpdateGroups.build(entries).size());
        assertEquals("priority", MirrorUpdateGroups.build(entries).get(0).get(0));
    }
}
