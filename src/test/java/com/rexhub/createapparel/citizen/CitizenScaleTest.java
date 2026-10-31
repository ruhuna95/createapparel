package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.population.*;
import com.rexhub.createapparel.citizen.route.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CitizenScaleTest {
    @BeforeAll static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }

    @Test void fixedScaleSaveLoadAndNetworkBudgets() throws Exception {
        for (int gauges : new int[]{200, 1000, 2000}) {
            var data = new CitizenPopulationSavedData();
            var routes = new CitizenRouteSavedData();
            var nodes = new ArrayList<CitizenGauge>();
            for (int i = 0; i < gauges; i++) {
                var pos = new BlockPos(i % 50 * 80, 64, i / 50 * 80);
                var gauge = new CitizenGauge(new UUID(1, i), pos.above(), new UUID(2, i), pos, 32, 10, 0, false);
                data.put(gauge); nodes.add(gauge);
                if (i % 100 != 0) data.link(nodes.get(i - 1).id(), gauge.id());
                for (int j = 0; j < 5; j++) data.register(new UUID(3, i * 5L + j), new CitizenPopulationState(gauge.districtId(), false, false, 0));
                for (int j = 0; j < 10; j++) {
                    var marker = RouteMarker.create(new UUID(4, i * 10L + j), pos.offset(j * 2, 0, 0), RouteMarkerType.NORMAL);
                    routes.put(marker.withDistrict(gauge.districtId()));
                    if (j > 0) routes.link(new UUID(4, i * 10L + j - 1), marker.id(), false);
                }
            }
            long started = System.nanoTime();
            for (var node : nodes) data.networks().request(node.id());
            int ticks = (gauges + 63) / 64;
            for (int i = 0; i < ticks; i++) data.networks().tick(64);
            long network = System.nanoTime() - started;
            assertNotNull(data.networks().cached(nodes.get(nodes.size() - 1).id()));
            started = System.nanoTime();
            var tag = data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup());
            var routeTag = routes.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup());
            long save = System.nanoTime() - started;
            var bytes = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(bytes)); NbtIo.write(routeTag, new DataOutputStream(bytes));
            started = System.nanoTime();
            var restored = CitizenPopulationSavedData.load(tag);
            var restoredRoutes = CitizenRouteSavedData.load(routeTag);
            long load = System.nanoTime() - started;
            assertEquals(gauges, restored.gauges().size()); assertEquals(gauges * 10, restoredRoutes.markers().size());
            for (var node : nodes) assertEquals(5, restored.population(node.districtId()));
            System.out.printf(Locale.ROOT, "CITIZEN_SCALE gauges=%d savedCitizens=%d markers=%d networkMs=%.3f saveMs=%.3f loadMs=%.3f nbtBytes=%d%n",
                    gauges, gauges * 5, gauges * 10, network / 1e6, save / 1e6, load / 1e6, bytes.size());
        }
    }

    @Test void compareWholeBacklogSortingWithIncrementalRequestScheduling() {
        for (int size : new int[]{200, 1000, 2000}) {
            var legacy = new ArrayList<PopulationRequest>(); var incremental = new PopulationRequestQueue();
            for (int i = 0; i < size; i++) {
                legacy.add(new PopulationRequest(new UUID(20, i), i)); incremental.put(new PopulationRequest(new UUID(20, i), i), 0);
            }
            long comparisons[] = {0}; int oldAttempts = 0, newAttempts = 0; var oldServed = new HashSet<UUID>(); var newServed = new HashSet<UUID>();
            var order = PopulationRequest.schedulingOrder(request -> 0);
            long start = System.nanoTime();
            for (long now = 2000; now < 14000; now += 20) {
                var waiting = new ArrayList<>(legacy); waiting.sort((a, b) -> { comparisons[0]++; return order.compare(a, b); }); int budget = 4;
                for (var request : waiting) {
                    if (request.retryAt() > now) continue; if (budget-- == 0) break;
                    request.attempted(now, 200); oldAttempts++; oldServed.add(request.requester());
                }
            }
            long legacyNanos = System.nanoTime() - start; start = System.nanoTime();
            for (long now = 2000; now < 14000; now++) {
                incremental.tick(now); if (now % 20 != 0) continue;
                for (int i = 0; i < 4; i++) {
                    var request = incremental.poll(); if (request == null) break;
                    request.attempted(now, 200); newAttempts++; newServed.add(request.requester()); incremental.put(request, 0);
                }
            }
            long incrementalNanos = System.nanoTime() - start;
            assertEquals(size, oldServed.size()); assertEquals(oldServed, newServed); assertEquals(oldAttempts, newAttempts);
            System.out.printf(Locale.ROOT, "CITIZEN_REQUEST_SCALE requests=%d legacyMs=%.3f incrementalMs=%.3f legacyComparisons=%d attempts=%d served=%d queueEntries=%d%n",
                    size, legacyNanos / 1e6, incrementalNanos / 1e6, comparisons[0], newAttempts, newServed.size(), incremental.entryCount());
        }
    }
}
