package com.rexhub.createapparel.client.garment;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GarmentSourceImageCacheTest {
    @Test
    void repeatedTexturesShareSourcesAndClearReleasesEachOnce() throws IOException {
        var loads = new AtomicInteger();
        var releases = new AtomicInteger();
        var cache = new GarmentSourceImageCache<ResourceLocation, Object>(key -> {
            loads.incrementAndGet();
            return new Object();
        }, image -> releases.incrementAndGet());
        var base = ResourceLocation.parse("createapparel:garment/t_shirt/base");
        Object first = cache.get(base);
        for (int tint = 0; tint < 33; tint++) assertSame(first, cache.get(ResourceLocation.parse(base.toString())));
        cache.get(ResourceLocation.parse("createapparel:garment/t_shirt/mask"));
        assertEquals(2, loads.get());
        assertEquals(0, releases.get());
        cache.clear();
        assertEquals(2, releases.get());
        cache.clear();
        assertEquals(2, releases.get());
        assertNotSame(first, cache.get(base));
        assertEquals(3, loads.get());
        cache.clear();
        assertEquals(3, releases.get());
    }

    @Test
    void failedLoadsCanBeRetriedAndDoNotOwnResources() throws IOException {
        var attempts = new AtomicInteger();
        var releases = new AtomicInteger();
        var cache = new GarmentSourceImageCache<String, Object>(key -> {
            if (attempts.incrementAndGet() == 1) throw new IOException("missing resource");
            return new Object();
        }, image -> releases.incrementAndGet());
        assertThrows(IOException.class, () -> cache.get("base"));
        cache.clear();
        assertEquals(0, releases.get());
        assertNotNull(cache.get("base"));
        assertEquals(2, attempts.get());
        cache.clear();
        assertEquals(1, releases.get());
    }
}
