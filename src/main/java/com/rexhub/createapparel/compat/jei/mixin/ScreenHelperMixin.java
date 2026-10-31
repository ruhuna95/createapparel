package com.rexhub.createapparel.compat.jei.mixin;

import com.rexhub.createapparel.client.advertisement.AdvertisementControlScreen;
import com.rexhub.createapparel.client.advertisement.PhotoEditScreen;
import com.rexhub.createapparel.client.advertisement.PhotographyScreen;
import com.rexhub.createapparel.client.screen.CitizenGaugeScreen;
import com.rexhub.createapparel.client.screen.WardrobeScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(targets = "mezz.jei.library.gui.helpers.ScreenHelper", remap = false)
abstract class ScreenHelperMixin {
    @Inject(method = "getGuiProperties", at = @At("HEAD"), cancellable = true)
    private void createapparel$hideApparelScreens(Screen screen, CallbackInfoReturnable<Optional<?>> callback) {
        if (screen instanceof WardrobeScreen<?> || screen instanceof CitizenGaugeScreen
                || screen instanceof PhotographyScreen || screen instanceof PhotoEditScreen
                || screen instanceof AdvertisementControlScreen)
            callback.setReturnValue(Optional.empty());
    }
}
