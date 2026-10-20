package com.rexhub.createapparel.gametest;

import com.mojang.authlib.GameProfile;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.citizen.population.*;
import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.init.*;
import com.rexhub.createapparel.item.CitizenGaugeItem;
import com.rexhub.createapparel.menu.CitizenGaugeMenu;
import com.rexhub.createapparel.network.CitizenGaugeActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenPopulationGameTests {
    private CitizenPopulationGameTests() { }
    private static CitizenPopulationManager manager(GameTestHelper helper) { return CitizenPopulationManager.get(helper.getLevel()); }
    private static void room(GameTestHelper helper) { GameTestFixtures.dryRoom(helper, 0, 0, 38); }
    private static CitizenGauge gauge(GameTestHelper helper, int x, int z, boolean source) {
        var pos = helper.absolutePos(new BlockPos(x, 1, z));
        helper.getLevel().setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
        var entity = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(pos);
        entity.initialize(source ? null : helper.absolutePos(new BlockPos(x, 1, z + 3)));
        var gauge = manager(helper).register(entity);
        if (!source) manager(helper).settings(gauge.id(), 2, 0);
        return manager(helper).data().gauge(gauge.id());
    }
    private static CitizenGaugeBlockEntity wirePanel(GameTestHelper helper, BlockPos pos, Direction facing, int slot) {
        helper.getLevel().setBlock(pos.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState(), 3);
        var state = ModBlocks.CITIZEN_GAUGE.get().defaultBlockState().setValue(com.rexhub.createapparel.block.CitizenGaugeBlock.FACING, facing);
        helper.getLevel().setBlock(pos, state, 3);
        var entity = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(pos); entity.initialize(slot, null); return entity;
    }
    private static void assertWireAvoidsPanels(GameTestHelper helper, boolean factory) {
        room(helper); int index = 0;
        for (var facing : Direction.Plane.HORIZONTAL) {
            var origin = helper.absolutePos(new BlockPos(8 + index++ % 2 * 18, 2, 8 + (index - 1) / 2 * 18));
            var along = facing.getClockWise();
            var from = wirePanel(helper, origin, facing, 0); var to = wirePanel(helper, origin.relative(along, 4), facing, 0);
            var middle = origin.relative(along, 2); var slots = com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot.values();
            if (factory) {
                helper.getLevel().setBlock(middle.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState(), 3);
                var state = com.simibubi.create.AllBlocks.FACTORY_GAUGE.getDefaultState()
                        .setValue(com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.FACING, facing)
                        .setValue(com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL);
                helper.getLevel().setBlock(middle, state, 3);
                ((com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity) helper.getLevel().getBlockEntity(middle)).addPanel(slots[0], null);
            } else wirePanel(helper, middle, facing, 0);
            var own = new com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition(origin, slots[0]);
            var target = new com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition(to.getBlockPos(), slots[0]);
            var connection = new com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection(target, 1); connection.arrowBendMode = 0;
            var direct = connection.getPath(helper.getLevel(), from.getBlockState(), own);
            helper.assertTrue(!GaugeWirePath.clear(helper.getLevel(), from.getBlockState(), own, direct), "Original fixed bend crosses the existing panel: " + facing);
            var path = GaugeWirePath.find(helper.getLevel(), from.getBlockState(), own, target);
            helper.assertTrue(path.size() > direct.size() && GaugeWirePath.clear(helper.getLevel(), from.getBlockState(), own, path),
                    "Rerouted wire avoids existing " + (factory ? "Factory" : "Citizen") + " panel on wall " + facing);
            var cell = BlockPos.ZERO; for (var direction : path) cell = cell.relative(direction);
            var end = GaugeWirePath.point(from.getBlockState(), own, cell);
            var expected = Vec3.atLowerCornerOf(to.getBlockPos()).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(to.getBlockState(), 0));
            helper.assertTrue(end.distanceToSqr(expected) < 1.0E-8, "All wall rotations preserve the exact target panel endpoint");
            helper.getLevel().destroyBlock(middle, false);
            helper.assertTrue(GaugeWirePath.clear(helper.getLevel(), from.getBlockState(), own, direct), "Removing a panel makes the original path clear again");
        }
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void wiresDetourAroundCitizenPanelsOnAllWallFaces(GameTestHelper helper) {
        assertWireAvoidsPanels(helper, false); assertWireCrossings(helper); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void wiresDetourAroundFactoryPanelsOnAllWallFaces(GameTestHelper helper) { assertWireAvoidsPanels(helper, true); helper.succeed(); }

    private static void assertWireCrossings(GameTestHelper helper) {
        room(helper); int index = 0;
        var slot = com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot.values()[0];
        for (var facing : Direction.Plane.HORIZONTAL) {
            var origin = helper.absolutePos(new BlockPos(8 + index % 2 * 18, 3, 8 + index / 2 * 18)); index++;
            var along = facing.getClockWise();
            var left = wirePanel(helper, origin.relative(along.getOpposite()), facing, 0);
            var right = wirePanel(helper, origin.relative(along), facing, 0);
            var bottom = wirePanel(helper, origin.below(2), facing, 0);
            var top = wirePanel(helper, origin.above(2), facing, 0);
            var first = new com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition(left.getBlockPos(), slot);
            var second = new com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition(right.getBlockPos(), slot);
            var existing = GaugeWirePath.find(helper.getLevel(), left.getBlockState(), first, second);
            var occupied = new GaugeWireOccupancy(); var points = new ArrayList<BlockPos>(); var cursor = BlockPos.ZERO;
            points.add(GaugeWirePath.project(facing, GaugeWirePath.point(left.getBlockState(), first, cursor)));
            for (var direction : existing) { cursor = cursor.relative(direction); points.add(GaugeWirePath.project(facing, GaugeWirePath.point(left.getBlockState(), first, cursor))); }
            occupied.reserve(points);
            var own = new com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition(bottom.getBlockPos(), slot);
            var target = new com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition(top.getBlockPos(), slot);
            var state = bottom.getBlockState();
            var start = GaugeWirePath.project(facing, GaugeWirePath.point(state, own, BlockPos.ZERO));
            var end = GaugeWirePath.project(facing, GaugeWirePath.point(top.getBlockState(), target, BlockPos.ZERO));
            java.util.function.BiPredicate<BlockPos, BlockPos> blocked = (a, b) -> occupied.blocks(
                    GaugeWirePath.project(facing, GaugeWirePath.point(state, own, a)), GaugeWirePath.project(facing, GaugeWirePath.point(state, own, b)), start, end);
            var direct = GaugeWirePath.find(helper.getLevel(), state, own, target);
            helper.assertTrue(!GaugeWirePath.clear(helper.getLevel(), state, own, direct, blocked), "Direct wire crosses the existing wire: " + facing);
            var path = GaugeWirePath.find(helper.getLevel(), state, own, target, blocked);
            helper.assertTrue(path.size() > direct.size() && GaugeWirePath.clear(helper.getLevel(), state, own, path, blocked), "No crossed or shared wire segments: " + facing);
        }
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void flowModePersistsAcrossBlockEntityItemAndSavedDataAndCannotChangeDistrictRole(GameTestHelper helper) {
        room(helper); var influx = gauge(helper, 8, 8, true); var population = manager(helper);
        helper.assertTrue(influx.influx(), "Empty gauge defaults to influx");
        population.flow(influx.id(), CitizenGauge.Flow.OUTFLOW);
        var outflow = population.data().gauge(influx.id());
        var entity = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(influx.pos());
        helper.assertTrue(outflow.outflow() && entity.outflow(0), "Authoritative role updates wall icon state");
        var restored = new CitizenGaugeBlockEntity(influx.pos(), entity.getBlockState()); restored.load(entity.saveWithFullMetadata());
        helper.assertTrue(restored.outflow(0) && entity.panelItem(0).getOrCreateTag().getBoolean("GaugeOutflow"), "Block entity and recovered item preserve outflow");
        helper.assertTrue(CitizenPopulationSavedData.load(population.data().save(new CompoundTag())).gauge(influx.id()).outflow(), "SavedData preserves role");
        var client = new CitizenGaugeBlockEntity(influx.pos(), entity.getBlockState()); client.load(entity.getUpdateTag());
        helper.assertTrue(client.outflow(0), "Client block entity update carries the role");
        var home = gauge(helper, 20, 8, false); population.flow(home.id(), CitizenGauge.Flow.OUTFLOW);
        helper.assertTrue(!population.data().gauge(home.id()).source(), "District identity cannot be replaced through flow action");
        population.flow(influx.id(), CitizenGauge.Flow.INFLUX);
        helper.assertTrue(population.data().gauge(influx.id()).influx() && !entity.outflow(0), "GUI can switch back to influx"); helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 400)
    public static void unassignedWandererMayJoinAnAlreadyFullDistrict(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var population = manager(helper);
        population.settings(home.id(), 2, 1); citizen(helper, home);
        var wanderer = citizen(helper, home);
        wanderer.setPos(Vec3.atBottomCenterOf(home.center().east(12)));
        wanderer.citizenState().setPopulation(CitizenPopulationState.DEFAULT); population.update(wanderer);
        wanderer.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, wanderer.blockPosition(), helper.getLevel().dimension(), 0));
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(wanderer, CitizenActivityScheduler.Reason.INTERACTION, 500);
        helper.succeedWhen(() -> {
            helper.assertTrue(home.districtId().equals(wanderer.citizenState().population().districtId())
                    && population.data().owns(home, wanderer.position()), "Unassigned wanderer enters a full district and joins it");
            helper.assertTrue(population.data().population(home.districtId()) == 2, "Target is not an admission cap");
        });
    }
    @GameTest(template = "apparel_state", batch = "citizen_population")
    public static void phoneObservesDistrictsInEitherHandWithoutGaugeEditingPermission(GameTestHelper helper) {
        var player = player(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.assertTrue(!GaugeInteractions.observingDistricts(player), "Empty hands do not observe districts");
        for (var hand : InteractionHand.values()) {
            player.setItemInHand(hand, new ItemStack(ModItems.SMART_PHONE.get()));
            helper.assertTrue(GaugeInteractions.observingDistricts(player) && !GaugeInteractions.holding(player), "Phone observes without pretending to be a gauge");
            helper.assertTrue(com.rexhub.createapparel.citizen.route.RouteMarkerPermissions.canEditRoutes(player, hand)
                    == (hand == InteractionHand.MAIN_HAND), "Offhand phone remains observation-only");
            player.setItemInHand(hand, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 340)
    public static void unlimitedUnassignedWanderSeeksNearbyDistrictAndJoinsOnlyAfterEntry(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var population = manager(helper);
        var citizen = citizen(helper, home);
        citizen.setPos(Vec3.atBottomCenterOf(home.center().offset(12, 0, 0)));
        citizen.citizenState().setPopulation(CitizenPopulationState.DEFAULT); population.update(citizen);
        citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, home.center().offset(200, 0, 200), helper.getLevel().dimension(), 0));
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 500);
        helper.assertTrue(citizen.citizenState().population().districtId() == null, "Approaching a district does not assign membership early");
        boolean[] joined = {false};
        for (int tick = 10; tick <= 300; tick += 10) helper.runAtTickTime(tick, () -> {
            if (citizen.citizenState().population().districtId() != null) {
                joined[0] = true;
                helper.assertTrue(home.districtId().equals(citizen.citizenState().population().districtId()), "Nearby district becomes home");
                helper.assertTrue(population.data().owns(home, citizen.position()), "Ordinary unlimited wander stays in the newly owned district");
            }
        });
        helper.runAtTickTime(305, () -> {
            helper.assertTrue(joined[0] && population.data().population(home.districtId()) == 1, "Nearby wander enters and registers once"); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 380)
    public static void unlimitedMemberStaysHomeAndReturnsAfterRangeChange(GameTestHelper helper) {
        room(helper); var initial = gauge(helper, 12, 12, false); var population = manager(helper);
        population.settings(initial.id(), 5, 0); var home = population.data().gauge(initial.id());
        var citizen = citizen(helper, home); var anchor = home.center().offset(-200, 0, -200);
        citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, anchor, helper.getLevel().dimension(), 0));
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 500);
        for (int tick = 10; tick <= 120; tick += 10) helper.runAtTickTime(tick, () ->
                helper.assertTrue(population.data().owns(home, citizen.position()), "Unlimited member stays inside district instead of its old anchor"));
        helper.runAtTickTime(130, () -> {
            population.settings(home.id(), 2, 0); citizen.setPos(Vec3.atBottomCenterOf(home.center().offset(10, 0, 0)));
            citizen.setDeltaMovement(Vec3.ZERO);
        });
        helper.runAtTickTime(340, () -> {
            helper.assertTrue(population.data().owns(population.data().gauge(home.id()), citizen.position()), "Geometry invalidation redirects the displaced wanderer home");
            helper.assertTrue(citizen.citizenState().behavior().anchor().equals(anchor), "Unlimited wander does not rewrite its saved anchor"); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 240)
    public static void unlimitedIndependentWanderNeverAutomaticallyJoinsNearbyDistrict(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 12, 12, false); var population = manager(helper); var citizen = citizen(helper, home);
        population.flags(citizen, true, false);
        citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, citizen.blockPosition(), helper.getLevel().dimension(), 0));
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 300);
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(citizen.citizenState().population().independent() && citizen.citizenState().population().districtId() == null,
                    "Independent remains explicitly unassigned during unlimited wander"); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void fourWallPanelsPlaceConnectReloadAndRecoverIndependently(GameTestHelper helper) {
        room(helper); var level = helper.getLevel(); var wall = helper.absolutePos(new BlockPos(12, 2, 12));
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        var player = player(helper, Vec3.atCenterOf(wall).add(0, 0, -2));
        for (var facing : Direction.Plane.HORIZONTAL) {
            var pos = wall.relative(facing);
            var state = ModBlocks.CITIZEN_GAUGE.get().defaultBlockState().setValue(com.rexhub.createapparel.block.CitizenGaugeBlock.FACING, facing);
            for (int slot = 0; slot < 4; slot++) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CITIZEN_GAUGE.get()));
                var hit = Vec3.atLowerCornerOf(pos).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(state, slot));
                var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, facing, slot % 2 == 0 ? wall : pos, false));
                helper.assertTrue(player.getMainHandItem().useOn(context).consumesAction(), "Places panel on every horizontal face");
                helper.assertTrue(manager(helper).data().at(pos, slot) != null, "Correct quadrant registered");
            }
            var entity = (CitizenGaugeBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(entity.panelCount() == 4 && !entity.initialize(3, null), "Maximum four; occupied panel cannot be overwritten");
            var a = manager(helper).data().at(pos, 0); var b = manager(helper).data().at(pos, 3);
            helper.assertTrue(manager(helper).link(a.id(), b.id()), "Same wall block panels link independently");
            var restored = new CitizenGaugeBlockEntity(pos, state); restored.load(entity.saveWithFullMetadata());
            helper.assertTrue(restored.panelCount() == 4 && b.id().equals(restored.gaugeId(3)), "All panel identities survive BE reload");
            var drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, entity);
            helper.assertTrue(drops.size() == 4, "Whole-block loot returns all four panels");
            var hit = Vec3.atLowerCornerOf(pos).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(state, 0));
            ((com.rexhub.createapparel.block.CitizenGaugeBlock) ModBlocks.CITIZEN_GAUGE.get()).onSneakWrenched(state, new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, facing, pos, false)));
            helper.assertTrue(entity.panelCount() == 3 && manager(helper).data().gauge(a.id()) == null && manager(helper).loaded(b), "Wrench removes only targeted panel");
            helper.assertTrue(manager(helper).data().linked(b.id()).isEmpty(), "Only removed panel links are invalidated");
            var menu = new CitizenGaugeMenu(17, player.getInventory(), b.id(), pos);
            helper.assertTrue(menu.stillValid(player), "Other panels on the same block remain editable");
        }
        level.destroyBlock(wall, false);
        for (var facing : Direction.Plane.HORIZONTAL) helper.assertTrue(level.getBlockState(wall.relative(facing)).isAir(), "Wall panels drop when backing is removed");
        helper.succeed();
    }
    private static Citizen citizen(GameTestHelper helper, CitizenGauge home) {
        var citizen = ModEntities.CITIZEN.get().create(helper.getLevel());
        if (citizen == null) throw new IllegalStateException("Missing Citizen");
        citizen.setPos(Vec3.atBottomCenterOf(home.center())); citizen.setOnGround(true);
        citizen.citizenState().setPopulation(new CitizenPopulationState(home.districtId(), false, false, 0));
        helper.getLevel().addFreshEntity(citizen); manager(helper).join(citizen); return citizen;
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 420)
    public static void outflowDeletesOneSurplusInPlaceAndNeverCrossesTarget(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var sink = gauge(helper, 20, 8, true); var population = manager(helper);
        population.settings(home.id(), 2, 1); population.flow(sink.id(), CitizenGauge.Flow.OUTFLOW); population.link(home.id(), sink.id());
        var citizens = List.of(citizen(helper, home), citizen(helper, home));
        for (var citizen : citizens) citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.STAND, home.center(), helper.getLevel().dimension(), 0));
        helper.runAtTickTime(350, () -> {
            helper.assertTrue(population.data().population(home.districtId()) == 1 && citizens.stream().filter(Citizen::isRemoved).count() == 1,
                    "Outflow removes only one surplus resident: population=" + population.data().population(home.districtId())
                            + ", removed=" + citizens.stream().filter(Citizen::isRemoved).count() + ", sink=" + population.data().gauge(sink.id())
                            + ", home=" + population.data().gauge(home.id()) + ", current=" + (population == manager(helper))
                            + ", network=" + population.data().networks().cached(sink.id())
                            + ", residents=" + citizens.stream().map(citizen -> citizen.getUUID() + ":" + citizen.position() + ":" + citizen.citizenState().population() + ":" + citizen.intents().active()).toList());
            var removed = citizens.stream().filter(Citizen::isRemoved).findFirst().orElseThrow();
            helper.assertTrue(population.data().citizen(removed.getUUID()) == null && !population.reserved(removed.getUUID()), "Removal clears registry and reservations");
            helper.assertTrue(population.data().gauge(sink.id()).cooldownUntil() > 0, "Outflow uses gauge cooldown");
            population.data().put(population.data().gauge(sink.id()).cooldown(0)); population.data().put(population.data().gauge(home.id()).cooldown(0));
        });
        helper.runAtTickTime(400, () -> {
            helper.assertTrue(population.data().population(home.districtId()) == 1 && citizens.stream().filter(Citizen::isAlive).count() == 1, "Repeated evaluation never deletes the target population");
            helper.succeed();
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 380)
    public static void outflowWaitsForUnloadedNetworkShortageAndProtectsForcedResidents(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var other = gauge(helper, 20, 8, false); var sink = gauge(helper, 30, 8, true); var population = manager(helper);
        population.settings(home.id(), 2, 1); population.settings(other.id(), 2, 1);
        population.flow(sink.id(), CitizenGauge.Flow.OUTFLOW); population.link(sink.id(), home.id()); population.link(home.id(), other.id());
        var residents = List.of(citizen(helper, home), citizen(helper, home));
        residents.forEach(citizen -> citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.STAND, home.center(), helper.getLevel().dimension(), 0)));
        var far = new BlockPos(27999900, 71, 27999900); population.data().put(population.data().gauge(other.id()).relocate(far, 0));
        helper.runAtTickTime(140, () -> {
            helper.assertTrue(residents.stream().allMatch(Citizen::isAlive), "Unloaded network shortage prevents deletion");
            helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4), "Outflow never force-loads gauges");
            residents.forEach(citizen -> population.flags(citizen, false, true));
            population.unlink(home.id(), other.id()); population.remove(other.id());
        });
        helper.runAtTickTime(340, () -> {
            helper.assertTrue(residents.stream().allMatch(Citizen::isAlive) && population.data().population(home.districtId()) == 2, "Force protects surplus residents even when the network is saturated"); helper.succeed();
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 700)
    public static void outflowPromotesSmallDeficitsAndMigratesBeforeDeleting(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var other = gauge(helper, 22, 8, false); var sink = gauge(helper, 32, 8, true); var population = manager(helper);
        population.settings(home.id(), 3, 3); population.settings(other.id(), 3, 3);
        population.flow(sink.id(), CitizenGauge.Flow.OUTFLOW); population.link(sink.id(), home.id()); population.link(home.id(), other.id());
        var residents = List.of(citizen(helper, home), citizen(helper, home));
        var virtual = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        for (int index = 0; index < virtual.size(); index++) population.data().register(virtual.get(index), new CitizenPopulationState(index < 2 ? home.districtId() : other.districtId(), false, false, 0));
        residents.forEach(citizen -> citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.STAND, home.center(), helper.getLevel().dimension(), 0)));
        helper.succeedWhen(() -> {
            helper.assertTrue(population.data().population(home.districtId()) == 3 && population.data().population(other.districtId()) == 3, "Existing citizen migrates into the Buffer-sized deficit before any outflow");
            helper.assertTrue(residents.stream().allMatch(Citizen::isAlive), "Migration preserves both citizens");
            population.unlink(home.id(), other.id()); population.unlink(sink.id(), home.id());
            population.settings(home.id(), 3, 0); population.settings(other.id(), 3, 0); virtual.forEach(population.data()::removeCitizen); helper.succeed();
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 160)
    public static void markersAutomaticallyFollowSquareTerritoryCreationExpansionSplittingAndDeletion(GameTestHelper helper) {
        room(helper); var level = helper.getLevel(); var population = manager(helper); var routes = CitizenRouteManager.get(level);
        var positions = List.of(helper.absolutePos(new BlockPos(8, 1, 11)), helper.absolutePos(new BlockPos(9, 1, 12)), helper.absolutePos(new BlockPos(12, 1, 11)));
        var blocks = List.of(ModBlocks.CITIZEN_ROUTE_MARKER.get(), ModBlocks.CROSSING_ROUTE_MARKER.get(), ModBlocks.GATEWAY_ROUTE_MARKER.get());
        var ids = new ArrayList<UUID>();
        for (int index = 0; index < positions.size(); index++) {
            var pos = positions.get(index); level.setBlock(pos, blocks.get(index).defaultBlockState(), 3);
            var marker = routes.register((com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity) level.getBlockEntity(pos));
            helper.assertTrue(marker.districtId() == null, "Markers start unassigned outside all Districts"); ids.add(marker.id());
        }
        var home = gauge(helper, 8, 8, false); population.settings(home.id(), 1, 0);
        var corner = helper.absolutePos(new BlockPos(7, 1, 10)); level.setBlock(corner, ModBlocks.CITIZEN_ROUTE_MARKER.get().defaultBlockState(), 3);
        var created = routes.register((com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity) level.getBlockEntity(corner));
        helper.assertTrue(home.districtId().equals(created.districtId()), "New Marker in radius-one square corner is assigned immediately");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(home.districtId().equals(routes.data().marker(ids.get(0)).districtId()) && home.districtId().equals(routes.data().marker(ids.get(1)).districtId()), "New District assigns existing markers of every type");
            helper.assertTrue(routes.data().marker(ids.get(2)).districtId() == null, "Outside Marker stays unassigned");
            population.settings(home.id(), 4, 0);
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(home.districtId().equals(routes.data().marker(ids.get(2)).districtId()), "Range expansion assigns the newly covered Marker");
                var other = gauge(helper, 14, 8, false); population.settings(other.id(), 4, 0);
                helper.runAfterDelay(10, () -> {
                    helper.assertTrue(other.districtId().equals(routes.data().marker(ids.get(2)).districtId()), "Midpoint partition gives overlap block to the closer District");
                    var saved = population.data().save(new CompoundTag());
                    helper.assertTrue(saved.getInt("SchemaVersion") == 6 && saved.getCompound("Territory").contains("Seeds"), "Territory policy and seeds are persisted for maps");
                    var restored = CitizenPopulationSavedData.load(saved);
                    helper.assertTrue(other.districtId().equals(restored.territory().districtAt(positions.get(2))), "Exact block ownership survives reload without terrain access");
                    population.remove(other.id());
                    helper.runAfterDelay(10, () -> {
                        helper.assertTrue(home.districtId().equals(routes.data().marker(ids.get(2)).districtId()), "Deleting neighboring District restores the remaining owner");
                        population.settings(home.id(), 1, 0);
                        helper.runAfterDelay(10, () -> {
                            helper.assertTrue(routes.data().marker(ids.get(2)).districtId() == null, "Shrinking range removes outside affiliation without deleting Marker"); helper.succeed();
                        });
                    });
                });
            });
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void allMarkerTypesStoreDistrictAndGatewayMenusRefreshLinkedDestinations(GameTestHelper helper) {
        room(helper); var a = gauge(helper, 8, 8, false); var b = gauge(helper, 15, 8, false); var population = manager(helper);
        var level = helper.getLevel(); var routes = CitizenRouteManager.get(level); var pos = helper.absolutePos(new BlockPos(8, 1, 13));
        level.setBlock(pos, ModBlocks.CITIZEN_ROUTE_MARKER.get().defaultBlockState(), 3);
        var node = routes.register((com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity) level.getBlockEntity(pos));
        var player = player(helper, node.center().add(0, 0, -2)); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        var menu = new com.rexhub.createapparel.menu.RouteMarkerMenu(51, player.getInventory(), node.id(), pos); player.containerMenu = menu;
        for (var type : RouteMarkerType.values()) {
            if (routes.data().marker(node.id()).type() != type) routes.changeType(node.id(), type);
            var packet = new com.rexhub.createapparel.network.RouteMarkerActionPacket(51, node.id(), routes.data().version(),
                    com.rexhub.createapparel.network.RouteMarkerActionPacket.Action.DISTRICT, 0, 0, a.districtId().toString(), BlockPos.ZERO, "", "", "", false, 0);
            menu.apply(player, packet);
            helper.assertTrue(a.districtId().equals(routes.data().marker(node.id()).districtId()) && menu.info().getString("DistrictName").equals(a.name()), "Every Marker type automatically retains spatial District membership");
        }
        routes.changeType(node.id(), RouteMarkerType.GATEWAY); menu.sendInfo(player);
        helper.assertTrue(menu.info().getList("Destinations", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(), "No implicit destination without Gauge Links");
        population.link(a.id(), b.id()); menu.sendInfo(player);
        var entries = menu.info().getList("Destinations", net.minecraft.nbt.Tag.TAG_COMPOUND);
        helper.assertTrue(entries.size() == 1 && b.districtId().equals(entries.getCompound(0).getUUID("Id")) && !entries.getCompound(0).getBoolean("Available"), "Linked District listed but unavailable without arrival Gateway");
        var target = helper.absolutePos(new BlockPos(15, 1, 13)); level.setBlock(target, ModBlocks.GATEWAY_ROUTE_MARKER.get().defaultBlockState(), 3);
        var arrival = routes.register((com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity) level.getBlockEntity(target)).withDistrict(b.districtId()); routes.data().put(arrival);
        menu.sendInfo(player); helper.assertTrue(menu.info().getList("Destinations", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getBoolean("Available"), "Gateway availability refreshes against current loaded blocks");
        population.settings(b.id(), b.range(), 0, "Destination name"); menu.sendInfo(player);
        helper.assertTrue(menu.info().getList("Destinations", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getString("Name").equals("Destination name"), "Destination metadata cache invalidates on rename");
        routes.changeType(arrival.id(), RouteMarkerType.NORMAL); menu.sendInfo(player);
        helper.assertTrue(!menu.info().getList("Destinations", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getBoolean("Available"), "Normal Marker in destination District cannot receive Gateway transit");
        population.unlink(a.id(), b.id()); menu.sendInfo(player);
        helper.assertTrue(menu.info().getList("Destinations", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(), "Unlink immediately removes cached destination");
        population.link(a.id(), b.id()); population.remove(b.id()); menu.sendInfo(player);
        helper.assertTrue(menu.info().getList("Destinations", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(), "Deleting linked District removes cached destination");
        var restored = CitizenRouteSavedData.load(routes.data().save(new CompoundTag()));
        helper.assertTrue(a.districtId().equals(restored.marker(node.id()).districtId()) && b.districtId().equals(restored.marker(arrival.id()).districtId()), "All marker affiliations survive SavedData reload");
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void districtNamesInMenusFollowMembershipRenameAndDeletion(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var population = manager(helper);
        var citizen = citizen(helper, home); citizen.setNoAi(true);
        var player = player(helper, citizen.position().add(0, 0, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        var citizenMenu = new com.rexhub.createapparel.menu.CitizenMenu(40, player.getInventory(), citizen);
        player.containerMenu = citizenMenu; citizenMenu.broadcastChanges();
        helper.assertTrue(citizenMenu.districtName().equals(home.districtId().toString()), "Citizen menu displays the default UUID name");
        var pos = helper.absolutePos(new BlockPos(8, 1, 13));
        helper.getLevel().setBlock(pos, ModBlocks.GATEWAY_ROUTE_MARKER.get().defaultBlockState(), 3);
        var routes = CitizenRouteManager.get(helper.getLevel());
        var marker = routes.register((com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity) helper.getLevel().getBlockEntity(pos)).withDistrict(home.districtId());
        routes.data().put(marker);
        var markerMenu = new com.rexhub.createapparel.menu.RouteMarkerMenu(41, player.getInventory(), marker.id(), pos);
        markerMenu.sendInfo(player);
        helper.assertTrue(markerMenu.info().getString("DistrictName").equals(home.districtId().toString()), "Marker menu resolves its explicitly configured District");
        long routeVersion = routes.data().version();
        population.settings(home.id(), home.range(), 0, "中央地区");
        for (int tick = 0; tick < 20; tick++) citizenMenu.broadcastChanges();
        markerMenu.sendInfo(player);
        helper.assertTrue(citizenMenu.districtName().equals("中央地区") && markerMenu.info().getString("DistrictName").equals("中央地区"), "Both open menus refresh renamed Districts");
        helper.assertTrue(routes.data().version() == routeVersion, "District rename does not mutate the route graph");
        population.flags(citizen, true, false);
        helper.assertTrue(citizenMenu.districtName().isEmpty(), "Independent Citizen immediately hides its old District name");
        citizen.citizenState().setPopulation(new CitizenPopulationState(home.districtId(), false, false, 0)); population.update(citizen);
        for (int tick = 0; tick < 20; tick++) citizenMenu.broadcastChanges();
        helper.assertTrue(citizenMenu.districtName().equals("中央地区"), "Reassigned Citizen displays its registered District again");
        population.settings(home.id(), 1, 0, "中央地区"); markerMenu.sendInfo(player);
        helper.assertTrue(!markerMenu.info().contains("DistrictName"), "Shrinking District range automatically clears outside Marker affiliation");
        routes.data().put(marker); population.remove(home.id()); markerMenu.sendInfo(player);
        for (int tick = 0; tick < 20; tick++) citizenMenu.broadcastChanges();
        helper.assertTrue(citizenMenu.districtName().isEmpty() && !markerMenu.info().contains("DistrictName"), "Deleted District names disappear from both menus");
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void guiWireAndRelocationKeepPanelIdentityAndPopulation(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var source = gauge(helper, 11, 8, true);
        var citizen = citizen(helper, home); var population = manager(helper); var level = helper.getLevel();
        var player = player(helper, Vec3.atCenterOf(home.pos()).add(0, 0, -2));
        var menu = new CitizenGaugeMenu(11, player.getInventory(), home.id(), home.pos()); player.containerMenu = menu;
        menu.apply(player, new CitizenGaugeActionPacket(11, home.id(), population.data().version(), CitizenGaugeActionPacket.Action.CONNECT, 0, 0, null));
        helper.assertTrue(GaugeInteractions.active(player), "GUI + begins explicit wiring");
        var state = level.getBlockState(source.pos());
        var point = Vec3.atLowerCornerOf(source.pos()).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(state, 0));
        GaugeInteractions.selectedUse(level, player, new BlockHitResult(point, Direction.NORTH, source.pos(), false));
        helper.assertTrue(population.data().linked(home.id()).contains(source.id()) && !GaugeInteractions.active(player), "Clicking second wall panel creates wire and ends selection");
        menu = new CitizenGaugeMenu(12, player.getInventory(), home.id(), home.pos()); player.containerMenu = menu;
        menu.apply(player, new CitizenGaugeActionPacket(12, home.id(), population.data().version(), CitizenGaugeActionPacket.Action.RELOCATE, 0, 0, null));
        point = Vec3.atLowerCornerOf(source.pos()).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(state, 2));
        GaugeInteractions.selectedUse(level, player, new BlockHitResult(point, Direction.NORTH, source.pos(), false));
        var moved = population.data().gauge(home.id());
        helper.assertTrue(moved.pos().equals(source.pos()) && moved.slot() == 2 && level.getBlockState(home.pos()).isAir(), "Move to empty quadrant without dropping the old panel");
        helper.assertTrue(home.districtId().equals(moved.districtId()) && home.center().equals(moved.center()) && population.data().population(home.districtId()) == 1
                && home.districtId().equals(citizen.citizenState().population().districtId()), "Move preserves District and population");
        helper.assertTrue(population.loaded(moved) && population.loaded(source) && population.data().linked(home.id()).contains(source.id()), "Move preserves BE identities and links");
        helper.assertTrue(!population.relocate(home.id(), new GaugeSurface.Target(source.pos(), 0)), "Occupied quadrant cannot be overwritten");
        helper.assertTrue(population.relocate(home.id(), new GaugeSurface.Target(source.pos(), 3)), "Moving within the same block preserves other panels");
        var entity = (CitizenGaugeBlockEntity) level.getBlockEntity(source.pos());
        helper.assertTrue(entity.panelCount() == 2 && entity.panelItem(3).hasFoil() && !entity.panelItem(0).hasFoil(), "Only recorded District items have enchantment glint");
        helper.assertTrue(new ItemStack(ModItems.CITIZEN_GAUGE.get(), 2).getMaxStackSize() >= 2, "Two-result recipe fits a legal item stack");
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void wiringRejectsDifferentWallsAndRelocationRejectsProtectedTargets(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var remote = gauge(helper, 12, 10, true); var level = helper.getLevel();
        var player = player(helper, Vec3.atCenterOf(home.pos()).add(0, 0, -2));
        GaugeInteractions.beginLink(player, home.id(), InteractionHand.MAIN_HAND, false);
        var state = level.getBlockState(remote.pos()); var point = Vec3.atLowerCornerOf(remote.pos()).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(state, 0));
        GaugeInteractions.selectedUse(level, player, new BlockHitResult(point, Direction.NORTH, remote.pos(), false));
        helper.assertTrue(manager(helper).data().linked(home.id()).isEmpty(), "Panels on different wall planes do not silently acquire wires");
        var target = home.pos().east(2); level.setBlock(target.south(), Blocks.STONE.defaultBlockState(), 3);
        GaugeInteractions.begin(player, home.id(), InteractionHand.MAIN_HAND, false, true);
        player.setGameMode(GameType.SPECTATOR);
        point = Vec3.atLowerCornerOf(target).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(state, 0));
        GaugeInteractions.selectedUse(level, player, new BlockHitResult(point, Direction.NORTH, target.south(), false));
        helper.assertTrue(manager(helper).data().gauge(home.id()).pos().equals(home.pos()) && level.getBlockState(target).isAir(), "Spectator cannot relocate a Gauge");
        helper.succeed();
    }
    private static ServerPlayer player(GameTestHelper helper, Vec3 position) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "gauge-test"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        player.setGameMode(GameType.SURVIVAL); player.setPos(position); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CITIZEN_GAUGE.get())); return player;
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void creativeDiscardRequiresCreativeModeAndNeverDeletesRegisteredDistricts(GameTestHelper helper) {
        room(helper); var center = helper.absolutePos(new BlockPos(10, 0, 10)); var stack = legacyRecordedGauge(helper, center, 2);
        var data = manager(helper).data(); var player = player(helper, Vec3.atCenterOf(center).add(0, 1, 2));
        var packet = com.rexhub.createapparel.network.GaugeReservationDiscardPacket.fromStack(stack.copyWithCount(1));
        helper.assertTrue(packet != null, "Only reservation-bearing Gauges have a discard notification");
        packet.handle(player); helper.assertTrue(!data.centerAvailable(center, null), "Survival players cannot use creative discard requests");
        player.setGameMode(GameType.CREATIVE); packet.handle(player);
        helper.assertTrue(!data.centerAvailable(center, null), "Creative deletion of one split item preserves the other copy");
        packet.handle(player); helper.assertTrue(data.centerAvailable(center, null), "Creative deletion of the last item releases its center");
        var home = CitizenGauge.create(helper.absolutePos(new BlockPos(14, 1, 10)), center); data.put(home); packet.handle(player);
        helper.assertTrue(data.centerUsed(center) && data.gauge(home.id()) != null, "A replayed discard cannot remove a wall-registered district");
        data.remove(home.id()); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void destroyedGaugeReleasesOnlyTheLastItemAndKeepsRecordedTooltip(GameTestHelper helper) {
        room(helper); var center = helper.absolutePos(new BlockPos(10, 0, 10)); var level = helper.getLevel();
        var stack = legacyRecordedGauge(helper, center, 3); var data = manager(helper).data();
        var tooltip = new ArrayList<net.minecraft.network.chat.Component>();
        stack.getItem().appendHoverText(stack, level, tooltip, net.minecraft.world.item.TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 1 && tooltip.get(0).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents centerLine
                && centerLine.getKey().equals("message.createapparel.gauge.center") && center.toShortString().equals(centerLine.getArgs()[0]), "Recorded location remains visible without Shift or usage explanations");
        var first = new net.minecraft.world.entity.item.ItemEntity(level, center.getX() + .5, center.getY() + 1, center.getZ() + .5, stack.split(1));
        level.addFreshEntity(first); first.hurt(level.damageSources().lava(), 100);
        helper.assertTrue(first.isRemoved() && !data.centerAvailable(center, null) && stack.getOrCreateTag().hasUUID("GaugeCenterReservation"), "Losing one split item preserves the other two items' reservation");
        CitizenGaugeItem.releaseDestroyedReservation(first);
        helper.assertTrue(!data.centerAvailable(center, null), "Repeated destruction notification does not subtract twice");
        var last = new net.minecraft.world.entity.item.ItemEntity(level, center.getX() + 2.5, center.getY() + 1, center.getZ() + .5, stack);
        level.addFreshEntity(last); last.hurt(level.damageSources().lava(), 100);
        helper.assertTrue(last.isRemoved() && data.centerAvailable(center, null), "Destroying the last recorded items releases the unplaced center"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void expiringVoidAndKilledGaugeItemsAutomaticallyReleaseTheirCenters(GameTestHelper helper) {
        room(helper); var level = helper.getLevel(); var data = manager(helper).data();
        for (int cause = 0; cause < 3; cause++) {
            var center = helper.absolutePos(new BlockPos(10 + cause, 0, 10)); var stack = recordedGauge(helper, center, 1);
            var item = new net.minecraft.world.entity.item.ItemEntity(level, center.getX() + .5, center.getY() + 1, center.getZ() + .5, stack);
            level.addFreshEntity(item);
            if (cause == 0) { item.lifespan = 1; item.tick(); }
            if (cause == 1) { item.setPos(item.getX(), level.getMinBuildHeight() - 65, item.getZ()); item.checkBelowWorld(); }
            if (cause == 2) item.kill();
            helper.assertTrue(item.isRemoved() && data.centerAvailable(center, null), "Actual item removal releases center for cause " + cause);
        }
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void cancelledExpirationPickupAndChunkUnloadDoNotReleaseAStoredGauge(GameTestHelper helper) {
        room(helper); var level = helper.getLevel(); var data = manager(helper).data();
        var center = helper.absolutePos(new BlockPos(10, 0, 10)); var stack = recordedGauge(helper, center, 1);
        var ticket = stack.getOrCreateTag().getUUID("GaugeCenterReservation");
        var item = new net.minecraft.world.entity.item.ItemEntity(level, center.getX() + .5, center.getY() + 1, center.getZ() + .5, stack);
        level.addFreshEntity(item); item.lifespan = 1;
        java.util.function.Consumer<net.minecraftforge.event.entity.item.ItemExpireEvent> cancellation = event -> {
            if (event.getEntity() == item) { event.setCanceled(true); event.setExtraLife(100); }
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, cancellation);
        try { item.tick(); } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancellation); }
        helper.assertTrue(!item.isRemoved() && !data.centerAvailable(center, null), "Late cancellation of expiration preserves the item and reservation");
        item.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(!data.centerAvailable(center, null), "Chunk unloading does not mean the item was destroyed");
        var reloaded = new net.minecraft.world.entity.item.ItemEntity(level, center.getX() + .5, center.getY() + 1, center.getZ() + .5, stack.copy());
        level.addFreshEntity(reloaded); reloaded.setNoPickUpDelay(); var player = player(helper, reloaded.position()); player.getInventory().clearContent();
        reloaded.playerTouch(player);
        helper.assertTrue(reloaded.isRemoved() && !data.centerAvailable(center, null) && player.getInventory().getItem(0).getOrCreateTag().hasUUID("GaugeCenterReservation"), "Pickup transfers the item rather than releasing its reservation");
        var chest = helper.absolutePos(new BlockPos(14, 1, 10)); level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ((net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chest)).setItem(0, player.getInventory().removeItemNoUpdate(0));
        helper.assertTrue(!data.centerAvailable(center, null) && CitizenPopulationSavedData.load(data.save(new CompoundTag())).centerAvailable(center, ticket), "Container storage and reload preserve the reservation owner");
        data.releaseCenter(ticket); helper.succeed();
    }
    private static ItemStack recordedGauge(GameTestHelper helper, BlockPos center, int count) {
        var player = player(helper, Vec3.atCenterOf(center).add(0, 1, 2)); player.setShiftKeyDown(true);
        var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), count); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.assertTrue(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(center).add(0, .5, 0), Direction.UP, center, false))).consumesAction(), "Records a fresh center");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); return stack;
    }
    private static ItemStack legacyRecordedGauge(GameTestHelper helper, BlockPos center, int count) {
        var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), count);
        var id = manager(helper).data().reserveCenter(center, null, count, helper.getLevel().dimension().location().toString(), helper.getLevel().getGameTime());
        helper.assertTrue(id != null, "Restores a legacy multi-item reservation fixture");
        var tag = stack.getOrCreateTag(); tag.putLong("GaugeCenter", center.asLong());
        tag.putString("GaugeDimension", helper.getLevel().dimension().location().toString()); tag.putUUID("GaugeCenterReservation", id);
        return stack;
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void forgePlayerDestroyEventReleasesOnlyDestroyedItemsAndNotPlacedDistricts(GameTestHelper helper) {
        room(helper); var level = helper.getLevel(); var data = manager(helper).data();
        var center = helper.absolutePos(new BlockPos(10, 0, 10)); var stack = legacyRecordedGauge(helper, center, 3);
        var player = player(helper, Vec3.atCenterOf(center));
        net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(player, stack.split(1), InteractionHand.MAIN_HAND);
        helper.assertTrue(!data.centerAvailable(center, null), "Forge's common destruction event preserves surviving split items");
        net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(player, stack, null);
        helper.assertTrue(data.centerAvailable(center, null), "MODs emitting Forge's destruction event release the final reservation");
        var home = CitizenGauge.create(helper.absolutePos(new BlockPos(14, 1, 10)), center); data.put(home);
        net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(player, stack, InteractionHand.MAIN_HAND);
        helper.assertTrue(data.centerUsed(center) && data.gauge(home.id()) != null, "Destruction notification never deletes an active district");
        data.remove(home.id()); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void gaugeResetCraftingPreviewIsPureAndBothGridsReleaseOneConsumedItemAtATime(GameTestHelper helper) {
        room(helper); var level = helper.getLevel(); var data = manager(helper).data();
        for (int width : new int[]{2, 3}) {
            var center = helper.absolutePos(new BlockPos(10 + width, 0, 10)); var stack = legacyRecordedGauge(helper, center, 3);
            stack.setHoverName(net.minecraft.network.chat.Component.literal("Custom Gauge")); stack.getOrCreateTag().putInt("OtherModData", 42);
            var player = player(helper, Vec3.atCenterOf(center)); player.getInventory().clearContent();
            net.minecraft.world.inventory.AbstractContainerMenu menu;
            if (width == 2) menu = player.inventoryMenu;
            else {
                var table = helper.absolutePos(new BlockPos(20, 1, 10)); level.setBlock(table, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                menu = new net.minecraft.world.inventory.CraftingMenu(12, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(level, table));
            }
            player.containerMenu = menu;
            var grid = (net.minecraft.world.inventory.CraftingContainer) menu.getSlot(1).container;
            grid.setItem(grid.getContainerSize() - 1, stack);
            var recipe = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level).orElseThrow();
            helper.assertTrue(recipe instanceof com.rexhub.createapparel.recipe.GaugeResetRecipe
                    && com.simibubi.create.AllRecipeTypes.shouldIgnoreInAutomation(recipe), "Reset uses the registered manual-only recipe in either grid");
            for (int preview = 0; preview < 5; preview++) {
                helper.assertTrue(recipe.matches(grid, level), "Preview continues matching without consuming input");
                var output = recipe.assemble(grid, level.registryAccess()); recipe.getRemainingItems(grid);
                helper.assertTrue(output.getCount() == 1 && output.getOrCreateTag().getInt("OtherModData") == 42
                        && output.getHoverName().getString().equals("Custom Gauge") && !output.hasFoil()
                        && !output.getOrCreateTag().contains("GaugeCenterReservation") && !output.getOrCreateTag().contains("GaugeDimension"), "Preview strips only gauge recording and retains unrelated metadata");
            }
            helper.assertTrue(!data.centerAvailable(center, null) && stack.getCount() == 3 && stack.hasFoil(), "Preview never releases a reservation or mutates input");
            var resultSlot = menu.getSlot(0); var first = resultSlot.remove(1); resultSlot.onTake(player, first);
            helper.assertTrue(grid.getItem(grid.getContainerSize() - 1).getCount() == 2 && !data.centerAvailable(center, null), "Taking one result preserves the other two reservations");
            var second = resultSlot.remove(1); resultSlot.onTake(player, second);
            helper.assertTrue(!data.centerAvailable(center, null), "Taking the second result preserves the final recorded item");
            var last = resultSlot.remove(1); resultSlot.onTake(player, last);
            helper.assertTrue(data.centerAvailable(center, null) && grid.isEmpty(), "Final committed craft releases the reservation");
        }
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void shiftCraftResetClearsWholeStackAndFlowWithoutDeletingPlacedDistrict(GameTestHelper helper) {
        room(helper); var level = helper.getLevel(); var data = manager(helper).data();
        var center = helper.absolutePos(new BlockPos(10, 0, 10)); var stack = legacyRecordedGauge(helper, center, 4);
        var stale = stack.copyWithCount(1);
        var player = player(helper, Vec3.atCenterOf(center)); player.getInventory().clearContent();
        var menu = player.inventoryMenu; var grid = menu.getCraftSlots(); grid.setItem(0, stack);
        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
        helper.assertTrue(grid.isEmpty() && data.centerAvailable(center, null)
                && player.getInventory().countItem(ModItems.CITIZEN_GAUGE.get()) == 4, "Native shift crafting consumes and resets the complete stack exactly once per item");
        var home = CitizenGauge.create(helper.absolutePos(new BlockPos(14, 1, 10)), center); data.put(home);
        grid.setItem(0, stale);
        var result = menu.getSlot(0).remove(1); menu.getSlot(0).onTake(player, result);
        helper.assertTrue(data.gauge(home.id()) != null && data.centerUsed(center), "Resetting a stale item never deletes a wall district");
        var flow = new ItemStack(ModItems.CITIZEN_GAUGE.get()); flow.getOrCreateTag().putBoolean("GaugeOutflow", true); grid.setItem(0, flow);
        helper.assertTrue(!menu.getSlot(0).getItem().isEmpty() && !menu.getSlot(0).getItem().hasTag(), "Outflow also resets to the blank default influx gauge");
        grid.setItem(1, new ItemStack(net.minecraft.world.item.Items.STICK));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Extra ingredients prevent reset");
        grid.clearContent(); grid.setItem(0, new ItemStack(ModItems.CITIZEN_GAUGE.get()));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Blank gauges do not produce a self-loop recipe");
        data.remove(home.id()); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void unplacedRecordedItemRejectsAnotherBlankGaugeBeforeWallPlacement(GameTestHelper helper) {
        room(helper); var level = helper.getLevel(); var data = manager(helper).data();
        var center = helper.absolutePos(new BlockPos(10, 0, 10));
        var owner = player(helper, Vec3.atCenterOf(center).add(0, 1, 2)); owner.setShiftKeyDown(true);
        var ground = new BlockHitResult(Vec3.atCenterOf(center).add(0, .5, 0), Direction.UP, center, false);
        var recorded = owner.getMainHandItem();
        helper.assertTrue(recorded.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, ground)).consumesAction(), "First blank item records and reserves the ground");
        var ticket = recorded.getOrCreateTag().getUUID("GaugeCenterReservation");
        helper.assertTrue(!data.centerUsed(center) && !data.centerAvailable(center, null), "Reservation exists before any wall district exists");
        var second = player(helper, owner.position()); second.setShiftKeyDown(true);
        var blank = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 2); blank.getOrCreateTag().putBoolean("GaugeOutflow", true);
        second.setItemInHand(InteractionHand.MAIN_HAND, blank); var before = blank.copy();
        helper.assertTrue(blank.useOn(new UseOnContext(second, InteractionHand.MAIN_HAND, ground)) == net.minecraft.world.InteractionResult.FAIL
                && ItemStack.matches(before, blank), "A different blank item cannot record the reserved center; NBT and count remain unchanged");
        var reloaded = CitizenPopulationSavedData.load(data.save(new CompoundTag()));
        helper.assertTrue(!reloaded.centerAvailable(center, null) && reloaded.centerAvailable(center, ticket), "Reservation owner survives SavedData reload");
        owner.setShiftKeyDown(false);
        helper.assertTrue(!recorded.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, ground)).consumesAction()
                && data.centerAvailable(center, ticket) && !data.centerAvailable(center, null), "Failed floor placement retains the reservation");
        var wall = helper.absolutePos(new BlockPos(20, 1, 10)); level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        var placement = new BlockHitResult(Vec3.atCenterOf(wall).add(0, 0, -.5), Direction.NORTH, wall, false);
        blank.getOrCreateTag().putLong("GaugeCenter", center.asLong());
        blank.getOrCreateTag().putString("GaugeDimension", level.dimension().location().toString());
        second.setShiftKeyDown(false); before = blank.copy();
        helper.assertTrue(blank.useOn(new UseOnContext(second, InteractionHand.MAIN_HAND, placement)) == net.minecraft.world.InteractionResult.FAIL
                && ItemStack.matches(before, blank) && level.getBlockState(wall.north()).isAir(), "Legacy recorded item cannot steal another item's reservation");
        helper.assertTrue(recorded.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, placement)).consumesAction(), "Reservation owner can place on the wall");
        var home = data.at(wall.north());
        helper.assertTrue(home != null && center.equals(home.center()) && data.centerUsed(center), "Wall placement converts the reservation into a district");
        level.removeBlock(wall.north(), false);
        helper.assertTrue(data.centerAvailable(center, null), "Removing the district leaves no stale pending reservation"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void rerecordingMovesOnlyTheOwnersReservationAndKeepsItOnFailure(GameTestHelper helper) {
        room(helper); var level = helper.getLevel(); var data = manager(helper).data();
        var a = helper.absolutePos(new BlockPos(10, 0, 10)); var b = a.east();
        var player = player(helper, Vec3.atCenterOf(a).add(0, 1, 2)); player.setShiftKeyDown(true); var stack = player.getMainHandItem();
        var first = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(a).add(0, .5, 0), Direction.UP, a, false));
        var next = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(b).add(0, .5, 0), Direction.UP, b, false));
        helper.assertTrue(stack.useOn(first).consumesAction(), "First reservation succeeds"); var ticket = stack.getOrCreateTag().getUUID("GaugeCenterReservation");
        helper.assertTrue(stack.useOn(first).consumesAction() && ticket.equals(stack.getOrCreateTag().getUUID("GaugeCenterReservation")), "Owner can record the same center idempotently");
        var other = data.reserveCenter(b, null); var before = stack.copy();
        helper.assertTrue(stack.useOn(next) == net.minecraft.world.InteractionResult.FAIL && ItemStack.matches(before, stack)
                && data.centerAvailable(a, ticket) && !data.centerAvailable(a, null), "Failed rerecording preserves the old claim and item");
        data.releaseCenter(other);
        helper.assertTrue(stack.useOn(next).consumesAction() && data.centerAvailable(a, null) && !data.centerAvailable(b, null), "Successful rerecording releases only the previous center");
        data.releaseCenter(stack.getOrCreateTag().getUUID("GaugeCenterReservation")); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void usedDistrictCenterRejectsRecordingAndStaleItemPlacementWithoutChangingItem(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var level = helper.getLevel(); var population = manager(helper);
        var center = home.center(); level.setBlock(center, Blocks.STONE.defaultBlockState(), 3);
        var player = player(helper, Vec3.atCenterOf(center).add(0, 1, 2)); player.setShiftKeyDown(true);
        var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 2); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.getOrCreateTag().putBoolean("GaugeOutflow", true);
        var before = stack.copy();
        var ground = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(center).add(0, .5, 0), Direction.UP, center, false));
        helper.assertTrue(stack.useOn(ground) == net.minecraft.world.InteractionResult.FAIL && ItemStack.matches(before, stack), "Used center rejects recording without changing mode, tags, or item count");
        var free = center.east(); level.setBlock(free, Blocks.STONE.defaultBlockState(), 3);
        var nearby = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(free).add(0, .5, 0), Direction.UP, free, false));
        helper.assertTrue(stack.useOn(nearby).consumesAction() && free.equals(CitizenGaugeItem.center(stack, level)), "Other centers remain recordable even inside the existing district range");
        before = stack.copy();
        helper.assertTrue(stack.useOn(ground) == net.minecraft.world.InteractionResult.FAIL && ItemStack.matches(before, stack), "Rejected rerecording preserves the old center");
        stack.getOrCreateTag().putLong("GaugeCenter", center.asLong()); before = stack.copy(); player.setShiftKeyDown(false);
        var wall = helper.absolutePos(new BlockPos(22, 1, 8)); level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        player.setPos(Vec3.atCenterOf(wall).add(0, 0, -2));
        var placement = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(wall).add(0, 0, -.5), Direction.NORTH, wall, false));
        helper.assertTrue(stack.useOn(placement) == net.minecraft.world.InteractionResult.FAIL && level.getBlockState(wall.north()).isAir()
                && ItemStack.matches(before, stack), "Stale recorded items cannot duplicate the center or consume an item");
        var far = new BlockPos(27999800, 71, 27999800); population.data().put(home.relocate(far, 0)); player.setShiftKeyDown(true);
        helper.assertTrue(stack.useOn(ground) == net.minecraft.world.InteractionResult.FAIL
                && !level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4), "Unloaded district still reserves its center without loading its chunk");
        population.remove(home.id());
        helper.assertTrue(stack.useOn(ground).consumesAction() && center.equals(CitizenGaugeItem.center(stack, level)), "Removing the district releases its center for reuse");
        population.data().releaseCenter(stack.getOrCreateTag().getUUID("GaugeCenterReservation")); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void centerRecordingPlacementAndBreakingKeepCoordinatesButNotIdentity(GameTestHelper helper) {
        room(helper); var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(10, 0, 10));
        var player = player(helper, Vec3.atCenterOf(pos).add(0, 1, 2)); player.setShiftKeyDown(true);
        var stack = player.getMainHandItem(); var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
        helper.assertTrue(stack.useOn(context).consumesAction() && pos.equals(CitizenGaugeItem.center(stack, level)), "Sneak use records center");
        helper.assertTrue(!level.getBlockState(pos.above()).is(ModBlocks.CITIZEN_GAUGE.get()), "Recording does not place a Gauge");
        player.setShiftKeyDown(false);
        helper.assertTrue(!stack.useOn(context).consumesAction(), "Normal use does not place on a floor");
        var wall = pos.above(); var placed = wall.north(); level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        var state = ModBlocks.CITIZEN_GAUGE.get().defaultBlockState();
        var click = Vec3.atLowerCornerOf(placed).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(state, 0));
        context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(click, Direction.NORTH, wall, false));
        helper.assertTrue(stack.useOn(context).consumesAction(), "Normal use places on a wall");
        var gauge = manager(helper).data().at(placed);
        helper.assertTrue(gauge != null && pos.equals(gauge.center()) && !gauge.source(), "Wall placement registers recorded center");
        var item = ((CitizenGaugeBlockEntity) level.getBlockEntity(placed)).panelItem(0);
        helper.assertTrue(pos.equals(CitizenGaugeItem.center(item, level)), "Recovered panel retains center and dimension");
        var oldId = gauge.id(); level.removeBlock(placed, false);
        helper.assertTrue(manager(helper).data().gauge(oldId) == null, "Breaking invalidates the Gauge");
        level.setBlock(placed, state, 3);
        var replacement = manager(helper).register((CitizenGaugeBlockEntity) level.getBlockEntity(placed));
        helper.assertTrue(replacement.source() && !replacement.id().equals(oldId), "Fresh source gets a new identity"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void rangeCrossingDoesNotChangeMembershipAndFlagsKeepTheirMeaning(GameTestHelper helper) {
        room(helper); var a = gauge(helper, 8, 8, false); var b = gauge(helper, 25, 8, false); var citizen = citizen(helper, a);
        citizen.setPos(Vec3.atBottomCenterOf(b.center())); manager(helper).update(citizen);
        helper.assertTrue(a.districtId().equals(citizen.citizenState().population().districtId()) && manager(helper).data().population(b.districtId()) == 0, "Physical presence does not reassign population");
        manager(helper).flags(citizen, false, true);
        helper.assertTrue(manager(helper).data().population(a.districtId()) == 1, "FORCE remains population");
        helper.assertTrue(!citizen.intents().request(CitizenIntent.district(CitizenIntent.Source.GAUGE_TRANSFER, b.districtId(), helper.getLevel().getGameTime(), 1000)), "FORCE rejects automatic transfers");
        manager(helper).flags(citizen, true, true);
        helper.assertTrue(citizen.citizenState().population().districtId() == null && manager(helper).data().population(a.districtId()) == 0, "Independent excludes membership");
        manager(helper).flags(citizen, false, false);
        helper.assertTrue(b.districtId().equals(citizen.citizenState().population().districtId()), "Leaving independence permits physical assignment"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void unloadReloadAndDeathKeepThePersistentRegistryConsistent(GameTestHelper helper) {
        room(helper); var a = gauge(helper, 10, 10, false); var citizen = citizen(helper, a); var manager = manager(helper);
        manager.leave(citizen, false);
        helper.assertTrue(manager.data().population(a.districtId()) == 1, "Unload retains logical population");
        var restored = CitizenPopulationSavedData.load(manager.data().save(new CompoundTag()));
        helper.assertTrue(restored.population(a.districtId()) == 1, "World reload retains unloaded population");
        citizen.citizenState().setPopulation(CitizenPopulationState.DEFAULT); manager.join(citizen); manager.join(citizen);
        helper.assertTrue(manager.data().population(a.districtId()) == 1 && a.districtId().equals(citizen.citizenState().population().districtId()), "Rejoin reconciles registry without duplicates");
        citizen.discard();
        helper.assertTrue(manager.data().citizen(citizen.getUUID()) == null && manager.data().population(a.districtId()) == 0, "Permanent removal removes logical population"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void districtDestinationsAreSafeAndDoNotForceLoadRemoteChunks(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 12, 12, false); var citizen = citizen(helper, home);
        var destination = DistrictDestinations.choose(helper.getLevel(), citizen, home);
        helper.assertTrue(destination != null && home.contains(destination) && CitizenRouteManager.get(helper.getLevel()).safeEndpoint(citizen, BlockPos.containing(destination)), "Safe location in District");
        helper.assertTrue(manager(helper).data().owns(home, destination), "Arrival column belongs to the non-overlapping District partition");
        var farPos = new BlockPos(20000000, 70, 20000000);
        var far = new CitizenGauge(UUID.randomUUID(), farPos, UUID.randomUUID(), farPos, 2, 10, 0, false);
        helper.assertTrue(DistrictDestinations.choose(helper.getLevel(), citizen, far) == null && !helper.getLevel().getChunkSource().hasChunk(farPos.getX() >> 4, farPos.getZ() >> 4), "Destination search never force-loads"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 600)
    public static void districtTransferChangesMembershipOnlyOnArrivalAndPreservesBaseMode(GameTestHelper helper) {
        room(helper); var from = gauge(helper, 8, 10, false); var to = gauge(helper, 25, 10, false); var citizen = citizen(helper, from);
        manager(helper).link(from.id(), to.id()); manager(helper).settings(to.id(), 2, 2);
        helper.runAtTickTime(500, () -> com.mojang.logging.LogUtils.getLogger().info("GAUGE_TRANSFER_DIAGNOSTIC position={} district={} intent={} route={} destination={} failures={} chains={} incoming={} cooldown={}",
                citizen.position(), citizen.citizenState().population().districtId(), citizen.intents().active(), citizen.routeMovement().state(),
                citizen.routeMovement().currentDestination(), citizen.routeMovement().consecutiveFailures(), manager(helper).chainCount(), manager(helper).incoming(to.id()),
                manager(helper).data().gauge(from.id()).cooldownUntil()));
        helper.succeedWhen(() -> {
            helper.assertTrue(to.districtId().equals(citizen.citizenState().population().districtId()), "One-hop transfer arrived");
            helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.STAND && citizen.intents().active() == null, "Base STAND survives temporary Intent");
            helper.assertTrue(manager(helper).data().population(from.districtId()) == 0 && manager(helper).data().population(to.districtId()) == 1, "Exactly one logical resident moved");
            helper.assertTrue(citizen.citizenState().population().transferCooldownUntil() > helper.getLevel().getGameTime(), "Transfer applies Citizen cooldown");
            manager(helper).settings(to.id(), 2, 0); manager(helper).unlink(from.id(), to.id());
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 400)
    public static void sourceSuppliesOnlyItsDirectNeighborAndNeverSpawnsInTheRemoteRequester(GameTestHelper helper) {
        room(helper); var source = gauge(helper, 5, 5, true); var middle = gauge(helper, 15, 12, false); var remote = gauge(helper, 29, 12, false);
        manager(helper).link(source.id(), middle.id()); manager(helper).link(middle.id(), remote.id()); manager(helper).settings(remote.id(), 2, 2);
        helper.succeedWhen(() -> {
            helper.assertTrue(manager(helper).data().population(middle.districtId()) == 1, "Source generated in directly linked District");
            helper.assertTrue(manager(helper).data().population(remote.districtId()) == 0 && manager(helper).incoming(remote.id()) == 1, "Remote supply remains reserved, without direct spawn");
            var id = manager(helper).data().residents(middle.districtId()).iterator().next();
            var citizen = (Citizen) helper.getLevel().getEntity(id);
            helper.assertTrue(citizen != null && middle.contains(citizen.position()) && citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WANDER
                    && citizen.citizenState().behavior().unlimitedWander()
                    && !citizen.citizenState().loadout().getStackInSlot(com.rexhub.createapparel.garment.GarmentSlot.TOP.ordinal()).isEmpty(), "Source Citizen is clothed and wanders in receiving District");
            manager(helper).unlink(source.id(), middle.id()); manager(helper).settings(remote.id(), 2, 0);
            helper.assertTrue(manager(helper).incoming(remote.id()) == 0 && manager(helper).outgoing(middle.id()) == 0, "Link removal releases all future hop reservations");
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 400)
    public static void playerOverrideCancelsTransferAndAllReservations(GameTestHelper helper) {
        room(helper); var from = gauge(helper, 8, 10, false); var to = gauge(helper, 28, 10, false); var citizen = citizen(helper, from);
        manager(helper).link(from.id(), to.id()); manager(helper).settings(to.id(), 2, 2);
        helper.succeedWhen(() -> {
            helper.assertTrue(manager(helper).reserved(citizen.getUUID()), "Citizen was reserved");
            helper.assertTrue(!citizen.intents().request(CitizenIntent.district(CitizenIntent.Source.FASHION, to.districtId(), helper.getLevel().getGameTime(), 1000)), "New Fashion cannot steal transfer reservation");
            citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.STAND, citizen.blockPosition(), helper.getLevel().dimension(), 8));
            helper.assertTrue(!manager(helper).reserved(citizen.getUUID()) && manager(helper).incoming(to.id()) == 0 && manager(helper).outgoing(from.id()) == 0, "Player instruction releases entire Chain");
            helper.assertTrue(from.districtId().equals(citizen.citizenState().population().districtId()), "Interruption does not move population");
            manager(helper).settings(to.id(), 2, 0); manager(helper).unlink(from.id(), to.id());
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void temporaryDistrictVisitsKeepHomeAndRejectHigherPriorityReplacement(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 10, false); var target = gauge(helper, 25, 10, false); var citizen = citizen(helper, home);
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(citizen.intents().request(CitizenIntent.district(CitizenIntent.Source.FASHION, target.districtId(), now, 1000)), "Fashion uses central Intent");
        helper.assertTrue(!citizen.intents().request(CitizenIntent.district(CitizenIntent.Source.GAUGE_TRANSFER, target.districtId(), now, 1000)), "Gauge cannot take a Fashion Citizen");
        helper.assertTrue(home.districtId().equals(citizen.citizenState().population().districtId()) && citizen.citizenState().behavior().mode() == CitizenBehaviorMode.STAND, "Temporary travel retains base mode and membership");
        citizen.intents().cancel(); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void gaugeMenuValidatesRangesVersionsIdentityAndDimension(GameTestHelper helper) {
        room(helper); var gauge = gauge(helper, 10, 10, false); var player = player(helper, Vec3.atCenterOf(gauge.pos()).add(0, 0, 2));
        var menu = new CitizenGaugeMenu(17, player.getInventory(), gauge.id(), gauge.pos()); player.containerMenu = menu;
        helper.assertTrue(menu.stillValid(player), "Nearby owner can edit");
        var version = manager(helper).data().version();
        new CitizenGaugeActionPacket(17, gauge.id(), version, CitizenGaugeActionPacket.Action.SETTINGS, 3, 0, null).handle(player);
        helper.assertTrue(manager(helper).data().gauge(gauge.id()).range() == 3, "Valid update accepted");
        new CitizenGaugeActionPacket(17, gauge.id(), version, CitizenGaugeActionPacket.Action.SETTINGS, 4, 4, null).handle(player);
        helper.assertTrue(manager(helper).data().gauge(gauge.id()).range() == 3, "Stale edit rejected");
        new CitizenGaugeActionPacket(17, gauge.id(), manager(helper).data().version(), CitizenGaugeActionPacket.Action.SETTINGS, 257, -1, null).handle(player);
        helper.assertTrue(manager(helper).data().gauge(gauge.id()).range() == 3, "Invalid limits rejected");
        new CitizenGaugeActionPacket(17, gauge.id(), manager(helper).data().version(), CitizenGaugeActionPacket.Action.SETTINGS, 3, 4, null).handle(player);
        helper.assertTrue(manager(helper).data().gauge(gauge.id()).range() == 3 && manager(helper).data().gauge(gauge.id()).target() == 0, "Server rejects targets above radius");
        var current = manager(helper).data().version();
        new CitizenGaugeActionPacket(17, gauge.id(), current, CitizenGaugeActionPacket.Action.SETTINGS, 3, 0, null, "Central District").handle(player);
        helper.assertTrue(manager(helper).data().gauge(gauge.id()).name().equals("Central District"), "District name saved authoritatively");
        new CitizenGaugeActionPacket(17, gauge.id(), current, CitizenGaugeActionPacket.Action.SETTINGS, 4, 0, null, "Stale").handle(player);
        helper.assertTrue(manager(helper).data().gauge(gauge.id()).name().equals("Central District"), "Stale rename rejected");
        new CitizenGaugeActionPacket(17, gauge.id(), manager(helper).data().version(), CitizenGaugeActionPacket.Action.SETTINGS, 4, 1, null, "bad\nname").handle(player);
        helper.assertTrue(manager(helper).data().gauge(gauge.id()).range() == 3 && manager(helper).data().gauge(gauge.id()).name().equals("Central District"), "Invalid rename does not partially save settings");
        player.setPos(player.position().add(100, 0, 0)); helper.assertTrue(!menu.stillValid(player), "Distant packets rejected"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void districtStatusSynchronizesNamePopulationAndLampPerPanel(GameTestHelper helper) {
        room(helper); var gauge = gauge(helper, 10, 10, false); var population = manager(helper);
        population.settings(gauge.id(), 3, 0, "Central District");
        var entity = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(gauge.pos()); entity.initialize(1, null);
        var received = new CitizenGaugeBlockEntity(gauge.pos(), entity.getBlockState()); received.load(entity.getUpdateTag());
        helper.assertTrue(received.districtStatus(0) != null && received.districtStatus(0).name().equals("Central District")
                && received.districtStatus(0).satisfied(), "Zero target and zero population are green");
        helper.assertTrue(received.districtStatus(0).districtId().equals(gauge.districtId()), "District icon UUID is synchronized with its own panel");
        helper.assertTrue(received.hasPanel(1) && received.districtStatus(1) == null, "Source sibling has no District lamp or hover status");
        var resident = UUID.randomUUID(); population.data().register(resident, new CitizenPopulationState(gauge.districtId(), false, false, 0));
        received.load(entity.getUpdateTag());
        helper.assertTrue(received.districtStatus(0).population() == 1 && !received.districtStatus(0).satisfied(), "Excess population is synchronized red");
        population.settings(gauge.id(), 3, 2); received.load(entity.getUpdateTag());
        helper.assertTrue(received.districtStatus(0).target() == 2 && !received.districtStatus(0).satisfied(), "Deficit population is red and rename is retained");
        helper.assertTrue(received.districtStatus(0).name().equals("Central District"), "Settings preserve District name");
        helper.assertTrue(received.districtStatus(0).districtId().equals(gauge.districtId()), "Population and settings do not change the District icon identity");
        population.data().removeCitizen(resident); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void gaugeLinksControlImplicitGatewaysAndDeletionDisablesThem(GameTestHelper helper) {
        room(helper); var a = gauge(helper, 8, 10, false); var b = gauge(helper, 25, 10, false); var manager = manager(helper);
        var routes = CitizenRouteManager.get(helper.getLevel());
        var pos = helper.absolutePos(new BlockPos(8, 1, 15)); helper.getLevel().setBlock(pos, ModBlocks.GATEWAY_ROUTE_MARKER.get().defaultBlockState(), 3);
        var node = routes.register((com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity) helper.getLevel().getBlockEntity(pos)).withDistrict(a.districtId()); routes.data().put(node);
        helper.assertTrue(routes.gatewayEnabled(node) && manager.linkedDistricts(a.districtId()).isEmpty(), "Valid District enables Gateway without inventing links");
        manager.link(a.id(), b.id()); helper.assertTrue(manager.linkedDistricts(a.districtId()).equals(List.of(b.districtId())), "Gauge Link is Gateway topology");
        manager.unlink(a.id(), b.id()); helper.assertTrue(manager.linkedDistricts(a.districtId()).isEmpty(), "Unlink takes effect immediately");
        manager.remove(a.id()); helper.assertTrue(!routes.gatewayEnabled(node) && helper.getLevel().getBlockState(pos).is(ModBlocks.GATEWAY_ROUTE_MARKER.get()), "Deleting District disables but retains Gateway"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void removingDistrictPanelImmediatelyDeletesItsRegistrationButNotCitizenOrSibling(GameTestHelper helper) {
        room(helper); var district = gauge(helper, 10, 10, false); var population = manager(helper);
        var entity = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(district.pos()); entity.initialize(1, null);
        var sibling = population.data().at(district.pos(), 1); population.link(district.id(), sibling.id());
        var citizen = citizen(helper, district); population.flags(citizen, false, true);
        entity.removePanel(0);
        helper.assertTrue(population.data().gauge(district.id()) == null && population.data().district(district.districtId()) == null
                && population.data().at(district.pos(), 0) == null, "Panel disappearance immediately deletes Gauge and District registration");
        helper.assertTrue(entity.hasPanel(1) && population.data().gauge(sibling.id()) != null && population.data().linked(sibling.id()).isEmpty(), "Sibling remains but its obsolete link is removed");
        helper.assertTrue(citizen.isAlive() && citizen.citizenState().population().districtId() == null && citizen.citizenState().population().force(), "Citizen survives unassigned with flags preserved");
        helper.getLevel().removeBlock(district.pos(), false);
        helper.assertTrue(population.data().gauge(sibling.id()) == null, "Removing the remaining block immediately removes the sibling"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 200)
    public static void missingLoadedGaugeIsPrunedWithoutDeletingUnloadedDistrict(GameTestHelper helper) {
        room(helper); var missing = gauge(helper, 10, 10, false); var retained = gauge(helper, 20, 10, false); var population = manager(helper);
        var far = new BlockPos(28000000, 71, 28000000);
        helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4), "Unloaded test region stays unloaded");
        population.data().put(retained.relocate(far, 0)); population.link(missing.id(), retained.id());
        helper.getLevel().removeBlockEntity(missing.pos());
        helper.succeedWhen(() -> {
            helper.assertTrue(population.data().gauge(missing.id()) == null && population.data().district(missing.districtId()) == null, "Missing loaded panel is pruned without waiting for a chunk reload");
            helper.assertTrue(population.data().gauge(retained.id()) != null && population.data().linked(retained.id()).isEmpty(), "Unloaded District registration is retained without obsolete links");
            helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4), "Validation never force-loads an unloaded chunk");
            population.remove(retained.id());
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 900)
    public static void multiHopSupplyUsesSeparateCitizensAndKeepsIntermediatePopulation(GameTestHelper helper) {
        room(helper); var a = gauge(helper, 5, 10, false); var b = gauge(helper, 18, 10, false); var c = gauge(helper, 31, 10, false);
        var first = citizen(helper, a); var second = citizen(helper, b); var population = manager(helper);
        var virtual = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        population.data().register(virtual.get(0), new CitizenPopulationState(a.districtId(), false, false, 0));
        population.data().register(virtual.get(1), new CitizenPopulationState(a.districtId(), false, false, 0));
        population.data().register(virtual.get(2), new CitizenPopulationState(b.districtId(), false, false, 0));
        population.data().register(virtual.get(3), new CitizenPopulationState(c.districtId(), false, false, 0));
        population.settings(a.id(), 2, 5); population.settings(b.id(), 2, 5); population.settings(c.id(), 2, 5);
        population.link(a.id(), b.id()); population.link(b.id(), c.id());
        var advanced = new boolean[1];
        helper.onEachTick(() -> {
            if (!advanced[0] && b.districtId().equals(first.citizenState().population().districtId())) {
                helper.assertTrue(first.citizenState().population().transferCooldownUntil() > helper.getLevel().getGameTime(), "First Citizen is on transfer cooldown");
                helper.assertTrue(population.data().population(b.districtId()) == 3 && population.outgoing(b.id()) == 1, "Intermediate District retains next-hop reservation");
                population.data().put(population.data().gauge(b.id()).cooldown(0)); population.data().put(population.data().gauge(c.id()).cooldown(0)); advanced[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(advanced[0] && c.districtId().equals(second.citizenState().population().districtId()), "Separate eligible Citizen completed second hop");
            helper.assertTrue(b.districtId().equals(first.citizenState().population().districtId()) && population.data().population(a.districtId()) == 2
                    && population.data().population(b.districtId()) == 2 && population.data().population(c.districtId()) == 2, "3/5, 2/5, 1/5 stabilizes at 2/5 each");
            population.settings(a.id(), 2, 0); population.settings(b.id(), 2, 0); population.settings(c.id(), 2, 0);
            population.unlink(a.id(), b.id()); population.unlink(b.id(), c.id()); virtual.forEach(population.data()::removeCitizen);
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 400)
    public static void sourcePausesAtTheLoadedLimitAndResumesWithoutDeletingCitizens(GameTestHelper helper) {
        room(helper); var source = gauge(helper, 5, 5, true); var target = gauge(helper, 20, 10, false); var population = manager(helper);
        var fillers = new ArrayList<Citizen>();
        int limit = com.rexhub.createapparel.config.ModConfigs.COMMON.maxLoadedCitizensPerDimension.get();
        while (population.loadedCount() < limit) {
            var citizen = ModEntities.CITIZEN.get().create(helper.getLevel());
            if (citizen == null) throw new IllegalStateException("Missing Citizen");
            int index = fillers.size();
            citizen.setNoAi(true); citizen.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2 + index % 17 * 2, 1, 2 + index / 17 * 2))));
            citizen.citizenState().setPopulation(new CitizenPopulationState(null, true, false, 0));
            helper.getLevel().addFreshEntity(citizen); fillers.add(citizen);
        }
        population.link(source.id(), target.id()); population.settings(target.id(), 2, 2);
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(population.data().population(target.districtId()) == 0 && fillers.stream().allMatch(Citizen::isAlive), "Loaded cap pauses Source and retains existing Citizens");
            fillers.forEach(Citizen::discard);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 100 && population.data().population(target.districtId()) == 1, "Source resumes when loaded population falls");
            population.settings(target.id(), 2, 0); population.unlink(source.id(), target.id());
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 700)
    public static void realGaugeGatewayTransitKeepsMembershipUntilTheFinalDistrictArrival(GameTestHelper helper) {
        room(helper); var a = gauge(helper, 5, 10, false); var b = gauge(helper, 27, 10, false); var population = manager(helper); var citizen = citizen(helper, a);
        population.settings(b.id(), 8, 0);
        var routes = CitizenRouteManager.get(helper.getLevel());
        var departurePos = helper.absolutePos(new BlockPos(7, 1, 13)); var arrivalPos = helper.absolutePos(new BlockPos(27, 1, 19));
        for (var pos : List.of(departurePos, arrivalPos)) helper.getLevel().setBlock(pos, ModBlocks.GATEWAY_ROUTE_MARKER.get().defaultBlockState(), 3);
        var departure = routes.register((com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity) helper.getLevel().getBlockEntity(departurePos))
                .withDistrict(a.districtId()).withRegion(0.5, 1);
        var arrival = routes.register((com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity) helper.getLevel().getBlockEntity(arrivalPos))
                .withDistrict(b.districtId()).withRegion(0.5, 1);
        routes.data().put(departure); routes.data().put(arrival);
        population.link(a.id(), b.id()); population.settings(b.id(), 8, 2);
        var transited = new boolean[1];
        helper.runAtTickTime(650, () -> com.mojang.logging.LogUtils.getLogger().info("GAUGE_GATEWAY_PROGRESS tick={} pos={} intent={} route={} goal={} nav={} failures={} requests={} chains={} populations={}/{} transited={}",
                helper.getTick(), citizen.position(), citizen.intents().active(), citizen.routeMovement().state(), citizen.routeMovement().currentDestination(),
                citizen.getNavigation().getTargetPos(), citizen.routeMovement().consecutiveFailures(), population.requestCount(), population.chainCount(),
                population.data().population(a.districtId()), population.data().population(b.districtId()), transited[0]));
        helper.onEachTick(() -> {
            if (!transited[0] && citizen.position().distanceToSqr(arrival.center()) < 2) {
                helper.assertTrue(a.districtId().equals(citizen.citizenState().population().districtId()), "Gateway itself did not change logical membership"); transited[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(b.districtId().equals(citizen.citizenState().population().districtId()), "Final District arrival completed transfer");
            helper.assertTrue(transited[0], "Used implicit Gateway from actual Gauge Link");
            population.settings(b.id(), 8, 0); population.unlink(a.id(), b.id());
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void randomHomeVisitsAreSuppressedWithoutBlockingExplicitIntents(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var other = gauge(helper, 20, 8, false); var population = manager(helper);
        population.link(home.id(), other.id());
        int originalTicks = ModConfigs.COMMON.populationAutonomyTicks.get();
        double originalChance = ModConfigs.COMMON.populationVisitChance.get();
        int visited = 0, stayed = 0;
        try {
            ModConfigs.COMMON.populationAutonomyTicks.set(20); ModConfigs.COMMON.populationVisitChance.set(1.0);
            for (int index = 0; index < 12; index++) {
                var citizen = citizen(helper, home);
                try {
                    citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, home.center(), helper.getLevel().dimension(), 0));
                    CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 40);
                    long seed = index * 7919L;
                    double roll = RandomSource.create(seed).nextDouble();
                    citizen.getRandom().setSeed(seed); citizen.intents().tick();
                    var intent = citizen.intents().active();
                    boolean expectedVisit = roll < 2.0 / 3;
                    helper.assertTrue((intent != null) == expectedVisit, "Home departure probability is reduced to two thirds: roll=" + roll + " intent=" + intent);
                    if (expectedVisit) {
                        helper.assertTrue(intent.source() == CitizenIntent.Source.IMPULSE_VISIT && other.districtId().equals(intent.district()), "Random visit still selects the linked district");
                        visited++;
                    } else stayed++;
                    citizen.intents().cancel();
                    for (var source : List.of(CitizenIntent.Source.RETURN_HOME, CitizenIntent.Source.GAUGE_TRANSFER, CitizenIntent.Source.PLAYER)) {
                        var target = source == CitizenIntent.Source.RETURN_HOME ? home : other;
                        helper.assertTrue(citizen.intents().request(CitizenIntent.district(source, target.districtId(), helper.getLevel().getGameTime(), 1000)),
                                "Explicit destination is not rejected by the random departure cost: " + source);
                        citizen.intents().cancel();
                    }
                } finally { citizen.intents().cancel(); citizen.discard(); }
            }
            helper.assertTrue(visited > 0 && stayed > 0, "Deterministic samples exercise both visits and suppressed departures");
        } finally {
            ModConfigs.COMMON.populationAutonomyTicks.set(originalTicks); ModConfigs.COMMON.populationVisitChance.set(originalChance);
        }
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void populationFlagsAndHomeDeletionPreserveExplicitPlayerTravel(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 10, false); var citizen = citizen(helper, home);
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(citizen.intents().request(new CitizenIntent(CitizenIntent.Source.PLAYER, CitizenIntent.DestinationType.POSITION,
                Vec3.atBottomCenterOf(home.center().east(10)), null, now, now + 1000, 0)), "Player position Intent accepted");
        manager(helper).flags(citizen, true, true);
        helper.assertTrue(citizen.intents().active() != null && citizen.intents().active().source() == CitizenIntent.Source.PLAYER, "Independent and FORCE leave Player travel intact");
        manager(helper).remove(home.id());
        helper.assertTrue(citizen.intents().active() != null && citizen.isAlive(), "Home deletion does not take Player Navigation"); citizen.intents().cancel(); helper.succeed();
    }
}
