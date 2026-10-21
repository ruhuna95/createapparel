package com.rexhub.createapparel.citizen.population;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.client.garment.GarmentDiagnostics;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import com.rexhub.createapparel.citizen.population.pipeline.PipelineBuffers;
import com.rexhub.createapparel.citizen.population.pipeline.PipelineProbe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class CitizenMovementClientChecks {
    private static final String[] CASES = System.getProperty("apparel.gaugeTest.movementCases",
            "fixed-3d,approach-3d,fixed-mesh,approach-mesh,fixed-impostor,approach-impostor").split(",");
    private static final Set<String> PATTERNS = Set.of("fixed", "approach", "depart", "boundary", "mixed", "camera", "turn", "both", "teleport", "chunks", "mirror", "mirrorcamera", "varied", "palette", "street", "spread", "occluded");
    private static final Map<UUID, java.util.Set<Integer>> OBSERVED = new HashMap<>();
    private static CitizenRepresentationPrototype prototype;
    private static int scenario, ticks;
    private static boolean started, measuring;
    private static volatile boolean pending;
    private static volatile Throwable failure;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static long generation;
    private static int phase;
    private static long sampleHits, sampleMisses, sampleBuilds, sampleSprites, sampleBuildNanos;
    private static int minLoaded, maxLoaded;
    private static final Map<UUID, java.lang.ref.WeakReference<Citizen>> FIRST_INSTANCES = new HashMap<>();
    private static final Set<UUID> RECREATED = new HashSet<>();
    private static final java.util.List<net.minecraft.nbt.CompoundTag> EQUIPMENT = new ArrayList<>();
    private static final java.util.List<net.minecraft.nbt.CompoundTag> INITIAL_EQUIPMENT = new ArrayList<>();
    private static final java.util.List<com.rexhub.createapparel.entity.CitizenAppearance> INITIAL_APPEARANCE = new ArrayList<>();

    private CitizenMovementClientChecks() { }

    static boolean tick(List<Citizen> citizens) throws Exception {
        if (failure != null) throw new AssertionError("Movement fixture failed", failure);
        var mc = Minecraft.getInstance();
        if (Boolean.getBoolean("apparel.gaugeTest.pipelineResourcePack") && !com.rexhub.createapparel.citizen.population.pipeline.PipelineAtlasResourceChecks.tick(() -> {
            CitizenPipelineImageChecks.runAtlasPack((com.rexhub.createapparel.client.renderer.CitizenRenderer) mc.getEntityRenderDispatcher().renderers.get(com.rexhub.createapparel.init.ModEntities.CITIZEN.get()));
            return null;
        })) return false;
        if (!started) {
            for (String name : CASES) {
                var pieces = name.split("-");
                if (pieces.length != 2 || !PATTERNS.contains(pieces[0]) || !Set.of("3d", "quiet", "mesh", "impostor", "request", "fixedbuf", "bulk", "atlas").contains(pieces[1]))
                    throw new IllegalArgumentException("Unknown movement case: " + name);
                if (Set.of("request", "fixedbuf", "bulk", "atlas").contains(pieces[1]) && !Boolean.getBoolean("apparel.gaugeTest.pipeline"))
                    throw new IllegalArgumentException("Pipeline prototypes require -PgaugePipeline=true");
            }
            started = true;
            prototype = CitizenRepresentationPrototype.install();
            if (Boolean.getBoolean("apparel.gaugeTest.pipeline")) com.rexhub.createapparel.citizen.population.pipeline.PipelineNativeChecks.run();
            if (Boolean.getBoolean("apparel.gaugeTest.pipelineImages")) CitizenPipelineImageChecks.run(prototype.original());
            if (Boolean.getBoolean("apparel.gaugeTest.representationImages")) CitizenRepresentationImageChecks.run(prototype);
            for (var citizen : citizens) {
                var equipment = citizen.citizenState().loadout().serializeNBT();
                EQUIPMENT.add(equipment); INITIAL_EQUIPMENT.add(equipment.copy()); INITIAL_APPEARANCE.add(citizen.citizenState().appearance());
            }
            configure();
        }
        if (reload != null) {
            if (!reload.isDone()) return false;
            reload.join(); reload = null;
            long next = com.rexhub.createapparel.client.garment.ClientGarmentAssets.INSTANCE.generation();
            if (next <= generation) throw new AssertionError("Resource generation unchanged");
            if (Boolean.getBoolean("apparel.gaugeTest.pipeline")) PipelineBuffers.verifyReloadProbe();
            prototype.close(); prototype = CitizenRepresentationPrototype.install();
            if (Boolean.getBoolean("apparel.gaugeTest.pipelineImages")) CitizenPipelineImageChecks.run(prototype.original());
            LogUtils.getLogger().info("CITIZEN_MOVEMENT_RELOAD generationBefore={} generationAfter={} oldResourcesClosed=true", generation, next);
            scenario = ticks = 0; phase++; configure();
        }
        if (pending) return false;
        ticks++;
        int warmup = Math.max(1, Integer.getInteger("apparel.gaugeTest.movementWarmupTicks", Boolean.getBoolean("apparel.gaugeTest.pipeline") ? 120 : 40));
        int sampleTicks = Math.max(200, Integer.getInteger("apparel.gaugeTest.movementSampleTicks", 200));
        int frame = Math.max(0, ticks - warmup);
        String name = CASES[scenario];
        String pattern = name.split("-")[0];
        if (ticks == 1) setup(citizens, pattern);
        if (!pending) update(citizens, pattern, frame);
        if (ticks == warmup) {
            if (Boolean.getBoolean("apparel.gaugeTest.pipeline")) PipelineProbe.reset();
            sampleHits = prototype.hits; sampleMisses = prototype.misses; sampleBuilds = prototype.builds;
            sampleSprites = prototype.spriteBuilds; sampleBuildNanos = prototype.buildNanos;
            if (phase == 0 && Boolean.getBoolean("apparel.gaugeTest.movementFramesOnly")) GarmentDiagnostics.startFramesOnly();
            else GarmentDiagnostics.start();
            measuring = true;
        }
        int loaded = 0;
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof Citizen citizen) {
            loaded++;
            if (pattern.equals("chunks")) {
                var first = FIRST_INSTANCES.putIfAbsent(citizen.getUUID(), new java.lang.ref.WeakReference<>(citizen));
                if (first != null && first.get() != citizen) RECREATED.add(citizen.getUUID());
            }
            int lod = prototype.lod(citizen);
            if (lod >= 0) OBSERVED.computeIfAbsent(citizen.getUUID(), ignored -> new HashSet<>()).add(lod);
            boolean still = pattern.equals("fixed") || pattern.equals("palette");
            citizen.walkAnimation.update(still ? 0 : .35F + (citizen.getId() % 3) * .15F, 1);
            if (!still) citizen.citizenState().setBehavior(com.rexhub.createapparel.entity.CitizenBehaviorState.anchored(
                    com.rexhub.createapparel.entity.CitizenBehaviorMode.WANDER, BlockPos.ZERO, mc.level.dimension(), 0));
        }
        minLoaded = Math.min(minLoaded, loaded); maxLoaded = Math.max(maxLoaded, loaded);
        if (name.equals("chunks-3d") && loaded == 0) {
            var field = prototype.original().getClass().getDeclaredField("snapshots"); field.setAccessible(true);
            var snapshots = (Map<?, ?>) field.get(prototype.original());
            for (var reference : FIRST_INSTANCES.values()) {
                var unloaded = reference.get();
                if (unloaded != null && snapshots.containsKey(unloaded)) throw new AssertionError("Unloaded Citizen retained render cache");
            }
        }
        if (ticks < warmup + sampleTicks) return false;
        GarmentDiagnostics.stop(); measuring = false;
        if (Boolean.getBoolean("apparel.gaugeTest.pipeline")) {
            PipelineBuffers.verifyApplied();
            if (PipelineProbe.enabled && !(phase == 0 && Boolean.getBoolean("apparel.gaugeTest.movementFramesOnly"))) PipelineProbe.verify();
            LogUtils.getLogger().info("CITIZEN_PIPELINE_PROFILE case={} count={} phase={} {} {}", name, citizens.size(), phase, PipelineBuffers.report(), PipelineProbe.report());
            PipelineBuffers.close(); PipelineBuffers.verifyClosed();
        }
        if (pattern.startsWith("mirror")) verifyReflections();
        if (pattern.equals("chunks")) {
            if (minLoaded != 0 || maxLoaded < citizens.size() || RECREATED.isEmpty()) throw new AssertionError("Chunk unload/reload was not exercised");
            LogUtils.getLogger().info("CITIZEN_MOVEMENT_CHUNKS_PASSED minLoaded={} maxLoaded={} recreatedUuids={} nativeRendererCacheRemovalChecked={}",
                    minLoaded, maxLoaded, RECREATED.size(), name.equals("chunks-3d"));
        }
        if (pattern.equals("boundary") && GarmentDiagnostics.timing().counter("citizenLodChanges") != 0)
            throw new AssertionError("LOD chattered inside the hysteresis margin");
        if (pattern.equals("approach") || pattern.equals("depart")) {
            long complete = OBSERVED.values().stream().filter(values -> values.containsAll(Set.of(0, 1, 2, 3))).count();
            if (complete == 0) throw new AssertionError("Movement failed to traverse all four LODs: " + OBSERVED);
            LogUtils.getLogger().info("CITIZEN_MOVEMENT_LODS case={} citizensWithAllFourLods={} observedCitizens={}", name, complete, OBSERVED.size());
        }
        for (int i = 0; i < citizens.size(); i++) if (!EQUIPMENT.get(i).equals(citizens.get(i).citizenState().loadout().serializeNBT()))
            throw new AssertionError("Representation changed server equipment");
        LogUtils.getLogger().info("CITIZEN_MOVEMENT_PROFILE case={} count={} phase={} trial={} warmupTicks={} sampleTicks={} controlledClientFixture=true {} {}",
                name, citizens.size(), phase, scenario + 1, warmup, sampleTicks, prototype.report(), GarmentDiagnostics.report());
        LogUtils.getLogger().info("CITIZEN_MOVEMENT_WINDOW case={} hits={} misses={} meshBuilds={} spriteBuilds={} buildTotalMs={} (measurement interval only; excludes warmup)",
                name, prototype.hits - sampleHits, prototype.misses - sampleMisses, prototype.builds - sampleBuilds,
                prototype.spriteBuilds - sampleSprites, (prototype.buildNanos - sampleBuildNanos) / 1e6);
        var directory = Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build", "citizen-movement-images");
        Files.createDirectories(directory);
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            String trial = Collections.frequency(Arrays.asList(CASES), name) > 1 ? "-trial" + (scenario + 1) : "";
            image.writeToFile(directory.resolve(citizens.size() + "-" + name + "-phase" + phase + trial + ".png"));
        }
        prototype.close();
        if (!prototype.resourcesClosed()) throw new AssertionError("Prototype resources were not released");
        LogUtils.getLogger().info("CITIZEN_REPRESENTATION_RELEASE case={} textureReleases={} nativeTargetClosed=true meshEntries=0", name, prototype.released);
        if (++scenario == CASES.length) {
            if (phase == 0 && Boolean.getBoolean("apparel.gaugeTest.movementReload")) {
                generation = com.rexhub.createapparel.client.garment.ClientGarmentAssets.INSTANCE.generation();
                if (Boolean.getBoolean("apparel.gaugeTest.pipeline")) PipelineBuffers.beginReloadProbe();
                reload = mc.reloadResourcePacks(); return false;
            }
            LogUtils.getLogger().info("CITIZEN_MOVEMENT_PASSED cases={} count={} phases={} equipmentRetained=true; prototype quality/adoption requires image review", CASES.length, citizens.size(), phase + 1);
            return true;
        }
        ticks = 0; configure();
        return false;
    }

    private static void verifyReflections() throws Exception {
        if (prototype.reflectionChecks == 0) throw new AssertionError("Moving mirror fixture did not render reflected Citizens");
        var snapshots = prototype.original().getClass().getDeclaredField("snapshots"); snapshots.setAccessible(true);
        var keys = Collections.newSetFromMap(new IdentityHashMap<>());
        var lods = new HashSet<Integer>();
        for (var cached : ((Map<?, ?>) snapshots.get(prototype.original())).values()) {
            var field = cached.getClass().getDeclaredField("reflectionLods"); field.setAccessible(true);
            var views = (Map<?, ?>) field.get(cached);
            if (views != null) { keys.addAll(views.keySet()); for (var value : views.values()) lods.add((int) value); }
        }
        keys.addAll(prototype.observedReflectionViews);
        if (keys.size() < 2) throw new AssertionError("Multiple mirror views were not exercised");
        LogUtils.getLogger().info("CITIZEN_MOVEMENT_MIRROR_PASSED checkedReflectionCalls={} independentViews={} reflectionLods={} mainLodNeverOverwritten=true",
                prototype.reflectionChecks, keys.size(), lods);
    }

    private static void configure() {
        if (Boolean.getBoolean("apparel.gaugeTest.pipeline")) {
            PipelineBuffers.resetCounters();
            PipelineProbe.enabled = !CASES[scenario].endsWith("quiet");
            PipelineBuffers.mode(CASES[scenario].endsWith("request") ? 1 : CASES[scenario].endsWith("fixedbuf") ? 2 : CASES[scenario].endsWith("bulk") ? 3 : CASES[scenario].endsWith("atlas") ? 4 : 0);
        }
        OBSERVED.clear();
        FIRST_INSTANCES.clear(); RECREATED.clear(); minLoaded = Integer.MAX_VALUE; maxLoaded = 0;
        GarmentDiagnostics.debugForceCitizenLod = -1;
        GarmentDiagnostics.debugDisableCitizenLod = false;
        GarmentDiagnostics.debugEnableCitizenOcclusion = false;
        GarmentDiagnostics.debugEnableReflectionCitizenLod = true;
        GarmentDiagnostics.debugDisableCitizenGarmentRendering = false;
        GarmentDiagnostics.debugDisableCitizenBodyRendering = false;
        prototype.mode(CASES[scenario].endsWith("impostor") ? CitizenRepresentationPrototype.Mode.IMPOSTOR
                : CASES[scenario].endsWith("mesh") ? CitizenRepresentationPrototype.Mode.MESH : CitizenRepresentationPrototype.Mode.ORIGINAL);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var renderers = new HashMap<>(dispatcher.renderers);
        boolean nativeUnload = CASES[scenario].equals("chunks-3d");
        renderers.put(com.rexhub.createapparel.init.ModEntities.CITIZEN.get(), nativeUnload ? prototype.original() : prototype);
        dispatcher.renderers = renderers;
    }

    private static void setup(List<Citizen> citizens, String pattern) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        pending = true;
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayers().get(0);
                player.connection.teleport(0, -58, 0, 0, 0);
                var level = server.overworld();
                for (int z : new int[]{16, 24, 28, 80, 100}) for (int x = -4; x <= 4; x++) for (int y = -60; y <= -53; y++)
                    level.setBlockAndUpdate(new BlockPos(x, y, z), pattern.startsWith("mirror") && (z == 16 && x < 0 || z == 28 && x > 0)
                            ? com.rexhub.createapparel.init.ModMirrors.MIRROR_BLOCK.get().defaultBlockState()
                            : pattern.equals("occluded") && z == 16 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                EQUIPMENT.clear();
                for (int i = 0; i < citizens.size(); i++) {
                    var citizen = citizens.get(i);
                    citizen.setNoAi(true);
                    boolean palette = pattern.equals("palette") || pattern.equals("street");
                    var loadout = pattern.equals("varied") || palette ? EnvelopeRenderClientChecks.outfit(i % 7).serializeNBT() : INITIAL_EQUIPMENT.get(i);
                    citizen.citizenState().loadout().deserializeNBT(loadout);
                    var original = INITIAL_APPEARANCE.get(i);
                    citizen.citizenState().setAppearance(pattern.equals("varied") || palette ? new com.rexhub.createapparel.entity.CitizenAppearance(palette ? com.rexhub.createapparel.apparel.SkinTone.values()[i % 9] : original.skinTone(),
                            com.rexhub.createapparel.apparel.ArmProfile.values()[i % 2], com.rexhub.createapparel.apparel.ChestProfile.values()[i / 2 % 2],
                            original.hairStyleId(), i * 15349 & 0xFFFFFF, original.eyeColorRgb()) : original);
                    citizen.syncState();
                    EQUIPMENT.add(citizen.citizenState().loadout().serializeNBT());
                }
            } catch (Throwable error) { failure = error; } finally { pending = false; }
        });
    }

    private static void update(List<Citizen> citizens, String pattern, int frame) {
        pending = true;
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try {
                double progress = (frame % 200) / 200.0;
                double wave = 1 - Math.abs(2 * progress - 1);
                var player = citizens.get(0).getServer().getPlayerList().getPlayers().get(0);
                double cameraZ = pattern.equals("chunks") ? (frame % 80 < 40 ? 0 : 400)
                        : pattern.equals("mirrorcamera") ? wave * 12 : pattern.equals("camera") || pattern.equals("both") ? wave * 76 : 0;
                if (pattern.equals("camera") || pattern.equals("both") || pattern.equals("mirrorcamera") || pattern.equals("chunks") || pattern.equals("turn"))
                    player.connection.teleport(0, -58, cameraZ, pattern.equals("turn") ? (float) (15 * Math.sin(frame * .06)) : 0,
                            pattern.equals("turn") ? (float) (8 * Math.sin(frame * .04)) : 0);
                for (int i = 0; i < citizens.size(); i++) {
                    double distance = switch (pattern) {
                        case "approach", "varied", "street", "mirror" -> 80 - wave * 76;
                        case "spread" -> new double[]{6, 20, 45, 80}[i % 4];
                        case "mirrorcamera" -> 12;
                        case "depart" -> 4 + wave * 76;
                        case "boundary" -> 32 + .8 * Math.sin(frame * .2);
                        case "mixed" -> 4 + 76 * (.5 + .5 * Math.cos(frame * (.02 + i % 5 * .003) + i % 11));
                        case "both" -> 80 - wave * 36;
                        case "teleport" -> frame % 80 < 40 ? 80 : 4;
                        case "chunks" -> 80;
                        default -> 80;
                    };
                    var citizen = citizens.get(i);
                    citizen.setPos((i % 32 - 15.5) * (pattern.startsWith("mirror") ? .3 : .1), -60, distance + i / 32 * .01);
                    citizen.yBodyRot = citizen.yBodyRotO = pattern.equals("mixed") ? frame * 2F + i : 180;
                    citizen.setYRot(citizen.yBodyRot);
                    citizen.hurtMarked = true;
                }
            } catch (Throwable error) { failure = error; } finally { pending = false; }
        });
    }

    static void close() { if (measuring) GarmentDiagnostics.stop(); if (prototype != null) prototype.close(); if (Boolean.getBoolean("apparel.gaugeTest.pipeline")) PipelineBuffers.close(); }
}
