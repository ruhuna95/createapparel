package com.rexhub.createapparel.citizen.population;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.ClothingRackBlock;
import com.rexhub.createapparel.block.entity.ClothingRackBlockEntity;
import com.rexhub.createapparel.block.entity.WallHangerBlockEntity;
import com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache;
import com.rexhub.createapparel.entity.Mannequin;
import com.rexhub.createapparel.entity.MannequinVariant;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class DisplayPerformanceClientChecks {
    private static final int[] COUNTS = {0, 32, 128};
    private static final long[] FRAMES = new long[240];
    private static final ConcurrentLinkedQueue<Long> SERVER_TIMES = new ConcurrentLinkedQueue<>();
    private static volatile boolean ready, sampling;
    private static volatile Throwable failure;
    private static int stage, scale, ticks, total, frames, textures;
    private static long previousFrame, serverStarted;

    private DisplayPerformanceClientChecks() { }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("apparel.gaugeTest.displayPerformance") || event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (failure != null) throw new AssertionError("Display server fixture failed", failure);
            MultiplayerCheck.require(++total < 6000, "Display performance timeout at " + stage);
            if (stage == 0 && mc.screen != null) {
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(6); mc.options.simulationDistance().set(5);
                mc.options.framerateLimit().set(260); mc.options.enableVsync().set(false);
                mc.options.fov().set(90);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720); mc.resizeDisplay();
                stage = 1;
                mc.createWorldOpenFlows().createFreshLevel("display-performance-" + System.nanoTime(),
                        new LevelSettings("Display performance QA", net.minecraft.world.level.GameType.CREATIVE, false,
                                net.minecraft.world.Difficulty.PEACEFUL, false, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(26, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            var server = mc.getSingleplayerServer();
            if (mc.level == null || mc.player == null || server == null) return;
            if (stage == 1) {
                mc.setScreen(null); stage = 2;
                server.execute(() -> {
                    try {
                        var level = server.overworld();
                        level.setDayTime(6000);
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                        for (var pos : BlockPos.betweenClosed(-4, 80, -4, 66, 80, 46)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
                        var player = server.getPlayerList().getPlayers().get(0);
                        player.getAbilities().flying = true; player.onUpdateAbilities();
                        player.connection.teleport(30, 94, -12, 0, 20);
                        ready = true;
                    } catch (Throwable exception) { failure = exception; }
                });
                return;
            }
            if (stage == 2 && ready && ++ticks >= 100 && mc.screen == null) {
                textures = RuntimeGarmentTextureCache.size(); frames = 0; previousFrame = 0;
                SERVER_TIMES.clear();
                serverStarted = 0; stage = 3; sampling = true;
            }
        } catch (Throwable exception) { fail(mc, exception); }
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (!sampling) return;
        if (event.phase == TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        try {
            long now = System.nanoTime();
            if (previousFrame == 0) { previousFrame = now; return; }
            FRAMES[frames++] = now - previousFrame; previousFrame = now;
            if (frames < FRAMES.length) return;
            sampling = false;
            MultiplayerCheck.require(RuntimeGarmentTextureCache.size() == textures, "Steady display frames generated new composite textures");
            int loadedMannequins = 0, cachedMannequins = 0;
            for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof Mannequin mannequin)) continue;
                loadedMannequins++;
                var renderer = mc.getEntityRenderDispatcher().getRenderer(mannequin);
                var snapshots = renderer.getClass().getDeclaredField("snapshots");
                snapshots.setAccessible(true);
                if (((java.util.Map<?, ?>) snapshots.get(renderer)).containsKey(mannequin)) cachedMannequins++;
            }
            MultiplayerCheck.require(loadedMannequins == COUNTS[scale], "Not all mannequins loaded");
            if (COUNTS[scale] > 0) MultiplayerCheck.require(cachedMannequins > 0, "Production renderer has not rendered any mannequin");
            int loadedHangers = 0, loadedRacks = 0;
            for (int i = 0; i < COUNTS[scale]; i++) {
                var pos = position(i);
                if (mc.level.getBlockEntity(pos) instanceof WallHangerBlockEntity) loadedHangers++;
                if (mc.level.getBlockEntity(pos.offset(2, 0, 0)) instanceof ClothingRackBlockEntity) loadedRacks++;
            }
            MultiplayerCheck.require(loadedHangers == COUNTS[scale] && loadedRacks == COUNTS[scale], "Not all display block entities loaded");
            var sorted = FRAMES.clone(); Arrays.sort(sorted);
            double average = Arrays.stream(FRAMES).average().orElseThrow() / 1e6;
            LogUtils.getLogger().info(String.format(Locale.ROOT,
                    "DISPLAY_PERFORMANCE countPerType=%d loadedHangers=%d loadedRacks=%d frames=%d meanFrameMs=%.3f p95FrameMs=%.3f meanFPS=%.3f loadedMannequins=%d cachedMannequins=%d textures=%d integratedMeanMSPT=%.3f resolution=1280x720 fpsSetting=260",
                    COUNTS[scale], loadedHangers, loadedRacks, frames, average, sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1e6,
                    1000 / average, loadedMannequins, cachedMannequins, textures, SERVER_TIMES.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6));
            var image = Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build", "display-performance-" + COUNTS[scale] + ".png");
            try (var screenshot = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) { screenshot.writeToFile(image); }
            if (++scale == COUNTS.length) {
                stage = 99; LogUtils.getLogger().info("DISPLAY_PERFORMANCE_CLIENT_PASSED"); mc.stop(); return;
            }
            ticks = 0; ready = false; stage = 2;
            Objects.requireNonNull(mc.getSingleplayerServer()).execute(() -> populate(mc));
        } catch (Throwable exception) { fail(mc, exception); }
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!sampling) return;
        if (event.phase == TickEvent.Phase.START) serverStarted = System.nanoTime();
        else if (serverStarted != 0) { SERVER_TIMES.add(System.nanoTime() - serverStarted); serverStarted = 0; }
    }

    private static BlockPos position(int i) { return new BlockPos(i % 16 * 4, 81, i / 16 * 5); }

    private static void populate(Minecraft mc) {
        try {
            var level = Objects.requireNonNull(mc.getSingleplayerServer()).overworld();
            for (int i = COUNTS[scale - 1]; i < COUNTS[scale]; i++) {
                var pos = position(i);
                level.setBlockAndUpdate(pos.south(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(pos, ModBlocks.WALL_HANGER.get().defaultBlockState());
                MultiplayerCheck.require(((WallHangerBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos)))
                        .insert(new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()), false), "Hanger insert");
                var rackPos = pos.offset(2, 0, 0);
                var state = ModBlocks.CLOTHING_RACK.get().defaultBlockState();
                level.setBlock(rackPos, state, Block.UPDATE_CLIENTS);
                level.setBlock(rackPos.above(), state.setValue(ClothingRackBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
                var rack = (ClothingRackBlockEntity) Objects.requireNonNull(level.getBlockEntity(rackPos));
                for (int slot : new int[]{1, 2}) MultiplayerCheck.require(rack.insert(slot, new ItemStack(ModItems.PULLOVER_HOODIE.get()), false), "Rack insert");
                var mannequin = Objects.requireNonNull(ModEntities.mannequins().get(MannequinVariant.FULL_WHITE_CLASSIC).get().create(level));
                mannequin.moveTo(pos.getX() + 1.5, 81, pos.getZ() + 2.5, 180, 0);
                mannequin.mannequinState().loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
                MultiplayerCheck.require(level.addFreshEntity(mannequin), "Mannequin spawn");
            }
            ready = true;
        } catch (Throwable exception) { failure = exception; }
    }

    private static void fail(Minecraft mc, Throwable exception) {
        sampling = false; stage = 99;
        LogUtils.getLogger().error("DISPLAY_PERFORMANCE_CLIENT_FAILED", exception); mc.stop();
    }

    private static final class MultiplayerCheck {
        static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    }
}
