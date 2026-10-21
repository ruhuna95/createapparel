package com.rexhub.createapparel.citizen.population.pipeline.mixin;

import com.rexhub.createapparel.citizen.population.pipeline.PipelineBuffers;

import com.rexhub.createapparel.client.renderer.CitizenRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CitizenRenderer.class, remap = false)
abstract class CitizenPipelineMixin {
    @ModifyVariable(method = "render(Lcom/rexhub/createapparel/entity/Citizen;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private MultiBufferSource apparel$source(MultiBufferSource source) { return PipelineBuffers.wrap(source); }
    @Inject(method = "render(Lcom/rexhub/createapparel/entity/Citizen;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"), require = 0)
    private void apparel$end(CallbackInfo callback) { PipelineBuffers.finishCitizen(); }
}
