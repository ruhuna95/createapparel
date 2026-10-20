package com.rexhub.createapparel.compat.jei;

import com.rexhub.createapparel.CreateApparel;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class GaugeJeiClientChecks implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "gauge_client_test");
    }
    @Override public void onRuntimeAvailable(IJeiRuntime available) { runtime = available; }
    @Override public void onRuntimeUnavailable() { runtime = null; }

    public static void assertHidden(Screen screen) {
        if (runtime == null || runtime.getScreenHelper().getGuiProperties(screen).isPresent()
                || runtime.getIngredientListOverlay().isListDisplayed()) {
            throw new AssertionError("JEI must be hidden on " + screen.getClass().getSimpleName());
        }
    }

    public static void assertInventoryVisible(Screen screen) {
        if (runtime == null || runtime.getScreenHelper().getGuiProperties(screen).isEmpty()
                || !runtime.getIngredientListOverlay().isListDisplayed()) {
            throw new AssertionError("JEI must remain visible on the normal inventory");
        }
    }
}
