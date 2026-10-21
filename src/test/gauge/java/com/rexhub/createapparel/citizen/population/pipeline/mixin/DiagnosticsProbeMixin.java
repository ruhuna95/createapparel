package com.rexhub.createapparel.citizen.population.pipeline.mixin;

import com.rexhub.createapparel.client.garment.GarmentDiagnostics;
import com.rexhub.createapparel.citizen.population.pipeline.PipelineProbe;
import com.rexhub.createapparel.citizen.population.pipeline.PipelineBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GarmentDiagnostics.class, remap = false)
abstract class DiagnosticsProbeMixin {
    @Inject(method = "start", at = @At("RETURN"), require = 0)
    private static void apparel$reset(CallbackInfo callback) { PipelineProbe.reset(); }
    @Inject(method = "report", at = @At("RETURN"), cancellable = true, require = 0)
    private static void apparel$report(CallbackInfoReturnable<String> callback) {
        if (Boolean.getBoolean("apparel.gaugeTest.pipelineInteractive")) callback.setReturnValue(callback.getReturnValue() + System.lineSeparator() + PipelineBuffers.report() + System.lineSeparator() + PipelineProbe.report());
    }
}
