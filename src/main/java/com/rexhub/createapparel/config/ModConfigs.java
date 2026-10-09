package com.rexhub.createapparel.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ModConfigs {
    private static final ForgeConfigSpec COMMON_SPEC;
    private static final ForgeConfigSpec CLIENT_SPEC;
    public static final Common COMMON;
    public static final Client CLIENT;

    static {
        var common = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();
        var client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private ModConfigs() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "createapparel-common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "createapparel-client.toml");
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue enableMirrorCoatingFallback;
        private Common(ForgeConfigSpec.Builder builder) {
            builder.push("recipes").push("mirror");
            enableMirrorCoatingFallback = builder.comment("Enable heated zinc, glowstone and water mixing. Reload recipes after changing.")
                    .define("enableMirrorCoatingFallback", false);
            builder.pop(2);
        }
    }

    public enum MirrorQuality { OFF, LOW, MEDIUM, HIGH }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.EnumValue<MirrorQuality> quality;
        public final ForgeConfigSpec.IntValue renderDistance;
        public final ForgeConfigSpec.IntValue maxSurfacesPerFrame;
        public final ForgeConfigSpec.IntValue recursionDepth;
        public final ForgeConfigSpec.DoubleValue resolutionScale;
        public final ForgeConfigSpec.BooleanValue dynamicResolution;
        public final ForgeConfigSpec.BooleanValue mergeCoplanarMirrors;
        public final ForgeConfigSpec.BooleanValue adaptiveUpdateRate;
        public final ForgeConfigSpec.IntValue maxReflectionFps;
        public final ForgeConfigSpec.IntValue nearbyReflectionFps;
        public final ForgeConfigSpec.DoubleValue updateBudgetMillis;
        private Client(ForgeConfigSpec.Builder builder) {
            builder.push("mirror");
            enabled = builder.define("enabled", true);
            quality = builder.defineEnum("quality", MirrorQuality.MEDIUM);
            renderDistance = builder.defineInRange("renderDistance", 32, 1, 64);
            maxSurfacesPerFrame = builder.comment("Maximum top-level world captures per frame and child surfaces per view. Coplanar clusters count once; synchronized updates may span frames.")
                    .defineInRange("maxSurfacesPerFrame", 4, 1, 8);
            recursionDepth = builder.comment("Additional mirror reflections inside a reflection. 0 disables recursion; higher values increase rendering cost.")
                    .defineInRange("recursionDepth", 1, 0, 4);
            resolutionScale = builder.defineInRange("resolutionScale", 0.5, 0.125, 1.0);
            dynamicResolution = builder.define("dynamicResolution", true);
            mergeCoplanarMirrors = builder.define("mergeCoplanarMirrors", true);
            adaptiveUpdateRate = builder.define("adaptiveUpdateRate", true);
            maxReflectionFps = builder.comment("Maximum texture refresh rate per mirror; cached textures still draw every frame.")
                    .defineInRange("maxReflectionFps", 30, 1, 120);
            nearbyReflectionFps = builder.comment("Refresh rate for the most prominent nearby mirror, independent of the background mirror rate.")
                    .defineInRange("nearbyReflectionFps", 60, 1, 120);
            updateBudgetMillis = builder.comment("CPU budget for starting top-level captures, including child rendering cost. Only the first capture may exceed it; incomplete batches retain published images.")
                    .defineInRange("updateBudgetMillis", 4.0, 0.5, 50.0);
            builder.pop();
        }
    }
}
