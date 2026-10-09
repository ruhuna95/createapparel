package com.rexhub.createapparel.client.mixin;

import com.rexhub.createapparel.client.mirror.MirrorLevelRendererAccess;
import com.rexhub.createapparel.client.mirror.MirrorRenderManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(LevelRenderer.class)
public abstract class MirrorLevelRendererMixin implements MirrorLevelRendererAccess {
    @Shadow @Final private ObjectArrayList<Object> renderChunksInFrustum;
    @Shadow private Frustum cullingFrustum;
    @Shadow private int renderedEntities;
    @Shadow private int culledEntities;
    @Shadow protected abstract void applyFrustum(Frustum frustum);
    @Unique private final Deque<Snapshot> createapparel$reflectionStates = new ArrayDeque<>();
    @Unique private final Deque<ObjectArrayList<Object>> createapparel$reflectionChunkLists = new ArrayDeque<>();

    @Override public void createapparel$beginReflection() {
        var chunks = createapparel$reflectionChunkLists.pollFirst();
        if (chunks == null) chunks = new ObjectArrayList<>(renderChunksInFrustum.size());
        chunks.addAll(renderChunksInFrustum);
        createapparel$reflectionStates.push(new Snapshot(chunks, cullingFrustum, renderedEntities, culledEntities));
    }

    @Override public void createapparel$endReflection() {
        var previous = createapparel$reflectionStates.pop();
        renderChunksInFrustum.clear();
        renderChunksInFrustum.addAll(previous.chunks());
        cullingFrustum = previous.frustum();
        renderedEntities = previous.renderedEntities();
        culledEntities = previous.culledEntities();
        previous.chunks().clear();
        createapparel$reflectionChunkLists.addFirst(previous.chunks());
    }

    @Inject(method = "setupRender", at = @At("HEAD"), cancellable = true)
    private void createapparel$reflectionCull(Camera camera, Frustum frustum, boolean captured, boolean spectator, CallbackInfo callback) {
        if (!MirrorRenderManager.INSTANCE.isRenderingReflection()) return;
        applyFrustum(new Frustum(frustum));
        callback.cancel();
    }

    @Inject(method = "compileChunks", at = @At("HEAD"), cancellable = true)
    private void createapparel$reuseCompiledChunks(Camera camera, CallbackInfo callback) {
        if (MirrorRenderManager.INSTANCE.isRenderingReflection()) callback.cancel();
    }

    @Inject(method = "shouldShowEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void createapparel$preserveMainOutlineTarget(CallbackInfoReturnable<Boolean> callback) {
        if (MirrorRenderManager.INSTANCE.isRenderingReflection()) callback.setReturnValue(false);
    }
}
