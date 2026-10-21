package com.rexhub.createapparel.citizen.population.pipeline.mixin;

import com.rexhub.createapparel.citizen.population.pipeline.PipelineProbe;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiBufferSource.BufferSource.class)
abstract class BufferSourceProbeMixin {
    @Inject(method = "getBuffer", at = @At("HEAD"), require = 0)
    private void apparel$request(RenderType type, CallbackInfoReturnable<VertexConsumer> callback) {
        PipelineProbe.request((MultiBufferSource) this, type);
    }
    @Inject(method = {"endBatch()V", "endBatch(Lnet/minecraft/client/renderer/RenderType;)V", "endLastBatch()V"}, at = @At("HEAD"), require = 0)
    private void apparel$mutation(CallbackInfo callback) {
        com.rexhub.createapparel.citizen.population.pipeline.PipelineBuffers.externalRequest((MultiBufferSource) this);
    }
}
