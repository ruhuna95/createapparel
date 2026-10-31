package com.rexhub.createapparel.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;

public final class ModConfigs {
    private static final ModConfigSpec COMMON_SPEC;
    private static final ModConfigSpec CLIENT_SPEC;
    private static final ModConfigSpec SERVER_SPEC;
    public static final Common COMMON;
    public static final Client CLIENT;
    public static final Server SERVER;

    static {
        var common = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();
        var client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
        var server = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
    }

    private ModConfigs() {
    }

    public static void register(ModContainer context) {
        context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "createapparel-common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "createapparel-client.toml");
        context.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, "createapparel-server.toml");
    }

    public static final class Common {
        public final com.rexhub.createapparel.fashion.FashionConfig fashion;
        public final ModConfigSpec.IntValue maxNewPathRequestsPerTick;
        public final ModConfigSpec.IntValue maxActiveCitizensPerDimension;
        public final ModConfigSpec.IntValue activityRotationTicks;
        public final ModConfigSpec.IntValue crossingWaitTimeout;
        public final ModConfigSpec.DoubleValue crossingPenalty;
        public final ModConfigSpec.IntValue gatewayTransitCooldown;
        public final ModConfigSpec.IntValue populationGaugeCooldown;
        public final ModConfigSpec.IntValue populationCitizenCooldown;
        public final ModConfigSpec.IntValue populationRetryTicks;
        public final ModConfigSpec.IntValue populationHopTimeout;
        public final ModConfigSpec.IntValue populationMaxHops;
        public final ModConfigSpec.IntValue populationQueryBudget;
        public final ModConfigSpec.IntValue maxLoadedCitizensPerDimension;
        public final ModConfigSpec.IntValue populationSpawnBudget;
        public final ModConfigSpec.IntValue populationMaxChains;
        public final ModConfigSpec.IntValue populationIntentTimeout;
        public final ModConfigSpec.IntValue populationStuckTicks;
        public final ModConfigSpec.IntValue populationStuckRetries;
        public final ModConfigSpec.IntValue populationReturnHomeTicks;
        public final ModConfigSpec.IntValue populationAutonomyTicks;
        public final ModConfigSpec.DoubleValue populationVisitChance;
        private Common(ModConfigSpec.Builder builder) {
            fashion = new com.rexhub.createapparel.fashion.FashionConfig(builder);
            builder.push("citizenRoutes");
            maxNewPathRequestsPerTick = builder.defineInRange("maxNewPathRequestsPerTick", 4, 1, 64);
            maxActiveCitizensPerDimension = builder.defineInRange("maxActiveCitizensPerDimension", 80, 1, 4096);
            activityRotationTicks = builder.defineInRange("activityRotationTicks", 1200, 20, 12000);
            crossingWaitTimeout = builder.defineInRange("crossingWaitTimeout", 600, 20, 12000);
            crossingPenalty = builder.defineInRange("crossingPenalty", 16.0, 0, 1024);
            gatewayTransitCooldown = builder.defineInRange("gatewayTransitCooldown", 100, 20, 12000);
            builder.pop();
            builder.push("citizenPopulation");
            populationGaugeCooldown = builder.defineInRange("gaugeActionCooldownTicks", 600, 20, 72000);
            populationCitizenCooldown = builder.defineInRange("citizenTransferCooldownTicks", 2400, 40, 144000);
            populationRetryTicks = builder.defineInRange("resolveRetryTicks", 200, 20, 12000);
            populationHopTimeout = builder.defineInRange("hopTimeoutTicks", 12000, 200, 144000);
            populationMaxHops = builder.defineInRange("maxQueryHops", 16, 1, 128);
            populationQueryBudget = builder.defineInRange("maxQueryNodes", 256, 8, 4096);
            maxLoadedCitizensPerDimension = builder.defineInRange("maxLoadedCitizensPerDimension", 300, 1, 16384);
            populationSpawnBudget = builder.defineInRange("maxSourceSpawnsPerTick", 1, 1, 16);
            populationMaxChains = builder.defineInRange("maxConcurrentPopulationChains", 128, 1, 1024);
            populationIntentTimeout = builder.defineInRange("intentTimeoutTicks", 12000, 200, 144000);
            populationStuckTicks = builder.defineInRange("stuckProgressTimeoutTicks", 600, 100, 12000);
            populationStuckRetries = builder.defineInRange("stuckRetries", 3, 2, 16);
            populationReturnHomeTicks = builder.defineInRange("returnHomeAfterTicks", 6000, 200, 144000);
            populationAutonomyTicks = builder.defineInRange("autonomyCheckTicks", 400, 20, 12000);
            populationVisitChance = builder.defineInRange("impulseVisitChance", 0.05, 0, 1);
            builder.pop();
        }
    }

    public static final class Server {
        public final ModConfigSpec.BooleanValue forceSyntheticFiberFallback;
        public final ModConfigSpec.BooleanValue forceMirrorCoatingFallback;

        private Server(ModConfigSpec.Builder builder) {
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
        public final ModConfigSpec.DoubleValue citizenLod0Distance, citizenLod1Distance, citizenLod2Distance, citizenLodHysteresis;
        public final ModConfigSpec.IntValue routeMarkerRenderDistance;
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.EnumValue<MirrorQuality> quality;
        public final ModConfigSpec.IntValue renderDistance;
        public final ModConfigSpec.IntValue maxSurfacesPerFrame;
        public final ModConfigSpec.IntValue recursionDepth;
        public final ModConfigSpec.DoubleValue resolutionScale;
        public final ModConfigSpec.BooleanValue dynamicResolution;
        public final ModConfigSpec.BooleanValue mergeCoplanarMirrors;
        public final ModConfigSpec.BooleanValue adaptiveUpdateRate;
        public final ModConfigSpec.IntValue maxReflectionFps;
        public final ModConfigSpec.IntValue nearbyReflectionFps;
        public final ModConfigSpec.DoubleValue updateBudgetMillis;
        private Client(ModConfigSpec.Builder builder) {
            builder.push("citizenRendering");
            citizenLod0Distance = builder.defineInRange("lod0Distance", 12.0, 1.0, 512.0);
            citizenLod1Distance = builder.defineInRange("lod1Distance", 32.0, 1.0, 512.0);
            citizenLod2Distance = builder.defineInRange("lod2Distance", 64.0, 1.0, 512.0);
            citizenLodHysteresis = builder.defineInRange("hysteresis", 1.0, 0.0, 8.0);
            builder.pop();
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
