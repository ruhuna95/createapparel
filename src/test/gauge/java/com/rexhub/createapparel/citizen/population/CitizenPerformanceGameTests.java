package com.rexhub.createapparel.citizen.population;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class CitizenPerformanceGameTests {
    private static final List<Citizen> CITIZENS = new ArrayList<>();
    private static long[] ticks;
    private static GameTestHelper helper;
    private static long started;
    private static int elapsed, samples;
    private static long fixtureStarted;
    private static boolean sampling;
    private static jdk.jfr.Recording recording;
    private CitizenPerformanceGameTests() { }
    @GameTest(template = "citizen_behavior", batch = "citizen_performance", timeoutTicks = 1000000)
    public static void isolatedLoadedCitizenCpuProfile(GameTestHelper test) {
        if (!Boolean.getBoolean("apparel.gaugeTest.performance")) { test.succeed(); return; }
        helper = test; elapsed = samples = 0; sampling = false; CITIZENS.clear();
        int sampleTicks = Integer.getInteger("apparel.gaugeTest.performanceSampleTicks", 400);
        if (sampleTicks < 100 || sampleTicks > 10000) throw new IllegalArgumentException("Invalid sample tick count");
        ticks = new long[sampleTicks];
        int count = Integer.getInteger("apparel.gaugeTest.performanceCount", 100);
        if (!Set.of(0, 50, 100, 300, 500, 1000).contains(count)) throw new IllegalArgumentException("Invalid performanceCount");
        var level = test.getLevel();
        var center = test.absolutePos(new BlockPos(32, 0, 32));
        for (int x = -16; x < 88; x++) for (int z = -16; z < 88; z++) level.setBlock(test.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
        for (int x = (center.getX() - 64) >> 4; x <= (center.getX() + 64) >> 4; x++)
            for (int z = (center.getZ() - 64) >> 4; z <= (center.getZ() + 64) >> 4; z++) level.setChunkForced(x, z, true);
        for (int i = 0; i < count; i++) {
            var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(level)); citizen.setUUID(new UUID(85, i)); citizen.getRandom().setSeed(i); citizen.setInvulnerable(true);
            var pos = test.absolutePos(new BlockPos(4 + i % 32 * 2, 1, 4 + i / 32 * 2)); citizen.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
            citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, pos, level.dimension(), 8));
            test.assertTrue(level.addFreshEntity(citizen), "Diagnostic Citizen joins"); CITIZENS.add(citizen);
        }
        fixtureStarted = System.nanoTime();
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void begin(TickEvent.ServerTickEvent event) {
        if (helper != null && event.phase == TickEvent.Phase.START) started = System.nanoTime();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void end(TickEvent.ServerTickEvent event) {
        if (helper == null || event.phase != TickEvent.Phase.END) return;
        elapsed++;
        if (!sampling) {
            if (elapsed < 120 || Boolean.getBoolean("apparel.gaugeTest.performanceSteady") && System.nanoTime() - fixtureStarted < 10_000_000_000L) return;
            sampling = true;
            if (Boolean.parseBoolean(System.getProperty("apparel.gaugeTest.performanceProfile", "true"))) CitizenPopulationManager.get(helper.getLevel()).metrics().start();
            if (Boolean.getBoolean("apparel.gaugeTest.performanceJfr")) {
                try { recording = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile")); recording.start(); }
                catch (java.io.IOException | java.text.ParseException exception) { throw new IllegalStateException(exception); }
            }
            LogUtils.getLogger().info("CITIZEN_CPU_SAMPLE_BEGIN steady={} warmupTicks={} sampleTicks={}", Boolean.getBoolean("apparel.gaugeTest.performanceSteady"), elapsed, ticks.length);
            return;
        }
        ticks[samples++] = System.nanoTime() - started;
        if (samples < ticks.length) return;
        var test = helper; helper = null; var metrics = CitizenPopulationManager.get(test.getLevel()).metrics(); metrics.stop();
        if (recording != null) {
            try {
                recording.stop(); var path = test.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("citizen-cpu.jfr");
                recording.dump(path); summarizeRecording(path);
            }
            catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
            finally { recording.close(); recording = null; }
        }
        test.assertTrue(CITIZENS.stream().allMatch(c -> c.isAlive() && c.tickCount >= ticks.length), "Every loaded Citizen actually ticks");
        if (!CITIZENS.isEmpty()) {
            var persisted = CITIZENS.get(0).citizenState().serializeNBT(); var restored = new CitizenState(); restored.deserializeNBT(persisted);
            test.assertTrue(persisted.equals(restored.serializeNBT()), "Diagnostic switches do not damage saved Citizen state or equipment");
        }
        var sorted = ticks.clone(); Arrays.sort(sorted);
        LogUtils.getLogger().info(String.format(Locale.ROOT, "CITIZEN_CPU_PROFILE count=%d samples=%d meanMs=%.6f p95Ms=%.6f p99Ms=%.6f goalDisabled=%s autonomyDisabled=%s populationDisabled=%s plannerDisabled=%s profile=%s%n%s",
                CITIZENS.size(), samples, Arrays.stream(ticks).average().orElse(0) / 1e6, DiagnosticTiming.quantile(sorted, .95) / 1e6, DiagnosticTiming.quantile(sorted, .99) / 1e6,
                CitizenDiagnostics.debugDisableCitizenBehaviorGoal, CitizenDiagnostics.debugDisableCitizenAutonomy, CitizenDiagnostics.debugDisableCitizenPopulationTick, CitizenDiagnostics.debugDisableCitizenRoutePlanning,
                System.getProperty("apparel.gaugeTest.performanceProfile", "true"), metrics.summary()));
        CITIZENS.forEach(Citizen::discard); CITIZENS.clear(); test.succeed();
    }
    private static void summarizeRecording(java.nio.file.Path path) throws java.io.IOException {
        var leaves = new HashMap<String, Integer>(); var callers = new HashMap<String, Integer>();
        int samples = 0, citizen = 0, travel = 0;
        try (var events = new jdk.jfr.consumer.RecordingFile(path)) {
            while (events.hasMoreEvents()) {
                var event = events.readEvent();
                if (!event.getEventType().getName().equals("jdk.ExecutionSample") || event.getThread("sampledThread") == null
                        || !event.getThread("sampledThread").getJavaName().equals("Server thread") || event.getStackTrace() == null) continue;
                var frames = event.getStackTrace().getFrames(); if (frames.isEmpty()) continue; samples++;
                var first = frames.get(0).getMethod(); leaves.merge(first.getType().getName() + "." + first.getName(), 1, Integer::sum);
                boolean inCitizen = false, inTravel = false; String caller = null;
                for (var frame : frames) {
                    var method = frame.getMethod(); String name = method.getType().getName().replace('/', '.');
                    inCitizen |= name.equals("com.rexhub.createapparel.entity.Citizen") && method.getName().equals("tick");
                    inTravel |= name.equals("net.minecraft.world.entity.LivingEntity") && method.getName().equals("travel");
                    if (caller == null && !name.startsWith("java.") && !name.startsWith("jdk.") && !name.startsWith("sun.")) caller = name + "." + method.getName();
                }
                if (inCitizen) citizen++; if (inTravel) travel++; if (caller != null) callers.merge(caller, 1, Integer::sum);
            }
        }
        LogUtils.getLogger().info("CITIZEN_JFR_SUMMARY serverSamples={} citizenTickStacks={} livingTravelStacks={} (overlapping stack occurrences, not CPU durations)", samples, citizen, travel);
        leaves.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(10)
                .forEach(entry -> LogUtils.getLogger().info("CITIZEN_JFR_LEAF {} samples={}", entry.getKey(), entry.getValue()));
        callers.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(10)
                .forEach(entry -> LogUtils.getLogger().info("CITIZEN_JFR_CALLER {} samples={} (first non-JDK caller, not ownership proof)", entry.getKey(), entry.getValue()));
    }
}
