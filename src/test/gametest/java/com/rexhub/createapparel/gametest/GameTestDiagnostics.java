package com.rexhub.createapparel.gametest;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

@EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class GameTestDiagnostics {
    private GameTestDiagnostics() { }

    @SubscribeEvent
    public static void loaded(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            net.neoforged.neoforge.gametest.GameTestHooks.registerGametests();
            var tests = net.minecraft.gametest.framework.GameTestRegistry.getAllTestFunctions();
            var names = java.util.Arrays.stream(System.getProperty("apparel.gameTest.names", "").split(","))
                    .map(String::strip).filter(name -> !name.isEmpty()).map(name -> name.toLowerCase(java.util.Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());
            if (!names.isEmpty()) {
                tests.removeIf(test -> !names.contains(test.testName().toLowerCase(java.util.Locale.ROOT)));
                if (tests.size() != names.size()) throw new AssertionError("Missing selected GameTests: " + names);
            }
            var wrapped = tests.stream().map(test -> new net.minecraft.gametest.framework.TestFunction(
                    test.batchName(), test.testName(), test.structureName(), test.rotation(), test.maxTicks(), test.setupTicks(),
                    test.required(), test.manualOnly(), test.maxAttempts(), test.requiredSuccesses(), test.skyAccess(), helper -> {
                        var bounds = helper.testInfo.getStructureBounds();
                        if (test.structureName().equals("createapparel:apparel_state"))
                            net.minecraft.gametest.framework.StructureUtils.removeBarriers(bounds, helper.getLevel());
                        test.run(helper);
                    })).toList();
            tests.clear();
            tests.addAll(wrapped);
            GlobalTestReporter.replaceWith(new LogTestReporter() {
                @Override public void onTestFailed(GameTestInfo test) {
                    super.onTestFailed(test);
                    LogUtils.getLogger().error("GameTest failure details: {}", test.getTestName(), test.getError());
                }
            });
        });
    }
}
