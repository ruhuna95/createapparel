package com.rexhub.createapparel.client.mirror;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public final class MirrorFramebufferPool implements AutoCloseable {
    static final int MAX_FREE_TARGETS = 8;
    static final long MAX_FREE_PIXELS = 8L * 1024 * 1024;
    static final long MAX_LEASED_PIXELS = 32L * 1024 * 1024;
    private final Map<Long, ArrayDeque<RenderTarget>> free = new LinkedHashMap<>(16, 0.75F, true);
    private final BiFunction<Integer, Integer, RenderTarget> factory;
    private final Consumer<RenderTarget> destroyer;
    private long freePixels;
    private long leasedPixels;
    private int freeTargets;

    public MirrorFramebufferPool() {
        this(MirrorFramebufferPool::create, MirrorFramebufferPool::destroy);
    }

    MirrorFramebufferPool(BiFunction<Integer, Integer, RenderTarget> factory, Consumer<RenderTarget> destroyer) {
        this.factory = factory;
        this.destroyer = destroyer;
    }

    static long pixels(int width, int height) { return (long) width * height; }
    long leasedPixels() { return leasedPixels; }
    long freePixels() { return freePixels; }
    int freeTargets() { return freeTargets; }
    boolean canAcquire(int width, int height) {
        long pixels = pixels(width, height);
        return width > 0 && height > 0 && pixels <= MAX_LEASED_PIXELS - leasedPixels;
    }

    @Nullable public RenderTarget acquire(int width, int height) {
        if (!canAcquire(width, height)) return null;
        var targets = free.get(key(width, height));
        RenderTarget target;
        long pixels = pixels(width, height);
        if (targets != null && !targets.isEmpty()) {
            target = targets.removeFirst();
            freePixels -= pixels;
            freeTargets--;
            if (targets.isEmpty()) free.remove(key(width, height));
        } else target = factory.apply(width, height);
        leasedPixels += pixels;
        return target;
    }

    @Nullable RenderTarget acquireForCapture(int width, int height) {
        if (free.containsKey(key(width, height)) || pixels(width, height) > MAX_FREE_PIXELS) return acquire(width, height);
        var prepared = acquire(width, height);
        if (prepared != null) release(prepared);
        return null;
    }

    public void release(RenderTarget target) {
        long pixels = pixels(target.width, target.height);
        leasedPixels -= pixels;
        var targets = free.get(key(target.width, target.height));
        if ((targets == null || targets.size() < 2) && pixels <= MAX_FREE_PIXELS) {
            while (freeTargets >= MAX_FREE_TARGETS || pixels > MAX_FREE_PIXELS - freePixels) evictFreeTarget();
            free.computeIfAbsent(key(target.width, target.height), ignored -> new ArrayDeque<>()).add(target);
            freePixels += pixels;
            freeTargets++;
        } else destroyer.accept(target);
    }

    private void evictFreeTarget() {
        var iterator = free.entrySet().iterator();
        var entry = iterator.next();
        var target = entry.getValue().removeFirst();
        if (entry.getValue().isEmpty()) iterator.remove();
        freePixels -= pixels(target.width, target.height);
        freeTargets--;
        destroyer.accept(target);
    }

    private static RenderTarget create(int width, int height) {
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        float[] clearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        try {
            var target = new ReflectionTarget(width, height);
            target.setFilterMode(GL11.GL_LINEAR);
            return target;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager._bindTexture(texture);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (!depth) GlStateManager._disableDepthTest();
            GlStateManager._clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            GlStateManager._clearDepth(clearDepth);
        }
    }

    private static void destroy(RenderTarget target) {
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean deletedTexture = texture == target.getColorTextureId() || texture == target.getDepthTextureId();
        try { target.destroyBuffers(); }
        finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager._bindTexture(deletedTexture ? 0 : texture);
        }
    }

    private static long key(int width, int height) { return ((long) width << 32) | (height & 0xFFFFFFFFL); }

    private static final class ReflectionTarget extends TextureTarget {
        private ReflectionTarget(int width, int height) { super(width, height, true, Minecraft.ON_OSX); }
        @Override public void bindWrite(boolean setViewport) { super.bindWrite(true); }
    }

    @Override public void close() {
        free.values().forEach(targets -> targets.forEach(destroyer));
        free.clear();
        freePixels = 0;
        freeTargets = 0;
    }
}
