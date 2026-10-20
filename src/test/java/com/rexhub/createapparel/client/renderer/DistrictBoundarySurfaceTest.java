package com.rexhub.createapparel.client.renderer;

import com.rexhub.createapparel.client.citizen.DistrictBoundarySurface;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DistrictBoundarySurfaceTest {
    @Test void selectsHighestSurfaceAtOrBelowFeetIncludingSlabsAndNegativeHeights() {
        assertEquals(71, DistrictBoundarySurface.highestSurface(71, -64, 90,
                y -> y == 80 ? 81 : y == 70 ? 71 : Double.NaN));
        assertEquals(70.5, DistrictBoundarySurface.highestSurface(70.75, -64, 90,
                y -> y == 70 ? 70.5 : y == 69 ? 70 : Double.NaN));
        assertEquals(70, DistrictBoundarySurface.highestSurface(70.25, -64, 90,
                y -> y == 70 ? 70.5 : y == 69 ? 70 : Double.NaN));
        assertEquals(-5.5, DistrictBoundarySurface.highestSurface(-5.25, -64, 90,
                y -> y == -6 ? -5.5 : Double.NaN));
        assertTrue(Double.isNaN(DistrictBoundarySurface.highestSurface(71, -64, 90, y -> Double.NaN)));
    }

    @Test void cachesReadsRefreshesTerrainAndNeverRendersAboveFeet() {
        var surfaces = new DistrictBoundarySurface(); var calls = new AtomicInteger();
        DistrictBoundarySurface.Reader reader = (x, z, ceiling) -> { calls.incrementAndGet(); return 70.5; };
        surfaces.request(1, 2, 71, 0); assertEquals(1, surfaces.update(71, 0, reader));
        assertEquals(70.503, surfaces.height(1, 2, 71), .00001);
        surfaces.request(1, 2, 71, 19); assertEquals(0, surfaces.update(71, 19, reader));
        assertEquals(1, calls.get()); assertTrue(Double.isNaN(surfaces.height(1, 2, 70)));
        surfaces.request(1, 2, 70, 19); assertEquals(1, surfaces.update(70, 19, (x, z, ceiling) -> 70));
        assertEquals(70, surfaces.height(1, 2, 70));
        surfaces.request(1, 2, 70, 39); assertEquals(1, surfaces.update(70, 39, (x, z, ceiling) -> 69));
        assertEquals(69.003, surfaces.height(1, 2, 70), .00001);
        surfaces.request(5, 6, 70, 39); surfaces.update(70, 39, (x, z, ceiling) -> Double.NaN);
        assertTrue(Double.isNaN(surfaces.height(5, 6, 70)));
    }

    @Test void denseQueuesStayBoundedAndRefreshRequestsDoNotStarveLaterColumns() {
        var surfaces = new DistrictBoundarySurface(); int count = 0;
        for (int x = 0; x < DistrictBoundarySurface.CACHE_LIMIT + 10; x++) surfaces.request(x, 0, 71, 0);
        for (int tick = 0; tick < DistrictBoundarySurface.CACHE_LIMIT / DistrictBoundarySurface.COLUMN_BUDGET; tick++) {
            int processed = surfaces.update(71, tick, (x, z, ceiling) -> 71);
            assertTrue(processed <= DistrictBoundarySurface.COLUMN_BUDGET); count += processed;
            surfaces.request(0, 0, 71, tick);
        }
        assertEquals(DistrictBoundarySurface.CACHE_LIMIT, count);
        assertEquals(71, surfaces.height(DistrictBoundarySurface.CACHE_LIMIT - 1, 0, 71));
        assertTrue(Double.isNaN(surfaces.height(DistrictBoundarySurface.CACHE_LIMIT, 0, 71)));
    }
}
