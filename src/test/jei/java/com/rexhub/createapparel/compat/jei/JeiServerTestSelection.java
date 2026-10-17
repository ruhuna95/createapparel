package com.rexhub.createapparel.compat.jei;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.gametest.ForgeGameTestHooks;

import java.util.Set;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class JeiServerTestSelection {
    @SubscribeEvent
    public static void loaded(FMLLoadCompleteEvent event) {
        if (!Boolean.getBoolean("apparel.jeiTest.server")) return;
        event.enqueueWork(() -> {
            ForgeGameTestHooks.registerGametests();
            var batches = Set.of("recipe_displays", "silkworm_rearing", "phase17_machines");
            GameTestRegistry.getAllTestFunctions().removeIf(test -> !batches.contains(test.getBatchName()));
            int count = GameTestRegistry.getAllTestFunctions().size();
            if (count < 15) throw new AssertionError("Missing production tests: " + count);
            LogUtils.getLogger().info("PHASE23_SERVER_TESTS_SELECTED {}", count);
        });
    }
}
