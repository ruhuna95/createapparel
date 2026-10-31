package com.rexhub.createapparel.recipe;

import com.rexhub.createapparel.CreateApparel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class RecipeCacheReloadListener {
    private static volatile Object generation = new Object();

    private RecipeCacheReloadListener() {
    }

    public static Object generation() {
        return generation;
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) manager -> invalidate(event.getServerResources().getRecipeManager()));
    }

    public static void invalidate(RecipeManager manager) {
        SewingRecipeInputIndex.rebuild(manager);
        generation = new Object();
    }
}
