package com.rexhub.createapparel.multiplayer;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.apparel.ClientApparelInventories;
import com.rexhub.createapparel.client.citizen.ClientRouteView;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.Mannequin;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.menu.WardrobeMenu;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.network.WardrobeActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class MultiplayerClientChecks {
    private static long started;
    private static int ticks, actionStage = -1, acknowledged = -1;
    private static int loginRetries;
    private static boolean done;
    private static net.minecraft.client.player.LocalPlayer beforeDeath;
    private static String waiting = "login";

    private MultiplayerClientChecks() { }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var role = System.getProperty("apparel.multiplayer.role");
        if (role == null || done || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            if (started == 0) {
                started = System.nanoTime();
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2); mc.options.simulationDistance().set(5);
                mc.options.framerateLimit().set(30);
            }
            MultiplayerProbe.require(System.nanoTime() - started < 900_000_000_000L, "Multiplayer client timeout: " + waiting);
            MultiplayerProbe.require(!MultiplayerProbe.marked("server-failed"), "Dedicated server failed");
            if (++ticks % 5 != 0 || mc.getOverlay() != null) return;
            if (mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen && acknowledged < 0) {
                MultiplayerProbe.require(++loginRetries <= 3, "Client login failed after three retries");
                var address = System.getProperty("apparel.multiplayer.address");
                LogUtils.getLogger().warn("MULTIPLAYER_LOGIN_RETRY role={} attempt={}", role, loginRetries);
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(), mc,
                        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                        new net.minecraft.client.multiplayer.ServerData("Apparel QA", address, false), false);
                return;
            }
            if (ticks % 200 == 0 && mc.player == null) {
                LogUtils.getLogger().info("MULTIPLAYER_LOGIN_WAIT role={} screen={} connection={}", role,
                        mc.screen == null ? "none" : mc.screen.getClass().getName(), mc.getConnection() != null);
                try (var screenshot = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    screenshot.writeToFile(MultiplayerProbe.ROOT.resolve(role + "-login.png"));
                }
            }
            if (!Files.isRegularFile(MultiplayerProbe.file("plan.nbt"))) return;
            var plan = NbtIo.readCompressed(MultiplayerProbe.file("plan.nbt").toFile());
            int stage = plan.getInt("Stage");
            if (stage == 30) {
                MultiplayerProbe.mark(role + "-passed", "real client completed " + MultiplayerProbe.SESSION);
                done = true; LogUtils.getLogger().info("MULTIPLAYER_CLIENT_PASSED role={} session={}", role, MultiplayerProbe.SESSION);
                mc.stop(); return;
            }
            if (mc.player == null || mc.level == null || acknowledged == stage) return;
            if (mc.player.isDeadOrDying()) { mc.player.respawn(); return; }
            if (stage == 2 && actionStage != stage) {
                if (!(mc.player.containerMenu instanceof WardrobeMenu)) return;
                ModNetwork.updateWardrobe(WardrobeActionPacket.style(GarmentSlot.TOP, "sleeves", "rolled"));
                actionStage = stage;
            }
            try {
                verify(mc, plan);
            } catch (AssertionError pending) {
                waiting = "stage=" + stage + " " + pending.getMessage();
                if (ticks % 200 == 0) LogUtils.getLogger().info("MULTIPLAYER_CLIENT_WAIT role={} {}", role, waiting);
                return;
            }
            MultiplayerProbe.mark(role + "-stage-" + stage, plan.getString("Name"));
            if (stage == 4) beforeDeath = mc.player;
            acknowledged = stage;
            LogUtils.getLogger().info("MULTIPLAYER_CLIENT_STAGE_PASSED role={} stage={} name={}", role, stage, plan.getString("Name"));
        } catch (Throwable failure) {
            done = true;
            LogUtils.getLogger().error("MULTIPLAYER_CLIENT_FAILED role={}", role, failure);
            try { MultiplayerProbe.mark(role + "-failed", failure.toString()); } catch (java.io.IOException ignored) { }
            mc.stop();
        }
    }

    private static void verify(Minecraft mc, CompoundTag plan) {
        var level = java.util.Objects.requireNonNull(mc.level);
        var local = java.util.Objects.requireNonNull(mc.player);
        if (plan.getInt("Stage") == 5 && "A".equals(System.getProperty("apparel.multiplayer.role"))) {
            MultiplayerProbe.require(beforeDeath != null && local != beforeDeath, "Waiting for a new player object after death");
        }
        var players = plan.getList("Players", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            var expected = players.getCompound(i);
            if (expected.getUUID("Id").equals(local.getUUID())) MultiplayerProbe.require(
                    expected.getString("Dimension").equals(level.dimension().location().toString()), "Local dimension has not synchronized");
            if (!expected.getString("Dimension").equals(level.dimension().location().toString())) {
                MultiplayerProbe.require(level.getPlayerByUUID(expected.getUUID("Id")) == null, "Other dimension player remains tracked");
                continue;
            }
            var player = level.getPlayerByUUID(expected.getUUID("Id"));
            MultiplayerProbe.require(player != null && !player.isDeadOrDying(), "Player is not tracked or alive");
            MultiplayerProbe.require(ItemStack.matches(ClientApparelInventories.getStack(player, GarmentSlot.TOP), ItemStack.of(expected.getCompound("Top"))), "Apparel item mismatch");
            MultiplayerProbe.require(expected.getString("Sleeves").equals(ClientApparelInventories.styleState(player, GarmentSlot.TOP).styles().getOrDefault("sleeves", "")), "Apparel style mismatch");
        }
        if (!plan.getBoolean("Fixtures")) {
            MultiplayerProbe.require(level.getChunkSource().getChunk(MultiplayerServerChecks.BASE.getX() >> 4,
                    MultiplayerServerChecks.BASE.getZ() >> 4, net.minecraft.world.level.chunk.ChunkStatus.FULL, false) == null,
                    "Client still holds the old fixture chunk");
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        var expected = plan.getCompound("Fixture");
        for (var entry : java.util.Map.of("Hanger", MultiplayerServerChecks.HANGER, "Rack", MultiplayerServerChecks.RACK).entrySet()) {
            var block = level.getBlockEntity(entry.getValue());
            MultiplayerProbe.require(block != null && block.getUpdateTag().equals(expected.getCompound(entry.getKey())), entry.getKey() + " BlockEntity mismatch");
        }
        var loom = level.getBlockState(MultiplayerServerChecks.LOOM);
        MultiplayerProbe.require(loom.hasProperty(com.rexhub.createapparel.block.LoomBlock.THREAD_VISIBLE)
                && loom.getValue(com.rexhub.createapparel.block.LoomBlock.THREAD_VISIBLE) == expected.getBoolean("Thread"), "Loom thread visibility mismatch");
        Citizen citizen = null;
        Mannequin mannequin = null;
        for (var entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(expected.getUUID("Citizen")) && entity instanceof Citizen found) citizen = found;
            if (entity.getUUID().equals(expected.getUUID("Mannequin")) && entity instanceof Mannequin found) mannequin = found;
        }
        MultiplayerProbe.require(citizen != null && mannequin != null, "Display entities are not tracked");
        MultiplayerProbe.require(ItemStack.matches(citizen.citizenState().loadout().getStack(GarmentSlot.TOP), ItemStack.of(expected.getCompound("CitizenTop")))
                && citizen.citizenState().age().name().equals(expected.getString("CitizenAge")), "Citizen equipment or age mismatch");
        MultiplayerProbe.require(mannequin.mannequinState().serializeNBT().equals(expected.getCompound("MannequinState")), "Mannequin state mismatch");
        var nodes = expected.getList("Nodes", CompoundTag.TAG_COMPOUND);
        MultiplayerProbe.require(ClientRouteView.nodes().size() == 3 && ClientRouteView.edges().size() == 2, "Route snapshot size mismatch");
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.getCompound(i);
            MultiplayerProbe.require(ClientRouteView.nodes().stream().anyMatch(actual -> actual.id().equals(node.getUUID("Id"))
                    && actual.type().name().equals(node.getString("Type"))), "Route marker identity or type mismatch");
        }
    }
}
