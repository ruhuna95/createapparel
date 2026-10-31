package com.rexhub.createapparel.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.client.mirror.MirrorRenderManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class MirrorGameRendererMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void createapparel$renderMirrors(LevelRenderer renderer, net.minecraft.client.DeltaTracker delta,
                                            boolean outline, Camera camera, GameRenderer gameRenderer, LightTexture light,
                                            Matrix4f viewMatrix, Matrix4f projection, Operation<Void> original) {
        var view = new PoseStack();
        view.mulPose(viewMatrix);
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        long finishTime = net.minecraft.Util.getNanos();
        var mirrors = MirrorRenderManager.INSTANCE;
        mirrors.beginFrame(view, camera);
        original.call(renderer, delta, outline, camera, gameRenderer, light, viewMatrix, projection);
        mirrors.finishFrame(renderer, view, partialTick, finishTime, camera, gameRenderer, light, projection);
    }

    @Inject(method = "getMainCamera", at = @At("HEAD"), cancellable = true)
    private void createapparel$reflectionCamera(CallbackInfoReturnable<Camera> callback) {
        var mirrors = MirrorRenderManager.INSTANCE;
        if (mirrors.isRenderingReflection()) callback.setReturnValue(mirrors.reflectionCamera());
    }
}
