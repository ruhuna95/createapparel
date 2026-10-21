package com.rexhub.createapparel.citizen.population.pipeline.mixin;

import com.rexhub.createapparel.citizen.population.pipeline.PipelineBuffers;
import com.rexhub.createapparel.citizen.population.pipeline.PipelineProbe;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderProbeMixin implements PipelineBuffers.OwnedBuffer {
    @Override @Accessor("buffer") public abstract java.nio.ByteBuffer apparel$nativeBuffer();
    @Override @Accessor("format") public abstract com.mojang.blaze3d.vertex.VertexFormat apparel$format();
    @Inject(method = "end", at = @At("RETURN"), require = 0)
    private void apparel$ended(CallbackInfoReturnable<BufferBuilder.RenderedBuffer> callback) {
        com.rexhub.createapparel.citizen.population.pipeline.PipelineBuffers.builderEnded();
        PipelineProbe.ended(callback.getReturnValue());
    }
    @Inject(method = "endOrDiscardIfEmpty", at = @At("RETURN"), require = 0)
    private void apparel$discarded(CallbackInfoReturnable<BufferBuilder.RenderedBuffer> callback) { com.rexhub.createapparel.citizen.population.pipeline.PipelineBuffers.builderEnded(); }
}
