package com.rexhub.createapparel.recipe;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.config.ModConfigs;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class OptionalRecipeRoutes {
    private static volatile RecipeRouteSelection selection = RecipeRouteSelection.BOOTSTRAP;

    private OptionalRecipeRoutes() { }

    public static RecipeRouteSelection selection() {
        return selection;
    }

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        selection = new RecipeRouteSelection(ModList.get().isLoaded("destroy"),
                ModConfigs.SERVER.forceSyntheticFiberFallback.get(),
                ModConfigs.SERVER.forceMirrorCoatingFallback.get());
    }

    @SubscribeEvent
    public static void onStarting(ServerStartingEvent event) {
        var server = event.getServer();
        server.reloadResources(server.getPackRepository().getSelectedIds()).join();
        LogUtils.getLogger().info("Apparel recipe routes ready: {}", selection);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        selection = RecipeRouteSelection.BOOTSTRAP;
    }
}
