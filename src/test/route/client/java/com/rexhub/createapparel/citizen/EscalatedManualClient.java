package com.rexhub.createapparel.citizen;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.citizen.population.CitizenPopulationState;
import com.rexhub.createapparel.citizen.route.CitizenRouteManager;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class EscalatedManualClient {
    private static int stage;
    private static volatile boolean prepared;
    private static volatile Throwable failure;
    private static String worldName;
    private static int readyTicks;
    private static final java.util.List<Patrol> PATROLS = new java.util.ArrayList<>();

    private EscalatedManualClient() { }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("apparel.escalatedClient") || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (stage == 3) {
            if (prepared && ++readyTicks % 20 == 0 && mc.getSingleplayerServer() != null)
                mc.getSingleplayerServer().execute(EscalatedManualClient::tracePatrols);
            return;
        }
        if (mc.getOverlay() != null) return;
        if (failure != null) {
            stage = 3;
            LogUtils.getLogger().error("ESCALATED_MANUAL_WORLD_FAILED", failure);
            return;
        }
        if (stage == 0 && mc.screen != null) {
            stage = 1;
            worldName = "escalated-manual-" + System.currentTimeMillis();
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(8);
            mc.options.simulationDistance().set(8);
            mc.createWorldOpenFlows().createFreshLevel(worldName,
                    new net.minecraft.world.level.LevelSettings("Escalated 手動確認", net.minecraft.world.level.GameType.CREATIVE,
                            false, net.minecraft.world.Difficulty.PEACEFUL, true, new GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                    new WorldOptions(29, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            return;
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (stage == 1) {
            stage = 2;
            mc.setScreen(null);
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                try { prepare(server.overworld()); prepared = true; }
                catch (Throwable throwable) { failure = throwable; }
            });
        } else if (prepared && ++readyTicks >= 60) {
            stage = 3;
            mc.setScreen(null);
            LogUtils.getLogger().info("ESCALATED_MANUAL_WORLD_READY name={} player=(-5,83,-5) lanes=0,10,22,34,46,56", worldName);
            try (var pixels = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                pixels.writeToFile(mc.gameDirectory.toPath().resolve("escalated-manual-ready.png"));
            } catch (java.io.IOException exception) {
                LogUtils.getLogger().warn("Cannot capture the manual world preview", exception);
            }
        }
    }

    private static void prepare(ServerLevel level) {
        level.setDayTime(6000);
        for (var rule : java.util.List.of(GameRules.RULE_DAYLIGHT, GameRules.RULE_WEATHER_CYCLE, GameRules.RULE_DOMOBSPAWNING))
            level.getGameRules().getRule(rule).set(false, level.getServer());
        for (int x = -8; x <= 20; x++) for (int z = -8; z <= 64; z++)
            level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState());
        walkway(level, 0, "metal", false, -64);
        walkway(level, 10, "wooden", false, -64);
        route(level, new BlockPos(-2, 81, -2), new BlockPos(12, 81, -2), false, "自動歩道・経路歩行");
        route(level, new BlockPos(-2, 81, 8), new BlockPos(12, 81, 8), true, "木製自動歩道・子供");
        var wanderer = citizen(level, new BlockPos(2, 81, 11), false, "自由歩行");
        wanderer.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, wanderer.blockPosition(), level.dimension(), 9));
        for (int z : new int[]{22, 34}) {
            for (int x = 8; x <= 17; x++) for (int side = z - 1; side <= z + 4; side++)
                for (int y = 81; y <= 85; y++) level.setBlockAndUpdate(new BlockPos(x, y, side), Blocks.STONE.defaultBlockState());
            for (int x = 3; x <= 7; x++) for (int side = z + 2; side <= z + 4; side++) {
                int rise = x - 2;
                for (int y = 81; y <= 80 + rise; y++) level.setBlockAndUpdate(new BlockPos(x, y, side), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, 80 + rise, side), Blocks.STONE_STAIRS.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            }
            walkway(level, z, "metal", true, z == 22 ? -64 : 64);
            route(level, new BlockPos(-2, 81, z), new BlockPos(13, 86, z), false, z == 22 ? "上りエスカレーター" : "下りエスカレーター");
        }
        terrain(level, 46, false);
        terrain(level, 56, true);
        label(level, 0, "金属の自動歩道 / WALK");
        label(level, 10, "木製の自動歩道 / 子供 + WANDER");
        label(level, 22, "上りエスカレーター / 帰路は横の階段");
        label(level, 34, "下りエスカレーター / 往路は横の階段");
        label(level, 46, "バニラの1ブロック段差");
        label(level, 56, "バニラの階段ブロック");
        var player = level.getServer().getPlayerList().getPlayers().get(0);
        player.getInventory().setItem(0, new ItemStack(ModItems.SMART_PHONE.get()));
        player.getInventory().setItem(1, AllItems.WRENCH.asStack());
        player.getInventory().setItem(2, new ItemStack(ModItems.CITIZEN_SPAWN_EGG.get()));
        player.getInventory().setItem(3, AllBlocks.CREATIVE_MOTOR.asStack());
        player.getInventory().setItem(4, AllItems.BELT_CONNECTOR.asStack());
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.connection.teleport(-5, 83, -5, -30, 25);
        level.setDefaultSpawnPos(new BlockPos(-5, 81, -5), 0);
        player.sendSystemMessage(Component.literal("確認用ワールドを準備しました。Z=0,10,22,34,46,56 の順に6コースあります。"));
        player.sendSystemMessage(Component.literal("スマートフォンで経路表示、モーター右クリックで速度変更。Escで閉じるまで自由に操作できます。"));
    }

    private static void walkway(ServerLevel level, int z, String material, boolean inclined, int rpm) {
        var origin = new BlockPos(0, 80, z);
        level.setBlockAndUpdate(origin, block(material + "_walkway_terminal", Direction.EAST));
        for (int i = 1; i <= 10; i++) {
            var pos = origin.east(i).above(inclined ? Math.max(0, Math.min(5, i - 2)) : 0);
            var state = block(material + (inclined ? "_narrow_escalator" : "_narrow_walkway"), Direction.EAST);
            if (inclined) for (var property : state.getProperties()) if (property.getName().equals("slope"))
                state = value(state, property, i <= 1 || i >= 8 ? "horizontal" : i == 2 ? "bottom" : i == 7 ? "top" : "middle");
            level.setBlockAndUpdate(pos, state);
        }
        level.setBlockAndUpdate(origin.east(11).above(inclined ? 5 : 0), block(material + "_walkway_terminal", Direction.WEST));
        var motorPos = origin.south();
        level.setBlockAndUpdate(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.NORTH));
        var motor = (CreativeMotorBlockEntity) Objects.requireNonNull(level.getBlockEntity(motorPos));
        motor.generatedSpeed.setValue(-rpm);
        motor.updateGeneratedRotation();
    }

    private static BlockState block(String id, Direction facing) {
        return Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("escalated", id)))
                .defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    private static <T extends Comparable<T>> BlockState value(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow());
    }

    private static void route(ServerLevel level, BlockPos start, BlockPos end, boolean child, String name) {
        var manager = CitizenRouteManager.get(level);
        var markers = new java.util.ArrayList<java.util.UUID>();
        for (var pos : java.util.List.of(start, end)) {
            level.setBlockAndUpdate(pos, ModBlocks.CITIZEN_ROUTE_MARKER.get().defaultBlockState());
            var marker = manager.register((CitizenRouteMarkerBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos))).withRegion(0.5, 1);
            manager.data().put(marker);
            markers.add(marker.id());
        }
        manager.data().link(markers.get(0), markers.get(1), false);
        var walker = citizen(level, start, child, name);
        walker.setBehavior(CitizenBehaviorState.walking());
        PATROLS.add(new Patrol(walker));
    }

    private static void tracePatrols() {
        for (var patrol : PATROLS) {
            if (patrol.walker.isRemoved() || patrol.proved) continue;
            var marker = patrol.walker.routeMovement().currentMarker();
            if (marker == null || marker.equals(patrol.lastMarker)) continue;
            patrol.lastMarker = marker;
            patrol.visits++;
            LogUtils.getLogger().info("ESCALATED_MANUAL_PATROL name={} visits={} pos={} marker={} goal={}",
                    patrol.walker.getName().getString(), patrol.visits, patrol.walker.position(), marker,
                    patrol.walker.routeMovement().currentDestination());
            if (patrol.visits >= 4) {
                patrol.proved = true;
                LogUtils.getLogger().info("ESCALATED_MANUAL_PATROL_PROVED name={} pos={}",
                        patrol.walker.getName().getString(), patrol.walker.position());
            }
        }
    }

    private static final class Patrol {
        private final Citizen walker;
        private java.util.UUID lastMarker;
        private int visits;
        private boolean proved;
        private Patrol(Citizen walker) { this.walker = walker; }
    }

    private static Citizen citizen(ServerLevel level, BlockPos pos, boolean child, String name) {
        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(level));
        citizen.setBaby(child);
        citizen.citizenState().setPopulation(new CitizenPopulationState(null, true, true, 0));
        citizen.setPos(Vec3.atBottomCenterOf(pos));
        citizen.setCustomName(Component.literal(name));
        citizen.setCustomNameVisible(true);
        level.addFreshEntity(citizen);
        return citizen;
    }

    private static void terrain(ServerLevel level, int z, boolean stairs) {
        for (int x = 0; x <= 10; x++) for (int side = z; side <= z + 2; side++) {
            int rise = Math.min(3, x + 1);
            for (int y = 81; y <= 80 + rise; y++) level.setBlockAndUpdate(new BlockPos(x, y, side), Blocks.STONE.defaultBlockState());
            if (stairs && x < 3) level.setBlockAndUpdate(new BlockPos(x, 80 + rise, side), Blocks.STONE_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        }
        route(level, new BlockPos(-2, 81, z), new BlockPos(8, 84, z), stairs, stairs ? "階段の往復" : "段差の往復");
    }

    private static void label(ServerLevel level, int z, String text) {
        var label = new ArmorStand(level, -5, 81, z + 0.5);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("Marker", true);
        label.readAdditionalSaveData(tag);
        label.setInvisible(true);
        label.setNoGravity(true);
        label.setCustomName(Component.literal(text));
        label.setCustomNameVisible(true);
        level.addFreshEntity(label);
    }
}
