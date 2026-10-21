package com.rexhub.createapparel.citizen.population.pipeline.mixin;

import com.rexhub.createapparel.citizen.population.pipeline.PipelineProbe;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlStateManager.class, remap = false)
abstract class GlProbeMixin {
    @Inject(method = "_drawElements", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDrawElements(IIIJ)V"), require = 0)
    private static void apparel$draw(int mode, int count, int type, long indices, CallbackInfo callback) { PipelineProbe.draw(); }
    @Inject(method = "_bindTexture", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBindTexture(II)V"), require = 0)
    private static void apparel$bind(int texture, CallbackInfo callback) { PipelineProbe.bind(); }
}
