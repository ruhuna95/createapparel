package com.rexhub.createapparel.recipe;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class RecipeTestSelection {
    @SubscribeEvent
    public static void loaded(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            GameTestHooks.registerGametests();
            GameTestRegistry.getAllTestFunctions().removeIf(test -> !test.batchName().startsWith("optional_integration"));
            if (GameTestRegistry.getAllTestFunctions().isEmpty()) throw new AssertionError("Missing optional integration tests");
            LogUtils.getLogger().info("PHASE25_SERVER_TESTS_SELECTED {}", GameTestRegistry.getAllTestFunctions().size());
        });
    }
}
