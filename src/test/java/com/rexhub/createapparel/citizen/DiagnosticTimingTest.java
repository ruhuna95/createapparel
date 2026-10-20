package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.population.DiagnosticTiming;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticTimingTest {
    @Test void disabledInstrumentationNeverReadsClockOrAllocatesSamples() {
        var timing = new DiagnosticTiming(() -> { throw new AssertionError("Disabled profiler read clock"); });
        try (var ignored = timing.measure("disabled")) { timing.count("nothing", 1); }
        assertEquals(0, timing.result("disabled").calls()); assertEquals(0, timing.counter("nothing"));
    }
    @Test void nestedAndExceptionalScopesPreserveInclusiveAndExclusiveTotals() {
        var clock = new AtomicLong(); var timing = new DiagnosticTiming(clock::get); timing.start();
        try (var outer = timing.measure("outer")) {
            clock.set(10);
            assertThrows(IllegalStateException.class, () -> { try (var inner = timing.measure("inner")) { clock.set(30); throw new IllegalStateException(); } });
            clock.set(50);
        }
        timing.interval(); timing.stop();
        assertEquals(50, timing.result("outer").inclusive()); assertEquals(30, timing.result("outer").exclusive());
        assertEquals(20, timing.result("inner").inclusive()); assertEquals(20, timing.result("inner").exclusive());
        assertEquals(1, timing.intervals()); assertTrue(timing.report("tick").contains("inclusive_times_overlap"));
    }
    @Test void quantilesAreBoundedWhileTotalsRemainCumulative() throws InterruptedException {
        var clock = new AtomicLong(); var timing = new DiagnosticTiming(clock::get); timing.start();
        for (int i = 0; i < 5000; i++) try (var ignored = timing.measure("section")) { clock.incrementAndGet(); }
        assertEquals(5000, timing.result("section").calls()); assertEquals(1, timing.result("section").p99());
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var other = new Thread(() -> {
            try {
                assertFalse(timing.enabled());
                try (var ignored = timing.measure("wrongThread")) { }
                assertThrows(IllegalStateException.class, timing::start);
                assertThrows(IllegalStateException.class, timing::stop);
            } catch (Throwable error) { failure.set(error); }
        });
        other.start(); other.join();
        assertNull(failure.get()); assertEquals(0, timing.result("wrongThread").calls());
    }
    @Test void rejectedForeignLifecycleDoesNotChangeActiveProfileCount() throws InterruptedException {
        var metrics = new com.rexhub.createapparel.citizen.population.CitizenSystemMetrics();
        metrics.start();
        try {
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var other = new Thread(() -> {
                try {
                    assertThrows(IllegalStateException.class, metrics::stop);
                    assertThrows(IllegalStateException.class, metrics::start);
                } catch (Throwable error) { failure.set(error); }
            });
            other.start(); other.join(); assertNull(failure.get());
            assertTrue(metrics.enabled());
            assertTrue(com.rexhub.createapparel.citizen.population.CitizenSystemMetrics.profiling());
        } finally { metrics.stop(); }
        assertFalse(com.rexhub.createapparel.citizen.population.CitizenSystemMetrics.profiling());
    }
}
