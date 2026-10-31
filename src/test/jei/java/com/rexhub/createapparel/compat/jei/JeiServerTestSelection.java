package com.rexhub.createapparel.compat.jei;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

import java.util.Set;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class JeiServerTestSelection {
    @SubscribeEvent
    public static void loaded(FMLLoadCompleteEvent event) {
        if (!Boolean.getBoolean("apparel.jeiTest.server")) return;
        event.enqueueWork(() -> {
            GameTestHooks.registerGametests();
            var batches = Set.of("recipe_displays", "silkworm_rearing", "phase17_machines");
            GameTestRegistry.getAllTestFunctions().removeIf(test -> !batches.contains(test.batchName()));
            int count = GameTestRegistry.getAllTestFunctions().size();
            if (count < 15) throw new AssertionError("Missing production tests: " + count);
            LogUtils.getLogger().info("PHASE23_SERVER_TESTS_SELECTED {}", count);
        });
    }
}
