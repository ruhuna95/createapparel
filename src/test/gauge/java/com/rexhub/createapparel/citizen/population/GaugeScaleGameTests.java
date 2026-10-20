package com.rexhub.createapparel.citizen.population;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.management.ManagementFactory;
import java.util.*;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class GaugeScaleGameTests {
    private static final int LOADED_CITIZENS = 100, SAMPLE_TICKS = 600;
    private static final java.lang.reflect.Method PATH_WAIT = pathWaitMethod();
    private static Run active;
    private GaugeScaleGameTests() { }
    @GameTest(template = "citizen_behavior", batch = "citizen_scale", timeoutTicks = 2400)
    public static void fixedLoadedDistributionAtAllThreeSavedScales(GameTestHelper helper) {
        for (int x = 0; x < 36; x++) for (int z = 0; z < 36; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y < 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        begin(helper, 0);
    }
    private static void begin(GameTestHelper helper, int phase) {
        active = new Run(helper, new int[]{200, 1000, 2000}[phase]); active.prepare();
        helper.runAfterDelay(SAMPLE_TICKS + 100, () -> {
            var run = active; active = null; run.finish(); run.cleanup();
            if (phase < 2) begin(helper, phase + 1); else helper.succeed();
        });
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tickStart(TickEvent.ServerTickEvent event) {
        if (active != null && event.phase == TickEvent.Phase.START) active.tickStarted = System.nanoTime();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tickEnd(TickEvent.ServerTickEvent event) {
        if (active == null || event.phase != TickEvent.Phase.END || active.tickStarted == 0) return;
        var run = active; long elapsed = System.nanoTime() - run.tickStarted; run.tickStarted = 0;
        run.elapsed++;
        if (run.elapsed <= 100) return;
        if (run.samples < run.ticks.length) { run.ticks[run.samples++] = elapsed; run.total += elapsed; }
        if (run.elapsed % 20 == 0) run.peakHeap = Math.max(run.peakHeap, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void maintenanceStart(TickEvent.LevelTickEvent event) {
        if (active != null && event.level == active.level && event.phase == TickEvent.Phase.END) active.maintenanceStarted = System.nanoTime();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void maintenanceEnd(TickEvent.LevelTickEvent event) {
        if (active == null || event.level != active.level || event.phase != TickEvent.Phase.END) return;
        var run = active;
        if (run.elapsed >= 100 && run.samples < SAMPLE_TICKS) run.maintenance += System.nanoTime() - run.maintenanceStarted;
        var data = CitizenPopulationManager.get(run.level).data();
        if (run.networkReadyAt < 0 && data.networks().cached(run.nodes.get(run.nodes.size() - 1).id()) != null) run.networkReadyAt = run.elapsed;
        run.maxPathWait = Math.max(run.maxPathWait, oldestPathWait(CitizenPathfindingScheduler.get(run.level)));
    }
    private static long oldestPathWait(CitizenPathfindingScheduler scheduler) {
        if (PATH_WAIT == null) return -1;
        try { return ((Number) PATH_WAIT.invoke(scheduler)).longValue(); }
        catch (ReflectiveOperationException ignored) { return -1; }
    }
    private static java.lang.reflect.Method pathWaitMethod() {
        try { return CitizenPathfindingScheduler.class.getMethod("oldestWaitTicks"); }
        catch (NoSuchMethodException ignored) { return null; }
    }
    private static long gcCount() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum(); }
    private static long gcMillis() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum(); }
    private static final class Run {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final int gaugeCount;
        private final List<CitizenGauge> nodes = new ArrayList<>();
        private final List<UUID> saved = new ArrayList<>(), markers = new ArrayList<>();
        private final List<Citizen> citizens = new ArrayList<>();
        private final long[] ticks = new long[SAMPLE_TICKS];
        private int samples, elapsed;
        private long tickStarted, maintenanceStarted, maintenance, total, peakHeap, gcCount, gcMillis, maxPathWait = -1, networkReadyAt = -1;
        private Run(GameTestHelper helper, int gauges) { this.helper = helper; level = helper.getLevel(); gaugeCount = gauges; }
        private void prepare() {
            var manager = CitizenPopulationManager.get(level); var data = manager.data(); var routes = CitizenRouteManager.get(level).data();
            for (int i = 0; i < gaugeCount; i++) {
                CitizenGauge gauge;
                if (i < 16) {
                    var pos = helper.absolutePos(new BlockPos(4 + i / 4 * 8, 2, 34));
                    level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
                    if (i % 4 == 0) level.setBlock(pos, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
                    var entity = (CitizenGaugeBlockEntity) level.getBlockEntity(pos);
                    entity.initialize(i % 4, helper.absolutePos(new BlockPos(4 + i % 4 * 9, 1, 4 + i / 4 * 9)));
                    gauge = data.at(pos, i % 4); manager.settings(gauge.id(), 4, 4); gauge = data.gauge(gauge.id());
                } else {
                    var pos = new BlockPos(1000000 + i % 50 * 80, 64, 1000000 + i / 50 * 80);
                    gauge = new CitizenGauge(new UUID(10, i), pos.above(), new UUID(11, i), pos, 32, 10, 0, false); data.put(gauge);
                }
                nodes.add(gauge);
                if (i % 100 != 0) data.link(nodes.get(i - 1).id(), gauge.id());
                for (int j = 0; j < 10; j++) {
                    var marker = RouteMarker.create(new UUID(12, i * 10L + j), gauge.center().offset(j * 2, 0, 0), RouteMarkerType.NORMAL);
                    if (i < 16 && j >= 2) marker = RouteMarker.create(marker.id(), new BlockPos(1500000 + i * 64 + j * 2, 64, 1500000), RouteMarkerType.NORMAL);
                    if (i < 16 && j < 2) {
                        level.setBlock(marker.pos(), ModBlocks.CITIZEN_ROUTE_MARKER.get().defaultBlockState(), 3);
                        var entity = (com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity) level.getBlockEntity(marker.pos());
                        marker = CitizenRouteManager.get(level).register(entity).withDistrict(gauge.districtId());
                    }
                    routes.put(marker.withDistrict(gauge.contains(marker.center()) ? gauge.districtId() : null)); markers.add(marker.id());
                    if (j > 0) routes.link(markers.get(markers.size() - 2), marker.id(), false);
                }
            }
            for (int i = 0; i < LOADED_CITIZENS; i++) {
                var citizen = ModEntities.CITIZEN.get().create(level); citizen.setUUID(new UUID(13, i)); citizen.setInvulnerable(true);
                citizen.getRandom().setSeed(i);
                var pos = helper.absolutePos(new BlockPos(2 + i % 10 * 3, 1, 2 + i / 10 * 3));
                citizen.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
                citizen.citizenState().setPopulation(new CitizenPopulationState(nodes.get(i % 16).districtId(), false, false, 0));
                citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, pos, level.dimension(), 0));
                if (!level.addFreshEntity(citizen)) throw new AssertionError("Could not spawn scale Citizen"); citizens.add(citizen);
            }
            for (int i = LOADED_CITIZENS; i < gaugeCount * 5; i++) {
                var id = new UUID(14, i); saved.add(id); data.register(id, new CitizenPopulationState(nodes.get(16 + (i - LOADED_CITIZENS) % (gaugeCount - 16)).districtId(), false, false, 0));
            }
            nodes.forEach(gauge -> data.networks().request(gauge.id()));
            gcCount = gcCount(); gcMillis = gcMillis();
        }
        private void finish() {
            var manager = CitizenPopulationManager.get(level); var data = manager.data(); var routes = CitizenRouteManager.get(level).data();
            helper.assertTrue(samples >= SAMPLE_TICKS - 2 && manager.loadedCount() == LOADED_CITIZENS, "Stable real loaded population is maintained");
            helper.assertTrue(nodes.stream().allMatch(gauge -> data.gauge(gauge.id()) != null), "Unloaded Gauge records remain registered");
            long start = System.nanoTime(); var nbt = data.save(new CompoundTag()); var routeNbt = routes.save(new CompoundTag()); long saveNanos = System.nanoTime() - start;
            helper.assertTrue(nbt.getList("Citizens", 10).size() == gaugeCount * 5 && routes.markers().size() == gaugeCount * 10, "Measured registry and route counts must match the scenario exactly");
            start = System.nanoTime(); CitizenPopulationSavedData.load(nbt); CitizenRouteSavedData.load(routeNbt); long loadNanos = System.nanoTime() - start;
            int bytes;
            try { var output = new ByteArrayOutputStream(); NbtIo.write(nbt, new DataOutputStream(output)); NbtIo.write(routeNbt, new DataOutputStream(output)); bytes = output.size(); }
            catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
            var sorted = Arrays.copyOf(ticks, samples); Arrays.sort(sorted);
            LogUtils.getLogger().info(String.format(Locale.ROOT,
                    "CITIZEN_LIVE_SCALE gauges=%d savedCitizens=%d loadedCitizens=%d markers=%d samples=%d meanMSPT=%.3f p95=%.3f p99=%.3f maintenanceMs=%.3f heapPeakBytes=%d GC_count=%d GC_ms=%d networkReadyTicks=%d pathWaitMaxTicks=%d saveMs=%.3f loadMs=%.3f nbtBytes=%d",
                    gaugeCount, gaugeCount * 5, manager.loadedCount(), gaugeCount * 10, samples, total / 1e6 / samples, sorted[(int) Math.ceil(samples * .95) - 1] / 1e6,
                    sorted[(int) Math.ceil(samples * .99) - 1] / 1e6, maintenance / 1e6 / samples, peakHeap, gcCount() - gcCount, gcMillis() - gcMillis,
                    networkReadyAt, maxPathWait, saveNanos / 1e6, loadNanos / 1e6, bytes));
        }
        private void cleanup() {
            var manager = CitizenPopulationManager.get(level); var data = manager.data(); var routes = CitizenRouteManager.get(level).data();
            citizens.forEach(Citizen::discard); saved.forEach(data::removeCitizen);
            for (var id : markers) { var marker = routes.marker(id); if (marker != null && marker.pos().getX() < 1000000) level.removeBlock(marker.pos(), false); routes.remove(id); }
            for (var gauge : nodes) { if (gauge.pos().getX() < 1000000) level.removeBlock(gauge.pos(), false); data.remove(gauge.id()); }
        }
    }
}
