package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.citizen.population.*;
import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenPopulationRecoveryGameTests {
    private static int originalStuckTicks, originalRetries, originalRetryTicks;
    private CitizenPopulationRecoveryGameTests() { }

    @BeforeBatch(batch = "citizen_population_recovery")
    public static void shortenRecovery(ServerLevel level) {
        originalStuckTicks = ModConfigs.COMMON.populationStuckTicks.get();
        originalRetries = ModConfigs.COMMON.populationStuckRetries.get();
        originalRetryTicks = ModConfigs.COMMON.populationRetryTicks.get();
        ModConfigs.COMMON.populationStuckTicks.set(100); ModConfigs.COMMON.populationStuckRetries.set(2);
        ModConfigs.COMMON.populationRetryTicks.set(20);
    }

    @AfterBatch(batch = "citizen_population_recovery")
    public static void restoreRecovery(ServerLevel level) {
        ModConfigs.COMMON.populationStuckTicks.set(originalStuckTicks); ModConfigs.COMMON.populationStuckRetries.set(originalRetries);
        ModConfigs.COMMON.populationRetryTicks.set(originalRetryTicks);
    }

    @BeforeBatch(batch = "citizen_population_recovery_density")
    public static void shortenDensityRecovery(ServerLevel level) { shortenRecovery(level); }

    @AfterBatch(batch = "citizen_population_recovery_density")
    public static void restoreDensityRecovery(ServerLevel level) { restoreRecovery(level); }

    private static CitizenPopulationManager population(GameTestHelper helper) { return CitizenPopulationManager.get(helper.getLevel()); }
    private static void room(GameTestHelper helper) { GameTestFixtures.dryRoom(helper, 0, 0, 38); }
    private static CitizenGauge gauge(GameTestHelper helper, int x, int z, boolean source) {
        return gauge(helper, new BlockPos(x, 1, z), source);
    }
    private static CitizenGauge gauge(GameTestHelper helper, BlockPos localPos, boolean source) {
        var pos = helper.absolutePos(localPos);
        helper.getLevel().setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
        var entity = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(pos);
        entity.initialize(source ? null : pos.offset(0, 0, 3));
        var gauge = population(helper).register(entity);
        if (!source) population(helper).settings(gauge.id(), 3, 0);
        return population(helper).data().gauge(gauge.id());
    }
    private static RouteMarker marker(GameTestHelper helper, BlockPos pos, boolean gateway) {
        return marker(helper, pos, gateway ? RouteMarkerType.GATEWAY : RouteMarkerType.NORMAL);
    }
    private static RouteMarker marker(GameTestHelper helper, BlockPos pos, RouteMarkerType type) {
        var block = switch (type) {
            case GATEWAY -> ModBlocks.GATEWAY_ROUTE_MARKER.get();
            case CROSSING -> ModBlocks.CROSSING_ROUTE_MARKER.get();
            case NORMAL -> ModBlocks.CITIZEN_ROUTE_MARKER.get();
        };
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        var routes = CitizenRouteManager.get(helper.getLevel());
        var marker = routes.register((CitizenRouteMarkerBlockEntity) helper.getLevel().getBlockEntity(pos)).withRegion(.5, 0);
        routes.data().put(marker); return marker;
    }
    private static Citizen citizen(GameTestHelper helper, CitizenGauge home, BlockPos start, CitizenBehaviorMode mode) {
        var citizen = ModEntities.CITIZEN.get().create(helper.getLevel());
        if (citizen == null) throw new IllegalStateException("Missing Citizen");
        citizen.setPos(Vec3.atBottomCenterOf(start)); citizen.setOnGround(true);
        citizen.citizenState().setPopulation(CitizenPopulationState.DEFAULT.district(home.districtId(), 0));
        citizen.setBehavior(mode == CitizenBehaviorMode.WALK ? CitizenBehaviorState.walking()
                : CitizenBehaviorState.anchored(mode, start, helper.getLevel().dimension(), 0));
        helper.getLevel().addFreshEntity(citizen); population(helper).join(citizen);
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 1800);
        return citizen;
    }
    private static BlockPos trap(GameTestHelper helper) {
        var start = helper.absolutePos(new BlockPos(22, 1, 11));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 3; y++)
            if (x != 0 || z != 0) helper.getLevel().setBlock(start.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
        return start;
    }
    private static void assertRecovery(GameTestHelper helper, CitizenBehaviorMode mode) {
        room(helper); var home = gauge(helper, 8, 8, false); var start = trap(helper);
        if (mode == CitizenBehaviorMode.WALK) {
            var first = marker(helper, start, false); var second = marker(helper, home.center(), false);
            var internal = marker(helper, home.center().west(3), false);
            var routes = CitizenRouteManager.get(helper.getLevel()).data();
            routes.link(first.id(), second.id(), false); routes.link(second.id(), internal.id(), false);
        }
        var citizen = citizen(helper, home, start, mode); var behavior = citizen.citizenState().behavior();
        boolean[] recovered = {false};
        helper.runAtTickTime(40, () -> helper.assertTrue(!population(helper).data().owns(home, citizen.position()), "Recovery is not an immediate teleport"));
        for (int tick = 5; tick <= 1300; tick += 5) helper.runAtTickTime(tick, () -> {
            helper.assertTrue(citizen.intents().active() == null, "Base autonomy recovery does not require a destination intent");
            if (population(helper).data().owns(home, citizen.position())) recovered[0] = true;
            helper.assertTrue(behavior.equals(citizen.citizenState().behavior())
                    && home.districtId().equals(citizen.citizenState().population().districtId()), "Recovery preserves base behavior and membership");
        });
        helper.runAtTickTime(1305, () -> {
            helper.assertTrue(recovered[0], "Repeatedly blocked base " + mode + " recovers into its loaded home district"); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 1340)
    public static void stuckBaseWalkRecoversHomeWithoutAnIntent(GameTestHelper helper) { assertRecovery(helper, CitizenBehaviorMode.WALK); }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 220)
    public static void deletedLastMarkerChangesWalkerToWanderWithoutWarp(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 18, 15, false); var node = marker(helper, home.center(), false);
        var citizen = citizen(helper, home, home.center(), CitizenBehaviorMode.WALK); Vec3[] previous = {citizen.position()};
        helper.onEachTick(() -> {
            helper.assertTrue(citizen.position().distanceToSqr(previous[0]) < 2.25, "Missing route changes mode without teleporting");
            previous[0] = citizen.position();
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK, "An existing isolated marker remains an intentional wait");
            helper.getLevel().destroyBlock(node.pos(), false);
        });
        helper.runAtTickTime(180, () -> {
            helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WANDER && !citizen.intents().stucked()
                    && home.districtId().equals(citizen.citizenState().population().districtId()), "Losing all nearby markers resumes wandering and preserves membership");
            helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 120)
    public static void missingMarkersDoNotOverridePlayerTravelOrWaitingForUnloadedMarkers(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 18, 15, false); var routes = CitizenRouteManager.get(helper.getLevel());
        var start = helper.absolutePos(new BlockPos(12, 1, 4)); var moving = citizen(helper, home, start, CitizenBehaviorMode.WALK);
        long now = helper.getLevel().getGameTime();
        moving.intents().request(new CitizenIntent(CitizenIntent.Source.PLAYER, CitizenIntent.DestinationType.POSITION,
                Vec3.atBottomCenterOf(start.south(28)), null, now, now + 400, 0));
        var waiting = citizen(helper, home, home.center(), CitizenBehaviorMode.WALK);
        var unloaded = RouteMarker.create(UUID.randomUUID(), home.center().east(2), RouteMarkerType.NORMAL);
        routes.data().put(unloaded);
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(moving.intents().active() != null && moving.citizenState().behavior().mode() == CitizenBehaviorMode.WALK,
                    "No route markers cannot cancel explicit Player navigation");
            helper.assertTrue(waiting.citizenState().behavior().mode() == CitizenBehaviorMode.WALK && !waiting.intents().stucked(),
                    "A saved marker awaiting its block entity is not mistaken for a deleted route");
            moving.intents().cancel(); routes.data().remove(unloaded.id()); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 650)
    public static void stuckWalkerWithNoUsableMarkerResumesWanderWithoutWarp(GameTestHelper helper) {
        GameTestFixtures.dryRoom(helper, new BlockPos(0, 128, 0), 38);
        var home = gauge(helper, new BlockPos(18, 129, 15), false); var node = marker(helper, home.center().east(4), false);
        helper.getLevel().setBlock(node.pos().above(), Blocks.STONE.defaultBlockState(), 3);
        var citizen = citizen(helper, home, home.center(), CitizenBehaviorMode.WALK); Vec3[] previous = {citizen.position()};
        helper.onEachTick(() -> {
            var nearbyMarkers = CitizenRouteManager.get(helper.getLevel()).nearby(citizen.position(), RouteSettings.DISCOVERY_RADIUS);
            if (citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK) {
                helper.assertTrue(nearbyMarkers.stream().allMatch(marker -> marker.id().equals(node.id())),
                        "Unusable-marker fixture must not see other test markers: " + nearbyMarkers.stream().map(RouteMarker::pos).toList());
            }
            if (citizen.position().distanceToSqr(previous[0]) >= 2.25) {
                var routes = CitizenRouteManager.get(helper.getLevel());
                helper.fail("No usable recovery marker must cause WANDER rather than a recovery warp: from=" + previous[0]
                        + " to=" + citizen.position() + " mode=" + citizen.citizenState().behavior().mode()
                        + " fixtureMarker=" + node.pos() + " nearby=" + routes.nearby(previous[0], RouteSettings.DISCOVERY_RADIUS).stream()
                        .map(marker -> marker.pos() + ":endpoints=" + routes.endpointCandidates(citizen, null, marker)).toList());
            }
            previous[0] = citizen.position();
        });
        helper.runAtTickTime(600, () -> {
            helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WANDER && !citizen.intents().stucked(),
                    "A route that repeatedly fails and has no safe marker endpoint falls back to wandering"); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery_density", timeoutTicks = 460)
    public static void districtWanderersFillOnlyAvailableRouteDensityAndProtectFlags(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 18, 15, false); var population = population(helper); population.settings(home.id(), 8, 0);
        var a = marker(helper, home.center().west(3), false); var b = marker(helper, home.center().east(3), false);
        var routes = CitizenRouteManager.get(helper.getLevel()).data(); routes.link(a.id(), b.id(), false);
        var other = gauge(helper, 32, 15, false); var foreign = marker(helper, other.center(), false);
        routes.link(b.id(), foreign.id(), false);
        var citizens = new ArrayList<Citizen>();
        for (int index = 0; index < 3; index++) citizens.add(citizen(helper, home, home.center().offset(index - 1, 0, 2), CitizenBehaviorMode.WANDER));
        var forced = citizen(helper, home, home.center().north(2), CitizenBehaviorMode.WANDER); population.flags(forced, false, true);
        var independent = citizen(helper, home, home.center().north(3), CitizenBehaviorMode.WANDER); population.flags(independent, true, false);
        var standing = citizen(helper, home, home.center().south(3), CitizenBehaviorMode.STAND);
        var capacityHistory = new ArrayList<String>();
        helper.onEachTick(() -> {
            if (helper.getTick() <= 240 && helper.getTick() % 20 == 0) capacityHistory.add(helper.getTick() + ":ready="
                    + population.routeCapacity().ready() + ":length=" + population.routeCapacity().length(home.districtId())
                    + ":walkers=" + population.data().walkingPopulation(home.districtId()));
        });
        helper.runAtTickTime(240, () -> {
            helper.assertTrue(Math.abs(population.routeCapacity().length(home.districtId()) - 6) < 1.0E-6,
                    "Six meters of edges are cached once per district: ready=" + population.routeCapacity().ready()
                            + " length=" + population.routeCapacity().length(home.districtId()) + " history=" + capacityHistory);
            helper.assertTrue(citizens.stream().filter(c -> c.citizenState().behavior().mode() == CitizenBehaviorMode.WALK).count() == 2
                    && population.data().walkingPopulation(home.districtId()) == 2, "Only two wanderers fill six meters of route capacity: indexed="
                            + population.data().walkingPopulation(home.districtId()) + " citizens=" + citizens.stream().map(c -> c.getUUID() + ":" + c.citizenState().behavior().mode()
                            + ":" + c.position() + ":" + c.intents().active() + ":" + c.routeMovement().state() + ":ticks=" + c.tickCount).toList()
                            + " history=" + capacityHistory);
            helper.assertTrue(forced.citizenState().behavior().mode() == CitizenBehaviorMode.WANDER
                    && independent.citizenState().behavior().mode() == CitizenBehaviorMode.WANDER
                    && standing.citizenState().behavior().mode() == CitizenBehaviorMode.STAND, "Autonomy respects FORCE, INDEPENDENT and STAND");
            forced.citizenState().setBehavior(CitizenBehaviorState.walking()); independent.citizenState().setBehavior(CitizenBehaviorState.walking());
            standing.setNoAi(true); standing.citizenState().setBehavior(CitizenBehaviorState.walking());
            routes.remove(a.id());
        });
        helper.runAtTickTime(400, () -> {
            helper.assertTrue(population.routeCapacity().ready() && population.routeCapacity().length(home.districtId()) == 0,
                    "Only the inter-district edge remains and contributes no walking capacity");
            helper.assertTrue(citizens.stream().allMatch(citizen -> citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WANDER),
                    "Over-capacity autonomous walkers gradually return to WANDER");
            helper.assertTrue(forced.citizenState().behavior().mode() == CitizenBehaviorMode.WALK
                    && independent.citizenState().behavior().mode() == CitizenBehaviorMode.WALK
                    && standing.citizenState().behavior().mode() == CitizenBehaviorMode.WALK
                    && population.data().walkingPopulation(home.districtId()) == 2, "Demotion protects FORCE, INDEPENDENT and NoAI; independent citizens are unassigned");
            helper.succeed();
        });
    }

    private static void assertInfluxMode(GameTestHelper helper, boolean saturated) {
        room(helper); var home = gauge(helper, 18, 15, false); var source = gauge(helper, 5, 5, true); var population = population(helper);
        population.settings(home.id(), 8, saturated ? 2 : 1);
        var a = marker(helper, home.center(), true); var b = marker(helper, home.center().east(3), false);
        CitizenRouteManager.get(helper.getLevel()).data().link(a.id(), b.id(), false);
        if (saturated) { var existing = citizen(helper, home, home.center().west(2), CitizenBehaviorMode.WALK); existing.setNoAi(true); }
        var initial = Set.copyOf(population.data().residents(home.districtId())); population.link(source.id(), home.id());
        if (saturated) population.data().put(population.data().gauge(home.id()).resolve(true));
        helper.succeedWhen(() -> {
            var ids = population.data().residents(home.districtId()).stream().filter(id -> !initial.contains(id)).toList();
            helper.assertTrue(ids.size() == 1, "One citizen arrives through influx");
            var created = (Citizen) helper.getLevel().getEntity(ids.get(0));
            helper.assertTrue(created != null && created.citizenState().behavior().mode() == (saturated ? CitizenBehaviorMode.WANDER : CitizenBehaviorMode.WALK),
                    "Influx selects WALK below density threshold and WANDER when saturated; mode=" + (created == null ? null : created.citizenState().behavior().mode())
                            + " ready=" + population.routeCapacity().ready() + " length=" + population.routeCapacity().length(home.districtId())
                            + " walkers=" + population.data().walkingPopulation(home.districtId()) + " pos=" + (created == null ? null : created.position()));
            helper.assertTrue(created.citizenState().behavior().unlimitedWander(), "Automatic mode selection preserves radius '-'");
            population.settings(home.id(), 8, 0); population.unlink(source.id(), home.id());
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 400)
    public static void influxPrefersWalkingWhenRouteHasSpace(GameTestHelper helper) { assertInfluxMode(helper, false); }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 400)
    public static void influxWandersWhenRouteIsSaturated(GameTestHelper helper) { assertInfluxMode(helper, true); }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 110)
    public static void childWalkingMatchesPlayingBabyVillagerSpeedAndOutpacesAdult(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 18, 15, false);
        var adultStart = helper.absolutePos(new BlockPos(12, 1, 4)); var childStart = helper.absolutePos(new BlockPos(24, 1, 4));
        var adult = citizen(helper, home, adultStart, CitizenBehaviorMode.WALK);
        var child = citizen(helper, home, childStart, CitizenBehaviorMode.WALK); child.setBaby(true);
        var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        if (villager == null) throw new IllegalStateException("Missing Villager");
        villager.setBaby(true);
        helper.assertTrue(Math.abs(child.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                - villager.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 0.6) < 1.0E-6,
                "Child Citizen matches the effective movement speed of baby-villager tag behavior");
        long now = helper.getLevel().getGameTime();
        adult.intents().request(new CitizenIntent(CitizenIntent.Source.PLAYER, CitizenIntent.DestinationType.POSITION,
                Vec3.atBottomCenterOf(adultStart.south(28)), null, now, now + 400, 0));
        child.intents().request(new CitizenIntent(CitizenIntent.Source.PLAYER, CitizenIntent.DestinationType.POSITION,
                Vec3.atBottomCenterOf(childStart.south(28)), null, now, now + 400, 0));
        helper.runAtTickTime(80, () -> {
            double adultDistance = adult.getZ() - adultStart.getZ() - 0.5, childDistance = child.getZ() - childStart.getZ() - 0.5;
            helper.assertTrue(adultDistance > 5 && childDistance > adultDistance * 1.1,
                    "Real navigation is faster for children: adult=" + adultDistance + " child=" + childDistance);
            child.setBaby(false);
            helper.assertTrue(child.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) == 0.25,
                    "Changing back to adult restores normal speed"); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 850)
    public static void walkingPopulationTransferFollowsConnectedEdgesBeforeGatewayAndWandersAtSaturation(GameTestHelper helper) {
        room(helper); var population = population(helper); var from = gauge(helper, 8, 8, false); var to = gauge(helper, 30, 8, false);
        population.settings(from.id(), 4, 0); population.settings(to.id(), 4, 3);
        var a = marker(helper, from.center(), true); var b = marker(helper, from.center().south(14), false);
        var c = marker(helper, to.center().south(14), false); var d = marker(helper, to.center(), true);
        var routes = CitizenRouteManager.get(helper.getLevel());
        routes.data().link(a.id(), b.id(), false); routes.data().link(b.id(), c.id(), false); routes.data().link(c.id(), d.id(), false);
        var internal = marker(helper, from.center().west(3), false); routes.data().link(a.id(), internal.id(), false);
        var citizen = citizen(helper, from, from.center(), CitizenBehaviorMode.WALK);
        for (int index : new int[]{-2, 2}) citizen(helper, to, to.center().east(index), CitizenBehaviorMode.WALK).setNoAi(true);
        population.data().put(population.data().gauge(to.id()).resolve(true));
        population.link(from.id(), to.id()); Vec3[] previous = {citizen.position()}; boolean[] detour = {false}; boolean[] arrived = {false};
        for (int tick : new int[]{80, 160, 240, 400, 600}) helper.runAtTickTime(tick, () ->
                com.mojang.logging.LogUtils.getLogger().info("WALK_TRANSFER_PROGRESS tick={} pos={} intent={} state={} marker={} goal={} failures={} nav={}",
                        helper.getTick(), citizen.position(), citizen.intents().active(), citizen.routeMovement().state(), citizen.routeMovement().currentMarker(),
                        citizen.routeMovement().currentDestination(), citizen.routeMovement().consecutiveFailures(), citizen.getNavigation().getTargetPos()));
        helper.onEachTick(() -> {
            if (arrived[0]) return;
            helper.assertTrue(citizen.position().distanceToSqr(previous[0]) < 2.25, "Connected route must take precedence over Gateway teleportation; tick="
                    + helper.getTick() + " from=" + previous[0] + " to=" + citizen.position() + " intent=" + citizen.intents().active()
                    + " state=" + citizen.routeMovement().state() + " marker=" + citizen.routeMovement().currentMarker()
                    + " goal=" + citizen.routeMovement().currentDestination() + " failures=" + citizen.routeMovement().consecutiveFailures()); previous[0] = citizen.position();
            if (citizen.getZ() > a.center().z + 8) detour[0] = true;
            if (to.districtId().equals(citizen.citizenState().population().districtId())) {
                arrived[0] = true;
                helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WANDER, "Saturated destination switches to WANDER only after arrival");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(arrived[0] && detour[0] && population.data().population(to.districtId()) == 3,
                    "Population transfer physically traverses the detour and commits final membership; position=" + citizen.position()
                            + " intent=" + citizen.intents().active() + " mode=" + citizen.citizenState().behavior().mode());
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 500)
    public static void walkingPopulationTransferFallsBackToGatewayWhenEdgesAreDisconnected(GameTestHelper helper) {
        room(helper); var population = population(helper); var from = gauge(helper, 8, 8, false); var to = gauge(helper, 30, 8, false);
        marker(helper, from.center(), true); marker(helper, to.center(), true);
        var citizen = citizen(helper, from, from.center(), CitizenBehaviorMode.WALK);
        population.settings(to.id(), 3, 1); population.link(from.id(), to.id());
        Vec3[] previous = {citizen.position()}; boolean[] transited = {false};
        helper.onEachTick(() -> {
            if (citizen.position().distanceToSqr(previous[0]) > 100) transited[0] = true;
            previous[0] = citizen.position();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(to.districtId().equals(citizen.citizenState().population().districtId()), "Disconnected WALK transfer still reaches its destination");
            helper.assertTrue(transited[0], "Gateway remains a fallback when no physical Edge route exists; transited=" + transited[0]
                    + " mode=" + citizen.citizenState().behavior().mode() + " capacityReady=" + population.routeCapacity().ready());
            population.settings(to.id(), 3, 0); population.unlink(from.id(), to.id());
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery")
    public static void districtDestinationPrefersStreetHeightOverAnUnreachableSafeRoof(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 18, 15, false); var citizen = citizen(helper, home, home.center(), CitizenBehaviorMode.STAND);
        citizen.getRandom().setSeed(385047);
        for (int attempt = 0; attempt < 32; attempt++) {
            var point = DistrictDestinations.choose(helper.getLevel(), citizen, home);
            helper.assertTrue(point != null && Math.abs(point.y - home.center().getY()) < 0.01,
                    "Same-density street candidates take precedence over the safe but unreachable sealed roof: " + point);
        }
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 80)
    public static void routeArrivalWaitsForIntentAcknowledgementBeforeResumingBaseWalk(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 18, 15, false);
        var a = marker(helper, home.center(), false); var b = marker(helper, home.center().east(5), false);
        CitizenRouteManager.get(helper.getLevel()).data().link(a.id(), b.id(), false);
        var citizen = citizen(helper, home, home.center(), CitizenBehaviorMode.WALK); citizen.intents().tick();
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(citizen.intents().request(new CitizenIntent(CitizenIntent.Source.PLAYER, CitizenIntent.DestinationType.POSITION,
                citizen.position(), null, now, now + 400, 0)), "Player intent is accepted between controller checks");
        citizen.routeMovement().tick();
        helper.assertTrue(citizen.routeMovement().hasDestination() && citizen.intents().active() != null && citizen.getNavigation().isDone(),
                "Arriving must hold position until the low-frequency Intent controller acknowledges completion");
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(citizen.intents().active() == null && citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK,
                    "Acknowledged arrival clears the intent and then resumes the unchanged base WALK mode"); helper.succeed();
        });
    }

    private static void assertFlatWander(GameTestHelper helper, net.minecraft.world.level.block.Block floor, boolean child) {
        room(helper); var home = gauge(helper, 18, 15, false); population(helper).settings(home.id(), 8, 0);
        for (int x = 2; x < 36; x++) for (int z = 2; z < 36; z++) helper.setBlock(new BlockPos(x, 0, z), floor);
        var start = home.center(); var citizen = citizen(helper, home, start, CitizenBehaviorMode.WANDER);
        citizen.setBaby(child); citizen.getRandom().setSeed(38421);
        Vec3[] previous = {citizen.position()}; double[] walked = {0};
        helper.onEachTick(() -> {
            double step = citizen.position().distanceTo(previous[0]);
            helper.assertTrue(step < 1.5, "Flat unobstructed wandering must not teleport; floor=" + floor + " step=" + step);
            walked[0] += Math.sqrt(citizen.position().subtract(previous[0]).horizontalDistanceSqr()); previous[0] = citizen.position();
            helper.assertTrue(!citizen.intents().stucked(), "Flat unobstructed wandering must not become STUCKED");
        });
        helper.runAtTickTime(180, () -> helper.assertTrue(walked[0] > 2, "Walkable flat floors must produce real walking instead of rejected candidates; floor=" + floor));
        helper.runAtTickTime(1250, () -> { helper.assertTrue(walked[0] > 8, "Wandering continues across repeated target selections"); helper.succeed(); });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 1280)
    public static void flatStoneWanderDoesNotTeleport(GameTestHelper helper) { assertFlatWander(helper, Blocks.STONE, false); }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 1280)
    public static void flatGrassWanderDoesNotTeleport(GameTestHelper helper) { assertFlatWander(helper, Blocks.GRASS_BLOCK, false); }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 1280)
    public static void flatGlassWanderDoesNotTeleport(GameTestHelper helper) { assertFlatWander(helper, Blocks.GLASS, false); }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 1280)
    public static void flatSlabChildWanderDoesNotTeleport(GameTestHelper helper) { assertFlatWander(helper, Blocks.STONE_SLAB, true); }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 700)
    public static void wanderWithoutAnyAvailableDestinationWaitsInsteadOfTeleporting(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var start = home.center();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 5; y++)
            if (x != 0 || z != 0) helper.getLevel().setBlock(start.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
        var citizen = citizen(helper, home, start, CitizenBehaviorMode.WANDER);
        citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, start, helper.getLevel().dimension(), 1));
        var position = citizen.position();
        helper.onEachTick(() -> helper.assertTrue(!citizen.intents().stucked() && citizen.position().distanceToSqr(position) < 1,
                "No optional wander destination or an already-reached candidate is idle, not a failed required journey"));
        helper.runAtTickTime(650, helper::succeed);
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 1280)
    public static void smallFlatDistrictWanderDoesNotTeleport(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 18, 15, false); population(helper).settings(home.id(), 1, 0);
        var citizen = citizen(helper, home, home.center(), CitizenBehaviorMode.WANDER); citizen.getRandom().setSeed(38421);
        Vec3[] previous = {citizen.position()}; double[] walked = {0};
        helper.onEachTick(() -> {
            var step = citizen.position().subtract(previous[0]);
            helper.assertTrue(step.lengthSqr() < 2.25 && !citizen.intents().stucked(), "Radius-one wandering does not teleport between short optional paths");
            walked[0] += Math.sqrt(step.horizontalDistanceSqr()); previous[0] = citizen.position();
        });
        helper.runAtTickTime(1250, () -> { helper.assertTrue(walked[0] > 2, "Small districts still permit real movement"); helper.succeed(); });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 1340)
    public static void stuckBaseWanderRecoversHomeWithoutAnIntent(GameTestHelper helper) { assertRecovery(helper, CitizenBehaviorMode.WANDER); }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 1340)
    public static void forceAndIndependentStuckCitizensAreFlaggedButNeverAutoTeleported(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var start = trap(helper);
        var forced = citizen(helper, home, start, CitizenBehaviorMode.WANDER); population(helper).flags(forced, false, true);
        var independent = citizen(helper, home, start, CitizenBehaviorMode.WANDER); population(helper).flags(independent, true, false);
        helper.runAtTickTime(1300, () -> {
            helper.assertTrue(forced.intents().stucked() && independent.intents().stucked(), "Repeated failures expose STUCKED independently of teleport permission");
            helper.assertTrue(forced.position().distanceToSqr(Vec3.atBottomCenterOf(start)) < 1
                    && independent.position().distanceToSqr(Vec3.atBottomCenterOf(start)) < 1, "FORCE and INDEPENDENT never auto teleport"); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 700)
    public static void isolatedMarkerAndStandAreNotMistakenForStuckMovement(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false);
        var isolatedStart = home.center().above(80);
        for (int x = -2; x <= 4; x++) for (int z = -2; z <= 2; z++)
            helper.getLevel().setBlockAndUpdate(isolatedStart.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        marker(helper, isolatedStart, false);
        var walker = citizen(helper, home, isolatedStart, CitizenBehaviorMode.WALK);
        var standing = citizen(helper, home, isolatedStart.offset(2, 0, 0), CitizenBehaviorMode.STAND);
        var walkerStart = walker.position(); var standStart = standing.position();
        helper.runAtTickTime(650, () -> {
            helper.assertTrue(!walker.intents().stucked() && !standing.intents().stucked()
                    && walker.position().distanceToSqr(walkerStart) < 1 && standing.position().distanceToSqr(standStart) < 1,
                    "Waiting at an isolated marker and STAND are intentional inactivity: walker=" + walker.position() + " start=" + walkerStart
                            + " mode=" + walker.citizenState().behavior().mode() + " stucked=" + walker.intents().stucked() + " intent=" + walker.intents().active()
                            + " standing=" + standing.position() + " start=" + standStart + " stucked=" + standing.intents().stucked()); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 580)
    public static void closedCrossingWaitDoesNotTriggerBaseRecovery(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 8, 8, false); var routes = CitizenRouteManager.get(helper.getLevel());
        var first = marker(helper, home.center(), RouteMarkerType.CROSSING);
        var second = marker(helper, home.center().offset(8, 0, 0), RouteMarkerType.CROSSING);
        routes.data().put(first); routes.data().put(second); routes.data().link(first.id(), second.id(), true);
        var citizen = citizen(helper, home, home.center(), CitizenBehaviorMode.WALK); var start = citizen.position();
        boolean[] waited = {false};
        for (int tick = 20; tick <= 520; tick += 20) helper.runAtTickTime(tick, () -> {
            if (citizen.routeMovement().state() == CitizenRouteMovement.State.WAIT_CROSSING) waited[0] = true;
            helper.assertTrue(!citizen.intents().stucked() && citizen.position().distanceToSqr(start) < 1,
                    "Closed crossing is an intentional wait even beyond the stuck timeout and retries: tick=" + helper.getTick()
                            + ", start=" + start + ", pos=" + citizen.position() + ", route=" + citizen.routeMovement().state()
                            + ", stucked=" + citizen.intents().stucked() + ", intent=" + citizen.intents().active());
        });
        helper.runAtTickTime(525, () -> { helper.assertTrue(waited[0], "Citizen actually waits for the crossing"); helper.succeed(); });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 500)
    public static void unsatisfiableOldRequestsDoNotStarveAYoungerSuppliedDistrict(GameTestHelper helper) {
        room(helper); var population = population(helper);
        for (int index = 0; index < 8; index++) {
            var impossible = gauge(helper, 5 + index % 4 * 6, 4 + index / 4 * 6, false);
            population.settings(impossible.id(), 1, 2);
        }
        var source = gauge(helper, 5, 26, true);
        CitizenGauge[] receiver = {null};
        helper.runAtTickTime(160, () -> {
            helper.assertTrue(population.requestCount() >= 8, "An older unresolved backlog is continuously retryable");
            receiver[0] = gauge(helper, 27, 26, false);
            population.settings(receiver[0].id(), 3, 2); population.link(source.id(), receiver[0].id());
        });
        helper.runAtTickTime(450, () -> {
            helper.assertTrue(receiver[0] != null && population.data().population(receiver[0].districtId()) == 1,
                    "Younger supplied request progresses despite older requests exhausting each period's budget");
            population.settings(receiver[0].id(), 3, 0); population.unlink(source.id(), receiver[0].id()); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery", timeoutTicks = 340)
    public static void sourceSpawnsAtReceivingGatewayWithUnlimitedWander(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 15, 12, false); var source = gauge(helper, 5, 5, true);
        var gateway = marker(helper, home.center(), true); var population = population(helper);
        helper.assertTrue(CitizenRouteManager.get(helper.getLevel()).gatewayEnabled(gateway), "Receiving gateway is automatically assigned and enabled");
        population.link(source.id(), home.id()); population.settings(home.id(), 3, 2);
        helper.succeedWhen(() -> {
            helper.assertTrue(population.data().population(home.districtId()) == 1, "Source generated one citizen");
            var citizen = (Citizen) helper.getLevel().getEntity(population.data().residents(home.districtId()).iterator().next());
            helper.assertTrue(citizen != null && gateway.contains(citizen.position()) && citizen.citizenState().behavior().unlimitedWander(),
                    "Source prefers the receiving gateway and uses radius '-' stored as zero");
            population.settings(home.id(), 3, 0); population.unlink(source.id(), home.id());
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_population_recovery")
    public static void spawnSelectionFallsBackWhenGatewayIsMissingBlockedOrOwnedByAnotherDistrict(GameTestHelper helper) {
        room(helper); var home = gauge(helper, 15, 12, false); var level = helper.getLevel();
        var citizen = ModEntities.CITIZEN.get().create(level);
        if (citizen == null) throw new IllegalStateException("Missing Citizen");
        var point = DistrictDestinations.chooseSpawn(level, citizen, home);
        helper.assertTrue(point != null && population(helper).data().owns(home, point), "No gateway falls back to a safe district-owned point");
        var gateway = marker(helper, home.center(), true);
        level.setBlock(gateway.pos().above(), Blocks.STONE.defaultBlockState(), 3);
        point = DistrictDestinations.chooseSpawn(level, citizen, home);
        helper.assertTrue(point != null && !gateway.contains(point), "Blocked gateway cannot place a citizen into a collision");
        var neighbor = gauge(helper, 25, 12, false);
        var foreign = marker(helper, neighbor.center(), true).withDistrict(home.districtId());
        CitizenRouteManager.get(level).data().put(foreign);
        point = DistrictDestinations.chooseSpawn(level, citizen, home);
        helper.assertTrue(point != null && population(helper).data().owns(home, point), "Stale gateway district cache is revalidated against exact territory ownership");
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = -8; y <= 12; y++) {
            var pos = home.center().offset(x, y, z);
            if (!pos.equals(home.pos())) level.setBlock(pos, (y == 12 ? Blocks.LAVA : Blocks.STONE).defaultBlockState(), 3);
        }
        helper.assertTrue(population(helper).loaded(home), "Blocked district remains registered and loaded");
        helper.assertTrue(DistrictDestinations.chooseSpawn(level, citizen, home) == null, "No safe district point means spawn is deferred"); helper.succeed();
    }
}
