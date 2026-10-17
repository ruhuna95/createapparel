package com.rexhub.createapparel.compat.destroy.mixin;

import com.petrolpark.destroy.chemistry.legacy.LegacyElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(value = LegacyElement.class, remap = false)
abstract class LegacyElementMixin {
    @SuppressWarnings("target")
    @Shadow @Final @Mutable private static LegacyElement[] $VALUES;

    @Invoker("<init>")
    private static LegacyElement createapparel$create(String name, int ordinal, String symbol,
                                                     Float mass, Float electronegativity, double[] valencies) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void createapparel$addSilver(CallbackInfo callback) {
        for (var element : $VALUES) {
            if (element.getSymbol().equals("Ag")) return;
        }
        int ordinal = $VALUES.length;
        $VALUES = Arrays.copyOf($VALUES, ordinal + 1);
        $VALUES[ordinal] = createapparel$create("SILVER", ordinal, "Ag", 107.8682f, 1.93f, new double[]{0, 1, 2});
    }
}
