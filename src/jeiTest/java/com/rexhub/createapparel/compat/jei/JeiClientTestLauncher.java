package com.rexhub.createapparel.compat.jei;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.recipe.SewingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class JeiClientTestLauncher {
    private static int ticks;
    private static boolean started;
    private static boolean finished;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (!started && mc.screen != null && mc.getOverlay() == null) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.guiScale().set(2);
                mc.options.renderDistance().set(3);
                mc.options.framerateLimit().set(60);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720);
                mc.resizeDisplay();
                mc.createWorldOpenFlows().createFreshLevel("phase23-" + System.nanoTime(),
                        new LevelSettings("Phase23 JEI tests", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(23, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            if (!started || mc.level == null || mc.player == null) return;
            if (++ticks > 6000) throw new AssertionError("JEI client tests timed out");
            if (ticks < 60) return;
            if (!ModList.get().isLoaded("jei")) {
                check(!mc.level.getRecipeManager().getAllRecipesFor(SewingRecipe.recipeType()).isEmpty(), "Recipes without JEI");
                finish("NO_JEI");
            } else {
                JeiClientChecks.tick();
            }
        } catch (Throwable error) {
            LogUtils.getLogger().error("PHASE23_CLIENT_FAILED", error);
            try { Files.writeString(root().resolve("build/phase23/client-failed.txt"), error.toString()); }
            catch (Exception ignored) { }
            finished = true;
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event) {
        if (!finished && ModList.get().isLoaded("jei")) JeiClientChecks.render(event);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static Path root() { return Path.of(System.getProperty("apparel.jeiTest.projectDir")); }

    static void finish(String mode) throws Exception {
        LogUtils.getLogger().info("PHASE23_{}_CLIENT_PASSED", mode);
        Files.writeString(root().resolve("build/phase23/client-" + mode.toLowerCase(java.util.Locale.ROOT) + "-passed.txt"), "PASSED\n");
        finished = true;
        Minecraft.getInstance().stop();
    }
}
