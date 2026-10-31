package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.recipe.RecipeCacheReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class SewingRecipeIndexEvents {
    private SewingRecipeIndexEvents() {
    }

    @SubscribeEvent
    public static void recipesUpdated(RecipesUpdatedEvent event) {
        RecipeCacheReloadListener.invalidate(event.getRecipeManager());
    }
}
