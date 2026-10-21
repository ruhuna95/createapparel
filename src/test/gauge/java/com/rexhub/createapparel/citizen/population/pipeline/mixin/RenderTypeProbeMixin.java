package com.rexhub.createapparel.citizen.population.pipeline.mixin;

import com.rexhub.createapparel.citizen.population.pipeline.PipelineProbe;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
abstract class RenderTypeProbeMixin {
    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private void apparel$flush(BufferBuilder builder, VertexSorting sorting, CallbackInfo callback) { PipelineProbe.flush((RenderType) (Object) this, builder); }
    @Inject(method = "end", at = @At("RETURN"), require = 0)
    private void apparel$flushed(BufferBuilder builder, VertexSorting sorting, CallbackInfo callback) { PipelineProbe.flushed(); }
}
