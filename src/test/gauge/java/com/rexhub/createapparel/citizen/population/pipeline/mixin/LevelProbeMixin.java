package com.rexhub.createapparel.citizen.population.pipeline.mixin;

import com.rexhub.createapparel.citizen.population.pipeline.PipelineProbe;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelProbeMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void apparel$worldBegin(CallbackInfo callback) { PipelineProbe.world(true); }
    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
    private void apparel$worldEnd(CallbackInfo callback) { PipelineProbe.world(false); }
}
