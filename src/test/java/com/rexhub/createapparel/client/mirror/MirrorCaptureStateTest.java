package com.rexhub.createapparel.client.mirror;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MirrorCaptureStateTest {
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
        var candidate = candidateConstructor.newInstance(key, List.of(), new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1), 0.1, 1.0, 1.0, false, false);
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
