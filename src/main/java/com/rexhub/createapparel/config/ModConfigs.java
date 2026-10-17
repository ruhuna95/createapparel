package com.rexhub.createapparel.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ModConfigs {
    private static final ForgeConfigSpec COMMON_SPEC;
    private static final ForgeConfigSpec CLIENT_SPEC;
    private static final ForgeConfigSpec SERVER_SPEC;
    public static final Common COMMON;
    public static final Client CLIENT;
    public static final Server SERVER;

    static {
        var common = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();
        var client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
        var server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
    }

    private ModConfigs() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "createapparel-common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "createapparel-client.toml");
        context.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, "createapparel-server.toml");
    }

    public static final class Common {
        public final ForgeConfigSpec.IntValue maxNewPathRequestsPerTick;
        public final ForgeConfigSpec.IntValue maxActiveCitizensPerDimension;
        public final ForgeConfigSpec.IntValue activityRotationTicks;
        public final ForgeConfigSpec.IntValue crossingWaitTimeout;
        public final ForgeConfigSpec.DoubleValue crossingPenalty;
        public final ForgeConfigSpec.IntValue gatewayTransitCooldown;
        private Common(ForgeConfigSpec.Builder builder) {
            builder.push("citizenRoutes");
            maxNewPathRequestsPerTick = builder.defineInRange("maxNewPathRequestsPerTick", 4, 1, 64);
            maxActiveCitizensPerDimension = builder.defineInRange("maxActiveCitizensPerDimension", 80, 1, 4096);
            activityRotationTicks = builder.defineInRange("activityRotationTicks", 1200, 20, 12000);
            crossingWaitTimeout = builder.defineInRange("crossingWaitTimeout", 600, 20, 12000);
            crossingPenalty = builder.defineInRange("crossingPenalty", 16.0, 0, 1024);
            gatewayTransitCooldown = builder.defineInRange("gatewayTransitCooldown", 100, 20, 12000);
            builder.pop();
        }
    }

    public static final class Server {
        public final ForgeConfigSpec.BooleanValue forceSyntheticFiberFallback;
        public final ForgeConfigSpec.BooleanValue forceMirrorCoatingFallback;

        private Server(ForgeConfigSpec.Builder builder) {
            builder.push("recipes");
            forceSyntheticFiberFallback = builder.comment("Also enable heated sugar, slime and water mixing when Destroy is installed; keep the nylon recipe available. Restart the world/server after changing.")
                    .worldRestart().define("forceSyntheticFiberFallback", false);
            forceMirrorCoatingFallback = builder.comment("Also enable heated zinc, glowstone and water mixing when Destroy and silver are available; keep the Destroy recipe available. Without either prerequisite, fallback is automatic. Replaces COMMON recipes.mirror.enableMirrorCoatingFallback; set this key explicitly. Restart the world/server after changing.")
                    .worldRestart().define("forceMirrorCoatingFallback", false);
            builder.pop();
        }
    }

    public enum MirrorQuality { OFF, LOW, MEDIUM, HIGH }

    public static final class Client {
        public final ForgeConfigSpec.IntValue routeMarkerRenderDistance;
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
            builder.push("citizenRoutes");
            routeMarkerRenderDistance = builder.defineInRange("renderDistance", 64, 8, 128);
            builder.pop();
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
