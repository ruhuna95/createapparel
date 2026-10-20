package com.rexhub.createapparel.citizen.population;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.gametest.ForgeGameTestHooks;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GaugeTestSelection {
    @SubscribeEvent public static void loaded(FMLLoadCompleteEvent event) {
        if (!Boolean.getBoolean("apparel.gaugeTest.server")) return;
        event.enqueueWork(() -> {
            ForgeGameTestHooks.registerGametests();
            boolean scale = Boolean.getBoolean("apparel.gaugeTest.scale");
            boolean trash = Boolean.getBoolean("apparel.gaugeTest.trash");
            boolean moving = Boolean.getBoolean("apparel.gaugeTest.moving");
            GameTestRegistry.getAllTestFunctions().removeIf(test -> moving ? !test.getBatchName().equals("citizen_moving_restart")
                    : scale ? !test.getBatchName().equals("citizen_scale")
                    : trash ? !test.getBatchName().equals("citizen_trash_compat")
                    : !test.getBatchName().equals("citizen_population") && !test.getBatchName().equals("citizen_population_recovery") && !test.getBatchName().equals("citizen_gauge_lifecycle"));
            if (GameTestRegistry.getAllTestFunctions().isEmpty()) throw new AssertionError("No Citizen Gauge tests selected");
            LogUtils.getLogger().info("GAUGE_TESTS_SELECTED {}", GameTestRegistry.getAllTestFunctions().size());
        });
    }
}
