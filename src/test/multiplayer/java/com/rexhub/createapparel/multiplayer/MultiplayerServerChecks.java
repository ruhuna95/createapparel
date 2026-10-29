package com.rexhub.createapparel.multiplayer;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ApparelCapabilities;
import com.rexhub.createapparel.block.ClothingRackBlock;
import com.rexhub.createapparel.block.LoomBlock;
import com.rexhub.createapparel.block.WardrobeBlock;
import com.rexhub.createapparel.block.entity.*;
import com.rexhub.createapparel.citizen.route.CitizenRouteManager;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

import java.util.Objects;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class MultiplayerServerChecks {
    static final BlockPos BASE = new BlockPos(1024, 80, 1024);
    static final BlockPos HANGER = BASE.offset(0, 1, 3);
    static final BlockPos RACK = BASE.offset(4, 1, 3);
    static final BlockPos WARDROBE = BASE.offset(2, 1, 0);
    static final BlockPos LOOM = BASE.offset(-2, 1, 3);
    private static final UUID CITIZEN = new UUID(2600, 1);
    private static final UUID MANNEQUIN = new UUID(2600, 2);
    private static int stage, elapsed, ticks;
    private static long started;
    private static boolean done;
    private static ServerPlayer deadPlayer;

    private MultiplayerServerChecks() { }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("apparel.multiplayer.server") || event.phase != TickEvent.Phase.END || done) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        try {
            if (started == 0) started = System.nanoTime();
            MultiplayerProbe.require(System.nanoTime() - started < 900_000_000_000L, "Multiplayer server timeout at stage " + stage);
            MultiplayerProbe.require(!MultiplayerProbe.marked("A-failed") && !MultiplayerProbe.marked("B-failed"), "Client reported failure");
            if (++ticks % 5 != 0) return;
            elapsed += 5;
            if (stage == 30) {
                if (!MultiplayerProbe.marked("A-passed") || !MultiplayerProbe.marked("B-passed")) return;
                MultiplayerProbe.mark("server-passed", "two real TCP clients; " + MultiplayerProbe.SESSION);
                LogUtils.getLogger().info("MULTIPLAYER_SERVER_PASSED session={}", MultiplayerProbe.SESSION);
                done = true; server.halt(false); return;
            }
            var a = server.getPlayerList().getPlayerByName("ApparelQAA");
            var b = server.getPlayerList().getPlayerByName("ApparelQAB");
            if (a == null || b == null) return;
            var level = server.overworld();
            switch (stage) {
                case 0 -> {
                    MultiplayerProbe.require(server.getPlayerList().getPlayerCount() == 2, "Exactly two real players must connect");
                    if (Boolean.getBoolean("apparel.multiplayer.resume")) {
                        level.getChunkAt(BASE);
                        if (level.getEntity(CITIZEN) == null || level.getEntity(MANNEQUIN) == null) return;
                        var expected = NbtIo.readCompressed(MultiplayerProbe.ROOT.resolve("persisted.nbt").toFile());
                        MultiplayerProbe.require(fixture(level).equals(expected.getCompound("Fixture")), "Restart changed saved fixture identity, equipment or graph");
                        MultiplayerProbe.require(playerSnapshot(server).equals(expected.getList("Players", CompoundTag.TAG_COMPOUND)), "Restart changed player equipment, colors or styles");
                        for (var player : new ServerPlayer[]{a, b}) {
                            MultiplayerProbe.require(apparel(player).getStack(GarmentSlot.TOP).is(ModItems.SHIRT_LONGSLEEVES.get()), "Login restores apparel item");
                            MultiplayerProbe.require("rolled".equals(apparel(player).styleState(GarmentSlot.TOP).styles().get("sleeves")), "Login restores equipped styles");
                        }
                        near(a, level, 0); near(b, level, 1);
                        publish(server, 20, "restart", true);
                    } else {
                        createFixture(server);
                        near(a, level, 0); near(b, level, 1);
                        for (var player : new ServerPlayer[]{a, b}) {
                            player.setGameMode(GameType.CREATIVE);
                            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
                            var shirt = new ItemStack(ModItems.SHIRT_LONGSLEEVES.get());
                            new com.rexhub.createapparel.garment.GarmentData(
                                    com.rexhub.createapparel.material.FabricMaterial.COTTON, "plain",
                                    player == a ? com.rexhub.createapparel.material.FabricColor.RED : com.rexhub.createapparel.material.FabricColor.BLUE,
                                    java.util.Optional.empty(), java.util.Optional.empty()).write(shirt);
                            apparel(player).setStackInSlot(GarmentSlot.TOP.ordinal(), shirt);
                            player.setRespawnPosition(Level.OVERWORLD, BASE.offset(0, 1, -2), 0, true, false);
                        }
                        stage = -1; elapsed = 0;
                    }
                }
                case -1 -> {
                    if (level.getEntity(CITIZEN) == null || level.getEntity(MANNEQUIN) == null) return;
                    publish(server, 1, "initial-sync", true);
                }
                case 1 -> {
                    if (!acknowledged()) return;
                    var wardrobe = (WardrobeBlockEntity) level.getBlockEntity(WARDROBE);
                    NetworkHooks.openScreen(a, Objects.requireNonNull(wardrobe), WARDROBE);
                    NetworkHooks.openScreen(b, wardrobe, WARDROBE);
                    publish(server, 2, "wardrobe-actions", true);
                }
                case 2 -> {
                    if (!acknowledged()) return;
                    for (var player : new ServerPlayer[]{a, b}) MultiplayerProbe.require(
                            "rolled".equals(apparel(player).styleState(GarmentSlot.TOP).styles().get("sleeves")), "Real Wardrobe C2S action reached server");
                    a.closeContainer(); b.closeContainer();
                    publish(server, 3, "wardrobe-observers", true);
                }
                case 3 -> {
                    if (!acknowledged()) return;
                    var hanger = (WallHangerBlockEntity) level.getBlockEntity(HANGER);
                    Objects.requireNonNull(hanger).take(a);
                    MultiplayerProbe.require(hanger.insert(new ItemStack(ModItems.PULLOVER_HOODIE.get()), false), "Replace hanger garment");
                    var rack = (ClothingRackBlockEntity) level.getBlockEntity(RACK);
                    Objects.requireNonNull(rack).take(1, a);
                    MultiplayerProbe.require(rack.insert(1, new ItemStack(ModItems.PULLOVER_HOODIE.get()), false), "Replace rack garment");
                    var citizen = (Citizen) Objects.requireNonNull(level.getEntity(CITIZEN));
                    citizen.citizenState().setAge(CitizenAge.CHILD);
                    citizen.citizenState().loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.PULLOVER_HOODIE.get()));
                    var mannequin = (Mannequin) Objects.requireNonNull(level.getEntity(MANNEQUIN));
                    mannequin.mannequinState().setBodyRotationStep(5);
                    mannequin.mannequinState().loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.PULLOVER_HOODIE.get()));
                    publish(server, 4, "display-and-citizen-change", true);
                }
                case 4 -> {
                    if (!acknowledged()) return;
                    deadPlayer = a;
                    a.setGameMode(GameType.SURVIVAL);
                    a.kill();
                    publish(server, 5, "death-respawn", true);
                }
                case 5 -> {
                    if (!acknowledged() || a.isDeadOrDying() || a == deadPlayer) return;
                    a.setGameMode(GameType.CREATIVE);
                    var nether = Objects.requireNonNull(server.getLevel(Level.NETHER));
                    for (var pos : BlockPos.betweenClosed(0, 79, 0, 4, 79, 4)) nether.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                    a.teleportTo(nether, 2.5, 80, 2.5, 0, 0);
                    publish(server, 6, "dimension-away", true);
                }
                case 6 -> {
                    if (!acknowledged()) return;
                    near(a, level, 0);
                    publish(server, 7, "dimension-return-tracking", true);
                }
                case 7 -> {
                    if (!acknowledged()) return;
                    for (var player : new ServerPlayer[]{a, b}) player.teleportTo(level, 4096.5, 90, 4096.5, 0, 0);
                    publish(server, 8, "chunk-unload", false);
                }
                case 8 -> {
                    if (!acknowledged() || elapsed < 400 || level.getChunkSource().hasChunk(BASE.getX() >> 4, BASE.getZ() >> 4)) return;
                    MultiplayerProbe.require(level.getEntity(CITIZEN) == null && level.getEntity(MANNEQUIN) == null, "Entities actually unloaded on server");
                    near(a, level, 0); near(b, level, 1);
                    stage = 9; elapsed = 0;
                }
                case 9 -> {
                    if (level.getEntity(CITIZEN) == null || level.getEntity(MANNEQUIN) == null) return;
                    publish(server, 10, "chunk-reload", true);
                }
                case 10 -> {
                    if (!acknowledged()) return;
                    var persisted = new CompoundTag();
                    persisted.put("Fixture", fixture(level)); persisted.put("Players", playerSnapshot(server));
                    NbtIo.writeCompressed(persisted, MultiplayerProbe.ROOT.resolve("persisted.nbt").toFile());
                    server.saveEverything(false, true, true);
                    publish(server, 30, "finish", true);
                }
                case 20 -> { if (acknowledged()) publish(server, 30, "finish", true); }
                default -> throw new AssertionError("Unknown stage " + stage);
            }
        } catch (Throwable failure) {
            done = true;
            LogUtils.getLogger().error("MULTIPLAYER_SERVER_FAILED stage={}", stage, failure);
            try { MultiplayerProbe.mark("server-failed", failure.toString()); } catch (java.io.IOException ignored) { }
            server.halt(false);
        }
    }

    private static com.rexhub.createapparel.apparel.ApparelInventory apparel(ServerPlayer player) {
        return ApparelCapabilities.get(player).orElseThrow(IllegalStateException::new);
    }

    private static void near(ServerPlayer player, ServerLevel level, int offset) {
        player.teleportTo(level, BASE.getX() + .5 + offset, BASE.getY() + 1, BASE.getZ() - 1.5, 0, 0);
    }

    private static boolean acknowledged() {
        return elapsed >= 20 && MultiplayerProbe.marked("A-stage-" + stage) && MultiplayerProbe.marked("B-stage-" + stage);
    }

    private static void createFixture(MinecraftServer server) {
        var level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        for (var pos : BlockPos.betweenClosed(BASE.offset(-8, 0, -8), BASE.offset(12, 0, 12))) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(HANGER.south(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(HANGER, ModBlocks.WALL_HANGER.get().defaultBlockState());
        MultiplayerProbe.require(((WallHangerBlockEntity) Objects.requireNonNull(level.getBlockEntity(HANGER)))
                .insert(new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()), false), "Initial hanger insert");
        var rackState = ModBlocks.CLOTHING_RACK.get().defaultBlockState();
        level.setBlock(RACK, rackState, Block.UPDATE_CLIENTS);
        level.setBlock(RACK.above(), rackState.setValue(ClothingRackBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        MultiplayerProbe.require(((ClothingRackBlockEntity) Objects.requireNonNull(level.getBlockEntity(RACK)))
                .insert(1, new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()), false), "Initial rack insert");
        var wardrobeState = ModBlocks.WARDROBE.get().defaultBlockState();
        level.setBlock(WARDROBE, wardrobeState, Block.UPDATE_CLIENTS);
        level.setBlock(WARDROBE.above(), wardrobeState.setValue(WardrobeBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        level.setBlockAndUpdate(LOOM, ModBlocks.LOOM.get().defaultBlockState());
        Objects.requireNonNull(level.getBlockEntity(LOOM)).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new)
                .insertItem(0, new ItemStack(ModItems.COTTON_THREAD.get()), false);
        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(level));
        citizen.setUUID(CITIZEN); citizen.setNoAi(true); citizen.setPersistenceRequired();
        citizen.moveTo(BASE.getX() + .5, BASE.getY() + 1, BASE.getZ() + 6.5, 180, 0);
        citizen.citizenState().loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
        MultiplayerProbe.require(level.addFreshEntity(citizen), "Citizen spawn");
        var mannequin = Objects.requireNonNull(ModEntities.mannequins().get(MannequinVariant.FULL_WHITE_CLASSIC).get().create(level));
        mannequin.setUUID(MANNEQUIN);
        mannequin.moveTo(BASE.getX() + 4.5, BASE.getY() + 1, BASE.getZ() + 6.5, 180, 0);
        mannequin.mannequinState().loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
        MultiplayerProbe.require(level.addFreshEntity(mannequin), "Mannequin spawn");
        var manager = CitizenRouteManager.get(level);
        UUID previous = null;
        int offset = 0;
        for (var block : new Block[]{ModBlocks.CITIZEN_ROUTE_MARKER.get(), ModBlocks.GATEWAY_ROUTE_MARKER.get(), ModBlocks.CROSSING_ROUTE_MARKER.get()}) {
            var pos = BASE.offset(8, 1, offset++ * 3);
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            var marker = manager.register((CitizenRouteMarkerBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos)));
            if (previous != null) MultiplayerProbe.require(manager.data().link(previous, marker.id(), false), "Route edge created");
            previous = marker.id();
        }
    }

    private static CompoundTag fixture(ServerLevel level) {
        var tag = new CompoundTag();
        for (var entry : java.util.Map.of("Hanger", HANGER, "Rack", RACK).entrySet()) tag.put(entry.getKey(), Objects.requireNonNull(level.getBlockEntity(entry.getValue())).getUpdateTag());
        tag.putBoolean("Thread", level.getBlockState(LOOM).getValue(LoomBlock.THREAD_VISIBLE));
        var citizen = (Citizen) Objects.requireNonNull(level.getEntity(CITIZEN));
        tag.putUUID("Citizen", CITIZEN);
        tag.put("CitizenTop", citizen.citizenState().loadout().getStack(GarmentSlot.TOP).save(new CompoundTag()));
        tag.putString("CitizenAge", citizen.citizenState().age().name());
        var mannequin = (Mannequin) Objects.requireNonNull(level.getEntity(MANNEQUIN));
        tag.putUUID("Mannequin", MANNEQUIN);
        tag.put("MannequinState", mannequin.mannequinState().serializeNBT());
        var nodes = new ListTag();
        var data = CitizenRouteManager.get(level).data();
        for (int i = 0; i < 3; i++) {
            var marker = Objects.requireNonNull(data.at(BASE.offset(8, 1, i * 3)));
            var node = new CompoundTag(); node.putUUID("Id", marker.id()); node.putString("Type", marker.type().name()); nodes.add(node);
        }
        tag.put("Nodes", nodes);
        return tag;
    }

    private static void publish(MinecraftServer server, int next, String name, boolean fixtures) throws java.io.IOException {
        stage = next; elapsed = 0;
        var plan = new CompoundTag(); plan.putInt("Stage", stage); plan.putString("Name", name);
        plan.put("Players", playerSnapshot(server));
        plan.putBoolean("Fixtures", fixtures);
        if (fixtures) plan.put("Fixture", fixture(server.overworld()));
        MultiplayerProbe.write(plan);
        LogUtils.getLogger().info("MULTIPLAYER_STAGE session={} stage={} name={}", MultiplayerProbe.SESSION, stage, name);
    }

    private static ListTag playerSnapshot(MinecraftServer server) {
        var players = new ListTag();
        for (var player : server.getPlayerList().getPlayers().stream().sorted(java.util.Comparator.comparing(ServerPlayer::getScoreboardName)).toList()) {
            var tag = new CompoundTag(); tag.putUUID("Id", player.getUUID()); tag.putString("Dimension", player.level().dimension().location().toString());
            tag.put("Top", apparel(player).getStack(GarmentSlot.TOP).save(new CompoundTag()));
            tag.putString("Sleeves", stage == 2 ? "rolled" : apparel(player).styleState(GarmentSlot.TOP).styles().getOrDefault("sleeves", ""));
            players.add(tag);
        }
        return players;
    }
}
