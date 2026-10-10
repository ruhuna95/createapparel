package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.recipe.RecipeCacheReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class SewingRecipeIndexEvents {
    private SewingRecipeIndexEvents() {
    }

    @SubscribeEvent
    public static void recipesUpdated(RecipesUpdatedEvent event) {
        RecipeCacheReloadListener.invalidate(event.getRecipeManager());
    }
}
