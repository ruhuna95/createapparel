package com.rexhub.createapparel.client.mirror;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MirrorCaptureStateTest {
    @Test void smallerPendingImagesDoNotDiscountPublishedPixelsBeforeCommit() throws ReflectiveOperationException {
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, ignored -> { });
        var manager = manager(pool);
        var batch = batch(1);
        set(manager, "pendingBatch", batch);
        var old = pool.acquire(1024, 1024);
        var next = pool.acquire(256, 256);
        var cache = cache(old);
        @SuppressWarnings("unchecked") var textures = (Map<Set<?>, Object>) get(get(manager, "mainView"), "textures");
        textures.put(Set.of("candidate0"), cache);
        var snapshot = snapshot(cache);
        set(cache, "target", next);
        var stage = MirrorRenderManager.class.getDeclaredMethod("stageCapture", nestedType("ReflectionCache"), nestedType("CaptureState"));
        stage.setAccessible(true);
        stage.invoke(manager, cache, snapshot);
        var pixels = MirrorRenderManager.class.getDeclaredMethod("cachedPixels");
        pixels.setAccessible(true);
        assertEquals(1024L * 1024, pixels.invoke(manager));
        set(batch, "next", 1);
        var publish = MirrorRenderManager.class.getDeclaredMethod("publishPendingBatch");
        publish.setAccessible(true);
        publish.invoke(manager);
        assertEquals(256L * 256, pixels.invoke(manager));
    }
    @Test void globalEvictionCountsAndReleasesRecursiveCaches() throws ReflectiveOperationException {
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, ignored -> { });
        var manager = manager(pool);
        set(manager, "frame", 20L);
        var root = cache(pool.acquire(2048, 2048));
        var child = cache(pool.acquire(2048, 2048));
        set(root, "updated", 20L);
        set(child, "updated", 19L);
        var main = get(manager, "mainView");
        @SuppressWarnings("unchecked") var roots = (Map<Set<?>, Object>) get(main, "textures");
        @SuppressWarnings("unchecked") var children = (Map<Set<?>, Object>) get(get(root, "children"), "textures");
        roots.put(Set.of("root"), root);
        children.put(Set.of("child"), child);
        var count = MirrorRenderManager.class.getDeclaredMethod("cacheCount", nestedType("RenderView"));
        count.setAccessible(true);
        assertEquals(2, count.invoke(manager, main));
        var pixels = MirrorRenderManager.class.getDeclaredMethod("cachedPixels");
        pixels.setAccessible(true);
        assertEquals(8L * 1024 * 1024, pixels.invoke(manager));
        var evict = MirrorRenderManager.class.getDeclaredMethod("evictOldest", List.class);
        evict.setAccessible(true);
        assertEquals(true, evict.invoke(manager, List.of()));
        assertTrue(children.isEmpty());
        assertEquals(1, count.invoke(manager, main));
        assertEquals(4L * 1024 * 1024, pixels.invoke(manager));
        assertEquals(4L * 1024 * 1024, pool.leasedPixels());
    }
    @Test void successfulCaptureReturnsOldTargetAndDoesNotKeepAPerCacheSpare() throws ReflectiveOperationException {
        var returned = new java.util.ArrayList<RenderTarget>();
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, returned::add);
        var manager = manager(pool);
        var old = pool.acquire(256, 128);
        var next = pool.acquire(512, 256);
        var cache = cache(old);
        var snapshot = snapshot(cache);
        set(cache, "target", next);
        finish(manager, cache, snapshot, true);
        assertSame(next, get(cache, "target"));
        assertEquals(512L * 256, pool.leasedPixels());
        assertSame(old, pool.acquire(256, 128));
        assertEquals(1, Arrays.stream(cache.getClass().getDeclaredFields())
                .filter(field -> field.getType() == RenderTarget.class).count());
    }

    @Test void failedCaptureReturnsTemporaryTargetAndPreservesPublishedImage() throws ReflectiveOperationException {
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, ignored -> { });
        var manager = manager(pool);
        var old = pool.acquire(256, 128);
        var next = pool.acquire(512, 256);
        var cache = cache(old);
        set(cache, "ready", true);
        set(cache, "updated", 11L);
        var snapshot = snapshot(cache);
        set(cache, "target", next);
        set(cache, "ready", false);
        set(cache, "updated", 12L);
        finish(manager, cache, snapshot, false);
        assertSame(old, get(cache, "target"));
        assertEquals(true, get(cache, "ready"));
        assertEquals(11L, get(cache, "updated"));
        assertEquals(256L * 128, pool.leasedPixels());
        assertSame(next, pool.acquire(512, 256));
    }

    @Test void multiFrameBatchPublishesOnlyWhenAllCapturesAreReady() throws ReflectiveOperationException {
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, ignored -> { });
        var manager = manager(pool);
        var batch = batch(2);
        var groupKey = Set.of(Set.of("candidate0"), Set.of("candidate1"));
        var cohortConstructor = nestedType("Cohort").getDeclaredConstructors()[0];
        cohortConstructor.setAccessible(true);
        set(batch, "cohort", cohortConstructor.newInstance(groupKey, 0, 3));
        set(manager, "pendingBatch", batch);
        var oldA = pool.acquire(256, 128);
        var oldB = pool.acquire(256, 128);
        var cacheA = cache(oldA);
        var cacheB = cache(oldB);
        var nextA = pool.acquire(512, 256);
        var nextB = pool.acquire(512, 256);
        var stage = MirrorRenderManager.class.getDeclaredMethod("stageCapture", nestedType("ReflectionCache"), nestedType("CaptureState"));
        stage.setAccessible(true);
        var stateA = snapshot(cacheA);
        var stateB = snapshot(cacheB);
        set(cacheA, "target", nextA);
        set(cacheA, "updated", 10L);
        stage.invoke(manager, cacheA, stateA);
        assertSame(oldA, get(cacheA, "target"));
        assertSame(oldB, get(cacheB, "target"));
        var publish = MirrorRenderManager.class.getDeclaredMethod("publishPendingBatch");
        publish.setAccessible(true);
        set(batch, "next", 1);
        var rejected = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> publish.invoke(manager));
        assertInstanceOf(IllegalStateException.class, rejected.getCause());
        set(cacheB, "target", nextB);
        set(cacheB, "updated", 11L);
        stage.invoke(manager, cacheB, stateB);
        assertSame(oldA, get(cacheA, "target"));
        assertSame(oldB, get(cacheB, "target"));
        set(manager, "frame", 12L);
        set(batch, "next", 2);
        publish.invoke(manager);
        assertSame(nextA, get(cacheA, "target"));
        assertSame(nextB, get(cacheB, "target"));
        assertEquals(12L, get(cacheA, "updated"));
        assertEquals(12L, get(cacheB, "updated"));
        assertNull(get(manager, "pendingBatch"));
        assertEquals(1, ((Map<?, ?>) get(get(manager, "mainView"), "nextBatchPart")).get(groupKey));
        assertEquals(2L * 512 * 256, pool.leasedPixels());
        assertEquals(2, pool.freeTargets());
    }

    @Test void cancellingAnUnfinishedBatchReturnsItsTargetsWithoutChangingPublishedImages() throws ReflectiveOperationException {
        var pool = new MirrorFramebufferPool(MirrorFramebufferPoolTest::target, ignored -> { });
        var manager = manager(pool);
        set(manager, "pendingBatch", batch(1));
        var old = pool.acquire(256, 128);
        var next = pool.acquire(512, 256);
        var cache = cache(old);
        var snapshot = snapshot(cache);
        set(cache, "target", next);
        var stage = MirrorRenderManager.class.getDeclaredMethod("stageCapture", nestedType("ReflectionCache"), nestedType("CaptureState"));
        stage.setAccessible(true);
        stage.invoke(manager, cache, snapshot);
        var cancel = MirrorRenderManager.class.getDeclaredMethod("cancelPendingBatch");
        cancel.setAccessible(true);
        cancel.invoke(manager);
        assertSame(old, get(cache, "target"));
        assertEquals(256L * 128, pool.leasedPixels());
        assertSame(next, pool.acquire(512, 256));
        assertNull(get(manager, "pendingBatch"));
    }

    private static Object manager(MirrorFramebufferPool pool) throws ReflectiveOperationException {
        var constructor = MirrorRenderManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var manager = constructor.newInstance();
        set(manager, "pool", pool);
        return manager;
    }

    private static Object cache(RenderTarget target) throws ReflectiveOperationException {
        var constructor = nestedType("ReflectionCache").getDeclaredConstructor(RenderTarget.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(target, 1);
    }

    private static Object snapshot(Object cache) throws ReflectiveOperationException {
        var constructor = nestedType("CaptureState").getDeclaredConstructor(nestedType("ReflectionCache"));
        constructor.setAccessible(true);
        return constructor.newInstance(cache);
    }

    private static Object batch(int size) throws ReflectiveOperationException {
        var constructor = nestedType("PendingBatch").getDeclaredConstructor(List.class, PoseStack.class, MirrorReflectionCamera.class,
                Matrix4f.class, float.class, int.class);
        constructor.setAccessible(true);
        var candidates = new java.util.ArrayList<Object>();
        var candidateConstructor = nestedType("Candidate").getDeclaredConstructors()[0];
        candidateConstructor.setAccessible(true);
        for (int i = 0; i < size; i++) candidates.add(candidateConstructor.newInstance(Set.of("candidate" + i), List.of(),
                new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1), 0.1, 1.0, 1.0, false, false, false));
        return constructor.newInstance(candidates, new PoseStack(), new MirrorReflectionCamera(), new Matrix4f(), 0.5F, 1);
    }

    private static void finish(Object manager, Object cache, Object state, boolean success) throws ReflectiveOperationException {
        var method = MirrorRenderManager.class.getDeclaredMethod("finishCapture", nestedType("ReflectionCache"), nestedType("CaptureState"), boolean.class);
        method.setAccessible(true);
        method.invoke(manager, cache, state, success);
    }
    @Test void pendingBatchAndCurrentFrameChildCapturesCannotBeEvicted() throws ReflectiveOperationException {
        var managerConstructor = MirrorRenderManager.class.getDeclaredConstructor();
        managerConstructor.setAccessible(true);
        var manager = managerConstructor.newInstance();
        set(manager, "frame", 2L);
        var cacheType = nestedType("ReflectionCache");
        var cacheConstructor = cacheType.getDeclaredConstructor(RenderTarget.class, int.class);
        cacheConstructor.setAccessible(true);
        var cache = cacheConstructor.newInstance(new RenderTarget(true) { }, 1);
        var key = Set.of("protected");
        @SuppressWarnings("unchecked")
        var textures = (Map<Set<?>, Object>) get(get(manager, "mainView"), "textures");
        textures.put(key, cache);
        var candidateConstructor = nestedType("Candidate").getDeclaredConstructors()[0];
        candidateConstructor.setAccessible(true);
        var candidate = candidateConstructor.newInstance(key, List.of(), new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1), 0.1, 1.0, 1.0, false, false, false);
        var evict = MirrorRenderManager.class.getDeclaredMethod("evictOldest", List.class);
        evict.setAccessible(true);
        set(cache, "updated", 1L);
        assertEquals(false, evict.invoke(manager, List.of(candidate)));
        assertSame(cache, textures.get(key));
        set(cache, "updated", 2L);
        assertEquals(false, evict.invoke(manager, List.of()));
        assertSame(cache, textures.get(key));
    }

    @Test void rejectedCaptureRestoresTheCompletePublishedImageAndItsSchedule() throws ReflectiveOperationException {
        var cacheType = nestedType("ReflectionCache");
        var constructor = cacheType.getDeclaredConstructor(RenderTarget.class, int.class);
        constructor.setAccessible(true);
        var published = new RenderTarget(true) { };
        var pending = new RenderTarget(true) { };
        var cache = constructor.newInstance(published, 1);
        var matrix = new Matrix4f().rotateY(0.3F);
        var coverage = new double[]{0.1, 0.2, 0.8, 0.9};
        set(cache, "projectionView", matrix);
        set(cache, "coverage", coverage);
        for (var field : new String[]{"eye", "origin", "normal"}) set(cache, field, new Vec3(1, 2, 3));
        for (var field : new String[]{"updated", "updatedNanos", "updateStartedNanos", "updateDeadlineNanos", "updateIntervalNanos"}) set(cache, field, 123L);
        set(cache, "ready", true);
        var snapshotType = nestedType("CaptureState");
        var snapshotConstructor = snapshotType.getDeclaredConstructor(cacheType);
        snapshotConstructor.setAccessible(true);
        var snapshot = snapshotConstructor.newInstance(cache);
        set(cache, "target", pending);
        set(cache, "projectionView", new Matrix4f());
        set(cache, "coverage", new double[]{0, 0, 1, 1});
        for (var field : new String[]{"eye", "origin", "normal"}) set(cache, field, Vec3.ZERO);
        for (var field : new String[]{"updated", "updatedNanos", "updateStartedNanos", "updateDeadlineNanos", "updateIntervalNanos"}) set(cache, field, 456L);
        set(cache, "ready", false);
        var restore = snapshotType.getDeclaredMethod("restore", cacheType);
        restore.setAccessible(true);
        restore.invoke(snapshot, cache);
        assertSame(published, get(cache, "target"));
        assertSame(matrix, get(cache, "projectionView"));
        assertSame(coverage, get(cache, "coverage"));
        assertEquals(true, get(cache, "ready"));
        for (var field : new String[]{"eye", "origin", "normal"}) assertEquals(new Vec3(1, 2, 3), get(cache, field));
        for (var field : new String[]{"updated", "updatedNanos", "updateStartedNanos", "updateDeadlineNanos", "updateIntervalNanos"}) assertEquals(123L, get(cache, field));
    }

    private static Class<?> nestedType(String name) {
        return Arrays.stream(MirrorRenderManager.class.getDeclaredClasses()).filter(type -> type.getSimpleName().equals(name)).findFirst().orElseThrow();
    }

    private static Object get(Object cache, String name) throws ReflectiveOperationException {
        var field = cache.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(cache);
    }

    private static void set(Object cache, String name, Object value) throws ReflectiveOperationException {
        var field = cache.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(cache, value);
    }
}
