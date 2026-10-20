package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.population.*;
import com.rexhub.createapparel.citizen.route.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DistrictTerritoryTest {
    @BeforeAll static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    @Test void radiusOneOwnsExactlyNineColumnsIncludingTheirFullBlockWidth() {
        var territory = new DistrictTerritory(); territory.put(new DistrictTerritory.Seed(A, -1, -1, 1));
        int owned = 0;
        for (int x = -4; x <= 2; x++) for (int z = -4; z <= 2; z++) if (territory.districtAt(x, z) != null) owned++;
        assertEquals(9, owned);
        assertEquals(A, territory.districtAt(new Vec3(-2, -100, -2)));
        assertEquals(A, territory.districtAt(new Vec3(.999, 300, .999)));
        assertNull(territory.districtAt(new Vec3(1, 64, 0)));
        assertNull(territory.districtAt(new Vec3(-2.001, 64, 0)));
    }

    @Test void midpointBoundariesAreUniqueStableAndIndependentOfInsertionOrder() {
        var first = new DistrictTerritory(); var reverse = new DistrictTerritory();
        var seeds = List.of(new DistrictTerritory.Seed(A, -2, 0, 4), new DistrictTerritory.Seed(B, 2, 0, 4), new DistrictTerritory.Seed(C, 0, 3, 4));
        seeds.forEach(first::put); for (int index = seeds.size() - 1; index >= 0; index--) reverse.put(seeds.get(index));
        assertEquals(A, first.districtAt(0, 0)); assertEquals(B, first.districtAt(1, 0)); assertEquals(C, first.districtAt(0, 3));
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            int px = x, pz = z;
            var expected = seeds.stream().filter(seed -> seed.contains(px, pz)).min(Comparator
                    .comparingLong((DistrictTerritory.Seed seed) -> (long) (px - seed.x()) * (px - seed.x()) + (long) (pz - seed.z()) * (pz - seed.z()))
                    .thenComparing(DistrictTerritory.Seed::districtId)).map(DistrictTerritory.Seed::districtId).orElse(null);
            assertEquals(expected, first.districtAt(x, z)); assertEquals(expected, reverse.districtAt(x, z));
        }
        first.put(new DistrictTerritory.Seed(B, -2, 0, 4)); assertEquals(A, first.districtAt(-2, 0));
        first.remove(A); assertEquals(B, first.districtAt(-2, 0));
    }

    @Test void tilesRoundTripAsPaletteRunsAndRevalidateLazilyAfterLoading() {
        var territory = new DistrictTerritory(); territory.put(new DistrictTerritory.Seed(A, -5, -1, 20)); territory.put(new DistrictTerritory.Seed(B, 8, 2, 20));
        var tile = territory.tile(-1, -1); assertSame(tile, territory.tile(-1, -1));
        var saved = territory.save(); var restored = DistrictTerritory.load(saved);
        assertEquals(territory.revision(), restored.revision()); assertEquals(1, restored.cachedTileCount());
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) assertEquals(territory.districtAt(-16 + x, -16 + z), restored.tile(-1, -1).districtAt(x, z));
        assertEquals(tile.save(), restored.tile(-1, -1).save());
        var corrupted = saved.copy(); corrupted.getList("Tiles", 10).getCompound(0).putIntArray("Runs", new int[]{256});
        var checked = DistrictTerritory.load(corrupted); assertEquals(tile.save(), checked.tile(-1, -1).save());
    }

    @Test void geometryEditsInvalidateOnlyIntersectingCachedTilesAndCacheIsBounded() {
        var territory = new DistrictTerritory(); territory.put(new DistrictTerritory.Seed(A, 0, 0, 5)); territory.put(new DistrictTerritory.Seed(B, 1000, 0, 5));
        var local = territory.tile(0, 0); var remote = territory.tile(62, 0); long revision = territory.revision();
        territory.put(new DistrictTerritory.Seed(A, 0, 0, 5)); assertEquals(revision, territory.revision()); assertSame(local, territory.tile(0, 0));
        territory.put(new DistrictTerritory.Seed(A, 0, 0, 8)); assertNotSame(local, territory.tile(0, 0)); assertSame(remote, territory.tile(62, 0));
        territory.remove(A); assertNull(territory.districtAt(0, 0));
        for (int index = 0; index < DistrictTerritory.MAX_CACHED_TILES + 10; index++) territory.tile(index, 100);
        assertEquals(DistrictTerritory.MAX_CACHED_TILES, territory.cachedTileCount());
        assertTrue(territory.save().getList("Tiles", 10).size() <= DistrictTerritory.MAX_CACHED_TILES);
    }

    @Test void markerMigrationAndDenseChunksNeverExceedTickBudgetAndResumeAfterRestart() {
        var territory = new DistrictTerritory(); territory.put(new DistrictTerritory.Seed(A, 0, 0, 32));
        var routes = new CitizenRouteSavedData();
        for (int index = 0; index < 400; index++) routes.put(RouteMarker.create(new UUID(1, index), new BlockPos(index & 15, index >> 8, index >> 4 & 15), RouteMarkerType.NORMAL));
        var sync = new MarkerDistrictSynchronizer(territory, routes);
        assertEquals(MarkerDistrictSynchronizer.MARKER_BUDGET, sync.tick()); assertTrue(sync.pending());
        routes = CitizenRouteSavedData.load(routes.save(new CompoundTag())); territory = DistrictTerritory.load(territory.save());
        sync = new MarkerDistrictSynchronizer(territory, routes); int ticks = 0;
        while (sync.pending()) { assertTrue(sync.tick() <= MarkerDistrictSynchronizer.MARKER_BUDGET); assertTrue(++ticks < 30); }
        assertTrue(routes.markers().stream().allMatch(marker -> A.equals(marker.districtId())));
        var warm = new MarkerDistrictSynchronizer(DistrictTerritory.load(territory.save()), CitizenRouteSavedData.load(routes.save(new CompoundTag())));
        assertFalse(warm.pending()); assertEquals(0, warm.tick());
        territory.put(new DistrictTerritory.Seed(B, 15, 0, 8));
        assertEquals(MarkerDistrictSynchronizer.MARKER_BUDGET, sync.tick());
        assertTrue(routes.markers().stream().anyMatch(marker -> B.equals(marker.districtId())));
        territory.remove(B); ticks = 0;
        while (sync.pending()) { assertTrue(sync.tick() <= MarkerDistrictSynchronizer.MARKER_BUDGET); assertTrue(++ticks < 30); }
        assertTrue(routes.markers().stream().allMatch(marker -> A.equals(marker.districtId())));
        territory.put(new DistrictTerritory.Seed(B, 15, 0, 8)); ticks = 0;
        while (sync.pending()) { assertTrue(sync.tick() <= MarkerDistrictSynchronizer.MARKER_BUDGET); assertTrue(++ticks < 30); }
        assertTrue(routes.markers().stream().anyMatch(marker -> B.equals(marker.districtId())));
    }
}
