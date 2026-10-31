package com.rexhub.createapparel.compat.destroy.mixin;

import petrolpark.mc.destroy.chemistry.legacy.LegacyElement;
import petrolpark.mc.destroy.core.chemistry.MoleculeRenderer.Geometry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(value = LegacyElement.class, remap = false)
abstract class LegacyElementMixin {
    @Invoker("<init>")
    private static LegacyElement createapparel$create(String name, ResourceLocation id, String symbol,
            Float mass, Float electronegativity, double[] valencies, Function<Integer, Geometry> geometry,
            ResourceLocation model) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void createapparel$addSilver(CallbackInfo callback) {
        for (var element : LegacyElement.values()) {
            if (element.getSymbol().equals("Ag")) return;
        }
        createapparel$create("SILVER", ResourceLocation.parse("createapparel:silver"), "Ag",
                107.8682f, 1.93f, new double[]{0, 1, 2}, null, null);
    }
}
