package com.rexhub.createapparel.citizen.population;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.garment.*;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.garment.*;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.material.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class CitizenPerformanceClient {
    private static final List<Citizen> CITIZENS = new ArrayList<>();
    private static final boolean LOD_COMPARE = Boolean.getBoolean("apparel.gaugeTest.lodComparison");
    private static final String[] CASES = cases();
    private static int stage, ticks, scenario;
    private static int comparisonPhase;
    private static final boolean COMPARE = Boolean.getBoolean("apparel.gaugeTest.envelopeComparison");
    private static boolean measuring;
    private static final int CASE_COUNT = Integer.getInteger("apparel.gaugeTest.performanceCaseCount", CASES.length);
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static final List<net.minecraft.nbt.CompoundTag> BASELINE_EQUIPMENT = new ArrayList<>();
    private static java.util.concurrent.CompletableFuture<Void> resourceReload;
    private static long reloadGeneration;
    private static LoadoutRenderSnapshot reloadSnapshot;
    private static ClientGarmentAssets.ResolvedPart reloadPart;
    private static Citizen reloadCitizen;
    private static net.minecraft.nbt.CompoundTag reloadEquipment;
    private static String[] cases() {
        var values = LOD_COMPARE ? new String[]{"lod0-legacy", "lod0", "lod1", "lod2", "lod-auto", "occlusion", "distance-mix", "lod-varied-legacy", "lod-varied"}
                : new String[]{"same-A", "same-B", "same-C", "varied", "none", "multi-part", "dyed-design", "head-varied"};
        if (LOD_COMPARE && Boolean.getBoolean("apparel.gaugeTest.lodReverse")) Collections.reverse(Arrays.asList(values));
        return values;
    }
    private CitizenPerformanceClient() { }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("apparel.gaugeTest.performanceClient") || event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance(); if (mc.getOverlay() != null) return;
        try {
            if (failure != null) throw new AssertionError("Server fixture failed", failure);
            if (stage == 0 && mc.screen != null) {
                if (CASE_COUNT < 1 || CASE_COUNT > CASES.length) throw new IllegalArgumentException("Invalid performanceCaseCount");
                if (COMPARE && CASE_COUNT != 3) throw new IllegalArgumentException("Envelope comparison requires A/B/C");
                configureBackend();
                LogUtils.getLogger().info("CITIZEN_CLIENT_DEVICE renderer={} vendor={} OpenGL={} Java={} logicalProcessors={}",
                        org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER), org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR),
                        org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION), System.getProperty("java.version"), Runtime.getRuntime().availableProcessors());
                mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(6); mc.options.simulationDistance().set(6); mc.options.framerateLimit().set(260); mc.options.enableVsync().set(false);
                mc.options.fov().set(70); mc.options.bobView().set(false); mc.options.entityDistanceScaling().set(2.0);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720); mc.resizeDisplay();
                stage = 1;
                mc.createWorldOpenFlows().createFreshLevel("citizen-performance-" + System.currentTimeMillis(),
                        new LevelSettings("Citizen Performance QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(0, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()); return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (stage == 3) {
                if (!resourceReload.isDone()) return;
                resourceReload.join();
                if (ClientGarmentAssets.INSTANCE.generation() <= reloadGeneration) throw new AssertionError("Reload did not invalidate asset generation");
                var refreshed = reloadSnapshot.garment(GarmentSlot.TOP).orElseThrow().parts().get(0);
                if (refreshed.model() == reloadPart.model()) throw new AssertionError("Snapshot retained pre-reload geometry");
                reloadSnapshot.texture(refreshed);
                if (!reloadEquipment.equals(reloadCitizen.citizenState().loadout().serializeNBT())) throw new AssertionError("Reload changed equipment");
                LogUtils.getLogger().info("CITIZEN_LOD_RELOAD_PASSED generationBefore={} generationAfter={} equipmentRetained=true geometryReplaced=true",
                        reloadGeneration, ClientGarmentAssets.INSTANCE.generation());
                finish(); return;
            }
            if (stage == 1) {
                mc.setScreen(null); stage = 2; ready = false;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var level = mc.getSingleplayerServer().overworld();
                        level.setDayTime(6000); level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                        var player = level.getServer().getPlayerList().getPlayers().get(0); player.getAbilities().flying = true; player.onUpdateAbilities(); player.connection.teleport(24, -35, -30, 0, 25);
                        int count = Integer.getInteger("apparel.gaugeTest.performanceCount", 100);
                        if (!Set.of(50, 100, 300, 500, 1000).contains(count)) throw new IllegalArgumentException("Invalid Citizen count");
                        for (int i = 0; i < count; i++) {
                            var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(level)); citizen.setUUID(new UUID(86, i)); citizen.getRandom().setSeed(i);
                            citizen.moveTo(1 + i % 32 * 1.5, -60, 1 + i / 32 * 1.5, 180, 0); citizen.setInvulnerable(true); citizen.setBehavior(CitizenBehaviorState.posed(PosePreset.STANDING));
                            level.addFreshEntity(citizen); CITIZENS.add(citizen);
                        }
                        equip(CASES[0]); ready = true;
                    } catch (Throwable error) { failure = error; }
                }); return;
            }
            if (!ready || mc.screen != null) return;
            mc.mouseHandler.releaseMouse();
            ticks++;
            if (ticks == 1 && scenario == 0 && comparisonPhase == 0 && Boolean.getBoolean("apparel.gaugeTest.envelopeImages")) EnvelopeRenderClientChecks.run();
            if (ticks == 1 && scenario == 0 && comparisonPhase == 0 && Boolean.getBoolean("apparel.gaugeTest.lodImages")) CitizenLodClientChecks.run();
            if (ticks == 40) GarmentDiagnostics.start();
            if (ticks == 80) {
                GarmentDiagnostics.stop();
                long visible = GarmentDiagnostics.visibleUniqueMax();
                if ((LOD_COMPARE || scenario != 2) && visible == 0) throw new AssertionError("Warmup camera did not render Citizens");
                long clientLoaded = java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false).filter(Citizen.class::isInstance).count();
                LogUtils.getLogger().info("CITIZEN_VISIBILITY_CENSUS case={} loadedServer={} loadedClient={} primaryVisibleMax={} (separate warmup sample, not FPS capture)", CASES[scenario], CITIZENS.size(), clientLoaded, visible);
            }
            if (ticks == 120) {
                RuntimeGarmentTextureCache.clear(); CitizenTextureCache.clear();
                GarmentDiagnostics.debugDisableCitizenGarmentRendering = !LOD_COMPARE && scenario == 1;
                GarmentDiagnostics.debugDisableCitizenBodyRendering = !LOD_COMPARE && scenario == 2;
                GarmentDiagnostics.debugDisableCitizenCosmeticRendering = false;
                if (framesOnly()) GarmentDiagnostics.startFramesOnly(); else GarmentDiagnostics.start(); measuring = true;
            }
            if (ticks < 320) return;
            if (!measuring) throw new AssertionError("Client measurement never started");
            GarmentDiagnostics.stop(); measuring = false;
            if (!framesOnly() && (LOD_COMPARE || scenario != 2) && GarmentDiagnostics.timing().counter("citizenRenderCalls") == 0) throw new AssertionError("Camera did not render Citizens");
            LogUtils.getLogger().info("ENVELOPE_COMPARISON phase={} backend={} mode={}", comparisonPhase,
                    GarmentDiagnostics.debugUseLegacyEnvelopeRendering ? "legacy" : "indexed", framesOnly() ? "frames" : "detail");
            if (LOD_COMPARE) LogUtils.getLogger().info("CITIZEN_LOD_CASE case={} phase={} forcedLod={} occlusion={} textureLegacy={}", CASES[scenario], comparisonPhase,
                    GarmentDiagnostics.debugForceCitizenLod, !GarmentDiagnostics.debugDisableCitizenOcclusion, GarmentDiagnostics.debugUseLegacyTextureComposition);
            LogUtils.getLogger().info("CITIZEN_FPS_PROFILE case={} loadedServerCitizens={} resolution={}x{} renderDistance={} simulationDistance={} position={} yaw={} pitch={} integratedServer=true coldCacheAtStart=true vsync=false framerateLimit=unlimited fov=70 entityDistanceScale=2 {}",
                    CASES[scenario], CITIZENS.size(), mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.options.renderDistance().get(), mc.options.simulationDistance().get(),
                    mc.player.position(), mc.player.getYRot(), mc.player.getXRot(), GarmentDiagnostics.report());
            int garmentEntries = RuntimeGarmentTextureCache.size(), headEntries = CitizenTextureCache.size();
            GarmentDiagnostics.start(); RuntimeGarmentTextureCache.clear(); CitizenTextureCache.clear(); GarmentDiagnostics.stop();
            if (GarmentDiagnostics.timing().counter("garmentCache.RELEASE") != garmentEntries || GarmentDiagnostics.timing().counter("citizenCache.RELEASE") != headEntries)
                throw new AssertionError("Native texture release notifications do not match the owned cache entries");
            LogUtils.getLogger().info("CITIZEN_CACHE_RELEASE case={} garmentTextures={} headTextures={} remainingGarment={} remainingHead={}", CASES[scenario], garmentEntries, headEntries, RuntimeGarmentTextureCache.size(), CitizenTextureCache.size());
            if (++scenario == CASE_COUNT) {
                if ((COMPARE || LOD_COMPARE) && ++comparisonPhase < (LOD_COMPARE ? 2 : 4)) { scenario = 0; configureBackend(); }
                else {
                    if (Boolean.getBoolean("apparel.gaugeTest.resourceReload")) {
                        reloadCitizen = java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                                .filter(Citizen.class::isInstance).map(Citizen.class::cast)
                                .filter(citizen -> citizen.getUUID().equals(CITIZENS.get(0).getUUID())).findFirst().orElseThrow();
                        var appearance = reloadCitizen.citizenState().appearance();
                        reloadEquipment = reloadCitizen.citizenState().loadout().serializeNBT();
                        reloadSnapshot = new LoadoutRenderSnapshot(reloadCitizen.citizenState().loadout(), appearance.armProfile(), appearance.chestProfile());
                        reloadPart = reloadSnapshot.garment(GarmentSlot.TOP).orElseThrow().parts().get(0);
                        reloadSnapshot.texture(reloadPart);
                        reloadGeneration = ClientGarmentAssets.INSTANCE.generation(); stage = 3;
                        resourceReload = mc.reloadResourcePacks(); return;
                    }
                    finish(); return;
                }
            }
            configureBackend();
            GarmentDiagnostics.debugDisableCitizenGarmentRendering = !LOD_COMPARE && scenario == 1;
            GarmentDiagnostics.debugDisableCitizenBodyRendering = !LOD_COMPARE && scenario == 2;
            ticks = 0; ready = false;
            mc.getSingleplayerServer().execute(() -> { try { equip(CASES[scenario]); ready = true; } catch (Throwable error) { failure = error; } });
        } catch (Throwable error) { stage = 99; LogUtils.getLogger().error("CITIZEN_PERFORMANCE_CLIENT_FAILED", error); mc.stop(); }
    }
    private static void finish() {
        stage = 99;
        LogUtils.getLogger().info("CITIZEN_PERFORMANCE_CLIENT_PASSED cases={} phases={}", CASE_COUNT, LOD_COMPARE ? 2 : COMPARE ? 4 : 1);
        Minecraft.getInstance().stop();
    }
    private static boolean framesOnly() {
        return LOD_COMPARE ? comparisonPhase == 0 : COMPARE ? comparisonPhase < 2 : Boolean.getBoolean("apparel.gaugeTest.performanceFrameOnly");
    }

    private static void configureBackend() {
        if (COMPARE) GarmentDiagnostics.debugUseLegacyEnvelopeRendering = (comparisonPhase % 2 == 0) != Boolean.getBoolean("apparel.gaugeTest.envelopeReverse");
        if (LOD_COMPARE) {
            GarmentDiagnostics.debugDisableCitizenLod = false;
            String name = CASES[scenario];
            GarmentDiagnostics.debugForceCitizenLod = switch (name) { case "lod0-legacy", "lod0", "occlusion" -> 0; case "lod1" -> 1; case "lod2" -> 2; default -> -1; };
            GarmentDiagnostics.debugDisableCitizenOcclusion = name.startsWith("lod0") || name.equals("lod1") || name.equals("lod2");
            GarmentDiagnostics.debugEnableCitizenOcclusion = !GarmentDiagnostics.debugDisableCitizenOcclusion;
            GarmentDiagnostics.debugUseLegacyTextureComposition = name.equals("lod-varied-legacy");
            GarmentDiagnostics.debugUseLegacyRigidRendering = name.equals("lod0-legacy");
        }
    }

    private static void equip(String scenario) {
        if (LOD_COMPARE) {
            var level = CITIZENS.get(0).level();
            var player = CITIZENS.get(0).getServer().getPlayerList().getPlayers().get(0);
            boolean mixed = scenario.equals("distance-mix");
            player.connection.teleport(24, mixed ? -58 : -35, mixed ? -10 : -30, 0, mixed ? 5 : 25);
            for (int i = 0; i < CITIZENS.size(); i++) {
                var citizen = CITIZENS.get(i);
                citizen.moveTo(mixed ? 13 + i % 16 * 1.5 : 1 + i % 32 * 1.5, -60,
                        mixed ? -10 + new int[]{8, 20, 45, 75}[i % 4] + i / 64 * .4 : 1 + i / 32 * 1.5, 180, 0);
                citizen.setBehavior(mixed && i % 2 == 0 ? CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, citizen.blockPosition(), level.dimension(), 3)
                        : CitizenBehaviorState.posed(PosePreset.STANDING));
            }
            scenario = scenario.startsWith("lod-varied") ? "varied" : "same-A";
        }
        if (scenario.equals("same-B") || scenario.equals("same-C")) {
            for (int i = 0; i < CITIZENS.size(); i++) if (!BASELINE_EQUIPMENT.get(i).equals(CITIZENS.get(i).citizenState().loadout().serializeNBT())) throw new AssertionError("Rendering switch changed Server equipment");
            return;
        }
        var definitions = GarmentDefinitionManager.all().stream().filter(definition -> ForgeRegistries.ITEMS.getValue(definition.id()) instanceof GarmentItem
                && definition.defaultSlot() != GarmentSlot.INNER_TOP && definition.defaultSlot() != GarmentSlot.INNER_BOTTOM)
                .sorted(Comparator.comparing(definition -> definition.id().toString())).toList();
        var same = definitions.stream().filter(definition -> definition.defaultSlot() == GarmentSlot.TOP).findFirst().orElseThrow();
        var multi = definitions.stream().filter(definition -> definition.defaultSlot() == GarmentSlot.COAT).findFirst().orElseThrow();
        LogUtils.getLogger().info("CITIZEN_LOADOUT_CASE case={} sameGarment={} multiPartGarment={} availableDefinitions={}", scenario, same.id(), multi.id(), definitions.size());
        for (int i = 0; i < CITIZENS.size(); i++) {
            var loadout = new com.rexhub.createapparel.apparel.ApparelLoadout();
            if (scenario.equals("none")) { CITIZENS.get(i).citizenState().loadout().deserializeNBT(loadout.serializeNBT()); CITIZENS.get(i).syncState(); continue; }
            var definition = scenario.equals("varied") ? definitions.get(i % definitions.size()) : scenario.equals("multi-part") ? multi : same;
            var stack = new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(definition.id())));
            var data = GarmentData.defaultFor(definition);
            if (scenario.equals("varied")) data = new GarmentData(data.material(), data.design(), FabricColor.values()[i / definitions.size() % FabricColor.values().length], Optional.empty(), Optional.empty());
            if (scenario.equals("dyed-design")) {
                var designs = new ArrayList<>(definition.designs()); Collections.sort(designs);
                data = new GarmentData(FabricMaterial.SYNTHETIC, designs.get(i % designs.size()), FabricColor.values()[i % FabricColor.values().length], Optional.empty(), Optional.empty());
            }
            data.write(stack); loadout.setStackInSlot(definition.defaultSlot().ordinal(), stack);
            CITIZENS.get(i).citizenState().loadout().deserializeNBT(loadout.serializeNBT()); CITIZENS.get(i).syncState();
            if (scenario.equals("head-varied")) {
                var appearance = CITIZENS.get(i).citizenState().appearance();
                CITIZENS.get(i).citizenState().setAppearance(new CitizenAppearance(appearance.skinTone(), appearance.armProfile(), appearance.chestProfile(),
                        appearance.hairStyleId(), i * 15349 & 0xFFFFFF, appearance.eyeColorRgb()));
            }
        }
        if (scenario.equals("same-A")) { BASELINE_EQUIPMENT.clear(); for (var citizen : CITIZENS) BASELINE_EQUIPMENT.add(citizen.citizenState().loadout().serializeNBT()); }
    }
}
