package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.population.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PopulationRequestQueueTest {
    @Test void cancelledFarFutureEntriesConvergeToZeroWithoutWaitingForTheirRetryTime() {
        for (int count : new int[]{16, 2000}) {
            var queue = new PopulationRequestQueue();
            for (int i = 0; i < count; i++) { var request = new PopulationRequest(new UUID(70, i), i); request.retryAt(Long.MAX_VALUE); queue.put(request, 0); }
            for (int tick = 0; tick < 40; tick++) queue.tick(tick);
            for (int i = 0; i < count; i++) queue.remove(new UUID(70, i));
            for (int tick = 40; tick < 100; tick++) { queue.tick(tick); assertNull(queue.poll()); }
            assertEquals(0, queue.size()); assertEquals(0, queue.entryCount());
        }
    }
    @Test void massUpdatesCancellationsAndIdenticalRetryTimesExecuteOnlyCurrentGenerationsWithoutStarvation() {
        var queue = new PopulationRequestQueue(); var live = new HashMap<UUID, PopulationRequest>();
        for (int i = 0; i < 2500; i++) { var request = new PopulationRequest(new UUID(71, i), i); request.retryAt(300); queue.put(request, 0); live.put(request.requester(), request); }
        for (int tick = 0; tick < 150; tick++) {
            for (var request : live.values()) { request.retryAt(300); queue.put(request, tick % 7); }
            queue.tick(tick); assertNull(queue.poll()); assertTrue(queue.entryCount() < 16000);
        }
        for (int i = 0; i < 2500; i += 3) { var id = new UUID(71, i); queue.remove(id); live.remove(id); }
        var served = new HashSet<UUID>(); int lastServed = -1;
        for (int tick = 150; tick < 800; tick++) {
            queue.tick(tick);
            for (int i = 0; i < 4; i++) { var request = queue.poll(); if (request == null) break;
                assertTrue(tick >= 300); assertSame(live.get(request.requester()), request); assertTrue(served.add(request.requester())); lastServed = tick; }
        }
        assertEquals(live.keySet(), served); assertTrue(lastServed <= 720); assertEquals(0, queue.size());
        for (int tick = 800; tick < 900; tick++) queue.tick(tick); assertEquals(0, queue.entryCount());
    }
    @Test void delayedRequestsDoNotHideReadyRequestsAndOldEntriesNeverRunTwice() {
        var queue = new PopulationRequestQueue();
        var delayed = new PopulationRequest(UUID.randomUUID(), 0); delayed.retryAt(200);
        var ready = new PopulationRequest(UUID.randomUUID(), 1);
        queue.put(delayed, 0); queue.put(ready, 1); queue.tick(0);
        assertSame(ready, queue.poll()); assertNull(queue.poll());
        for (int i = 0; i < 200; i++) { delayed.retryAt(200 + i); queue.put(delayed, i); queue.tick(i); }
        for (int i = 0; i < 5; i++) queue.tick(500 + i);
        assertSame(delayed, queue.poll()); assertNull(queue.poll());
        for (int i = 0; i < 20; i++) queue.tick(500);
        assertTrue(queue.entryCount() <= 64); assertEquals(0, queue.size());
    }

    @Test void backlogRemainsFairAtBothRetryIntervals() {
        for (int retry : new int[]{20, 200}) {
            var queue = new PopulationRequestQueue(); var served = new HashSet<UUID>();
            for (int i = 0; i < 97; i++) queue.put(new PopulationRequest(new UUID(0, i), i), 0);
            for (long now = 200; now <= 1600; now++) {
                queue.tick(now);
                if (now % 20 != 0) continue;
                for (int i = 0; i < 4; i++) {
                    var request = queue.poll(); if (request == null) break;
                    served.add(request.requester()); request.attempted(now, retry); queue.put(request, 0);
                }
            }
            assertEquals(97, served.size());
        }
    }

    @Test void updatedRatioAndAgePreserveTheExistingSchedulingOrder() {
        var requests = new ArrayList<PopulationRequest>(); var queue = new PopulationRequestQueue();
        var ratios = new HashMap<UUID, Double>();
        for (int i = 0; i < 40; i++) {
            var request = new PopulationRequest(new UUID(0, i), i / 4); requests.add(request);
            ratios.put(request.id(), (i % 3) * .25); queue.put(request, ratios.get(request.id()));
        }
        requests.sort(PopulationRequest.schedulingOrder(request -> ratios.get(request.id())));
        queue.tick(0);
        for (var expected : requests) assertSame(expected, queue.poll());
    }
    @Test void thousandsOfUnloadedOrBusyRequestsCannotStarveALoadedRequesterBehindTheInspectionLimit() {
        var queue = new PopulationRequestQueue();
        for (int i = 0; i < 2000; i++) queue.put(new PopulationRequest(new UUID(50, i), i), 0);
        var usable = new PopulationRequest(new UUID(51, 0), 2001); queue.put(usable, 0);
        boolean served = false;
        for (long now = 3000; now < 5000 && !served; now++) {
            queue.tick(now); if (now % 20 != 0) continue;
            for (int inspected = 0; inspected < 64; inspected++) {
                var request = queue.poll(); if (request == null) break;
                if (request == usable) { served = true; break; }
                request.defer(now, now + 20); queue.put(request, 0);
            }
        }
        assertTrue(served);
    }
    @Test void continuousBulkPriorityChangesAreCoalescedAndCannotGrowStaleHeapsWithoutBound() {
        var queue = new PopulationRequestQueue(); var requests = new ArrayList<PopulationRequest>();
        for (int i = 0; i < 1000; i++) requests.add(new PopulationRequest(new UUID(60, i), i));
        for (int tick = 0; tick < 200; tick++) {
            for (var request : requests) queue.put(request, tick);
            queue.tick(tick);
            assertTrue(queue.entryCount() <= requests.size() * 6 + 256);
        }
        var served = new HashSet<UUID>();
        for (int tick = 200; tick < 600; tick++) {
            queue.tick(tick);
            for (int i = 0; i < 64; i++) { var request = queue.poll(); if (request == null) break; served.add(request.requester()); }
        }
        assertEquals(1000, served.size()); assertEquals(0, queue.size());
    }
}
