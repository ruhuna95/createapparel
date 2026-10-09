package com.rexhub.createapparel.client.mirror;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public final class MirrorFramebufferPool implements AutoCloseable {
    private final Map<Long, ArrayDeque<RenderTarget>> free = new HashMap<>();

    public RenderTarget acquire(int width, int height) {
        var targets = free.get(key(width, height));
        if (targets != null && !targets.isEmpty()) return targets.removeFirst();
        var target = new ReflectionTarget(width, height);
        target.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
        return target;
    }

    public void release(RenderTarget target) {
        var targets = free.computeIfAbsent(key(target.width, target.height), ignored -> new ArrayDeque<>());
        if (targets.size() < 2) targets.add(target); else destroy(target);
    }

    private static void destroy(RenderTarget target) {
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try { target.destroyBuffers(); }
        finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        }
    }

    private static long key(int width, int height) { return ((long) width << 32) | (height & 0xFFFFFFFFL); }

    private static final class ReflectionTarget extends TextureTarget {
        private ReflectionTarget(int width, int height) { super(width, height, true, Minecraft.ON_OSX); }
        @Override public void bindWrite(boolean setViewport) { super.bindWrite(true); }
    }

    @Override public void close() {
        free.values().forEach(targets -> targets.forEach(MirrorFramebufferPool::destroy));
        free.clear();
    }
}
