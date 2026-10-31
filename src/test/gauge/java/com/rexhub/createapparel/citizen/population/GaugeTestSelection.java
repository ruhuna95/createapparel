package com.rexhub.createapparel.citizen.population;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class GaugeTestSelection {
    @SubscribeEvent public static void loaded(FMLLoadCompleteEvent event) {
        if (!Boolean.getBoolean("apparel.gaugeTest.server")) return;
        event.enqueueWork(() -> {
            GameTestHooks.registerGametests();
            boolean scale = Boolean.getBoolean("apparel.gaugeTest.scale");
            boolean trash = Boolean.getBoolean("apparel.gaugeTest.trash");
            boolean moving = Boolean.getBoolean("apparel.gaugeTest.moving");
            boolean performance = Boolean.getBoolean("apparel.gaugeTest.performance");
            GameTestRegistry.getAllTestFunctions().removeIf(test -> performance ? !test.batchName().equals("citizen_performance")
                    : moving ? !test.batchName().equals("citizen_moving_restart")
                    : scale ? !test.batchName().equals("citizen_scale")
                    : trash ? !test.batchName().equals("citizen_trash_compat")
                    : !test.batchName().startsWith("citizen_population") && !test.batchName().equals("citizen_gauge_lifecycle"));
            if (GameTestRegistry.getAllTestFunctions().isEmpty()) throw new AssertionError("No Citizen Gauge tests selected");
            LogUtils.getLogger().info("GAUGE_TESTS_SELECTED {}", GameTestRegistry.getAllTestFunctions().size());
        });
    }
}
