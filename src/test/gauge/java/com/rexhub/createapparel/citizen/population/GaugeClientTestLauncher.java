package com.rexhub.createapparel.citizen.population;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.client.screen.*;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.init.*;
import com.rexhub.createapparel.menu.*;
import com.rexhub.createapparel.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Path;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class GaugeClientTestLauncher {
    private static int stage, ticks, total;
    private static volatile Throwable failure;
    private static volatile UUID district, source;
    private static volatile UUID markerHome;
    private static volatile UUID centerReservation;
    private static volatile int citizenId, mannequinId;
    private static String capture;
    private static String worldCapture;
    private static String world;
    private static volatile boolean saved;
    private static java.util.List<String> restart;
    private static final BlockPos DISTRICT = new BlockPos(0, 71, 0), SOURCE = new BlockPos(4, 71, 0);
    private static final BlockPos RELOCATED = new BlockPos(1, 71, 0);
    private static final BlockPos CENTER_CHECK_GROUND = new BlockPos(-5, 70, -5);
    private static final UUID STATUS_RESIDENT = UUID.randomUUID();
    private static final String DISTRICT_NAME = "East District";
    private static final String LONG_DISTRICT_NAME = "W".repeat(CitizenGauge.MAX_NAME_LENGTH);
    @SubscribeEvent public static void initialized(ScreenEvent.Init.Post event) {
        if (stage != 123 || !(event.getScreen() instanceof CitizenGaugeScreen screen)) return;
        try {
            check(screen.getMenu().info().getString("Flow").equals("OUTFLOW"), "Outflow role arrives before the first GUI frame");
            check(screen.children().stream().anyMatch(child -> child instanceof Button button && button.visible
                    && button.getMessage().equals(net.minecraft.network.chat.Component.translatable("screen.createapparel.gauge.switch_influx"))
                    && button.getY() == screen.getGuiTop() + screen.getYSize() - 25), "First-frame toggle already offers influx in the gray footer");
        } catch (Throwable exception) { failure = exception; }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance(); if (mc.getOverlay() != null) return;
        try {
            if (failure != null) throw new AssertionError("Server check failed", failure);
            check(++total < 2400, "Client timeout at stage " + stage);
            if (stage == 0 && mc.screen != null) {
                mc.options.pauseOnLostFocus = false; mc.options.guiScale().set(2); mc.options.renderDistance().set(3); mc.options.framerateLimit().set(60);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720); mc.resizeDisplay();
                stage = 1;
                if (Boolean.getBoolean("apparel.gaugeTest.resume")) {
                    restart = java.nio.file.Files.readAllLines(Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build/gauge-client-world.txt"));
                    world = restart.get(0); stage = 80; mc.createWorldOpenFlows().loadLevel(new net.minecraft.client.gui.screens.TitleScreen(), world); return;
                }
                world = "citizen-gauge-" + System.nanoTime();
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings("Citizen Gauge QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(0, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            ticks++;
            switch (stage) {
                case 1 -> {
                    mc.setScreen(null); advance(2);
                    server(() -> {
                        var player = serverPlayer(); var level = player.serverLevel();
                        for (int x = -8; x < 16; x++) for (int z = -8; z < 16; z++) {
                            level.setBlock(new BlockPos(x, 70, z), Blocks.STONE.defaultBlockState(), 3);
                            level.setBlock(new BlockPos(x, 71, z), Blocks.AIR.defaultBlockState(), 3);
                            level.setBlock(new BlockPos(x, 72, z), Blocks.AIR.defaultBlockState(), 3);
                        }
                        for (int x = 0; x <= 4; x++) level.setBlock(new BlockPos(x, 71, 1), Blocks.STONE.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(-6, 69, -6), Blocks.STONE.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(-6, 70, -6), Blocks.STONE_SLAB.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(-6, 74, -6), Blocks.STONE.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(-5, 71, -6), Blocks.WHITE_CARPET.defaultBlockState(), 3);
                        level.setBlock(DISTRICT, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
                        ((CitizenGaugeBlockEntity) level.getBlockEntity(DISTRICT)).initialize(new BlockPos(0, 71, 6));
                        level.setBlock(SOURCE, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
                        ((CitizenGaugeBlockEntity) level.getBlockEntity(SOURCE)).initialize(null);
                        var wireObstacle = new BlockPos(2, 71, 0);
                        level.setBlock(wireObstacle, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
                        ((CitizenGaugeBlockEntity) level.getBlockEntity(wireObstacle)).initialize(null);
                        var panels = (CitizenGaugeBlockEntity) level.getBlockEntity(DISTRICT);
                        for (int slot = 1; slot < 4; slot++) panels.initialize(slot, null);
                        var population = CitizenPopulationManager.get(level);
                        district = population.data().at(DISTRICT).id(); source = population.data().at(SOURCE).id(); population.settings(district, 5, 0);
                        var citizen = ModEntities.CITIZEN.get().create(level); check(citizen != null, "Create Citizen"); citizen.setPos(2.5, 71, 3.5); level.addFreshEntity(citizen); citizenId = citizen.getId();
                        population.flags(citizen, false, true);
                        player.teleportTo(0.5, 71, -2.5); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CITIZEN_GAUGE.get()));
                        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 6000, 0, false, false));
                    });
                }
                case 2 -> { if (ticks > 40 && mc.level.getBlockState(DISTRICT).is(ModBlocks.CITIZEN_GAUGE.get())) {
                    checkChildWalking(); com.rexhub.createapparel.client.guide.GaugeTooltipClientChecks.check();
                    server(() -> {
                        var player = serverPlayer();
                        checkResyncThrottle(player);
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CITIZEN_GAUGE.get(), 8));
                        player.inventoryMenu.broadcastChanges();
                        player.connection.teleport(-4.5, 71, -2.5, 0, 60);
                    }); advance(140);
                } }
                case 140 -> { if (ticks > 15 && mc.player.getX() < -3) { mc.options.keyShift.setDown(true); advance(141); } }
                case 141 -> { if (ticks > 10) {
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(CENTER_CHECK_GROUND).add(0, .5, 0), net.minecraft.core.Direction.UP, CENTER_CHECK_GROUND, false));
                    advance(142);
                } }
                case 142 -> { if (ticks > 5) {
                    check(CENTER_CHECK_GROUND.equals(com.rexhub.createapparel.item.CitizenGaugeItem.center(mc.player.getMainHandItem(), mc.level)), "Actual client receives the first recorded center");
                    check(mc.player.getMainHandItem().getCount() == 1 && mc.player.getMainHandItem().getMaxStackSize() == 1
                            && mc.player.getInventory().countItem(ModItems.CITIZEN_GAUGE.get()) == 8, "Actual client receives one unstackable recorded Gauge and all seven unrecorded remainder items");
                    server(() -> {
                        var player = serverPlayer();
                        check(player.isShiftKeyDown(), "Real server receives sneak input");
                        var tag = player.getMainHandItem().getTag();
                        check(tag != null && tag.hasUUID("GaugeCenterReservation"), "Actual item-use packet reserves the unplaced center");
                        centerReservation = tag.getUUID("GaugeCenterReservation");
                        check(CitizenPopulationManager.get(player.serverLevel()).data().centerClaim(centerReservation).reservedItemCount() == 1,
                                "The actual item-use packet creates a singleton claim, not a claim for the original stack");
                        player.getInventory().setItem(2, player.getMainHandItem());
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CITIZEN_GAUGE.get()));
                        player.inventoryMenu.broadcastChanges();
                    }); advance(147);
                } }
                case 147 -> { if (ticks > 10 && !mc.player.getMainHandItem().hasTag()) {
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(CENTER_CHECK_GROUND).add(0, .5, 0), net.minecraft.core.Direction.UP, CENTER_CHECK_GROUND, false));
                    advance(148);
                } }
                case 148 -> { if (ticks > 5) {
                    check(!mc.player.getMainHandItem().hasTag(), "Different blank item stays blank before any wall placement");
                    server(() -> {
                        var player = serverPlayer(); var data = CitizenPopulationManager.get(player.serverLevel()).data();
                        check(!player.getMainHandItem().hasTag(), "Actual second item-use packet rejects an unplaced center reservation");
                        check(!data.centerUsed(CENTER_CHECK_GROUND) && !data.centerAvailable(CENTER_CHECK_GROUND, null)
                                && data.centerAvailable(CENTER_CHECK_GROUND, centerReservation), "Only the first recorded item owns the pending center");
                    }); worldCapture = "gauge-client-unplaced-center-rejected.png"; advance(143);
                } }
                case 143 -> { if (ticks > 5 && worldCapture == null) {
                    mc.options.keyShift.setDown(false);
                    var screen = new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player, mc.level.enabledFeatures(), false);
                    mc.setScreen(screen);
                    var select = screen.getClass().getDeclaredMethod("selectTab", net.minecraft.world.item.CreativeModeTab.class);
                    select.setAccessible(true);
                    select.invoke(screen, net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(net.minecraft.world.item.CreativeModeTabs.INVENTORY.location()));
                    advance(149);
                } }
                case 149 -> { if (ticks > 5 && mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
                    var slot = screen.getMenu().slots.stream().filter(candidate -> candidate.getItem().hasTag()
                            && candidate.getItem().getTag().hasUUID("GaugeCenterReservation")).findFirst().orElseThrow();
                    check(screen.mouseClicked(screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8, 0), "Pick up recorded Gauge in the creative inventory");
                    screen.mouseReleased(screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8, 0);
                    advance(150);
                } }
                case 150 -> { if (ticks > 3 && mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
                    check(screen.getMenu().getCarried().hasTag() && screen.getMenu().getCarried().getTag().hasUUID("GaugeCenterReservation"), "Recorded Gauge is on the creative cursor");
                    var trash = screen.getMenu().slots.get(screen.getMenu().slots.size() - 1);
                    check(screen.mouseClicked(screen.getGuiLeft() + trash.x + 8, screen.getGuiTop() + trash.y + 8, 0), "Discard recorded Gauge through the real creative trash slot");
                    screen.mouseReleased(screen.getGuiLeft() + trash.x + 8, screen.getGuiTop() + trash.y + 8, 0);
                    advance(151);
                } }
                case 151 -> { if (ticks > 5 && mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
                    check(screen.getMenu().getCarried().isEmpty(), "Creative trash clears the cursor");
                    server(() -> {
                        var player = serverPlayer(); var data = CitizenPopulationManager.get(player.serverLevel()).data();
                        check(data.centerAvailable(CENTER_CHECK_GROUND, null), "Creative trash packet automatically releases the pending center");
                        for (int index = 1; index <= 2; index++) {
                            var center = CENTER_CHECK_GROUND.east(index); var id = data.reserveCenter(center, null);
                            check(id != null, "Prepare separate claims for clear-inventory trash");
                            var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get()); var tag = stack.getOrCreateTag();
                            tag.putLong("GaugeCenter", center.asLong()); tag.putString("GaugeDimension", player.level().dimension().location().toString());
                            tag.putUUID("GaugeCenterReservation", id); player.getInventory().setItem(index, stack);
                        }
                        player.inventoryMenu.broadcastChanges();
                    }); advance(152);
                } }
                case 152 -> { if (ticks > 10 && mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
                    check(screen.getMenu().slots.stream().filter(slot -> slot.getItem().hasTag() && slot.getItem().getTag().hasUUID("GaugeCenterReservation")).count() == 2, "Creative clear-inventory fixture is synchronized");
                    var trash = screen.getMenu().slots.get(screen.getMenu().slots.size() - 1);
                    var click = screen.getClass().getDeclaredMethod("slotClicked", net.minecraft.world.inventory.Slot.class, int.class, int.class, net.minecraft.world.inventory.ClickType.class);
                    click.setAccessible(true); click.invoke(screen, trash, trash.index, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE);
                    advance(153);
                } }
                case 153 -> { if (ticks > 5) {
                    server(() -> {
                        var player = serverPlayer(); var data = CitizenPopulationManager.get(player.serverLevel()).data();
                        check(data.centerAvailable(CENTER_CHECK_GROUND.east(), null) && data.centerAvailable(CENTER_CHECK_GROUND.east(2), null), "Native clear-inventory trash releases every discarded Gauge claim");
                        var center = CENTER_CHECK_GROUND.east(3); var id = data.reserveCenter(center, null, 3);
                        check(id != null, "Prepare palette discard reservation"); centerReservation = id;
                        var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 3); var tag = stack.getOrCreateTag();
                        tag.putLong("GaugeCenter", center.asLong()); tag.putString("GaugeDimension", player.level().dimension().location().toString());
                        tag.putUUID("GaugeCenterReservation", id); player.getInventory().setItem(1, stack);
                        player.inventoryMenu.broadcastChanges();
                    }); advance(154);
                } }
                case 154 -> { if (ticks > 10 && mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
                    var slot = screen.getMenu().slots.stream().filter(candidate -> candidate.getItem().hasTag()
                            && candidate.getItem().getTag().hasUUID("GaugeCenterReservation")).findFirst().orElseThrow();
                    screen.mouseClicked(screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8, 0);
                    screen.mouseReleased(screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8, 0);
                    var select = screen.getClass().getDeclaredMethod("selectTab", net.minecraft.world.item.CreativeModeTab.class);
                    select.setAccessible(true);
                    select.invoke(screen, net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(net.minecraft.world.item.CreativeModeTabs.BUILDING_BLOCKS.location()));
                    advance(155);
                } }
                case 155 -> { if (ticks > 5 && mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
                    check(screen.getMenu().getCarried().getCount() == 3, "Changing creative tabs preserves the recorded cursor stack");
                    server(() -> check(!CitizenPopulationManager.get(serverPlayer().serverLevel()).data().centerAvailable(CENTER_CHECK_GROUND.east(3), null), "Changing tabs alone never releases the claim"));
                    var palette = screen.getMenu().slots.get(0);
                    screen.mouseClicked(screen.getGuiLeft() + palette.x + 8, screen.getGuiTop() + palette.y + 8, 1);
                    screen.mouseReleased(screen.getGuiLeft() + palette.x + 8, screen.getGuiTop() + palette.y + 8, 1);
                    advance(156);
                } }
                case 156 -> { if (ticks > 5 && mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
                    check(screen.getMenu().getCarried().getCount() == 2, "Right clicking creative palette discards exactly one carried gauge");
                    server(() -> {
                        var data = CitizenPopulationManager.get(serverPlayer().serverLevel()).data();
                        var entries = data.save(new CompoundTag()).getList("CenterReservations", net.minecraft.nbt.Tag.TAG_COMPOUND);
                        boolean found = false;
                        for (var entry : entries) { var tag = (CompoundTag) entry;
                            if (tag.getUUID("Id").equals(centerReservation)) { check(tag.getInt("Items") == 2, "Palette packet subtracts only one reservation"); found = true; }
                        }
                        check(found, "Partial palette discard retains the reservation");
                    });
                    var palette = screen.getMenu().slots.get(0);
                    screen.mouseClicked(screen.getGuiLeft() + palette.x + 8, screen.getGuiTop() + palette.y + 8, 0);
                    screen.mouseReleased(screen.getGuiLeft() + palette.x + 8, screen.getGuiTop() + palette.y + 8, 0);
                    advance(157);
                } }
                case 157 -> { if (ticks > 5 && mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
                    check(screen.getMenu().getCarried().isEmpty(), "Left clicking creative palette discards the remaining cursor stack");
                    mc.setScreen(null);
                    server(() -> {
                        var player = serverPlayer(); var data = CitizenPopulationManager.get(player.serverLevel()).data();
                        check(data.centerAvailable(CENTER_CHECK_GROUND.east(3), null), "Palette discard releases the final recorded items without trash use");
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CITIZEN_GAUGE.get()));
                        player.connection.teleport(.5, 71, -2.5, 0, 0);
                    }); advance(144);
                } }
                case 144 -> { if (ticks > 15 && mc.player.getX() > 0) { use(DISTRICT); advance(3); } }
                case 3 -> { if (ticks > 5 && mc.screen instanceof CitizenGaugeScreen && mc.player.containerMenu instanceof CitizenGaugeMenu menu && menu.info().hasUUID("District")) {
                    check(menu.info().getInt("Range") == 5, "District menu receives authoritative data");
                    check(menu.info().contains("Effective") && menu.info().contains("ActiveChains") && menu.info().contains("BlockedReason")
                            && menu.info().contains("RequestStatus") && menu.info().contains("LastSuccessfulAction"), "District diagnostic snapshot arrives with population, request and transfer state");
                    check(menu.info().getString("Name").equals(menu.info().getUUID("District").toString()), "Initial District name is the District UUID");
                    checkDistrictIcon(menu, DISTRICT);
                    check(com.rexhub.createapparel.client.citizen.DistrictBoundarySurface.read(mc.level, -6, -6, 71) == 70.5,
                            "Boundary follows a slab below feet and ignores the roof above feet");
                    check(com.rexhub.createapparel.client.citizen.DistrictBoundarySurface.read(mc.level, -6, -6, 70.25) == 70,
                            "Boundary never uses a surface above feet");
                    check(com.rexhub.createapparel.client.citizen.DistrictBoundarySurface.read(mc.level, -6, -6, 75) == 75,
                            "Boundary selects the highest eligible surface when above the roof");
                    check(Double.isNaN(com.rexhub.createapparel.client.citizen.DistrictBoundarySurface.read(mc.level, 30000000, 30000000, 71)),
                            "Unloaded terrain is skipped without loading a chunk");
                    check(com.rexhub.createapparel.client.citizen.DistrictBoundarySurface.read(mc.level, -5, -6, 72) == 71.0625,
                            "Boundary includes thin collision surfaces such as carpet");
                    capture = "gauge-client-district.png";
                    ModNetwork.updateGauge(new CitizenGaugeActionPacket(menu.containerId, menu.gaugeId(), menu.info().getLong("Version"), CitizenGaugeActionPacket.Action.SETTINGS, 6, 0, null)); advance(4);
                } }
                case 4 -> { if (ticks > 15 && mc.player.containerMenu instanceof CitizenGaugeMenu menu && menu.info().getInt("Range") == 6 && capture == null) {
                    capture = "gauge-client-tooltip-normal.png"; advance(145);
                } }
                case 145 -> { if (ticks > 5 && capture == null) { capture = "gauge-client-tooltip-shift.png"; advance(146); } }
                case 146 -> { if (ticks > 5 && capture == null && mc.player.containerMenu instanceof CitizenGaugeMenu menu) {
                    var screen = (CitizenGaugeScreen) mc.screen;
                    var fields = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                            .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
                    fields.get(0).setValue("999");
                    screen.setFocused(fields.get(0));
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                    check(mc.screen == screen, "Invalid radius cannot be saved with Enter");
                    capture = "gauge-client-radius-warning.png"; advance(130);
                } }
                case 130 -> { if (ticks > 5 && capture == null && mc.screen instanceof CitizenGaugeScreen screen) {
                    var fields = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                            .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
                    fields.get(0).setValue("8"); fields.get(1).setValue("9"); screen.setFocused(fields.get(1));
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                    check(mc.screen == screen, "Target above radius cannot be saved with Enter");
                    capture = "gauge-client-target-warning.png"; advance(131);
                } }
                case 131 -> { if (ticks > 5 && capture == null && mc.screen instanceof CitizenGaugeScreen screen && mc.player.containerMenu instanceof CitizenGaugeMenu menu) {
                    var fields = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                            .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
                    fields.get(1).setValue("0"); advance(132);
                } }
                case 132 -> { if (ticks > 5 && capture == null && mc.screen instanceof CitizenGaugeScreen screen && mc.player.containerMenu instanceof CitizenGaugeMenu menu) {
                    var fields = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                            .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
                    int left = screen.getGuiLeft(), top = screen.getGuiTop();
                    check(left == (screen.width - CitizenGaugeScreen.DISTRICT_WIDTH) / 2 - 32, "Panel and restored preview are centered together");
                    check(fields.get(2).getValue().isEmpty(), "Default UUID is a display placeholder, not editable text");
                    check(fields.get(2).getX() == left + 12, "District form uses the centered full panel width");
                    check(fields.get(2).getWidth() - 8 >= mc.font.width(menu.info().getUUID("District").toString()), "Default UUID fits without clipping");
                    check(fields.get(2).getY() + fields.get(2).getHeight() == top + CitizenGaugeScreen.DISTRICT_HEIGHT - 10, "Name input has ten pixels of bottom padding");
                    check(screen.mouseClicked(left + 20, top + CitizenGaugeScreen.DISTRICT_HEIGHT - 18, 0), "District name field is editable in the grey footer");
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_E, 0, 0); screen.charTyped('E', 0);
                    check(mc.screen == screen && fields.get(2).getValue().equals("E"), "Inventory key does not close a focused name field");
                    fields.get(2).setValue(DISTRICT_NAME);
                    String clipboard = mc.keyboardHandler.getClipboard();
                    try {
                        check(screen.mouseClicked(left + CitizenGaugeScreen.DISTRICT_WIDTH - 47, top + CitizenGaugeScreen.DISTRICT_HEIGHT - 25, 0), "Copy button sits immediately left of save");
                        check(mc.keyboardHandler.getClipboard().equals(menu.info().getUUID("District").toString()), "Copy still copies District UUID, not the editable name");
                    } finally { mc.keyboardHandler.setClipboard(clipboard); }
                    check(screen.mouseClicked(left + CitizenGaugeScreen.DISTRICT_WIDTH - 25, top + CitizenGaugeScreen.DISTRICT_HEIGHT - 25, 0), "Create-style confirm saves radius and name"); advance(17);
                } }
                case 17 -> { if (ticks > 20) { use(DISTRICT); advance(18); } }
                case 18 -> { if (ticks > 5 && mc.screen instanceof CitizenGaugeScreen screen && mc.player.containerMenu instanceof CitizenGaugeMenu menu && menu.info().getInt("Range") == 8) {
                    check(menu.info().getString("Name").equals(DISTRICT_NAME), "District rename persists on reopening"); capture = "gauge-client-district-edited.png";
                    checkDistrictIcon(menu, DISTRICT);
                    advance(30);
                } }
                case 30 -> { if (ticks > 5 && capture == null && mc.screen instanceof CitizenGaugeScreen screen) {
                    var fields = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                            .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
                    fields.get(2).setValue(""); fields.get(1).setValue("1");
                    check(screen.mouseClicked(screen.getGuiLeft() + CitizenGaugeScreen.DISTRICT_WIDTH - 25,
                            screen.getGuiTop() + CitizenGaugeScreen.DISTRICT_HEIGHT - 25, 0), "Blank name and edited target can be saved"); advance(31);
                } }
                case 31 -> { if (ticks > 20) { use(DISTRICT); advance(32); } }
                case 32 -> { if (ticks > 5 && mc.screen instanceof CitizenGaugeScreen screen && mc.player.containerMenu instanceof CitizenGaugeMenu menu && menu.info().hasUUID("District")) {
                    check(menu.info().getString("Name").equals(menu.info().getUUID("District").toString()) && menu.info().getInt("Target") == 1,
                            "Saving no input restores UUID and saves the target");
                    var fields = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                            .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
                    check(fields.get(2).getValue().isEmpty(), "Reopened UUID name starts with empty editable text");
                    capture = "gauge-client-district-reset.png"; advance(33);
                } }
                case 33 -> { if (ticks > 5 && capture == null && mc.screen instanceof CitizenGaugeScreen screen) {
                    var fields = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                            .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
                    fields.get(2).setValue("W".repeat(CitizenGauge.MAX_NAME_LENGTH)); advance(36);
                } }
                case 36 -> { if (ticks > 5 && mc.screen instanceof CitizenGaugeScreen screen) {
                    var fields = screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                            .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
                    fields.get(2).moveCursorToStart(); fields.get(2).setHighlightPos(CitizenGauge.MAX_NAME_LENGTH);
                    check(fields.get(2).getHighlighted().length() == CitizenGauge.MAX_NAME_LENGTH, "Flat input preserves native text selection");
                    fields.get(2).insertText(DISTRICT_NAME); fields.get(1).setValue("0");
                    check(fields.get(2).getValue().equals(DISTRICT_NAME), "Long name can be replaced with a shorter name");
                    capture = "gauge-client-input-shortened.png"; advance(37);
                } }
                case 37 -> { if (ticks > 5 && capture == null && mc.screen instanceof CitizenGaugeScreen screen) {
                    screen.mouseClicked(screen.getGuiLeft() + CitizenGaugeScreen.DISTRICT_WIDTH - 25,
                            screen.getGuiTop() + CitizenGaugeScreen.DISTRICT_HEIGHT - 25, 0); advance(34);
                } }
                case 34 -> { if (ticks > 20) { use(DISTRICT); advance(35); } }
                case 35 -> { if (ticks > 5 && mc.screen instanceof CitizenGaugeScreen screen && mc.player.containerMenu instanceof CitizenGaugeMenu menu
                        && menu.info().getString("Name").equals(DISTRICT_NAME) && menu.info().getInt("Target") == 0) {
                    check(screen.mouseClicked(screen.getGuiLeft() + 26, screen.getGuiTop() + 55, 0), "Create-style + button starts connection");
                    advance(5); server(() -> { var manager = CitizenPopulationManager.get(serverPlayer().serverLevel()); check(manager.data().gauge(district).range() == 8, "GUI confirm applied authoritative settings"); serverPlayer().teleportTo(4.5, 71, -2.5); });
                } }
                case 5 -> { if (ticks > 20 && mc.player.getX() > 3) { use(SOURCE); advance(14); } }
                case 14 -> { if (ticks > 20) { use(SOURCE); advance(6); } }
                case 6 -> { if (ticks > 5 && mc.screen instanceof CitizenGaugeScreen screen && mc.player.containerMenu instanceof CitizenGaugeMenu menu && menu.info().hasUUID("Id") && !menu.info().hasUUID("District")) {
                    checkFlowContentLayout(screen);
                    check(screen.getYSize() == CitizenGaugeScreen.SOURCE_HEIGHT && screen.getYSize() == 104, "Source height fits just the two-button row and footer");
                    var buttons = screen.children().stream().filter(child -> child instanceof com.simibubi.create.foundation.gui.widget.IconButton button && button.visible)
                            .map(com.simibubi.create.foundation.gui.widget.IconButton.class::cast).toList();
                    check(buttons.get(0).getY() - screen.getGuiTop() == 25 && buttons.get(1).getY() - screen.getGuiTop() == 45,
                            "Two Source buttons are vertically centered in the compact brown area");
                    check(com.rexhub.createapparel.client.renderer.CitizenGaugeRenderer.ITEM_ICON_SCALE == .2578125F, "Wall icon scale matches Factory Gauge's flat item transform");
                    check(screen.children().stream().filter(child -> child instanceof com.simibubi.create.foundation.gui.widget.IconButton button && button.visible).count() == 3,
                            "Source only exposes connect, relocate and save");
                    check(!screen.mouseScrolled(screen.getGuiLeft() + 80, screen.getGuiTop() + 40, 1), "Removed Source slots do not scroll");
                    check(screen.children().stream().filter(child -> child instanceof Button button && button.visible).count() == 1,
                            "Flow GUI exposes one mode switch without adding footer buttons");
                    check(mc.getResourceManager().getResource(com.rexhub.createapparel.client.citizen.DistrictGaugeIcon.INFLUX).isPresent()
                            && mc.getResourceManager().getResource(com.rexhub.createapparel.client.citizen.DistrictGaugeIcon.OUTFLOW).isPresent(), "Supplied flow icon textures are loaded");
                    check(menu.info().getList("Links", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 1, "Source has linked District"); capture = "gauge-client-source.png"; advance(120);
                } }
                case 120 -> { if (ticks > 15 && capture == null && mc.screen instanceof CitizenGaugeScreen screen) {
                    check(screen.mouseClicked(screen.getGuiLeft() + 80, screen.getGuiTop() + screen.getYSize() - 16, 0), "Footer flow button can select outflow");
                    checkFlowContentLayout(screen);
                    capture = "gauge-client-outflow.png"; advance(121);
                } }
                case 121 -> { if (ticks > 15 && capture == null && mc.screen instanceof CitizenGaugeScreen screen) {
                    check(screen.mouseClicked(screen.getGuiLeft() + screen.getXSize() - 24, screen.getGuiTop() + screen.getYSize() - 16, 0), "Flow mode saves through existing footer confirmation");
                    advance(122);
                } }
                case 122 -> { if (ticks > 20 && mc.screen == null && mc.level.getBlockEntity(SOURCE) instanceof CitizenGaugeBlockEntity entity && entity.outflow(0)) {
                    server(() -> check(CitizenPopulationManager.get(serverPlayer().serverLevel()).data().gauge(source).outflow(), "Outflow GUI action updates server state"));
                    use(SOURCE); advance(123);
                } }
                case 123 -> { if (ticks > 15 && mc.screen instanceof CitizenGaugeScreen screen && mc.player.containerMenu instanceof CitizenGaugeMenu menu && menu.info().getString("Flow").equals("OUTFLOW")) {
                    check(screen.mouseClicked(screen.getGuiLeft() + 80, screen.getGuiTop() + screen.getYSize() - 16, 0), "Reopened GUI can select influx again");
                    check(screen.mouseClicked(screen.getGuiLeft() + screen.getXSize() - 24, screen.getGuiTop() + screen.getYSize() - 16, 0), "Saves restored influx mode");
                    advance(7);
                } }
                case 7 -> { if (ticks > 20 && capture == null) { mc.player.closeContainer(); advance(8); server(() -> {
                    var player = serverPlayer(); var citizen = (Citizen) player.serverLevel().getEntity(citizenId); check(citizen != null, "Citizen present");
                    CitizenPopulationManager.get(player.serverLevel()).flags(citizen, false, false);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
                    net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider((id, inventory, viewer) -> new CitizenMenu(id, inventory, citizen),
                            net.minecraft.network.chat.Component.literal("Citizen")), buffer -> buffer.writeVarInt(citizenId));
                }); } }
                case 8 -> { if (mc.screen instanceof CitizenScreen && mc.player.containerMenu instanceof CitizenMenu menu) {
                    ModNetwork.updateCitizen(CitizenActionPacket.tab(menu, CitizenMenu.Tab.BEHAVIOR)); menu.setTab(CitizenMenu.Tab.BEHAVIOR); advance(9);
                } }
                case 9 -> { if (ticks > 15 && mc.screen instanceof CitizenScreen screen && mc.player.containerMenu instanceof CitizenMenu menu) {
                    check(screen.children().stream().filter(child -> child instanceof Button button && button.visible).count() >= 5, "Behavior controls include population flags");
                    check(screen.children().stream().filter(child -> child instanceof Button button && button.visible
                            && button.getY() == screen.getGuiTop() + 207).count() == 2, "Population buttons follow the destination and district rows");
                    if (!menu.districtName().equals(DISTRICT_NAME)) break;
                    check(net.minecraft.network.chat.Component.translatable("screen.createapparel.gauge.membership", DISTRICT_NAME)
                            .getString().contains(DISTRICT_NAME), "District membership translation is loaded");
                    capture = "gauge-client-citizen-district.png"; advance(90);
                } }
                case 90 -> { if (ticks > 10 && capture == null) {
                    server(() -> CitizenPopulationManager.get(serverPlayer().serverLevel()).settings(district, 8, 0, LONG_DISTRICT_NAME)); advance(91);
                } }
                case 91 -> { if (ticks > 25 && mc.player.containerMenu instanceof CitizenMenu menu) {
                    check(menu.districtName().equals(LONG_DISTRICT_NAME), "Open Citizen menu refreshes maximum-length District name without reopening");
                    capture = "gauge-client-citizen-district-long.png";
                    server(() -> CitizenPopulationManager.get(serverPlayer().serverLevel()).settings(district, 8, 0, DISTRICT_NAME)); advance(92);
                } }
                case 92 -> { if (ticks > 25 && mc.player.containerMenu instanceof CitizenMenu menu) {
                    check(menu.districtName().equals(DISTRICT_NAME), "Citizen district display follows the server name");
                    server(() -> ((Citizen) serverPlayer().serverLevel().getEntity(citizenId)).setNoAi(true));
                    ModNetwork.updateCitizen(CitizenActionPacket.select(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, "wander")); advance(110);
                } }
                case 110 -> { if (ticks > 15 && mc.screen instanceof CitizenScreen screen && mc.player.containerMenu instanceof CitizenMenu menu
                        && menu.citizen().citizenState().behavior().mode() == CitizenBehaviorMode.WANDER) {
                    check(menu.citizen().citizenState().behavior().unlimitedWander(), "Wander defaults to unlimited over entity synchronization");
                    check(screen.children().stream().anyMatch(child -> child instanceof Button button && button.visible
                            && button.getX() == screen.getGuiLeft() + 204 && button.getY() == screen.getGuiTop() + 52
                            && button.getMessage().getString().equals("-")), "Unlimited radius is shown as dash");
                    capture = "gauge-client-citizen-unlimited.png"; advance(114);
                } }
                case 114 -> { if (ticks > 5 && capture == null && mc.screen instanceof CitizenScreen screen) {
                    check(screen.mouseScrolled(screen.getGuiLeft() + 250, screen.getGuiTop() + 61, -1), "Radius wheel handles unlimited to one"); advance(111);
                } }
                case 111 -> { if (ticks > 15 && mc.screen instanceof CitizenScreen screen && mc.player.containerMenu instanceof CitizenMenu menu) {
                    check(menu.citizen().citizenState().behavior().wanderRadius() == 1, "Radius wheel increments dash to one");
                    check(screen.mouseScrolled(screen.getGuiLeft() + 250, screen.getGuiTop() + 61, 1), "Radius wheel handles one to unlimited"); advance(112);
                } }
                case 112 -> { if (ticks > 15 && mc.screen instanceof CitizenScreen screen && mc.player.containerMenu instanceof CitizenMenu menu) {
                    check(menu.citizen().citizenState().behavior().unlimitedWander(), "Radius wheel decrements one to dash");
                    check(screen.mouseClicked(screen.getGuiLeft() + 250, screen.getGuiTop() + 61, 0), "Radius choices include the unlimited entry");
                    capture = "gauge-client-citizen-unlimited-options.png"; advance(113);
                } }
                case 113 -> { if (ticks > 10 && capture == null && mc.screen instanceof CitizenScreen screen && mc.player.containerMenu instanceof CitizenMenu menu) {
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    ModNetwork.updateCitizen(CitizenActionPacket.select(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, "stand"));
                    server(() -> ((Citizen) serverPlayer().serverLevel().getEntity(citizenId)).setNoAi(false));
                    ModNetwork.updateCitizen(CitizenActionPacket.select(menu, CitizenActionPacket.Action.POPULATION_FORCE, "true"));
                    ModNetwork.updateCitizen(CitizenActionPacket.select(menu, CitizenActionPacket.Action.POPULATION_INDEPENDENT, "true")); advance(10);
                } }
                case 10 -> { if (ticks > 20 && mc.player.containerMenu instanceof CitizenMenu menu && menu.citizen().citizenState().population().independent()) {
                    check(menu.citizen().citizenState().population().force() && menu.citizen().citizenState().population().districtId() == null, "Flags synchronize through entity data");
                    check(menu.districtName().isEmpty(), "Independent Citizen hides its former District name");
                    capture = "gauge-client-citizen.png"; advance(93);
                } }
                case 93 -> { if (ticks > 10 && capture == null) {
                    mc.player.closeContainer(); advance(94); server(() -> {
                        var player = serverPlayer(); var level = player.serverLevel(); var pos = new BlockPos(3, 71, -1);
                        level.setBlock(pos, ModBlocks.CITIZEN_ROUTE_MARKER.get().defaultBlockState(), 3);
                        var routes = com.rexhub.createapparel.citizen.route.CitizenRouteManager.get(level);
                        var population = CitizenPopulationManager.get(level); markerHome = population.data().gauge(district).districtId();
                        for (int index = 0; index < 8; index++) {
                            var gaugePos = new BlockPos(5 + index, 71, 0);
                            level.setBlock(gaugePos.south(), Blocks.STONE.defaultBlockState(), 3);
                            level.setBlock(gaugePos, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
                            ((CitizenGaugeBlockEntity) level.getBlockEntity(gaugePos)).initialize(new BlockPos(5 + index, 71, 9));
                            var other = population.data().at(gaugePos); population.settings(other.id(), 1, 0, "Destination " + (index + 1));
                            check(population.link(district, other.id()), "Create bounded destination fixture link");
                            if (index == 0) {
                                var arrivalPos = new BlockPos(5, 71, 9); level.setBlock(arrivalPos, ModBlocks.GATEWAY_ROUTE_MARKER.get().defaultBlockState(), 3);
                                var arrival = routes.register((com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity) level.getBlockEntity(arrivalPos));
                                routes.data().put(arrival.withDistrict(other.districtId()));
                            }
                        }
                        var marker = routes.register((com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity) level.getBlockEntity(pos))
                                .withDistrict(markerHome);
                        routes.data().put(marker);
                        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                                (id, inventory, viewer) -> new RouteMarkerMenu(id, inventory, marker.id(), pos), net.minecraft.network.chat.Component.literal("Route Marker")),
                                buffer -> { buffer.writeUUID(marker.id()); buffer.writeBlockPos(pos); });
                    });
                } }
                case 94 -> { if (ticks > 20 && mc.screen instanceof RouteMarkerScreen screen && mc.player.containerMenu instanceof RouteMarkerMenu menu) {
                    check(menu.info().getString("DistrictName").equals(DISTRICT_NAME), "Normal Route Marker receives the configured District name");
                    var fields = screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox field && field.visible)
                            .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
                    check(fields.size() == 2 && fields.get(0).getY() - screen.getGuiTop() == 64 && fields.get(1).getY() - screen.getGuiTop() == 94,
                            "District is automatic and read-only; Settings order retains type, radius, height, affiliation, delete");
                    capture = "gauge-client-marker-district.png"; advance(100);
                } }
                case 100 -> { if (ticks > 10 && capture == null && mc.screen instanceof RouteMarkerScreen screen) {
                    server(() -> CitizenPopulationManager.get(serverPlayer().serverLevel()).settings(district, 5, 0, DISTRICT_NAME)); advance(101);
                } }
                case 101 -> { if (ticks > 20 && mc.screen instanceof RouteMarkerScreen screen && mc.player.containerMenu instanceof RouteMarkerMenu menu) {
                    check(!menu.info().getCompound("Marker").hasUUID("DistrictId") && menu.info().getString("DistrictName").isEmpty(), "Range shrink automatically clears Normal Marker affiliation");
                    server(() -> CitizenPopulationManager.get(serverPlayer().serverLevel()).settings(district, 8, 0, DISTRICT_NAME)); advance(102);
                } }
                case 102 -> { if (ticks > 20 && mc.screen instanceof RouteMarkerScreen screen && mc.player.containerMenu instanceof RouteMarkerMenu menu) {
                    check(menu.info().getString("DistrictName").equals(DISTRICT_NAME), "Range expansion automatically assigns Normal Marker affiliation");
                    check(screen.mouseScrolled(screen.getGuiLeft() + 220, screen.getGuiTop() + 42, -1), "Type selector wheel operates from the first settings row"); advance(103);
                } }
                case 103 -> { if (ticks > 20 && mc.screen instanceof RouteMarkerScreen screen && mc.player.containerMenu instanceof RouteMarkerMenu menu) {
                    check(menu.info().getCompound("Marker").getString("Type").equals("GATEWAY") && menu.info().getCompound("Marker").getUUID("DistrictId").equals(markerHome), "Changing type retains affiliation");
                    var destinations = menu.info().getList("Destinations", net.minecraft.nbt.Tag.TAG_COMPOUND);
                    check(destinations.size() == 8 && destinations.stream().filter(raw -> ((net.minecraft.nbt.CompoundTag) raw).getBoolean("Available")).count() == 1,
                            "Gateway lists linked Districts and evaluates current arrival availability");
                    check(screen.mouseClicked(screen.getGuiLeft() + 200, screen.getGuiTop() - 12, 0), "Open refreshed Gateway Type Settings");
                    check(screen.children().stream().noneMatch(child -> child instanceof net.minecraft.client.gui.components.EditBox field && field.visible), "Gateway Type Settings has no old UUID input");
                    capture = "gauge-client-marker-gateway-destinations.png"; advance(104);
                } }
                case 104 -> { if (ticks > 10 && capture == null && mc.screen instanceof RouteMarkerScreen screen) {
                    check(screen.mouseScrolled(screen.getGuiLeft() + 120, screen.getGuiTop() + 70, -1), "Gateway destination list scrolls without a new server query");
                    capture = "gauge-client-marker-gateway-scrolled.png"; advance(105);
                } }
                case 105 -> { if (ticks > 10 && capture == null && mc.screen instanceof RouteMarkerScreen screen) {
                    screen.mouseClicked(screen.getGuiLeft() + 60, screen.getGuiTop() - 12, 0); advance(95);
                } }
                case 95 -> { if (ticks > 10 && capture == null) {
                    server(() -> CitizenPopulationManager.get(serverPlayer().serverLevel()).settings(district, 8, 0, LONG_DISTRICT_NAME)); advance(96);
                } }
                case 96 -> { if (ticks > 20 && mc.player.containerMenu instanceof RouteMarkerMenu menu) {
                    check(menu.info().getString("DistrictName").equals(LONG_DISTRICT_NAME), "Open Marker menu refreshes maximum-length names without a route version change");
                    capture = "gauge-client-marker-district-long.png";
                    server(() -> {
                        var player = serverPlayer(); var routes = com.rexhub.createapparel.citizen.route.CitizenRouteManager.get(player.serverLevel());
                        var serverMenu = (RouteMarkerMenu) player.containerMenu;
                        CitizenPopulationManager.get(player.serverLevel()).settings(district, 5, 0, DISTRICT_NAME);
                    }); advance(97);
                } }
                case 97 -> { if (ticks > 20 && mc.player.containerMenu instanceof RouteMarkerMenu menu) {
                    check(menu.info().getString("DistrictName").isEmpty() && menu.info().getList("Destinations", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(), "Clearing Marker District hides name and destinations");
                    server(() -> {
                        var level = serverPlayer().serverLevel();
                        for (int index = 0; index < 8; index++) level.removeBlock(new BlockPos(5 + index, 71, 0), false);
                        level.removeBlock(new BlockPos(5, 71, 9), false);
                        CitizenPopulationManager.get(level).settings(district, 8, 0, DISTRICT_NAME);
                    }); advance(40);
                } }
                case 40 -> { if (ticks > 10 && capture == null) {
                    mc.player.closeContainer(); advance(41); server(() -> {
                        var player = serverPlayer(); var level = player.serverLevel(); var pos = new BlockPos(0, 71, -1);
                        level.setBlock(pos, ModBlocks.WARDROBE.get().defaultBlockState(), 3);
                        level.setBlock(pos.above(), ModBlocks.WARDROBE.get().defaultBlockState().setValue(
                                com.rexhub.createapparel.block.WardrobeBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
                        var wardrobe = (com.rexhub.createapparel.block.entity.WardrobeBlockEntity) level.getBlockEntity(pos);
                        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                                (id, inventory, viewer) -> new WardrobeMenu(id, inventory, wardrobe), net.minecraft.network.chat.Component.literal("Wardrobe")), pos);
                    });
                } }
                case 41 -> { if (ticks > 15 && mc.screen instanceof WardrobeScreen<?> && mc.player.containerMenu instanceof WardrobeMenu) {
                    capture = "gauge-client-wardrobe.png"; advance(42);
                } }
                case 42 -> { if (ticks > 10 && capture == null) {
                    mc.player.closeContainer(); advance(43); server(() -> {
                        var player = serverPlayer(); var mannequin = ModEntities.FULL_MANNEQUIN_WHITE_CLASSIC.get().create(player.serverLevel());
                        check(mannequin != null, "Create mannequin"); mannequin.setPos(1.5, 71, -1.5); player.serverLevel().addFreshEntity(mannequin); mannequinId = mannequin.getId();
                    });
                } }
                case 43 -> { if (ticks > 20 && mc.level.getEntity(mannequinId) instanceof Mannequin) {
                    advance(44); server(() -> {
                        var player = serverPlayer(); var mannequin = (Mannequin) player.serverLevel().getEntity(mannequinId);
                        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                                (id, inventory, viewer) -> new MannequinMenu(id, inventory, mannequin), net.minecraft.network.chat.Component.literal("Mannequin")), buffer -> buffer.writeVarInt(mannequinId));
                    });
                } }
                case 44 -> { if (ticks > 15 && mc.screen instanceof WardrobeScreen<?> && mc.player.containerMenu instanceof MannequinMenu) {
                    capture = "gauge-client-mannequin.png"; advance(45);
                } }
                case 45 -> { if (ticks > 10 && capture == null) {
                    mc.player.closeContainer(); mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)); advance(46);
                } }
                case 46 -> { if (ticks > 15) {
                    if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) com.rexhub.createapparel.compat.jei.GaugeJeiClientChecks.assertInventoryVisible(mc.screen);
                    mc.setScreen(null); advance(11);
                } }
                case 11 -> { if (ticks > 20 && capture == null) {
                    mc.player.closeContainer(); advance(19); server(() -> {
                        var player = serverPlayer(); var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get());
                        stack.getOrCreateTag().putLong("GaugeCenter", new BlockPos(0, 71, 6).asLong());
                        stack.getOrCreateTag().putString("GaugeDimension", player.level().dimension().location().toString());
                        player.setItemInHand(InteractionHand.MAIN_HAND, stack); player.connection.teleport(.5, 71, -2.5, 0, 10);
                    });
                } }
                case 19 -> { if (ticks > 20) { use(DISTRICT); advance(20); } }
                case 20 -> { if (ticks > 5 && mc.screen instanceof CitizenGaugeScreen screen && mc.player.containerMenu instanceof CitizenGaugeMenu menu && menu.info().hasUUID("District")) {
                    check(mc.player.getMainHandItem().hasFoil(), "Recorded District item glows in the actual client");
                    check(screen.mouseClicked(screen.getGuiLeft() + 26, screen.getGuiTop() + 75, 0), "Move Gauge button is present and starts relocation"); advance(21);
                } }
                case 21 -> { if (ticks > 15 && mc.screen == null) {
                    var point = Vec3.atLowerCornerOf(RELOCATED).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(mc.level.getBlockState(DISTRICT), 0));
                    mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, point); advance(22);
                } }
                case 22 -> { if (ticks > 15) { worldCapture = "gauge-client-move-preview.png"; advance(23); } }
                case 23 -> { if (ticks > 10 && worldCapture == null) {
                    var point = Vec3.atLowerCornerOf(RELOCATED).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(mc.level.getBlockState(DISTRICT), 0));
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, net.minecraft.core.Direction.NORTH, RELOCATED.south(), false)); advance(24);
                } }
                case 24 -> { if (ticks > 25 && mc.level.getBlockEntity(RELOCATED) instanceof CitizenGaugeBlockEntity panel && panel.contains(district)) {
                    advance(12); server(() -> {
                        var player = serverPlayer(); player.setNoGravity(true); player.connection.teleport(2.5, 75, -7.5, 0, 30);
                        var population = CitizenPopulationManager.get(player.serverLevel());
                        check(population.data().gauge(district).pos().equals(RELOCATED) && population.data().gauge(district).range() == 8 && population.data().linked(district).contains(source), "GUI relocation preserves settings, identity and connection");
                        var citizen = (Citizen) player.serverLevel().getEntity(citizenId);
                        try { java.nio.file.Files.writeString(Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build/gauge-client-world.txt"),
                                world + "\n" + district + "\n" + source + "\n" + population.data().gauge(district).districtId() + "\n" + citizen.getUUID() + "\n"); saved = true; }
                        catch (java.io.IOException error) { throw new IllegalStateException(error); }
                    });
                } }
                case 12 -> { if (ticks > 50 && saved && mc.screen == null) { worldCapture = "gauge-client-world.png"; advance(13); } }
                case 13 -> { if (ticks > 15 && worldCapture == null) {
                    server(() -> serverPlayer().connection.teleport(2.5, 70.5, -3.5, 0, 12)); advance(15);
                } }
                case 15 -> { if (ticks > 25) {
                    check(mc.level.getBlockEntity(DISTRICT) instanceof CitizenGaugeBlockEntity panels && panels.panelCount() == 3
                            && mc.level.getBlockEntity(RELOCATED) instanceof CitizenGaugeBlockEntity moved && moved.panelCount() == 1, "Only selected wall panel moves; siblings remain synchronized");
                    worldCapture = "gauge-client-wall.png"; advance(16);
                } }
                case 16 -> { if (ticks > 15 && worldCapture == null) {
                    com.rexhub.createapparel.client.renderer.GaugeWireClientChecks.assertDetour(RELOCATED, SOURCE);
                    var status = ((CitizenGaugeBlockEntity) mc.level.getBlockEntity(RELOCATED)).districtStatus(0);
                    check(status != null && status.districtId().equals(java.util.UUID.fromString(java.nio.file.Files.readAllLines(
                            Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build/gauge-client-world.txt")).get(3))), "Relocation preserves District icon identity");
                    check(status != null && status.name().equals(DISTRICT_NAME) && status.satisfied(), "Client receives renamed District with green zero-target lamp");
                    mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atLowerCornerOf(RELOCATED).add(
                            com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(mc.level.getBlockState(RELOCATED), 0)));
                    server(() -> serverPlayer().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)); advance(25);
                } }
                case 25 -> { if (ticks > 15) {
                    check(mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(RELOCATED), "Cursor targets the relocated District for hover HUD");
                    var hit = (BlockHitResult) mc.hitResult;
                    check(com.rexhub.createapparel.block.CitizenGaugeBlock.targetedSlot(RELOCATED, mc.level.getBlockState(RELOCATED), hit.getLocation()) == 0
                            && mc.player.getMainHandItem().isEmpty(), "Empty-hand cursor targets the correct District quadrant");
                    worldCapture = "gauge-client-district-hover-green.png"; advance(26);
                } }
                case 26 -> { if (ticks > 10 && worldCapture == null) {
                    server(() -> { var population = CitizenPopulationManager.get(serverPlayer().serverLevel());
                        population.data().register(STATUS_RESIDENT, new CitizenPopulationState(population.data().gauge(district).districtId(), false, false, 0)); }); advance(27);
                } }
                case 27 -> { if (ticks > 20) {
                    var status = ((CitizenGaugeBlockEntity) mc.level.getBlockEntity(RELOCATED)).districtStatus(0);
                    check(status != null && status.population() == 1 && !status.satisfied(), "Actual population change updates red lamp without reopening GUI");
                    worldCapture = "gauge-client-district-hover-red.png"; advance(28);
                } }
                case 28 -> { if (ticks > 10 && worldCapture == null) {
                    server(() -> CitizenPopulationManager.get(serverPlayer().serverLevel()).data().removeCitizen(STATUS_RESIDENT)); advance(29);
                } }
                case 29 -> { if (ticks > 15) {
                    server(() -> {
                        var player = serverPlayer();
                        player.connection.teleport(.5, 71, -4.5, 0, 25);
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
                        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    }); advance(115);
                } }
                case 115 -> { if (ticks > 125) {
                    check(GaugeInteractions.observingDistricts(mc.player) && !GaugeInteractions.holding(mc.player), "Main-hand phone observes district terrain boundaries without a gauge");
                    worldCapture = "gauge-client-phone-boundaries.png"; advance(116);
                } }
                case 116 -> { if (ticks > 10 && worldCapture == null) {
                    server(() -> {
                        serverPlayer().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        serverPlayer().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
                    }); advance(117);
                } }
                case 117 -> { if (ticks > 125) {
                    check(GaugeInteractions.observingDistricts(mc.player) && !GaugeInteractions.holding(mc.player)
                            && !com.rexhub.createapparel.citizen.route.RouteMarkerPermissions.canEditRoutes(mc.player, InteractionHand.OFF_HAND),
                            "Offhand-only phone observes boundaries without enabling marker editing");
                    worldCapture = "gauge-client-offhand-phone-boundaries.png"; advance(118);
                } }
                case 118 -> { if (ticks > 10 && worldCapture == null) {
                    server(() -> serverPlayer().setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)); advance(119);
                } }
                case 119 -> { if (ticks > 15) {
                    check(!GaugeInteractions.observingDistricts(mc.player), "Putting away phone and gauge hides district boundaries");
                    server(() -> {
                        var player = serverPlayer(); var level = player.serverLevel(); var pos = new BlockPos(12, 71, 0);
                        level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
                        level.setBlock(pos, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
                        var panel = (CitizenGaugeBlockEntity) level.getBlockEntity(pos); panel.initialize(new BlockPos(12, 71, 6));
                        var population = CitizenPopulationManager.get(level); population.settings(panel.gaugeId(), 8, 0);
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
                        player.connection.teleport(6.5, 77, -1.5, 0, 68);
                    }); advance(125);
                } }
                case 125 -> { if (ticks > 160) { worldCapture = "gauge-client-dual-boundaries.png"; advance(126); } }
                case 126 -> { if (ticks > 10 && worldCapture == null) {
                    server(() -> {
                        var player = serverPlayer(); var pos = new BlockPos(12, 71, 0);
                        player.serverLevel().removeBlock(pos, false); player.serverLevel().removeBlock(pos.south(), false);
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    }); advance(127);
                } }
                case 127 -> { if (ticks > 20) {
                    server(() -> {
                        var player = serverPlayer();
                        com.rexhub.createapparel.client.renderer.GaugeWireClientChecks.installCrossing(player.serverLevel(), false);
                        player.connection.teleport(15.5, 72, -5, 0, 8);
                    }); advance(220);
                } }
                case 220 -> { if (ticks > 45) {
                    com.rexhub.createapparel.client.renderer.GaugeWireClientChecks.assertCrossing(false);
                    worldCapture = "gauge-client-wire-crossing.png"; advance(221);
                } }
                case 221 -> { if (ticks > 10 && worldCapture == null) {
                    server(() -> com.rexhub.createapparel.client.renderer.GaugeWireClientChecks.installCrossing(serverPlayer().serverLevel(), true)); advance(222);
                } }
                case 222 -> { if (ticks > 45) {
                    com.rexhub.createapparel.client.renderer.GaugeWireClientChecks.assertCrossing(true);
                    worldCapture = "gauge-client-wire-factory.png"; advance(223);
                } }
                case 223 -> { if (ticks > 10 && worldCapture == null) {
                    stage = 99; LogUtils.getLogger().info("CITIZEN_GAUGE_CLIENT_PASSED: districtMenu, sourceMenu, settingsPackets, links, citizenFlags, rangePreview, screenshots"); mc.stop();
                } }
                case 80 -> { if (ticks > 50) { advance(81); server(() -> {
                    var population = CitizenPopulationManager.get(serverPlayer().serverLevel());
                    var a = UUID.fromString(restart.get(1)); var b = UUID.fromString(restart.get(2)); var member = UUID.fromString(restart.get(4));
                    check(population.data().gauge(a) != null && population.data().gauge(b) != null && population.data().gauge(a).districtId().equals(UUID.fromString(restart.get(3))), "Gauge and District identities survive process restart");
                    check(population.data().linked(a).contains(b) && population.data().gauge(a).range() == 8, "Links and settings survive restart");
                    check(population.data().gauge(a).name().equals(DISTRICT_NAME), "Edited District name survives process restart");
                    check(population.data().gauge(a).pos().equals(RELOCATED) && population.data().atAll(DISTRICT).size() == 3
                            && serverPlayer().serverLevel().getBlockEntity(RELOCATED) instanceof CitizenGaugeBlockEntity moved && moved.panelCount() == 1, "Relocated panel and siblings survive process restart");
                    var state = population.data().citizen(member); check(state != null && state.independent() && state.force() && state.districtId() == null, "Registry flags survive restart");
                    check(population.chainCount() == 0 && population.incoming(a) == 0, "No stale transfer reservations after restart"); saved = true;
                }); } }
                case 81 -> { if (saved) { stage = 99; LogUtils.getLogger().info("CITIZEN_GAUGE_CLIENT_PASSED: processRestart, gaugeId, districtId, links, settings, citizenRegistry, noStaleReservations"); mc.stop(); } }
                default -> { }
            }
        } catch (Throwable error) { stage = 99; LogUtils.getLogger().error("CITIZEN_GAUGE_CLIENT_FAILED", error); mc.stop(); }
    }
    private static void checkResyncThrottle(net.minecraft.server.level.ServerPlayer player) {
        var metrics = CitizenPopulationManager.get(player.serverLevel()).metrics();
        GaugeViewSynchronization.clear(player.getUUID()); metrics.start();
        try {
            GaugeViewSynchronization.resync(player); check(metrics.packetCount() == 1, "First resync sends one measured snapshot");
            GaugeViewSynchronization.stopObserving(player.getUUID()); GaugeViewSynchronization.resync(player);
            check(metrics.packetCount() == 1, "Holding-item toggles cannot bypass the resync rate limit");
        } finally { metrics.stop(); }
    }
    private static void advance(int next) { stage = next; ticks = 0; }
    private static void checkFlowContentLayout(CitizenGaugeScreen screen) throws ReflectiveOperationException {
        var contentX = CitizenGaugeScreen.class.getDeclaredMethod("flowContentX", net.minecraft.network.chat.Component.class);
        contentX.setAccessible(true);
        var font = Minecraft.getInstance().font;
        for (var label : java.util.List.of(net.minecraft.network.chat.Component.translatable("screen.createapparel.gauge.source"),
                net.minecraft.network.chat.Component.translatable("screen.createapparel.gauge.outflow"),
                net.minecraft.network.chat.Component.literal("流入ゲージ"), net.minecraft.network.chat.Component.literal("流出ゲージ"))) {
            int iconX = (int) contentX.invoke(screen, label), textX = iconX + 20, right = textX + font.width(label);
            check(textX - (iconX + 16) == 4, "Flow icon and name have a fixed four-pixel gap");
            check(iconX >= 60 && right <= screen.getXSize() - 12, "Both localized flow labels fit beside the two buttons");
            check(Math.abs(iconX + right - (60 + screen.getXSize() - 12)) <= 1, "Flow icon and name are centered as a single group");
        }
    }
    private static void checkDistrictIcon(CitizenGaugeMenu menu, BlockPos pos) {
        var mc = Minecraft.getInstance(); var id = menu.info().getUUID("District");
        var status = ((CitizenGaugeBlockEntity) mc.level.getBlockEntity(pos)).districtStatus(menu.info().getInt("Slot"));
        check(status != null && id.equals(status.districtId()), "World and GUI use the same District UUID for their icon");
        check(com.rexhub.createapparel.client.citizen.DistrictGaugeIcon.color(status.districtId()) == com.rexhub.createapparel.client.citizen.DistrictGaugeIcon.color(id),
                "World and GUI tint use the same UUID-derived RGB");
        check(mc.getResourceManager().getResource(com.rexhub.createapparel.client.citizen.DistrictGaugeIcon.TEXTURE).isPresent(), "District item texture is loaded");
    }
    private static void checkChildWalking() {
        var mc = Minecraft.getInstance(); var citizen = ModEntities.CITIZEN.get().create(mc.level);
        check(citizen != null, "Create isolated animation sample");
        var model = new com.rexhub.createapparel.client.model.CitizenModel(com.rexhub.createapparel.client.model.CitizenModel.createBodyLayer().bakeRoot());
        citizen.walkAnimation.update(.35F, 1);
        float phase = citizen.walkAnimation.position(1), amount = citizen.walkAnimation.speed(1);
        for (var age : CitizenAge.values()) {
            citizen.citizenState().setAge(age); model.apply(citizen, 1);
            float swing = phase * (age == CitizenAge.CHILD ? 1.5F : 1);
            check(Math.abs(model.rightLeg.xRot - net.minecraft.util.Mth.cos(swing * .6662F) * 1.4F * amount) < .00001F,
                    "Actual model applies age-correct walking phase without changing amplitude: " + age);
        }
    }
    private static net.minecraft.server.level.ServerPlayer serverPlayer() { return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID()); }
    private static void server(Runnable action) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { action.run(); } catch (Throwable error) { failure = error; } }); }
    private static void use(BlockPos pos) {
        var mc = Minecraft.getInstance(); var hit = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(
                com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(mc.level.getBlockState(pos), 0)), net.minecraft.core.Direction.NORTH, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void screenshot(ScreenEvent.Render.Post event) {
        if (capture == null) return;
        try {
            if (!(event.getScreen() instanceof RouteMarkerScreen) && net.minecraftforge.fml.ModList.get().isLoaded("jei"))
                com.rexhub.createapparel.compat.jei.GaugeJeiClientChecks.assertHidden(event.getScreen());
            if (capture.startsWith("gauge-client-tooltip-")) {
                var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get());
                stack.getOrCreateTag().putLong("GaugeCenter", new BlockPos(4, 70, 6).asLong());
                var minecraft = Minecraft.getInstance();
                var lines = capture.endsWith("-shift.png") ? com.rexhub.createapparel.client.guide.GaugeTooltipClientChecks.expanded(stack)
                        : stack.getTooltipLines(minecraft.player, net.minecraft.world.item.TooltipFlag.NORMAL);
                event.getGuiGraphics().renderComponentTooltip(minecraft.font, lines, 40, 40);
            }
            event.getGuiGraphics().flush();
            try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build", capture)); }
            if (capture.endsWith("-district-long.png") && event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
                int y = screen instanceof CitizenScreen ? 196 : 132;
                screen.render(event.getGuiGraphics(), screen.getGuiLeft() + 50, screen.getGuiTop() + y, 0);
                event.getGuiGraphics().flush();
                try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                    image.writeToFile(Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build", capture.replace(".png", "-tooltip.png")));
                }
            }
            if (capture.equals("gauge-client-district-edited.png") && event.getScreen() instanceof CitizenGaugeScreen screen) {
                screen.render(event.getGuiGraphics(), screen.getGuiLeft() + CitizenGaugeScreen.DISTRICT_WIDTH - 47,
                        screen.getGuiTop() + CitizenGaugeScreen.DISTRICT_HEIGHT - 25, 0);
                event.getGuiGraphics().flush();
                try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                    image.writeToFile(Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build/gauge-client-copy-tooltip.png"));
                }
            }
            if (capture.equals("gauge-client-district.png") && event.getScreen() instanceof CitizenGaugeScreen screen) {
                screen.render(event.getGuiGraphics(), screen.getGuiLeft() + 26, screen.getGuiTop() + 102, 0);
                event.getGuiGraphics().flush();
                try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                    image.writeToFile(Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build/gauge-client-diagnostics.png"));
                }
            }
            capture = null;
        } catch (Exception error) { failure = error; }
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void worldScreenshot(RenderGuiEvent.Post event) {
        if (worldCapture == null) return;
        try {
            event.getGuiGraphics().flush();
            try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build", worldCapture)); }
            worldCapture = null;
        } catch (Exception error) { failure = error; }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
