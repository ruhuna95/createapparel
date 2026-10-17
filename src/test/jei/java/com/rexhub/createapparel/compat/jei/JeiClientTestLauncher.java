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
    private static int worldIndex;
    private static boolean switchingWorld;
    private static boolean disconnecting;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished || disconnecting) return;
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
                openWorld();
            }
            if (switchingWorld && mc.level == null && mc.getSingleplayerServer() == null && mc.getOverlay() == null) {
                check(com.rexhub.createapparel.recipe.OptionalRecipeRoutes.selection().equals(
                        com.rexhub.createapparel.recipe.RecipeRouteSelection.BOOTSTRAP), "Stopping a world clears its recipe selection");
                switchingWorld = false;
                openWorld();
            }
            if (!started || mc.level == null || mc.player == null) return;
            if (++ticks > 6000) throw new AssertionError("JEI client tests timed out");
            if (ticks < 60) return;
            var tabs = net.minecraftforge.common.CreativeModeTabRegistry.getSortedCreativeModeTabs();
            int create = tabs.indexOf(com.simibubi.create.AllCreativeModeTabs.BASE_CREATIVE_TAB.get());
            int palettes = tabs.indexOf(com.simibubi.create.AllCreativeModeTabs.PALETTES_CREATIVE_TAB.get());
            int apparel = tabs.indexOf(com.rexhub.createapparel.init.ModCreativeModeTabs.CREATE_APPAREL.get());
            int garment = tabs.indexOf(com.rexhub.createapparel.init.ModCreativeModeTabs.CREATE_APPAREL_GARMENT.get());
            check(create >= 0 && create < palettes && palettes < apparel && apparel < garment,
                    "Apparel tabs follow both Create tabs without separating them");
            var server = mc.getSingleplayerServer();
            for (String path : java.util.List.of("mixing/synthetic_fiber", "synthetic_fiber_from_nylon", "mixing/mirror_coating_fluid",
                    "mixing/mirror_coating_fluid_from_destroy")) {
                var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path);
                check(mc.level.getRecipeManager().byKey(id).isPresent() == server.getRecipeManager().byKey(id).isPresent(),
                        "Optional recipes are synchronized from the integrated server: " + path);
            }
            if (!ModList.get().isLoaded("jei")) {
                check(!mc.level.getRecipeManager().getAllRecipesFor(SewingRecipe.recipeType()).isEmpty(), "Recipes without JEI");
                boolean forced = worldIndex == 1;
                check(com.rexhub.createapparel.recipe.OptionalRecipeRoutes.selection().equals(
                        new com.rexhub.createapparel.recipe.RecipeRouteSelection(ModList.get().isLoaded("destroy"), forced, forced)),
                        "Integrated server uses this world's SERVER values: " + worldIndex);
                if (worldIndex < 2) {
                    worldIndex++;
                    ticks = 0;
                    switchingWorld = true;
                    disconnecting = true;
                    try {
                        mc.level.disconnect();
                        mc.clearLevel(new net.minecraft.client.gui.screens.TitleScreen());
                    } finally {
                        disconnecting = false;
                    }
                    return;
                }
                LogUtils.getLogger().info("PHASE25_WORLD_SWITCH_PASSED false/false -> true/true -> false/false");
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

    private static void openWorld() throws Exception {
        var mc = Minecraft.getInstance();
        String name = "phase23-" + System.nanoTime();
        var config = mc.gameDirectory.toPath().resolve("saves").resolve(name)
                .resolve("serverconfig/createapparel-server.toml");
        Files.createDirectories(config.getParent());
        boolean forced = !ModList.get().isLoaded("jei") && worldIndex == 1;
        boolean mirrorForced = ModList.get().isLoaded("jei") ? Boolean.getBoolean("apparel.jeiTest.mirrorFallback") : forced;
        Files.writeString(config, "[recipes]\nforceSyntheticFiberFallback = " + forced
                + "\nforceMirrorCoatingFallback = " + mirrorForced + "\n");
        mc.createWorldOpenFlows().createFreshLevel(name,
                new LevelSettings("Phase23 JEI tests", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT),
                new WorldOptions(23, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
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
