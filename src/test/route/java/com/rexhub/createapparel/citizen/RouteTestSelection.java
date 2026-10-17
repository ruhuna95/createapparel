package com.rexhub.createapparel.citizen;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.gametest.ForgeGameTestHooks;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RouteTestSelection {
    @SubscribeEvent
    public static void loaded(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            ForgeGameTestHooks.registerGametests();
            var names = Arrays.stream(System.getProperty("apparel.routeTest.names", "").split(","))
                    .map(String::strip).filter(name -> !name.isEmpty()).map(name -> name.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            GameTestRegistry.getAllTestFunctions().removeIf(test -> names.isEmpty()
                    ? !test.getBatchName().startsWith("citizen_route") && !test.getBatchName().startsWith("citizen_navigation")
                    : names.stream().noneMatch(name -> test.getTestName().toLowerCase(Locale.ROOT).equals(name)
                    || test.getTestName().toLowerCase(Locale.ROOT).endsWith("." + name)));
            int count = GameTestRegistry.getAllTestFunctions().size();
            if (count == 0 || !names.isEmpty() && count != names.size()) throw new AssertionError("Missing route tests: " + names);
            LogUtils.getLogger().info("ROUTE_TESTS_SELECTED {} {}", count, names);
        });
    }
}
