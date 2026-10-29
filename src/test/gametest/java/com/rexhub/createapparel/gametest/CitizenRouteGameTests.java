package com.rexhub.createapparel.gametest;

import com.mojang.authlib.GameProfile;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.CitizenRouteMarkerBlock;
import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.init.*;
import com.rexhub.createapparel.menu.RouteMarkerMenu;
import com.rexhub.createapparel.network.RouteMarkerActionPacket;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenRouteGameTests {
    private CitizenRouteGameTests() { }

    private static CitizenRouteManager manager(GameTestHelper helper) { return CitizenRouteManager.get(helper.getLevel()); }
    private static void floor(GameTestHelper helper) {
        for (int x = 1; x < 38; x++) for (int z = 1; z < 38; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }
    private static RouteMarker marker(GameTestHelper helper, int x, int z, RouteMarkerType type) {
        var pos = helper.absolutePos(new BlockPos(x, 1, z));
        helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var block = switch (type) { case NORMAL -> ModBlocks.CITIZEN_ROUTE_MARKER; case GATEWAY -> ModBlocks.GATEWAY_ROUTE_MARKER; case CROSSING -> ModBlocks.CROSSING_ROUTE_MARKER; };
        helper.getLevel().setBlock(pos, block.get().defaultBlockState(), 3);
        var entity = (CitizenRouteMarkerBlockEntity) helper.getLevel().getBlockEntity(pos);
        if (entity == null) throw new IllegalStateException("Missing marker BE");
        var node = manager(helper).register(entity).withRegion(0.5, 1);
        manager(helper).data().put(node);
        return node;
    }
    private static Citizen citizen(GameTestHelper helper, Vec3 position, boolean spawn) {
        var citizen = ModEntities.CITIZEN.get().create(helper.getLevel());
        if (citizen == null) throw new IllegalStateException("Missing Citizen");
        citizen.setPos(position); citizen.setOnGround(true);
        if (spawn) {
            helper.getLevel().addFreshEntity(citizen);
            CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 1000);
        }
        return citizen;
    }
    private static ServerPlayer player(GameTestHelper helper, Vec3 position) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "route-test"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        player.setGameMode(GameType.SURVIVAL); player.setPos(position); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        return player;
    }
    private static RouteMarkerActionPacket packet(RouteMarkerMenu menu, long version, RouteMarkerActionPacket.Action action, String value, double radius, double tolerance) {
        return new RouteMarkerActionPacket(menu.containerId, menu.markerId(), version, action, radius, tolerance, value, BlockPos.ZERO, "", "", "", false, 0);
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void markerIsInvisibleNoncollidingPickableAndNavigationPassable(GameTestHelper helper) {
        floor(helper); var node = marker(helper, 10, 10, RouteMarkerType.NORMAL); var level = helper.getLevel(); var state = level.getBlockState(node.pos());
        helper.assertTrue(state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE && !state.canOcclude(), "Invisible and nonoccluding");
        helper.assertTrue(state.getCollisionShape(level, node.pos()).isEmpty() && !state.getShape(level, node.pos()).isEmpty(), "Guide is pickable without collision");
        helper.assertTrue(state.isPathfindable(level, node.pos(), net.minecraft.world.level.pathfinder.PathComputationType.LAND), "Marker permits standard Navigation");
        var citizen = citizen(helper, node.center().add(-3, 0, 0), false);
        var path = citizen.getNavigation().createPath(node.pos().east(3), 0);
        helper.assertTrue(path != null && path.canReach(), "Vanilla path passes through marker");
        var saved = ((CitizenRouteMarkerBlockEntity) level.getBlockEntity(node.pos())).saveWithFullMetadata();
        var restored = new CitizenRouteMarkerBlockEntity(node.pos(), state); restored.load(saved);
        helper.assertTrue(node.id().equals(restored.markerId()), "BE keeps persistent UUID");
        var viewer = player(helper, node.center());
        helper.assertTrue(!state.getShape(level, node.pos(), net.minecraft.world.phys.shapes.CollisionContext.of(viewer)).isEmpty(), "Phone permits native pick");
        viewer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(state.getShape(level, node.pos(), net.minecraft.world.phys.shapes.CollisionContext.of(viewer)).isEmpty(), "Without phone there is no vanilla selection outline");
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void everyTypeTransitionKeepsIdsAndReclassifiesCrossingEdges(GameTestHelper helper) {
        var a = marker(helper, 10, 10, RouteMarkerType.NORMAL); var b = marker(helper, 18, 10, RouteMarkerType.CROSSING); var manager = manager(helper);
        manager.data().link(a.id(), b.id(), false);
        var edgeId = manager.data().connected(a.id()).get(0).id();
        for (var type : List.of(RouteMarkerType.GATEWAY, RouteMarkerType.CROSSING, RouteMarkerType.NORMAL, RouteMarkerType.CROSSING, RouteMarkerType.GATEWAY, RouteMarkerType.NORMAL)) {
            long before = manager.data().version(); manager.changeType(a.id(), type);
            helper.assertTrue(manager.loaded(manager.data().marker(a.id())) && manager.data().marker(a.id()).type() == type, "Type and UUID preserved");
            helper.assertTrue(manager.data().connected(a.id()).size() == 1 && manager.data().connected(b.id()).size() == 1, "Single connection retained");
            helper.assertTrue(manager.data().edge(edgeId).conditional() == (type == RouteMarkerType.CROSSING), "Crossing automatically follows both endpoints");
            helper.assertTrue(manager.data().version() == before + 1, "Atomic type transition changes version once");
            helper.assertTrue(((CitizenRouteMarkerBlockEntity) helper.getLevel().getBlockEntity(a.pos())).markerId().equals(a.id()), "Same BE identity");
        }
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void groundBreakRemovesMarkerEdgesPairAndIndex(GameTestHelper helper) {
        var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var b = marker(helper, 18, 10, RouteMarkerType.CROSSING); var data = manager(helper).data();
        data.link(a.id(), b.id(), false); data.link(a.id(), b.id(), true); long version = data.version();
        helper.getLevel().removeBlock(a.pos().below(), false);
        helper.assertTrue(helper.getLevel().isEmptyBlock(a.pos()) && data.marker(a.id()) == null && data.connected(b.id()).isEmpty(), "Support removal cleans every edge");
        helper.assertTrue(data.version() > version && data.at(a.pos()) == null, "Version and spatial index updated");
        var recreated = marker(helper, 10, 10, RouteMarkerType.NORMAL); helper.assertTrue(!recreated.id().equals(a.id()), "Reinstallation has fresh UUID"); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void supportRejectsFluidsBottomSlabAndFloatingSpace(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(10, 1, 10));
        for (var block : List.of(Blocks.AIR, Blocks.WATER, Blocks.LAVA)) {
            helper.getLevel().setBlock(pos.below(), block.defaultBlockState(), 3);
            helper.assertTrue(!CitizenRouteMarkerBlock.supported(helper.getLevel(), pos), "No floating or liquid support");
        }
        helper.getLevel().setBlock(pos.below(), Blocks.STONE_SLAB.defaultBlockState(), 3);
        helper.assertTrue(!CitizenRouteMarkerBlock.supported(helper.getLevel(), pos), "Bottom slab top lies below placement plane");
        helper.getLevel().setBlock(pos.below(), Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP), 3);
        helper.assertTrue(CitizenRouteMarkerBlock.supported(helper.getLevel(), pos), "Top slab provides full support plane"); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void phonePlacementRejectsOtherFacesAndProtectedPlacement(GameTestHelper helper) {
        floor(helper); var ground = helper.absolutePos(new BlockPos(10, 0, 10)); var player = player(helper, Vec3.atBottomCenterOf(ground).add(0, 1, -2));
        player.setShiftKeyDown(true); player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(ground).add(0, 0.5, 0));
        var hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
        for (var face : List.of(Direction.NORTH, Direction.DOWN)) helper.assertTrue(!SmartPhoneRouteEditor.place(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit.getLocation(), face, ground, false)), "Non-top hit rejected");
        player.setPos(Vec3.atCenterOf(ground).add(0, -player.getEyeHeight(), -2));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(ground));
        helper.assertTrue(!SmartPhoneRouteEditor.place(player, InteractionHand.MAIN_HAND, hit), "Forged top-face packet with actual side aim rejected");
        player.setPos(Vec3.atBottomCenterOf(ground).add(0, 1, -2));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, hit.getLocation());
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(cancel);
        try { helper.assertTrue(!SmartPhoneRouteEditor.place(player, InteractionHand.MAIN_HAND, hit), "Protection event rejects placement"); }
        finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
        helper.assertTrue(helper.getLevel().isEmptyBlock(ground.above()) && manager(helper).data().at(ground.above()) == null, "Rejected placement leaves no marker");
        helper.assertTrue(SmartPhoneRouteEditor.place(player, InteractionHand.MAIN_HAND, hit), "Top hit places directly above ground");
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && manager(helper).data().at(ground.above()) != null, "Phone not consumed; registry updated");
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void phoneSelectionsArePerPlayerCancelDuplicateAndHandChanges(GameTestHelper helper) {
        var a = marker(helper, 10, 10, RouteMarkerType.NORMAL); var b = marker(helper, 14, 10, RouteMarkerType.NORMAL);
        var p = player(helper, a.center().add(0, 0, -2)); var q = player(helper, b.center().add(0, 0, -2));
        SmartPhoneRouteEditor.clickMarker(p, a, InteractionHand.MAIN_HAND); SmartPhoneRouteEditor.clickMarker(q, b, InteractionHand.MAIN_HAND);
        helper.assertTrue(a.id().equals(SmartPhoneRouteEditor.selected(p)) && b.id().equals(SmartPhoneRouteEditor.selected(q)), "Two players have independent starts");
        SmartPhoneRouteEditor.clickMarker(p, b, InteractionHand.MAIN_HAND);
        helper.assertTrue(manager(helper).data().connected(a.id()).size() == 1 && SmartPhoneRouteEditor.selected(p) == null, "Two clicks create one bidirectional edge");
        SmartPhoneRouteEditor.clickMarker(q, a, InteractionHand.MAIN_HAND);
        helper.assertTrue(manager(helper).data().connected(a.id()).size() == 1 && b.id().equals(SmartPhoneRouteEditor.selected(q)), "Duplicate preserves selection and existing edge");
        SmartPhoneRouteEditor.clickMarker(q, b, InteractionHand.MAIN_HAND); helper.assertTrue(SmartPhoneRouteEditor.selected(q) == null, "Same marker cancels");
        SmartPhoneRouteEditor.clickMarker(p, a, InteractionHand.MAIN_HAND); p.getInventory().selected = 1; SmartPhoneRouteEditor.tick(p);
        helper.assertTrue(SmartPhoneRouteEditor.selected(p) == null, "Hotbar change clears pending start");
        p.getInventory().selected = 0; p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        SmartPhoneRouteEditor.clickMarker(p, a, InteractionHand.MAIN_HAND);
        var mainPhone = p.getMainHandItem(); var offPhone = p.getOffhandItem();
        p.setItemInHand(InteractionHand.MAIN_HAND, offPhone); p.setItemInHand(InteractionHand.OFF_HAND, mainPhone); SmartPhoneRouteEditor.tick(p);
        helper.assertTrue(SmartPhoneRouteEditor.selected(p) == null, "Swapping two phones clears selection even when both hands still hold a phone");
        SmartPhoneRouteEditor.clickMarker(p, a, InteractionHand.MAIN_HAND);
        var origin = p.serverLevel(); var otherLevel = origin.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (otherLevel == null) throw new IllegalStateException("Missing Nether");
        p.setServerLevel(otherLevel); SmartPhoneRouteEditor.tick(p);
        helper.assertTrue(SmartPhoneRouteEditor.selected(p) == null, "Dimension change clears pending start without loading target chunks");
        p.setServerLevel(origin);
        SmartPhoneRouteEditor.logout(p.getUUID()); SmartPhoneRouteEditor.logout(q.getUUID()); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void markerSneakOpensOnlyMenuAndTwoPhonesDoNotDoubleFire(GameTestHelper helper) {
        var a = marker(helper, 10, 10, RouteMarkerType.NORMAL); var p = player(helper, a.center().add(0, 0, -2));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SMART_PHONE.get())); p.setShiftKeyDown(true);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, a.center().add(0, 0.125, 0));
        var hit = (BlockHitResult) p.pick(p.getBlockReach(), 0, false);
        var first = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, a.pos(), hit);
        SmartPhoneRouteEditor.interact(first);
        helper.assertTrue(first.isCanceled() && p.containerMenu instanceof RouteMarkerMenu, "Sneak opens settings Menu");
        var menu = p.containerMenu;
        SmartPhoneRouteEditor.interact(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.OFF_HAND, a.pos(), hit));
        helper.assertTrue(p.containerMenu == menu && manager(helper).data().at(a.pos().above()) == null, "Offhand does not reopen or place a duplicate");
        helper.assertTrue(SmartPhoneRouteEditor.selected(p) == null, "Settings do not initiate a link");
        SmartPhoneRouteEditor.logout(p.getUUID()); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void wallOcclusionPreventsMarkerInteraction(GameTestHelper helper) {
        var a = marker(helper, 10, 10, RouteMarkerType.NORMAL); var p = player(helper, a.center().add(0, 0, -3));
        p.lookAt(EntityAnchorArgument.Anchor.EYES, a.center().add(0, 0.125, 0));
        helper.getLevel().setBlock(a.pos().north(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(a.pos().north().above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!RouteMarkerPermissions.aimingAt(p, a.pos()), "Native raycast stops at the wall");
        SmartPhoneRouteEditor.interact(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, a.pos(),
                new BlockHitResult(a.center(), Direction.UP, a.pos(), false)));
        helper.assertTrue(SmartPhoneRouteEditor.selected(p) == null, "Forged block interaction cannot pass through wall");
        SmartPhoneRouteEditor.logout(p.getUUID()); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void offhandPhoneKeepsViewAndCitizenMenuButCannotEditRoutes(GameTestHelper helper) {
        floor(helper);
        var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var b = marker(helper, 14, 10, RouteMarkerType.CROSSING);
        var data = manager(helper).data(); data.link(a.id(), b.id(), true);
        var p = player(helper, a.center().add(0, 0, -2));
        var menu = new RouteMarkerMenu(7, p.getInventory(), a.id(), a.pos()); p.containerMenu = menu;
        SmartPhoneRouteEditor.clickMarker(p, a, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.OFF_HAND, p.getMainHandItem()); p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        SmartPhoneRouteEditor.tick(p);
        helper.assertTrue(RouteMarkerPermissions.holdingPhone(p) && !RouteMarkerPermissions.canEditRoutes(p, InteractionHand.OFF_HAND), "Offhand retains viewing only");
        helper.assertTrue(SmartPhoneRouteEditor.selected(p) == null && !menu.stillValid(p), "Moving phone to offhand clears link and invalidates marker menu");
        var attack = net.minecraftforge.common.ForgeHooks.onLeftClickBlock(p, a.pos(), Direction.NORTH,
                net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        helper.assertTrue(attack.isCanceled(), "Offhand cannot mine a visible marker");
        for (var gameMode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
            p.setGameMode(gameMode);
            var blockBreak = new BlockEvent.BreakEvent(p.level(), a.pos(), p.level().getBlockState(a.pos()), p);
            MinecraftForge.EVENT_BUS.post(blockBreak);
            helper.assertTrue(blockBreak.isCanceled(), "Offhand rejects direct marker destruction in " + gameMode);
        }
        p.setGameMode(GameType.SURVIVAL);
        var groundBreak = new BlockEvent.BreakEvent(p.level(), a.pos().below(), p.level().getBlockState(a.pos().below()), p);
        MinecraftForge.EVENT_BUS.post(groundBreak);
        helper.assertTrue(!groundBreak.isCanceled(), "Offhand read-only restriction does not block normal terrain edits");
        long version = data.version();
        menu.apply(p, packet(menu, version, RouteMarkerActionPacket.Action.REGION, "", 4, 2));
        SmartPhoneRouteEditor.clickMarker(p, a, InteractionHand.OFF_HAND);
        helper.assertTrue(SmartPhoneRouteEditor.selected(p) == null, "Offhand cannot select a marker");
        p.containerMenu = p.inventoryMenu;
        p.lookAt(EntityAnchorArgument.Anchor.EYES, a.center().add(0, 0.125, 0));
        for (boolean sneak : List.of(false, true)) {
            p.setShiftKeyDown(sneak);
            var hit = new BlockHitResult(a.center(), Direction.NORTH, a.pos(), false);
            SmartPhoneRouteEditor.interact(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.OFF_HAND, a.pos(), hit));
            helper.assertTrue(p.containerMenu == p.inventoryMenu && SmartPhoneRouteEditor.selected(p) == null, "Offhand cannot open marker settings or link");
        }
        var ground = a.pos().south(2).below();
        helper.assertTrue(!SmartPhoneRouteEditor.place(p, InteractionHand.OFF_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false)), "Offhand cannot place markers");
        helper.assertTrue(!SmartPhoneRouteEditor.connectControl(p, a.pos().north(2), InteractionHand.OFF_HAND), "Offhand cannot register control blocks");
        SmartPhoneRouteEditor.deleteEdge(p, data.connected(a.id()).get(0).id(), InteractionHand.OFF_HAND);
        helper.assertTrue(data.version() == version && data.connected(a.id()).size() == 1, "Offhand rejects stale settings and direct edge removal without graph changes");
        var citizen = citizen(helper, a.center(), true);
        ((com.rexhub.createapparel.item.SmartPhoneItem) p.getOffhandItem().getItem())
                .interact(new PlayerInteractEvent.EntityInteract(p, InteractionHand.OFF_HAND, citizen));
        helper.assertTrue(p.containerMenu instanceof com.rexhub.createapparel.menu.CitizenMenu, "Offhand still opens Citizen menu");
        citizen.discard(); SmartPhoneRouteEditor.logout(p.getUUID()); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes", timeoutTicks = 60)
    public static void citizenMenuRefreshesAndClearsDestinationWithoutChangingPersistentState(GameTestHelper helper) {
        floor(helper); var pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(20, 1, 20)));
        var citizen = citizen(helper, pos, true); citizen.setNoAi(true);
        var p = player(helper, pos.add(0, 0, -2));
        var menu = new com.rexhub.createapparel.menu.CitizenMenu(8, p.getInventory(), citizen); p.containerMenu = menu;
        var saved = citizen.citizenState().serializeNBT().copy();
        var destination = pos.add(5, 0, 0);
        citizen.routeMovement().requestDestination(destination, CitizenPathfindingScheduler.Priority.PLAYER);
        menu.broadcastChanges();
        helper.assertTrue(BlockPos.containing(destination).equals(menu.destination()), "Menu includes the server destination intent");
        citizen.routeMovement().reset();
        helper.onEachTick(menu::broadcastChanges);
        helper.runAtTickTime(25, () -> {
            menu.broadcastChanges();
            helper.assertTrue(menu.destination() == null, "Menu clears a destination after reset");
            helper.assertTrue(saved.equals(citizen.citizenState().serializeNBT()), "Destination display does not write persistent state");
            citizen.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void menuValidatesStaleVersionIdentityBoundsDistanceAndGameMode(GameTestHelper helper) {
        var a = marker(helper, 10, 10, RouteMarkerType.NORMAL); var p = player(helper, a.center().add(0, 0, -2)); var q = player(helper, a.center().add(0, 0, -2));
        var menu = new RouteMarkerMenu(19, p.getInventory(), a.id(), a.pos()); p.containerMenu = menu; var data = manager(helper).data();
        long version = data.version();
        packet(menu, version, RouteMarkerActionPacket.Action.REGION, "", Double.NaN, 1).handle(p);
        helper.assertTrue(data.version() == version, "NaN rejected");
        packet(menu, version, RouteMarkerActionPacket.Action.REGION, "", 4, 2).handle(q);
        helper.assertTrue(data.version() == version, "Wrong owner cannot edit");
        packet(menu, version, RouteMarkerActionPacket.Action.REGION, "", 4, 2).handle(p);
        helper.assertTrue(data.marker(a.id()).horizontalRadius() == 4, "Valid region update succeeds");
        packet(menu, version, RouteMarkerActionPacket.Action.REGION, "", 6, 3).handle(p);
        helper.assertTrue(data.marker(a.id()).horizontalRadius() == 4, "Old version rejected");
        var otherMenu = new RouteMarkerMenu(19, q.getInventory(), a.id(), a.pos()); q.containerMenu = otherMenu;
        packet(otherMenu, version, RouteMarkerActionPacket.Action.REGION, "", 7, 3).handle(q);
        helper.assertTrue(data.marker(a.id()).horizontalRadius() == 4, "Second player with a stale valid Menu cannot overwrite updated state");
        long current = data.version(); var origin = p.serverLevel(); var otherLevel = origin.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (otherLevel == null) throw new IllegalStateException("Missing Nether");
        p.setServerLevel(otherLevel); packet(menu, current, RouteMarkerActionPacket.Action.REGION, "", 7, 3).handle(p);
        helper.assertTrue(data.version() == current && !menu.stillValid(p), "Origin dimension is required by Menu updates"); p.setServerLevel(origin);
        Consumer<BlockEvent.BreakEvent> protectedRegion = event -> { if (event.getPlayer() == p && event.getPos().equals(a.pos())) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(protectedRegion);
        try { packet(menu, current, RouteMarkerActionPacket.Action.DELETE, "CONFIRM", 0, 0).handle(p); }
        finally { MinecraftForge.EVENT_BUS.unregister(protectedRegion); }
        helper.assertTrue(data.marker(a.id()) != null && data.version() == current, "GUI deletion respects protection events");
        p.setGameMode(GameType.SPECTATOR); packet(menu, current, RouteMarkerActionPacket.Action.DELETE, "CONFIRM", 0, 0).handle(p);
        helper.assertTrue(data.marker(a.id()) != null, "Spectator cannot delete"); p.setGameMode(GameType.SURVIVAL); p.setPos(a.center().add(20, 0, 0));
        packet(menu, current, RouteMarkerActionPacket.Action.REGION, "", 8, 3).handle(p);
        helper.assertTrue(data.version() == current && !menu.stillValid(p), "Distance invalidates menu"); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void guiPairsEdgesAndDeletionCleanAllReferences(GameTestHelper helper) {
        var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var b = marker(helper, 14, 10, RouteMarkerType.CROSSING);
        var p = player(helper, a.center().add(0, 0, -2)); var menu = new RouteMarkerMenu(19, p.getInventory(), a.id(), a.pos()); p.containerMenu = menu;
        var data = manager(helper).data(); data.link(a.id(), b.id(), false);
        packet(menu, data.version(), RouteMarkerActionPacket.Action.PAIR, b.id().toString(), 0, 0).handle(p);
        helper.assertTrue(data.connected(a.id()).size() == 1 && data.connected(a.id()).get(0).conditional(), "Phone link is already crossing; GUI does not duplicate it");
        packet(menu, data.version(), RouteMarkerActionPacket.Action.UNPAIR, "", 0, 0).handle(p);
        helper.assertTrue(data.connected(b.id()).isEmpty(), "Legacy unpair cleans crossing connections from both endpoints");
        data.link(a.id(), b.id(), false);
        var edge = data.connected(a.id()).get(0); packet(menu, data.version(), RouteMarkerActionPacket.Action.EDGE, edge.id().toString(), 0, 0).handle(p);
        helper.assertTrue(data.connected(a.id()).isEmpty(), "GUI explicitly deletes selected normal edge");
        data.link(a.id(), b.id(), false); packet(menu, data.version(), RouteMarkerActionPacket.Action.DELETE, "CONFIRM", 0, 0).handle(p);
        helper.assertTrue(data.marker(a.id()) == null && data.connected(b.id()).isEmpty() && !menu.stillValid(p), "Deletion clears every reference and invalidates editor"); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void automaticCrossingBranchesRespectEveryProtectedEndpoint(GameTestHelper helper) {
        var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var b = marker(helper, 14, 10, RouteMarkerType.CROSSING);
        var c = marker(helper, 10, 14, RouteMarkerType.CROSSING); var data = manager(helper).data();
        data.link(a.id(), b.id(), false); data.link(a.id(), c.id(), false); data.link(b.id(), c.id(), false);
        helper.assertTrue(data.connected(a.id()).size() == 2 && data.connected(a.id()).stream().allMatch(RouteEdge::conditional), "Both branch links become crossing without explicit pairs");
        var p = player(helper, a.center().add(0, 0, -2)); var menu = new RouteMarkerMenu(19, p.getInventory(), a.id(), a.pos()); p.containerMenu = menu;
        long version = data.version();
        Consumer<BlockEvent.BreakEvent> protectedEnd = event -> { if (event.getPlayer() == p && event.getPos().equals(c.pos())) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(protectedEnd);
        try { packet(menu, version, RouteMarkerActionPacket.Action.UNPAIR, "", 0, 0).handle(p); }
        finally { MinecraftForge.EVENT_BUS.unregister(protectedEnd); }
        helper.assertTrue(data.version() == version && data.connected(a.id()).size() == 2, "Legacy clear cannot bypass any protected endpoint or partially mutate the graph");
        packet(menu, version, RouteMarkerActionPacket.Action.UNPAIR, "", 0, 0).handle(p);
        helper.assertTrue(data.connected(a.id()).isEmpty() && data.connected(b.id()).size() == 1 && data.version() == version + 1, "Allowed clear removes only source crossings atomically");
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void directEdgeDeletionValidatesAimSneakPhoneOcclusionAndBothEndpoints(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.NORMAL); var b = marker(helper, 18, 10, RouteMarkerType.NORMAL);
        var data = manager(helper).data(); data.link(a.id(), b.id(), false); var edge = data.connected(a.id()).get(0);
        var mid = a.center().lerp(b.center(), 0.5).add(0, 0.15, 0);
        var p = player(helper, mid.add(0, -0.15, -2)); p.setShiftKeyDown(true); p.lookAt(EntityAnchorArgument.Anchor.EYES, mid);
        helper.assertTrue(SmartPhoneRouteEditor.targetedEdge(p) != null && SmartPhoneRouteEditor.targetedEdge(p).id().equals(edge.id()), "Shared ray targeting selects the visible edge");
        new com.rexhub.createapparel.network.RouteEdgeActionPacket(UUID.randomUUID(), InteractionHand.MAIN_HAND).handle(p);
        helper.assertTrue(data.edge(edge.id()) != null, "Forged edge identity cannot delete another edge"); SmartPhoneRouteEditor.logout(p.getUUID());
        p.setShiftKeyDown(false); new com.rexhub.createapparel.network.RouteEdgeActionPacket(edge.id(), InteractionHand.MAIN_HAND).handle(p);
        helper.assertTrue(data.edge(edge.id()) != null, "Sneak is required"); p.setShiftKeyDown(true);
        Consumer<BlockEvent.BreakEvent> protect = event -> { if (event.getPlayer() == p && event.getPos().equals(b.pos())) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(protect);
        try { new com.rexhub.createapparel.network.RouteEdgeActionPacket(edge.id(), InteractionHand.MAIN_HAND).handle(p); }
        finally { MinecraftForge.EVENT_BUS.unregister(protect); }
        helper.assertTrue(data.edge(edge.id()) != null, "Protection on the remote endpoint prevents deletion"); SmartPhoneRouteEditor.logout(p.getUUID());
        var wall = helper.absolutePos(new BlockPos(14, 1, 9));
        helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3); helper.getLevel().setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        new com.rexhub.createapparel.network.RouteEdgeActionPacket(edge.id(), InteractionHand.MAIN_HAND).handle(p);
        helper.assertTrue(data.edge(edge.id()) != null && SmartPhoneRouteEditor.targetedEdge(p) == null, "Wall occlusion rejects forged edge deletion"); SmartPhoneRouteEditor.logout(p.getUUID());
        helper.getLevel().removeBlock(wall, false); helper.getLevel().removeBlock(wall.above(), false);
        p.setPos(a.center().add(0, 0, -2)); p.lookAt(EntityAnchorArgument.Anchor.EYES, a.center().add(0, 0.125, 0));
        helper.assertTrue(SmartPhoneRouteEditor.targetedEdge(p) == null, "Marker GUI has priority over overlapping edge targeting");
        p.setPos(mid.add(0, -0.15, -2)); p.lookAt(EntityAnchorArgument.Anchor.EYES, mid);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        new com.rexhub.createapparel.network.RouteEdgeActionPacket(edge.id(), InteractionHand.MAIN_HAND).handle(p);
        helper.assertTrue(data.edge(edge.id()) != null, "A phone in the requested hand is required");
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        long version = data.version(); new com.rexhub.createapparel.network.RouteEdgeActionPacket(edge.id(), InteractionHand.OFF_HAND).handle(p);
        helper.assertTrue(data.edge(edge.id()) != null && data.version() == version, "Offhand direct deletion is rejected");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        new com.rexhub.createapparel.network.RouteEdgeActionPacket(edge.id(), InteractionHand.MAIN_HAND).handle(p);
        helper.assertTrue(data.edge(edge.id()) == null && data.connected(a.id()).isEmpty() && data.connected(b.id()).isEmpty() && data.version() == version + 1, "Main-hand deletion removes both references atomically");
        helper.assertTrue(data.at(BlockPos.containing(mid)) == null, "Edge deletion does not also place a marker");
        SmartPhoneRouteEditor.logout(p.getUUID()); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void immediateEditsAcknowledgeRejectReplaysAndDeleteWithoutConfirmation(GameTestHelper helper) {
        var node = marker(helper, 10, 10, RouteMarkerType.NORMAL); var p = player(helper, node.center().add(0, 0, -2));
        var menu = new RouteMarkerMenu(19, p.getInventory(), node.id(), node.pos()); p.containerMenu = menu; var data = manager(helper).data();
        var first = new RouteMarkerActionPacket(19, node.id(), data.version(), RouteMarkerActionPacket.Action.REGION, 3, 2, "", BlockPos.ZERO, "", "", "", false, 0, 1);
        first.handle(p);
        helper.assertTrue(menu.info().getInt("EditSequence") == 1 && menu.info().getBoolean("EditAccepted") && data.marker(node.id()).horizontalRadius() == 3, "Immediate edit is acknowledged with authoritative data");
        long version = data.version(); first.handle(p);
        helper.assertTrue(data.version() == version && menu.info().getInt("EditSequence") == 1, "Replayed edit cannot mutate or advance the acknowledgment");
        new RouteMarkerActionPacket(19, node.id(), version, RouteMarkerActionPacket.Action.REGION, 3, 2, "", BlockPos.ZERO, "", "", "", false, 0, 2).handle(p);
        helper.assertTrue(data.version() == version && menu.info().getBoolean("EditAccepted"), "Unchanged valid values are acknowledged without an error or graph mutation");
        data.put(data.marker(node.id()).withRegion(4, 2));
        new RouteMarkerActionPacket(19, node.id(), version, RouteMarkerActionPacket.Action.REGION, 5, 2, "", BlockPos.ZERO, "", "", "", false, 0, 3).handle(p);
        helper.assertTrue(data.marker(node.id()).horizontalRadius() == 4 && menu.info().getInt("EditSequence") == 3 && !menu.info().getBoolean("EditAccepted"), "Stale automatic update cannot overwrite a concurrent edit");
        new RouteMarkerActionPacket(19, node.id(), data.version(), RouteMarkerActionPacket.Action.DELETE, 0, 0, "", BlockPos.ZERO, "", "", "", false, 0, 4).handle(p);
        helper.assertTrue(data.marker(node.id()) == null && !menu.stillValid(p), "Single delete action needs no confirmation phase"); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void regionHighlightsOnlyExposedBlockTopsInsideRadiusAndHeight(GameTestHelper helper) {
        floor(helper); var node = marker(helper, 10, 10, RouteMarkerType.NORMAL);
        var surfaces = RouteRegionSurfaces.collect(helper.getLevel(), node.pos(), 2, 0);
        helper.assertTrue(surfaces.size() == 13 && surfaces.stream().allMatch(face -> face.y() == node.pos().getY()), "Radius two selects thirteen complete block tops at marker height");
        var slab = node.pos().below().east(); helper.getLevel().setBlock(slab, Blocks.STONE_SLAB.defaultBlockState(), 3);
        helper.assertTrue(RouteRegionSurfaces.collect(helper.getLevel(), node.pos(), 2, 0).size() == 12, "Bottom slab lies outside zero vertical tolerance");
        surfaces = RouteRegionSurfaces.collect(helper.getLevel(), node.pos(), 2, 1);
        helper.assertTrue(surfaces.stream().anyMatch(face -> face.minX() == slab.getX() && face.minZ() == slab.getZ() && face.y() == node.pos().getY() - 0.5), "Slab highlight follows its actual half-block top");
        var stairs = node.pos().below().west(); helper.getLevel().setBlock(stairs, Blocks.STONE_STAIRS.defaultBlockState(), 3);
        surfaces = RouteRegionSurfaces.collect(helper.getLevel(), node.pos(), 2, 1);
        double area = surfaces.stream().filter(face -> face.minX() >= stairs.getX() && face.maxX() <= stairs.getX() + 1 && face.minZ() >= stairs.getZ() && face.maxZ() <= stairs.getZ() + 1)
                .mapToDouble(face -> (face.maxX() - face.minX()) * (face.maxZ() - face.minZ())).sum();
        helper.assertTrue(Math.abs(area - 1) < 0.000001, "Stair upper and lower exposed tops cover one block without double highlighting internal faces");
        var cover = node.pos().offset(0, 0, 2); helper.getLevel().setBlock(cover, Blocks.STONE.defaultBlockState(), 3);
        surfaces = RouteRegionSurfaces.collect(helper.getLevel(), node.pos(), 2, 0);
        helper.assertTrue(surfaces.stream().noneMatch(face -> face.minX() == cover.getX() && face.minZ() == cover.getZ()), "Covered floor is not highlighted and the raised top is outside height tolerance");
        var far = new BlockPos(2_000_000, node.pos().getY(), 2_000_000);
        helper.assertTrue(RouteRegionSurfaces.collect(helper.getLevel(), far, 2, 1).isEmpty() && !helper.getLevel().getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4), "Surface sampling never force-loads remote chunks");
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void redstoneThresholdUsesActualStrengthAndStaysClosedForUnavailableSources(GameTestHelper helper) {
        floor(helper); var node = marker(helper, 10, 10, RouteMarkerType.CROSSING); var target = node.pos().south(2);
        helper.getLevel().setBlock(target, Blocks.REDSTONE_WIRE.defaultBlockState(), 2);
        helper.getLevel().setBlock(target, Blocks.REDSTONE_WIRE.defaultBlockState().setValue(BlockStateProperties.POWER, 7), 2);
        var below = new CrossingCondition(CrossingCondition.Mode.REDSTONE, target, "", "", "", false, 8);
        var at = new CrossingCondition(CrossingCondition.Mode.REDSTONE, target, "", "", "", false, 7);
        var inverted = new CrossingCondition(CrossingCondition.Mode.REDSTONE, target, "", "", "", true, 8);
        helper.assertTrue(below.signalStrength(helper.getLevel()) == 7 && !below.open(helper.getLevel()) && at.open(helper.getLevel()), "Threshold compares the actual strength inclusively");
        helper.assertTrue(inverted.open(helper.getLevel()), "Inversion applies after the strength threshold");
        helper.getLevel().removeBlock(target, false);
        helper.assertTrue(inverted.signalStrength(helper.getLevel()) == -1 && !inverted.open(helper.getLevel()), "Missing block remains OFF even when inverted");
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void thresholdMenuValidatesBoundsAndReportsThisNodeWithoutCrossingEdges(GameTestHelper helper) {
        floor(helper); var node = marker(helper, 10, 10, RouteMarkerType.CROSSING); var target = node.pos().south(2);
        helper.getLevel().setBlock(target, Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
        var data = manager(helper).data(); data.put(node.withCondition(new CrossingCondition(CrossingCondition.Mode.REDSTONE, target, "", "", "", false)));
        var p = player(helper, node.center().add(0, 0, -2)); var menu = new RouteMarkerMenu(19, p.getInventory(), node.id(), node.pos()); p.containerMenu = menu;
        menu.sendInfo(p);
        helper.assertTrue(menu.info().getBoolean("NodeOn") && data.connected(node.id()).isEmpty(), "Node ON is independent of crossing edge count or partner state");
        long before = data.version();
        for (int threshold : new int[]{0, 16, Integer.MAX_VALUE}) {
            new RouteMarkerActionPacket(19, node.id(), data.version(), RouteMarkerActionPacket.Action.CONDITION, 0, 0, "REDSTONE", target, "", "", "", false, 0, 0, threshold).handle(p);
            helper.assertTrue(data.version() == before && data.marker(node.id()).condition().threshold() == 1, "Forged out-of-range threshold is rejected");
        }
        new RouteMarkerActionPacket(19, node.id(), data.version(), RouteMarkerActionPacket.Action.CONDITION, 0, 0, "REDSTONE", target, "", "", "", false, 0, 0, 15).handle(p);
        helper.assertTrue(data.marker(node.id()).condition().threshold() == 15 && menu.info().getBoolean("NodeOn"), "Strength fifteen meets threshold fifteen");
        helper.getLevel().setBlock(target, Blocks.REDSTONE_WIRE.defaultBlockState(), 2);
        helper.getLevel().setBlock(target, Blocks.REDSTONE_WIRE.defaultBlockState().setValue(BlockStateProperties.POWER, 14), 2); menu.sendInfo(p);
        helper.assertTrue(!menu.info().getBoolean("NodeOn"), "GUI state follows current signal without a graph version edit");
        helper.assertTrue(CitizenRouteSavedData.load(data.save(new CompoundTag())).marker(node.id()).condition().threshold() == 15, "Threshold survives graph save and reload");
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void phoneControlUsesLatestRotationEvenWhenHeadYawIsStale(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var target = a.pos().south(3);
        var p = player(helper, a.center().add(2, 0, 1)); helper.getLevel().setBlock(target, Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
        SmartPhoneRouteEditor.clickMarker(p, a, InteractionHand.MAIN_HAND); p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
        p.setYHeadRot(p.getYRot() + 90);
        helper.assertTrue(RouteMarkerPermissions.aimingAt(p, target) && SmartPhoneRouteEditor.connectControl(p, target, InteractionHand.MAIN_HAND), "Current rotation, not the previous head yaw, selects the clicked control");
        SmartPhoneRouteEditor.logout(p.getUUID()); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void phoneConnectsRedstoneControlWithoutAddingNavigableEdges(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var data = manager(helper).data();
        var target = a.pos().south(3); var p = player(helper, a.center().add(2, 0, 1));
        SmartPhoneRouteEditor.clickMarker(p, a, InteractionHand.MAIN_HAND);
        helper.getLevel().setBlock(target, Blocks.STONE.defaultBlockState(), 2); p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
        helper.assertTrue(!SmartPhoneRouteEditor.connectControl(p, target, InteractionHand.MAIN_HAND) && a.id().equals(SmartPhoneRouteEditor.selected(p)), "Non-redstone blocks preserve selection without registering a condition");
        helper.getLevel().setBlock(target, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,
                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR), 2);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atBottomCenterOf(target).add(0, 0.1, 0));
        helper.assertTrue(!SmartPhoneRouteEditor.connectControl(p, target, InteractionHand.OFF_HAND), "Connection must use the selected phone hand");
        helper.assertTrue(SmartPhoneRouteEditor.connectControl(p, target, InteractionHand.MAIN_HAND), "Selected crossing marker links to an unpowered redstone source");
        helper.assertTrue(data.marker(a.id()).condition().pos().equals(target) && data.marker(a.id()).condition().configured()
                && data.connected(a.id()).isEmpty() && SmartPhoneRouteEditor.selected(p) == null, "Control is saved separately from walkable graph edges");
        var restored = CitizenRouteSavedData.load(data.save(new CompoundTag())).marker(a.id());
        helper.assertTrue(restored.condition().equals(data.marker(a.id()).condition()), "Redstone binding survives save and load");
        helper.assertTrue(!helper.getLevel().getBlockState(target).getValue(BlockStateProperties.POWERED), "Registration never activates the source block");
        helper.assertTrue(CrossingCondition.connectionPoint(helper.getLevel(), target).y < target.getY() + 0.5,
                "Control wire attaches to the small lever shape instead of floating at the full block center");
        SmartPhoneRouteEditor.logout(p.getUUID()); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void redstoneControlValidatesProtectionOcclusionAndMarkerType(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var manager = manager(helper);
        var target = a.pos().south(3); var p = player(helper, a.center().add(2, 0, 1));
        helper.getLevel().setBlock(target, Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
        SmartPhoneRouteEditor.clickMarker(p, a, InteractionHand.MAIN_HAND); p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
        Consumer<BlockEvent.BreakEvent> protect = event -> { if (event.getPlayer() == p && event.getPos().equals(target)) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(protect);
        try { helper.assertTrue(!SmartPhoneRouteEditor.connectControl(p, target, InteractionHand.MAIN_HAND), "Protected control block cannot be bound"); }
        finally { MinecraftForge.EVENT_BUS.unregister(protect); }
        p.setPos(a.center().add(0, 0, 1)); p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
        helper.getLevel().setBlock(target.north(), Blocks.STONE.defaultBlockState(), 2);
        helper.getLevel().setBlock(target.north().above(), Blocks.STONE.defaultBlockState(), 2);
        helper.assertTrue(!SmartPhoneRouteEditor.connectControl(p, target, InteractionHand.MAIN_HAND), "Forged interaction cannot pass through a wall");
        helper.getLevel().removeBlock(target.north(), false); helper.getLevel().removeBlock(target.north().above(), false);
        manager.changeType(a.id(), RouteMarkerType.NORMAL);
        helper.assertTrue(!SmartPhoneRouteEditor.connectControl(p, target, InteractionHand.MAIN_HAND) && !manager.data().marker(a.id()).condition().configured(), "Only crossing markers accept controls");
        SmartPhoneRouteEditor.logout(p.getUUID()); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void controlWireDeletionUsesItsOwnKindAndPreservesRouteEdges(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var b = marker(helper, 18, 10, RouteMarkerType.CROSSING);
        var data = manager(helper).data(); var target = a.pos().south(4);
        helper.getLevel().setBlock(target, Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
        data.put(a.withCondition(new CrossingCondition(CrossingCondition.Mode.REDSTONE, target, "", "", "", false, 9))); data.link(a.id(), b.id(), false);
        var mid = a.center().add(0, 0.325, 2); var p = player(helper, mid.add(2, -0.325, 0)); p.setShiftKeyDown(true);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, mid);
        var picked = SmartPhoneRouteEditor.targetedEdge(p);
        helper.assertTrue(picked != null && picked.control() && picked.id().equals(a.id()), "Control wire uses the block-center endpoint for picking");
        new com.rexhub.createapparel.network.RouteEdgeActionPacket(a.id(), InteractionHand.MAIN_HAND).handle(p);
        helper.assertTrue(data.marker(a.id()).condition().configured(), "Wrong target kind cannot clear control"); SmartPhoneRouteEditor.logout(p.getUUID());
        Consumer<BlockEvent.BreakEvent> protect = event -> { if (event.getPlayer() == p && event.getPos().equals(target)) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(protect);
        try { new com.rexhub.createapparel.network.RouteEdgeActionPacket(a.id(), InteractionHand.MAIN_HAND, true).handle(p); }
        finally { MinecraftForge.EVENT_BUS.unregister(protect); }
        helper.assertTrue(data.marker(a.id()).condition().configured(), "Control deletion respects remote block protection"); SmartPhoneRouteEditor.logout(p.getUUID());
        new com.rexhub.createapparel.network.RouteEdgeActionPacket(a.id(), InteractionHand.MAIN_HAND, true).handle(p);
        helper.assertTrue(!data.marker(a.id()).condition().configured() && data.marker(a.id()).condition().threshold() == 9 && data.connected(a.id()).size() == 1, "Sneak deletion preserves threshold and the walking edge");
        SmartPhoneRouteEditor.logout(p.getUUID()); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void guiConditionPacketsCannotRegisterOrReplaceControlBlocks(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var p = player(helper, a.center().add(0, 0, -2));
        var data = manager(helper).data(); var target = a.pos().south(2); helper.getLevel().setBlock(target, Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
        var menu = new RouteMarkerMenu(19, p.getInventory(), a.id(), a.pos()); p.containerMenu = menu; long before = data.version();
        new RouteMarkerActionPacket(19, a.id(), before, RouteMarkerActionPacket.Action.CONDITION, 0, 0, "REDSTONE", target, "", "", "", false, 0).handle(p);
        helper.assertTrue(data.version() == before && !data.marker(a.id()).condition().configured(), "GUI packets cannot bypass native phone registration");
        data.put(a.withCondition(new CrossingCondition(CrossingCondition.Mode.REDSTONE, target, "", "", "", false)));
        new RouteMarkerActionPacket(19, a.id(), data.version(), RouteMarkerActionPacket.Action.CONDITION, 0, 0, "REDSTONE", target, "", "", "", true, 0).handle(p);
        helper.assertTrue(data.marker(a.id()).condition().inverted(), "GUI may immediately invert the existing binding");
        before = data.version();
        new RouteMarkerActionPacket(19, a.id(), before, RouteMarkerActionPacket.Action.CONDITION, 0, 0, "BLOCK_STATE", target, "minecraft:redstone_block", "", "", false, 0).handle(p);
        helper.assertTrue(data.version() == before && data.marker(a.id()).condition().inverted(), "Removed block-state control is rejected"); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void crossingNeedsBothConditionsAndMissingConditionBlockClosesIt(GameTestHelper helper) {
        var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var b = marker(helper, 18, 10, RouteMarkerType.CROSSING); var manager = manager(helper);
        var first = a.pos().north(2); var second = b.pos().north(2);
        helper.getLevel().setBlock(first, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true), 2);
        helper.getLevel().setBlock(second, Blocks.LEVER.defaultBlockState(), 2);
        manager.data().put(a.withCondition(new CrossingCondition(CrossingCondition.Mode.REDSTONE, first, "", "", "", false)));
        manager.data().put(b.withCondition(new CrossingCondition(CrossingCondition.Mode.REDSTONE, second, "", "", "", false)));
        manager.data().link(a.id(), b.id(), true); var edge = manager.data().connected(a.id()).get(0);
        helper.assertTrue(!manager.crossingOpen(edge), "One endpoint false closes pair");
        helper.getLevel().setBlock(second, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true), 2);
        helper.assertTrue(manager.crossingOpen(edge), "Both redstone sources open the crossing");
        helper.getLevel().removeBlock(first, false); helper.assertTrue(!manager.crossingOpen(edge), "Destroyed condition block closes safely");
        manager.data().put(manager.data().marker(a.id()).withCondition(new CrossingCondition(CrossingCondition.Mode.REDSTONE, first, "", "", "", true)));
        helper.assertTrue(!manager.crossingOpen(edge), "Inversion cannot make a missing condition block safe"); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void plannerUsesGraphAlternativesAndTemporaryFailureDoesNotDeleteEdges(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.NORMAL); var b = marker(helper, 18, 10, RouteMarkerType.NORMAL); var c = marker(helper, 10, 22, RouteMarkerType.NORMAL);
        var manager = manager(helper); var data = manager.data(); data.link(a.id(), b.id(), false); data.link(a.id(), c.id(), false); data.link(c.id(), b.id(), false);
        var citizen = citizen(helper, a.center(), false); var direct = manager.planner().between(citizen, a.id(), b.id());
        helper.assertTrue(direct.nodes().equals(List.of(a.id(), b.id())), "Shortest route uses direct edge"); long version = data.version();
        manager.runtime().failed(citizen.getUUID(), direct.edges().get(0), helper.getLevel().getGameTime(), version);
        var alternative = manager.planner().between(citizen, a.id(), b.id());
        helper.assertTrue(alternative.nodes().equals(List.of(a.id(), c.id(), b.id())), "Local failure recalculates alternative graph path");
        helper.assertTrue(data.version() == version && data.connected(a.id()).size() == 2, "Path failure never deletes persistent edge");
        manager.runtime().update(version, helper.getLevel().getGameTime() + RouteSettings.TEMPORARY_BLOCK_TICKS + 1);
        helper.assertTrue(manager.planner().between(citizen, a.id(), b.id()).nodes().equals(List.of(a.id(), b.id())), "Expiration restores direct route");
        var home = UUID.randomUUID(); var other = UUID.randomUUID();
        data.put(a.withDistrict(home)); data.put(b.withDistrict(home)); data.put(c.withDistrict(other));
        var local = marker(helper, 10, 25, RouteMarkerType.NORMAL).withDistrict(home); data.put(local);
        data.link(a.id(), local.id(), false); data.link(local.id(), b.id(), false);
        manager.runtime().failed(citizen.getUUID(), direct.edges().get(0), helper.getLevel().getGameTime(), data.version());
        var preferred = manager.planner().between(citizen, a.id(), b.id());
        helper.assertTrue(preferred.nodes().equals(List.of(a.id(), local.id(), b.id())), "District surcharge prefers a slightly longer local path");
        helper.assertTrue(a.center().distanceTo(local.center()) + local.center().distanceTo(b.center())
                > a.center().distanceTo(c.center()) + c.center().distanceTo(b.center()), "Local preference is not merely geometric shortest distance");
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void endpointsStayInsideRadiusAndHeightAndRevalidateObstacles(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.NORMAL); var b = marker(helper, 18, 10, RouteMarkerType.NORMAL).withRegion(4, 2); var manager = manager(helper);
        manager.data().put(b); var citizen = citizen(helper, a.center(), false); var endpoints = manager.endpointCandidates(citizen, a, b);
        helper.assertTrue(endpoints.size() > 1, "Region offers several parallel endpoint candidates");
        for (var endpoint : endpoints) helper.assertTrue(b.contains(Vec3.atBottomCenterOf(endpoint)) && manager.safeEndpoint(citizen, endpoint), "Every endpoint safe and inside region");
        helper.getLevel().setBlock(endpoints.get(0).above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!manager.safeEndpoint(citizen, endpoints.get(0)), "Cached endpoint safety is rechecked before use"); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_route_movement", timeoutTicks = 300)
    public static void walkContinuesThroughCornersAvoidsImmediateUTurnAndStopsOnModeChange(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.NORMAL); var b = marker(helper, 18, 10, RouteMarkerType.NORMAL); var c = marker(helper, 18, 18, RouteMarkerType.NORMAL);
        var data = manager(helper).data(); data.link(a.id(), b.id(), false); data.link(b.id(), c.id(), false);
        var citizen = citizen(helper, a.center(), true); citizen.setBehavior(CitizenBehaviorState.walking()); var seen = new ArrayList<UUID>();
        helper.onEachTick(() -> { var current = citizen.routeMovement().currentMarker(); if (current != null && (seen.isEmpty() || !current.equals(seen.get(seen.size() - 1)))) seen.add(current); });
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(seen.size() >= 3 && seen.subList(0, 3).equals(List.of(a.id(), b.id(), c.id())), "WALK visits=" + seen
                    + ", pos=" + citizen.position() + ", state=" + citizen.routeMovement().state() + ", current=" + citizen.routeMovement().currentMarker());
            helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK, "WALK mode remains persistent");
            citizen.setBehavior(CitizenBehaviorState.posed(PosePreset.CASUAL));
            helper.assertTrue(citizen.getNavigation().isDone() && citizen.routeMovement().currentMarker() == null, "Mode change stops and releases route state"); citizen.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_route_movement", timeoutTicks = 180)
    public static void crossingWaitsWithoutStuckAndFinishesAfterSignalCloses(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var b = marker(helper, 18, 10, RouteMarkerType.CROSSING); var manager = manager(helper);
        var first = a.pos().north(2); var second = b.pos().north(2);
        helper.getLevel().setBlock(first, Blocks.LEVER.defaultBlockState(), 2); helper.getLevel().setBlock(second, Blocks.LEVER.defaultBlockState(), 2);
        var conditionA = new CrossingCondition(CrossingCondition.Mode.REDSTONE, first, "", "", "", false);
        var conditionB = new CrossingCondition(CrossingCondition.Mode.REDSTONE, second, "", "", "", false);
        manager.data().put(a.withCondition(conditionA)); manager.data().put(b.withCondition(conditionB)); manager.data().link(a.id(), b.id(), true);
        var citizen = citizen(helper, a.center(), true); citizen.setBehavior(CitizenBehaviorState.walking());
        citizen.setYRot(90); citizen.setYBodyRot(90); citizen.setYHeadRot(90);
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(citizen.routeMovement().state() == CitizenRouteMovement.State.WAIT_CROSSING && a.contains(citizen.position()) && citizen.getNavigation().isDone(), "Closed crossing waits inside marker");
            float yaw = (float) (net.minecraft.util.Mth.atan2(b.center().z - citizen.getZ(), b.center().x - citizen.getX()) * 180 / Math.PI) - 90;
            helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(citizen.yBodyRot - yaw)) < 5, "Waiting body faces the intended crossing direction");
            helper.assertTrue(b.center().equals(citizen.routeMovement().currentDestination()), "Waiting destination remains the opposite crossing node");
            helper.getLevel().setBlock(first, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true), 2);
            helper.getLevel().setBlock(second, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true), 2);
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(!a.contains(citizen.position()), "Open conditions started crossing");
            helper.getLevel().setBlock(first, Blocks.LEVER.defaultBlockState(), 2); helper.getLevel().setBlock(second, Blocks.LEVER.defaultBlockState(), 2);
            manager.data().put(manager.data().marker(a.id()).withCondition(CrossingCondition.UNSET));
        });
        helper.runAtTickTime(140, () -> {
            helper.assertTrue(b.id().equals(citizen.routeMovement().currentMarker()) && b.contains(citizen.position()), "Crossing finish: pos=" + citizen.position()
                    + ", target=" + b.center() + ", state=" + citizen.routeMovement().state() + ", current=" + citizen.routeMovement().currentMarker());
            citizen.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_route_movement", timeoutTicks = 180)
    public static void destinationIntentUsesVanillaWhenNoGraphExists(GameTestHelper helper) {
        for (int x = 1; x < 38; x++) for (int z = 1; z < 38; z++) helper.setBlock(new BlockPos(x, 128, z), Blocks.STONE);
        var start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(20, 129, 20)));
        helper.assertTrue(manager(helper).nearby(start, RouteSettings.DISCOVERY_RADIUS).isEmpty(), "Fixture has no nearby graph");
        var citizen = citizen(helper, start, true);
        var destination = start.add(5, 0, 0); citizen.routeMovement().requestDestination(destination, CitizenPathfindingScheduler.Priority.PLAYER);
        helper.runAtTickTime(140, () -> {
            helper.assertTrue(citizen.position().distanceToSqr(destination) <= 1.1, "Destination fallback: pos=" + citizen.position()
                    + ", destination=" + destination + ", state=" + citizen.routeMovement().state() + ", intent=" + citizen.routeMovement().hasDestination()
                    + ", active=" + CitizenActivityScheduler.get(helper.getLevel()).state(citizen) + ", done=" + citizen.getNavigation().isDone());
            helper.assertTrue(!citizen.routeMovement().hasDestination() && citizen.citizenState().behavior().mode() == CitizenBehaviorMode.STAND, "Transient intent leaves saved mode untouched");
            citizen.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void noGraphWalkWaitsAndUnloadedRecordsDoNotForceChunks(GameTestHelper helper) {
        var position = new BlockPos(2_000_000, 70, 2_000_000); var data = manager(helper).data();
        var citizen = citizen(helper, Vec3.atBottomCenterOf(position), false); citizen.setBehavior(CitizenBehaviorState.walking());
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.PLAYER, 40);
        citizen.routeMovement().tick(); helper.assertTrue(citizen.routeMovement().state() == CitizenRouteMovement.State.WAIT_ROUTE
                && citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK, "Missing graph waits without WANDER fallback");
        var node = RouteMarker.create(UUID.randomUUID(), position, RouteMarkerType.NORMAL); data.put(node); manager(helper).loadChanged();
        long version = data.version(); helper.assertTrue(!manager(helper).loaded(node) && data.marker(node.id()) != null, "Unloaded marker retained in registry");
        helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4), "No chunk forcing");
        var restored = CitizenRouteSavedData.load(data.save(new CompoundTag())); helper.assertTrue(restored.marker(node.id()) != null && restored.version() == version, "Unloaded data survives save restore");
        data.remove(node.id()); CitizenActivityScheduler.get(helper.getLevel()).remove(citizen.getUUID()); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_route_scheduler", timeoutTicks = 80)
    public static void queueBudgetAndActivityRotationProtectLargeCitizenSets(GameTestHelper helper) {
        floor(helper); var scheduler = CitizenPathfindingScheduler.get(helper.getLevel()); var activity = CitizenActivityScheduler.get(helper.getLevel());
        var citizens = new ArrayList<Citizen>(); var executed = new int[]{0};
        for (int i = 0; i < 120; i++) {
            var citizen = citizen(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(10, 1, 10))), false); citizens.add(citizen); activity.register(citizen);
            scheduler.submit(citizen, CitizenPathfindingScheduler.Priority.PLAYER, () -> true, () -> {
                executed[0]++; citizen.getNavigation().createPath(helper.absolutePos(new BlockPos(12, 1, 10)), 0);
            });
        }
        helper.assertTrue(citizens.stream().anyMatch(citizen -> activity.state(citizen) == CitizenActivityScheduler.State.THROTTLED), "More than active budget produces throttled Citizens");
        var throttled = citizens.stream().filter(citizen -> activity.state(citizen) == CitizenActivityScheduler.State.THROTTLED).findFirst().orElseThrow();
        activity.forceActive(throttled, CitizenActivityScheduler.Reason.GAUGE_TRANSFER, 40);
        helper.assertTrue(activity.state(throttled) == CitizenActivityScheduler.State.ACTIVE, "Important intent can exceed soft active budget");
        helper.onEachTick(() -> helper.assertTrue(scheduler.lastExecuted() <= ModConfigs.COMMON.maxNewPathRequestsPerTick.get(), "Hard path budget respected"));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(executed[0] == 120, "Queued requests drain without dropping Citizens");
            for (var citizen : citizens) { activity.remove(citizen.getUUID()); scheduler.cancel(citizen); }
            helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void gatewayRemainsDisabledWithoutGaugeAndKeepsDistrictOnReload(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.GATEWAY); var district = UUID.randomUUID(); var manager = manager(helper);
        manager.data().put(a.withDistrict(district)); a = manager.data().marker(a.id()); var citizen = citizen(helper, a.center(), false); var start = citizen.position();
        helper.assertTrue(!manager.gatewayEnabled(a) && !manager.transitGateway(citizen, a.id(), UUID.randomUUID()) && citizen.position().equals(start), "No Gauge means no virtual transfer");
        var restored = CitizenRouteSavedData.load(manager.data().save(new CompoundTag())); helper.assertTrue(district.equals(restored.marker(a.id()).districtId()), "District UUID persists without fabricating membership"); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_route_timeout", timeoutTicks = 760)
    public static void longClosedCrossingReplansThroughAlternativeGraph(GameTestHelper helper) {
        var chunk = new net.minecraft.world.level.ChunkPos(helper.absolutePos(new BlockPos(20, 1, 20)));
        var chunks = helper.getLevel().getChunkSource();
        chunks.addRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 3, chunk);
        for (int x = chunk.x - 3; x <= chunk.x + 3; x++) for (int z = chunk.z - 3; z <= chunk.z + 3; z++) helper.getLevel().getChunk(x, z);
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.CROSSING); var b = marker(helper, 18, 10, RouteMarkerType.CROSSING); var c = marker(helper, 10, 34, RouteMarkerType.NORMAL);
        var manager = manager(helper); var data = manager.data(); data.link(a.id(), b.id(), true); data.link(a.id(), c.id(), false); data.link(c.id(), b.id(), false);
        var citizen = citizen(helper, a.center(), true);
        citizen.routeMovement().requestDestination(b.center(), CitizenPathfindingScheduler.Priority.PLAYER);
        var crossing = data.connected(a.id()).stream().filter(RouteEdge::conditional).findFirst().orElseThrow();
        var waited = new boolean[]{false}; var detoured = new boolean[]{false};
        helper.onEachTick(() -> {
            if (citizen.routeMovement().state() == CitizenRouteMovement.State.WAIT_CROSSING) waited[0] = true;
            if (citizen.getZ() > a.center().z + 4) detoured[0] = true;
        });
        helper.runAtTickTime(40, () -> helper.assertTrue(citizen.routeMovement().state() == CitizenRouteMovement.State.WAIT_CROSSING, "Shortest closed crossing waits first"));
        helper.runAtTickTime(710, () -> {
            try {
            helper.assertTrue(waited[0], "Closed crossing waited before choosing a detour: pos=" + citizen.position() + ", state=" + citizen.routeMovement().state()
                    + ", wait=" + citizen.routeMovement().crossingWaitTicks(helper.getLevel().getGameTime()) + ", timeout=" + ModConfigs.COMMON.crossingWaitTimeout.get());
            helper.assertTrue(citizen.routeMovement().state() != CitizenRouteMovement.State.WAIT_CROSSING && detoured[0],
                    "Alternate route: pos=" + citizen.position() + ", state=" + citizen.routeMovement().state() + ", current=" + citizen.routeMovement().currentMarker()
                            + ", loaded=" + com.rexhub.createapparel.citizen.CitizenNavigation.hasLoadedRegion(helper.getLevel(), citizen.blockPosition(), 24)
                            + ", detourNode=" + manager.loaded(data.marker(c.id())) + ", saved=" + data.marker(c.id()) + ", planner=" + manager.planner().statistics()
                            + ", feet=" + helper.getLevel().getBlockState(citizen.blockPosition()) + ", support=" + helper.getLevel().getBlockState(citizen.blockPosition().below())
                            + ", velocity=" + citizen.getDeltaMovement() + ", requests=" + citizen.routeMovement().navigationRequests());
            helper.assertTrue(data.edge(crossing.id()) != null, "Timeout does not remove saved conditional edge"); helper.succeed();
            } finally { citizen.discard(); chunks.removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 3, chunk); }
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_routes")
    public static void gatewayIntegrationBoundaryRequiresLiveLinkAndUsesSafeLoadedCandidates(GameTestHelper helper) {
        floor(helper); var source = UUID.randomUUID(); var target = UUID.randomUUID(); var a = marker(helper, 10, 10, RouteMarkerType.GATEWAY);
        var b = marker(helper, 18, 10, RouteMarkerType.GATEWAY); var c = marker(helper, 26, 10, RouteMarkerType.GATEWAY); var manager = manager(helper);
        manager.data().put(a.withDistrict(source)); manager.data().put(b.withDistrict(target)); manager.data().put(c.withDistrict(target));
        var linked = new boolean[]{true};
        manager.setGatewayAccess(new CitizenGatewayAccess() {
            public boolean enabled(UUID district) { return district.equals(source) || district.equals(target); }
            public List<UUID> linkedDistricts(UUID district) { return district.equals(source) && linked[0] ? List.of(target) : List.of(); }
        });
        try {
            var seen = new HashSet<UUID>();
            for (int i = 0; i < 12; i++) {
                var citizen = citizen(helper, a.center(), false); citizen.getRandom().setSeed(i * 419L + 7);
                var saved = citizen.citizenState().serializeNBT();
                helper.assertTrue(manager.transitGateway(citizen, a.id(), target), "A live Gauge adapter link permits one hop");
                if (b.contains(citizen.position())) seen.add(b.id()); else if (c.contains(citizen.position())) seen.add(c.id()); else helper.fail("Unsafe or unknown arrival");
                helper.assertTrue(saved.equals(citizen.citizenState().serializeNBT()) && !citizen.routeMovement().gatewayReady(helper.getLevel().getGameTime()), "Transfer preserves saved state and activates cooldown");
            }
            helper.assertTrue(seen.size() == 2, "Random arrival uses both loaded safe candidates");
            linked[0] = false; var citizen = citizen(helper, a.center(), false);
            helper.assertTrue(!manager.transitGateway(citizen, a.id(), target), "Deleting Gauge link immediately disables implicit transition");
        } finally { manager.setGatewayAccess(CitizenGatewayAccess.UNAVAILABLE); }
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_route_movement", timeoutTicks = 400)
    public static void inaccessibleStartMarkerRetriesAnotherLoadedCandidate(GameTestHelper helper) {
        var chunk = new net.minecraft.world.level.ChunkPos(helper.absolutePos(new BlockPos(20, 1, 20)));
        var chunks = helper.getLevel().getChunkSource();
        chunks.addRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 3, chunk);
        for (int x = chunk.x - 3; x <= chunk.x + 3; x++) for (int z = chunk.z - 3; z <= chunk.z + 3; z++) helper.getLevel().getChunk(x, z);
        GameTestFixtures.dryRoom(helper, 0, 0, 40);
        floor(helper); var a = marker(helper, 10, 10, RouteMarkerType.NORMAL); var b = marker(helper, 18, 10, RouteMarkerType.NORMAL); var c = marker(helper, 26, 10, RouteMarkerType.NORMAL);
        var data = manager(helper).data(); data.link(a.id(), b.id(), false); data.link(b.id(), c.id(), false);
        helper.getLevel().setBlock(a.pos().above(), Blocks.STONE.defaultBlockState(), 3);
        var citizen = citizen(helper, a.center().add(-4, 0, 0), true); citizen.setBehavior(CitizenBehaviorState.walking());
        var reached = new boolean[]{false};
        helper.onEachTick(() -> { if (b.id().equals(citizen.routeMovement().currentMarker()) || c.id().equals(citizen.routeMovement().currentMarker())) reached[0] = true; });
        Runnable cleanup = () -> {
            citizen.discard();
            chunks.removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 3, chunk);
        };
        helper.runAtTickTime(399, cleanup);
        helper.succeedWhen(() -> {
            helper.assertTrue(reached[0], "Inaccessible nearest marker does not prevent discovery of another loaded start: pos=" + citizen.position()
                    + ", state=" + citizen.routeMovement().state() + ", entityTicks=" + citizen.tickCount + ", requests=" + citizen.routeMovement().navigationRequests()
                    + ", failures=" + citizen.routeMovement().consecutiveFailures() + ", retry=" + citizen.routeMovement().retryAt()
                    + ", alternative=" + b.center());
            helper.assertTrue(data.marker(a.id()) != null && data.connected(a.id()).size() == 1, "Failed entry preserves marker and saved edge: a="
                    + data.marker(a.id()) + ", aBlock=" + helper.getLevel().getBlockState(a.pos()) + ", bBlock=" + helper.getLevel().getBlockState(b.pos()));
            cleanup.run();
        });
    }
}
