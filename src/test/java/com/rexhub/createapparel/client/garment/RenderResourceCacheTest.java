package com.rexhub.createapparel.client.garment;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RenderResourceCacheTest {
    @Test void directReferencesReuseEntriesAndRecoverAfterEvictionAndClear() {
        var released = new ArrayList<String>();
        var loads = new AtomicInteger();
        var cache = new RenderResourceCache<String, String>(1, released::add);
        var reference = cache.reference("a", () -> { loads.incrementAndGet(); return "a"; });
        assertEquals("a", reference.get()); assertEquals("a", reference.get());
        cache.get("b", () -> { loads.incrementAndGet(); return "b"; });
        cache.endFrame(); cache.get("b", () -> fail("Active entry reloaded")); cache.endFrame(); cache.clear();
        assertEquals(List.of("a", "b"), released);
        assertEquals("a", reference.get());
        assertEquals(3, loads.get());
        cache.clear();
        assertEquals(List.of("a", "b", "a"), released);
    }
    @Test
    void cachedReferencesAvoidKeyLookupsAndShareResources() {
        AtomicInteger hashes = new AtomicInteger();
        Object key = new Object() {
            @Override public int hashCode() { hashes.incrementAndGet(); return 1; }
        };
        var cache = new RenderResourceCache<Object, Object>(2, ignored -> {});
        AtomicInteger loads = new AtomicInteger();
        var reference = cache.reference(key, () -> { loads.incrementAndGet(); return new Object(); });
        var expected = reference.get();
        int initialHashes = hashes.get();
        for (int i = 0; i < 100; i++) assertSame(expected, reference.get());
        assertEquals(initialHashes, hashes.get());
        assertSame(expected, cache.reference(key, () -> { fail("Duplicate composition"); return null; }).get());
        assertEquals(1, loads.get());
    }

    @Test
    void evictionWaitsUntilFrameEndAndInvalidReferencesReload() {
        List<String> released = new ArrayList<>();
        var cache = new RenderResourceCache<String, String>(2, released::add);
        AtomicInteger loads = new AtomicInteger();
        var a = cache.reference("a", () -> "a" + loads.incrementAndGet());
        var b = cache.reference("b", () -> "b");
        var c = cache.reference("c", () -> "c");
        assertEquals("a1", a.get());
        b.get();
        a.get();
        c.get();
        assertTrue(released.isEmpty());
        assertEquals(3, cache.size());
        cache.endFrame();
        assertTrue(released.isEmpty());
        a.get();
        c.get();
        cache.endFrame();
        assertEquals(List.of("b"), released);
        assertEquals(2, cache.size());
        assertEquals("a1", a.get());
        b.get();
        cache.endFrame();
        assertEquals(List.of("b", "c"), released);
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(4, released.size());
        assertEquals("a2", a.get());
    }

    @Test
    void activeWorkingSetAboveCapacityDoesNotRecomposeEveryFrame() {
        var cache = new RenderResourceCache<Integer, Integer>(1, ignored -> fail("Active resource released"));
        var a = cache.reference(1, () -> 1);
        var b = cache.reference(2, () -> 2);
        for (int frame = 0; frame < 20; frame++) {
            assertEquals(1, a.get());
            assertEquals(2, b.get());
            cache.endFrame();
        }
        assertEquals(2, cache.size());
    }
}
