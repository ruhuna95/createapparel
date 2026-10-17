package com.rexhub.createapparel.recipe;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.gametest.ForgeGameTestHooks;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RecipeTestSelection {
    @SubscribeEvent
    public static void loaded(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            ForgeGameTestHooks.registerGametests();
            GameTestRegistry.getAllTestFunctions().removeIf(test -> !test.getBatchName().startsWith("optional_integration"));
            if (GameTestRegistry.getAllTestFunctions().isEmpty()) throw new AssertionError("Missing optional integration tests");
            LogUtils.getLogger().info("PHASE25_SERVER_TESTS_SELECTED {}", GameTestRegistry.getAllTestFunctions().size());
        });
    }
}
