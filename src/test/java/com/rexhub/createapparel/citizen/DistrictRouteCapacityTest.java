package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.population.*;
import com.rexhub.createapparel.citizen.route.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DistrictRouteCapacityTest {
    @BeforeAll static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }
    private static RouteMarker marker(CitizenRouteSavedData routes, int x, int y, int z) {
        var marker = RouteMarker.create(UUID.randomUUID(), new BlockPos(x, y, z), RouteMarkerType.NORMAL);
        routes.put(marker); return marker;
    }
    private static void finish(DistrictRouteCapacity capacity) {
        for (int tick = 0; tick < 1000; tick++) {
            assertTrue(capacity.tick() <= DistrictRouteCapacity.EDGE_BUDGET);
            if (capacity.ready()) return;
        }
        fail("Capacity computation must finish within a bounded number of slices");
    }
    @Test void edgesAreCountedOnceAndDensityUsesThreeMetersPerWalker() {
        var routes = new CitizenRouteSavedData(); var territory = new DistrictTerritory(); var district = UUID.randomUUID();
        territory.put(new DistrictTerritory.Seed(district, 0, 0, 20));
        var a = marker(routes, 0, 1, 0); var b = marker(routes, 6, 1, 0); routes.link(a.id(), b.id(), false);
        var capacity = new DistrictRouteCapacity(routes, territory, ignored -> true, () -> 0); finish(capacity);
        assertEquals(6, capacity.length(district), 1.0E-8);
        assertTrue(capacity.hasSpace(district, 0)); assertTrue(capacity.hasSpace(district, 1)); assertFalse(capacity.hasSpace(district, 2));
        assertEquals(0, capacity.tick());
        var c = marker(routes, 9, 1, 0); routes.link(b.id(), c.id(), false); finish(capacity);
        assertEquals(9, capacity.length(district), 1.0E-8); assertTrue(capacity.hasSpace(district, 2)); assertFalse(capacity.hasSpace(district, 3));
        routes.remove(b.id()); finish(capacity); assertEquals(0, capacity.length(district));
    }
    @Test void crossDistrictEdgesAreExcludedRatherThanSplit() {
        var routes = new CitizenRouteSavedData(); var territory = new DistrictTerritory();
        var left = new UUID(0, 1); var right = new UUID(0, 2);
        territory.put(new DistrictTerritory.Seed(left, 0, 0, 10)); territory.put(new DistrictTerritory.Seed(right, 8, 0, 10));
        var a = marker(routes, 0, 1, 0); var b = marker(routes, 8, 1, 0); routes.link(a.id(), b.id(), false);
        var capacity = new DistrictRouteCapacity(routes, territory, ignored -> true, () -> 0); finish(capacity);
        assertEquals(0, capacity.length(left)); assertEquals(0, capacity.length(right));
        territory.remove(right); finish(capacity); assertEquals(8, capacity.length(left), 1.0E-8); assertEquals(0, capacity.length(right));
    }
    @Test void diagonalVerticalAndReversedEdgesHaveStableLengths() {
        var routes = new CitizenRouteSavedData(); var territory = new DistrictTerritory(); var district = UUID.randomUUID();
        territory.put(new DistrictTerritory.Seed(district, 0, 0, 20));
        var a = marker(routes, 6, 1, 6); var b = marker(routes, 0, 4, 0); var c = marker(routes, 0, 8, 0);
        routes.link(a.id(), b.id(), false); routes.link(b.id(), c.id(), false);
        var capacity = new DistrictRouteCapacity(routes, territory, ignored -> true, () -> 0); finish(capacity);
        assertEquals(13, capacity.length(district), 1.0E-8);
    }
    @Test void unloadingInvalidatesCapacityWithoutLoadingChunks() {
        var routes = new CitizenRouteSavedData(); var territory = new DistrictTerritory(); var district = UUID.randomUUID();
        territory.put(new DistrictTerritory.Seed(district, 0, 0, 20));
        var a = marker(routes, 0, 1, 0); var b = marker(routes, 9, 1, 0); routes.link(a.id(), b.id(), false);
        var loaded = new AtomicBoolean(true); var revision = new AtomicLong();
        var capacity = new DistrictRouteCapacity(routes, territory, ignored -> loaded.get(), revision::get);
        finish(capacity); assertEquals(9, capacity.length(district), 1.0E-8);
        loaded.set(false); revision.incrementAndGet();
        assertFalse(capacity.ready()); assertFalse(capacity.hasSpace(district, 0)); assertFalse(capacity.overCapacity(district, 10));
        finish(capacity); assertEquals(0, capacity.length(district));
        assertFalse(capacity.overCapacity(district, 1), "Unloaded internal edges must not cause a false demotion");
        loaded.set(true); revision.incrementAndGet(); assertFalse(capacity.ready());
        finish(capacity); assertEquals(9, capacity.length(district), 1.0E-8);
    }
    @Test void outsideEndpointsDoNotAddThePortionPassingThroughADistrict() {
        var routes = new CitizenRouteSavedData(); var territory = new DistrictTerritory(); var district = UUID.randomUUID();
        territory.put(new DistrictTerritory.Seed(district, 0, 0, 256));
        var a = marker(routes, -10000, 1, 0); var b = marker(routes, 10000, 1, 0); routes.link(a.id(), b.id(), false);
        var capacity = new DistrictRouteCapacity(routes, territory, ignored -> true, () -> 0);
        assertEquals(1, capacity.tick()); assertTrue(capacity.ready()); assertFalse(capacity.hasSpace(district, 0));
        assertEquals(0, capacity.length(district));
    }
    @Test void edgeBudgetKeepsPartialTotalsHiddenAndExactCapacityDoesNotOscillate() {
        var routes = new CitizenRouteSavedData(); var territory = new DistrictTerritory(); var district = UUID.randomUUID();
        territory.put(new DistrictTerritory.Seed(district, 0, 0, 256));
        var previous = marker(routes, 0, 1, 0);
        for (int index = 1; index <= 100; index++) { var next = marker(routes, index, 1, 0); routes.link(previous.id(), next.id(), false); previous = next; }
        var capacity = new DistrictRouteCapacity(routes, territory, ignored -> true, () -> 0);
        assertEquals(DistrictRouteCapacity.EDGE_BUDGET, capacity.tick()); assertFalse(capacity.ready());
        assertEquals(0, capacity.length(district)); assertFalse(capacity.hasSpace(district, 0)); assertFalse(capacity.overCapacity(district, 100));
        finish(capacity); assertEquals(100, capacity.length(district)); assertEquals(34, capacity.limit(district));
        assertTrue(capacity.hasSpace(district, 33)); assertFalse(capacity.hasSpace(district, 34));
        assertFalse(capacity.overCapacity(district, 34)); assertTrue(capacity.overCapacity(district, 35));
        routes.remove(previous.id()); finish(capacity); assertEquals(33, capacity.limit(district)); assertTrue(capacity.overCapacity(district, 34));
    }
    @Test void walkerCountsFollowMembershipModeRemovalAndSaveReload() {
        var data = new CitizenPopulationSavedData(); var a = CitizenGauge.create(new BlockPos(0, 1, 0), BlockPos.ZERO);
        var b = CitizenGauge.create(new BlockPos(20, 1, 0), new BlockPos(20, 0, 0)); data.put(a); data.put(b);
        var id = UUID.randomUUID(); var state = CitizenPopulationState.DEFAULT.district(a.districtId(), 0);
        data.register(id, state, true); data.register(id, state, true); assertEquals(1, data.walkingPopulation(a.districtId()));
        data = CitizenPopulationSavedData.load(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup())); assertEquals(1, data.walkingPopulation(a.districtId()));
        data.register(id, state.district(b.districtId(), 0)); assertEquals(0, data.walkingPopulation(a.districtId())); assertEquals(1, data.walkingPopulation(b.districtId()));
        data.walking(id, false); assertEquals(0, data.walkingPopulation(b.districtId()));
        data.walking(id, true); data.remove(b.id()); assertEquals(0, data.walkingPopulation(b.districtId()));
        data.register(id, state, true); data.removeCitizen(id); assertEquals(0, data.walkingPopulation(a.districtId()));
    }
    @Test void edgeBudgetAlsoBoundsLoadChecksForSkippedEdges() {
        var routes = new CitizenRouteSavedData(); var previous = marker(routes, 0, 1, 0);
        for (int index = 1; index <= 101; index++) {
            var next = marker(routes, index, 1, 0); routes.link(previous.id(), next.id(), false); previous = next;
        }
        int[] checks = {0}; var capacity = new DistrictRouteCapacity(routes, new DistrictTerritory(), ignored -> { checks[0]++; return false; }, () -> 0);
        assertEquals(DistrictRouteCapacity.EDGE_BUDGET, capacity.tick()); assertFalse(capacity.ready()); assertEquals(DistrictRouteCapacity.EDGE_BUDGET, checks[0]);
        assertEquals(37, capacity.tick()); assertTrue(capacity.ready()); assertEquals(101, checks[0]);
    }

    @Test void newDistrictAndEdgesCannotBeJudgedAgainstAnOlderEmptySnapshot() {
        var routes = new CitizenRouteSavedData(); var territory = new DistrictTerritory(); var district = UUID.randomUUID();
        var capacity = new DistrictRouteCapacity(routes, territory, ignored -> true, () -> 0); finish(capacity);
        territory.put(new DistrictTerritory.Seed(district, 0, 0, 8));
        var a = marker(routes, 0, 1, 0); var b = marker(routes, 3, 1, 0); routes.link(a.id(), b.id(), false);
        assertFalse(capacity.ready(), "Graph and district edits invalidate the snapshot before the next population tick");
        assertFalse(capacity.overCapacity(district, 1), "A stale zero capacity must not demote an existing walker");
        assertFalse(capacity.hasSpace(district, 0));
        finish(capacity); assertEquals(1, capacity.limit(district)); assertFalse(capacity.overCapacity(district, 1));
    }

    @Test void graphAndTerritoryEditsInvalidateBothModeDecisionsBeforeTheNextTick() {
        var routes = new CitizenRouteSavedData(); var territory = new DistrictTerritory(); var district = UUID.randomUUID();
        territory.put(new DistrictTerritory.Seed(district, 0, 0, 8));
        var a = marker(routes, 0, 1, 0); var b = marker(routes, 6, 1, 0); routes.link(a.id(), b.id(), false);
        var capacity = new DistrictRouteCapacity(routes, territory, ignored -> true, () -> 0); finish(capacity);
        territory.put(new DistrictTerritory.Seed(district, 0, 0, 2));
        assertFalse(capacity.ready()); assertEquals(0, capacity.length(district));
        assertFalse(capacity.hasSpace(district, 0)); assertFalse(capacity.overCapacity(district, 10));
        finish(capacity); assertTrue(capacity.overCapacity(district, 1));
        territory.put(new DistrictTerritory.Seed(district, 0, 0, 8)); finish(capacity);
        routes.remove(b.id());
        assertFalse(capacity.ready()); assertFalse(capacity.hasSpace(district, 0)); assertFalse(capacity.overCapacity(district, 10));
        finish(capacity); assertTrue(capacity.overCapacity(district, 1));
    }
}
