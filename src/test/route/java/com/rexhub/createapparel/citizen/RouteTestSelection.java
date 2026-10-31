package com.rexhub.createapparel.citizen;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class RouteTestSelection {
    @SubscribeEvent
    public static void loaded(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            GameTestHooks.registerGametests();
            if (!Boolean.getBoolean("apparel.escalatedTests"))
                GameTestRegistry.getAllTestFunctions().removeIf(test -> test.batchName().equals("citizen_escalated"));
            var names = Arrays.stream(System.getProperty("apparel.routeTest.names", "").split(","))
                    .map(String::strip).filter(name -> !name.isEmpty()).map(name -> name.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            GameTestRegistry.getAllTestFunctions().removeIf(test -> names.isEmpty()
                    ? !test.batchName().startsWith("citizen_route") && !test.batchName().startsWith("citizen_navigation")
                    && !test.batchName().equals("citizen_escalated")
                    : names.stream().noneMatch(name -> test.testName().toLowerCase(Locale.ROOT).equals(name)
                    || test.testName().toLowerCase(Locale.ROOT).endsWith("." + name)));
            int count = GameTestRegistry.getAllTestFunctions().size();
            if (count == 0 || !names.isEmpty() && count != names.size()) throw new AssertionError("Missing route tests: " + names);
            LogUtils.getLogger().info("ROUTE_TESTS_SELECTED {} {}", count, names);
        });
    }
}
