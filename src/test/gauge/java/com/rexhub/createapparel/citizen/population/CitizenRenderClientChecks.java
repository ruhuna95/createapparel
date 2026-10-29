package com.rexhub.createapparel.citizen.population;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.garment.*;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class CitizenRenderClientChecks {
    private static int stage, ticks, total;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static final List<Citizen> CITIZENS = new ArrayList<>();
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static long generation;
    private static LoadoutRenderSnapshot snapshot;
    private static ClientGarmentAssets.ResolvedPart oldPart;
    private static net.minecraft.client.renderer.texture.DynamicTexture oldTexture;
    private CitizenRenderClientChecks() { }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("apparel.gaugeTest.renderClient") || Boolean.getBoolean("apparel.gaugeTest.displayPerformance")
                || event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (failure != null) throw new AssertionError("Render fixture server failed", failure);
            if (++total > 2400) throw new AssertionError("Render QA timed out at stage " + stage);
            if (stage == 0 && mc.screen != null) {
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(6); mc.options.simulationDistance().set(6);
                mc.options.entityDistanceScaling().set(2.0); mc.options.fov().set(70);
                mc.options.bobView().set(false);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720); mc.resizeDisplay();
                stage = 1;
                mc.createWorldOpenFlows().createFreshLevel("citizen-render-" + System.nanoTime(),
                        new net.minecraft.world.level.LevelSettings("Citizen Render QA", net.minecraft.world.level.GameType.CREATIVE, false,
                                net.minecraft.world.Difficulty.PEACEFUL, false, new net.minecraft.world.level.GameRules(),
                                net.minecraft.world.level.WorldDataConfiguration.DEFAULT), new WorldOptions(86, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (stage == 1) {
                mc.setScreen(null); stage = 2;
                server(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    level.setDayTime(6000);
                    level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    player.getAbilities().flying = true; player.onUpdateAbilities();
                    player.connection.teleport(24, -35, -30, 0, 25);
                    for (int i = 0; i < 8; i++) {
                        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(level));
                        citizen.setUUID(new UUID(86, i)); citizen.setNoAi(true);
                        citizen.moveTo(21 + i % 7, -60, -2, 180, 0);
                        citizen.setBehavior(CitizenBehaviorState.posed(PosePreset.WALKING));
                        citizen.citizenState().loadout().deserializeNBT(EnvelopeRenderClientChecks.outfit(i % 7).serializeNBT());
                        level.addFreshEntity(citizen); citizen.syncState(); CITIZENS.add(citizen);
                    }
                });
                return;
            }
            if (!ready || mc.screen != null) return;
            if (stage == 2) {
                if (clientCitizens().size() < 8 || ++ticks < 40) return;
                EnvelopeRenderClientChecks.run(); CitizenLodClientChecks.run();
                snapshot = new LoadoutRenderSnapshot(CITIZENS.get(0).citizenState().loadout(),
                        CITIZENS.get(0).citizenState().appearance().armProfile(), CITIZENS.get(0).citizenState().appearance().chestProfile());
                oldPart = snapshot.garment(com.rexhub.createapparel.garment.GarmentSlot.TOP).orElseThrow().parts().get(0);
                oldTexture = (net.minecraft.client.renderer.texture.DynamicTexture) mc.getTextureManager().getTexture(snapshot.texture(oldPart));
                generation = ClientGarmentAssets.INSTANCE.generation();
                reload = mc.reloadResourcePacks(); stage = 3; return;
            }
            if (stage == 3) {
                if (!reload.isDone()) return;
                reload.join();
                var next = snapshot.garment(com.rexhub.createapparel.garment.GarmentSlot.TOP).orElseThrow().parts().get(0);
                if (ClientGarmentAssets.INSTANCE.generation() <= generation || next.model() == oldPart.model() || oldTexture.getPixels() != null)
                    throw new AssertionError("Reload retained old geometry or texture pixels");
                snapshot.texture(next);
                EnvelopeRenderClientChecks.run(); CitizenLodClientChecks.run(); RenderReferenceChecks.finish();
                LogUtils.getLogger().info("CITIZEN_RENDER_RELOAD_PASSED geometryReplaced=true oldNativeImageClosed=true");
                server(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).connection.teleport(24, -58, -10, 0, 5));
                stage = 4; ticks = 0; return;
            }
            if (stage == 4 && CitizenRenderWorldChecks.tick(CITIZENS)) {
                LogUtils.getLogger().info("CITIZEN_RENDER_CLIENT_PASSED images=true resourceReload=true movement=true mirror=true chunks=true");
                stage = 99; mc.stop();
            }
        } catch (Throwable exception) {
            stage = 99; LogUtils.getLogger().error("CITIZEN_RENDER_CLIENT_FAILED", exception); mc.stop();
        }
    }

    static List<Citizen> clientCitizens() {
        var result = new ArrayList<Citizen>();
        for (var entity : Minecraft.getInstance().level.entitiesForRendering()) if (entity instanceof Citizen citizen) result.add(citizen);
        return result;
    }

    private static void server(Runnable action) {
        ready = false;
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable exception) { failure = exception; } finally { ready = true; }
        });
    }
}
