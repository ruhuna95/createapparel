package com.rexhub.createapparel.client.mirror;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MirrorFramebufferPoolTest {
    @Test void poolMissPreparesOneBufferWithoutStartingACapture() {
        var created = new ArrayList<RenderTarget>();
        var pool = new MirrorFramebufferPool((width, height) -> {
            var target = target(width, height);
            created.add(target);
            return target;
        }, ignored -> fail("Unexpected destruction"));
        assertNull(pool.acquireForCapture(1024, 576));
        assertEquals(1, created.size());
        assertEquals(0, pool.leasedPixels());
        assertEquals(1, pool.freeTargets());
        assertSame(created.get(0), pool.acquireForCapture(1024, 576));
        assertEquals(1, created.size());
    }

    @Test void resolutionChangePreservesThePublishedTargetWhilePreparingTheNewSize() {
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, ignored -> fail("Unexpected destruction"));
        var published = pool.acquire(256, 144);
        assertNotNull(published);
        assertNull(pool.acquireForCapture(1024, 576));
        assertEquals(256, published.width);
        assertEquals(256L * 144, pool.leasedPixels());
        var temporary = pool.acquireForCapture(1024, 576);
        assertNotNull(temporary);
        assertNotSame(published, temporary);
        pool.release(temporary);
        assertEquals(256L * 144, pool.leasedPixels());
        assertSame(temporary, pool.acquireForCapture(1024, 576));
        pool.release(published);
    }

    @Test void fullPoolReplacesOldSizesInsteadOfRecreatingTheCurrentResolutionEveryUpdate() {
        var created = new ArrayList<RenderTarget>();
        var destroyed = new ArrayList<RenderTarget>();
        var pool = new MirrorFramebufferPool((width, height) -> {
            var target = target(width, height);
            created.add(target);
            return target;
        }, destroyed::add);
        for (int width = 128; width < 136; width++) pool.release(pool.acquire(width, 64));
        assertEquals(MirrorFramebufferPool.MAX_FREE_TARGETS, pool.freeTargets());
        assertNull(pool.acquireForCapture(1024, 576));
        assertEquals(1, destroyed.size());
        var current = pool.acquireForCapture(1024, 576);
        assertNotNull(current);
        assertNull(pool.acquireForCapture(1024, 576));
        var next = pool.acquireForCapture(1024, 576);
        assertNotNull(next);
        int allocations = created.size();
        for (int i = 0; i < 100; i++) {
            pool.release(current);
            current = next;
            next = pool.acquireForCapture(1024, 576);
            assertNotNull(next);
            assertTrue(pool.freeTargets() <= MirrorFramebufferPool.MAX_FREE_TARGETS);
            assertTrue(pool.freePixels() <= MirrorFramebufferPool.MAX_FREE_PIXELS);
        }
        assertEquals(allocations, created.size());
    }

    @Test void freePixelPressureEvictsOlderBuffersAndKeepsTheNewSizeReusable() {
        var destroyed = new ArrayList<RenderTarget>();
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, destroyed::add);
        var first = pool.acquire(2048, 2048);
        var second = pool.acquire(2048, 2048);
        pool.release(first);
        pool.release(second);
        var replacement = pool.acquire(1024, 576);
        pool.release(replacement);
        assertEquals(java.util.List.of(first), destroyed);
        assertSame(replacement, pool.acquireForCapture(1024, 576));
        assertTrue(pool.freePixels() <= MirrorFramebufferPool.MAX_FREE_PIXELS);
    }

    @Test void evictionKeepsRecentlyUsedSizesAheadOfUnusedOnes() {
        var destroyed = new ArrayList<RenderTarget>();
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, destroyed::add);
        var targets = new ArrayList<RenderTarget>();
        for (int width = 128; width < 136; width++) {
            var target = pool.acquire(width, 64);
            targets.add(target);
            pool.release(target);
        }
        pool.release(pool.acquire(128, 64));
        pool.release(pool.acquire(1024, 576));
        assertEquals(java.util.List.of(targets.get(1)), destroyed);
        assertSame(targets.get(0), pool.acquireForCapture(128, 64));
    }

    @Test void targetsTooLargeForTheFreePoolAreNotPermanentlyDeferred() {
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, ignored -> { });
        var target = pool.acquireForCapture(2048, 4608);
        assertNotNull(target);
        assertEquals(2048L * 4608, pool.leasedPixels());
        pool.release(target);
        assertEquals(0, pool.freeTargets());
        assertEquals(0, pool.leasedPixels());
        assertNotNull(pool.acquireForCapture(2048, 4608));
    }

    @Test void eachSizeRetainsAtMostTwoTargets() {
        var destroyed = new ArrayList<RenderTarget>();
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, destroyed::add);
        var first = pool.acquire(256, 128);
        var second = pool.acquire(256, 128);
        var third = pool.acquire(256, 128);
        pool.release(first);
        pool.release(second);
        pool.release(third);
        assertEquals(2, pool.freeTargets());
        assertEquals(java.util.List.of(third), destroyed);
    }
    static RenderTarget target(int width, int height) {
        var target = new RenderTarget(true) { };
        target.width = width;
        target.height = height;
        return target;
    }

    @Test void releasedTargetsAreReusedWithoutCreatingNewBuffers() {
        var created = new ArrayList<RenderTarget>();
        var pool = new MirrorFramebufferPool((width, height) -> {
            var target = target(width, height);
            created.add(target);
            return target;
        }, ignored -> fail("Unexpected destruction"));
        var first = pool.acquire(1024, 576);
        assertNotNull(first);
        pool.release(first);
        assertEquals(0, pool.leasedPixels());
        assertSame(first, pool.acquire(1024, 576));
        assertEquals(1, created.size());
        assertEquals(0, pool.freeTargets());
        assertEquals(0, pool.freePixels());
    }

    @Test void freeBuffersAreBoundedAcrossAllSizesAndByTotalPixels() {
        var destroyed = new ArrayList<RenderTarget>();
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, destroyed::add);
        for (int width = 2048; width >= 256; width -= 128) {
            var targets = new ArrayList<RenderTarget>();
            for (int i = 0; i < 3; i++) targets.add(pool.acquire(width, width / 2));
            targets.forEach(pool::release);
            assertTrue(pool.freePixels() <= MirrorFramebufferPool.MAX_FREE_PIXELS);
            assertTrue(pool.freeTargets() <= MirrorFramebufferPool.MAX_FREE_TARGETS);
            assertEquals(0, pool.leasedPixels());
        }
        assertFalse(destroyed.isEmpty());
        pool.close();
        assertEquals(0, pool.freePixels());
        assertEquals(0, pool.freeTargets());
    }

    @Test void checkedOutBuffersHaveAHardPixelLimitEvenWhenRecursionKeepsThemBusy() {
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, ignored -> { });
        var targets = new ArrayList<RenderTarget>();
        while (pool.canAcquire(2048, 2048)) targets.add(pool.acquire(2048, 2048));
        assertEquals(8, targets.size());
        assertNull(pool.acquire(2048, 2048));
        assertEquals(MirrorFramebufferPool.MAX_LEASED_PIXELS, pool.leasedPixels());
        pool.release(targets.remove(0));
        assertNotNull(pool.acquire(2048, 2048));
        assertFalse(pool.canAcquire(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertFalse(pool.canAcquire(0, 64));
    }
}
