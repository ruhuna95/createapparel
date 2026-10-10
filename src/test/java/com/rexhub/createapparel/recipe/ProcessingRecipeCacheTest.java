package com.rexhub.createapparel.recipe;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingRecipeCacheTest {
    @Test
    void unchangedInputAndPauseResumeReuseResolution() {
        var cache = new ProcessingRecipeCache<Object>();
        var generation = new Object();
        var recipe = new Object();
        var calls = new AtomicInteger();
        Supplier<Optional<Object>> resolve = () -> {
            calls.incrementAndGet();
            return Optional.of(recipe);
        };
        for (int tick = 0; tick < 200; tick++) assertSame(recipe, cache.get(generation, resolve).orElseThrow());
        assertSame(recipe, cache.get(generation, resolve).orElseThrow());
        assertEquals(1, calls.get());
        cache.invalidate();
        assertSame(recipe, cache.get(generation, resolve).orElseThrow());
        assertEquals(2, calls.get());
    }

    @Test
    void missingRecipeIsCachedUntilInputChangesOrReloads() {
        var cache = new ProcessingRecipeCache<String>();
        var generation = new Object();
        var calls = new AtomicInteger();
        Supplier<Optional<String>> missing = () -> {
            calls.incrementAndGet();
            return Optional.empty();
        };
        for (int tick = 0; tick < 200; tick++) assertTrue(cache.get(generation, missing).isEmpty());
        assertEquals(1, calls.get());
        assertEquals("added", cache.get(new Object(), () -> Optional.of("added")).orElseThrow());
        cache.invalidate();
        assertTrue(cache.get(generation, missing).isEmpty());
        assertEquals(2, calls.get());
    }

    @Test
    void reloadReplacesSameIdAndRemovesDeletedRecipes() {
        var cache = new ProcessingRecipeCache<String>();
        var generation = new Object();
        assertEquals("original", cache.get(generation, () -> Optional.of("original")).orElseThrow());
        assertEquals("replacement", cache.get(new Object(), () -> Optional.of("replacement")).orElseThrow());
        assertTrue(cache.get(new Object(), Optional::empty).isEmpty());
    }
}
