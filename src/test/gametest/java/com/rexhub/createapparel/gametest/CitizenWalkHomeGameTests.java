package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.citizen.population.*;
import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenWalkHomeGameTests {
    private static int originalReturnTicks;
    private CitizenWalkHomeGameTests() { }

    @BeforeBatch(batch = "citizen_walk_home")
    public static void shortenReturnDelay(ServerLevel level) {
        originalReturnTicks = ModConfigs.COMMON.populationReturnHomeTicks.get();
        ModConfigs.COMMON.populationReturnHomeTicks.set(200);
    }

    @AfterBatch(batch = "citizen_walk_home")
    public static void restoreReturnDelay(ServerLevel level) {
        ModConfigs.COMMON.populationReturnHomeTicks.set(originalReturnTicks);
    }

    private static CitizenGauge home(GameTestHelper helper) {
        GameTestFixtures.dryRoom(helper, 0, 0, 38);
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        helper.getLevel().setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
        var entity = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(pos);
        entity.initialize(helper.absolutePos(new BlockPos(8, 1, 11)));
        var population = CitizenPopulationManager.get(helper.getLevel()); var gauge = population.register(entity);
        population.settings(gauge.id(), 2, 0);
        var first = marker(helper, 6, 10); var second = marker(helper, 6, 13);
        CitizenRouteManager.get(helper.getLevel()).data().link(first.id(), second.id(), false);
        return population.data().gauge(gauge.id());
    }

    private static RouteMarker marker(GameTestHelper helper, int x, int z) {
        var pos = helper.absolutePos(new BlockPos(x, 1, z));
        helper.getLevel().setBlock(pos, ModBlocks.CITIZEN_ROUTE_MARKER.get().defaultBlockState(), 3);
        var routes = CitizenRouteManager.get(helper.getLevel());
        var marker = routes.register((CitizenRouteMarkerBlockEntity) helper.getLevel().getBlockEntity(pos)).withRegion(.5, 1);
        routes.data().put(marker); return marker;
    }

    private static Citizen walker(GameTestHelper helper, CitizenGauge home, BlockPos start) {
        var citizen = ModEntities.CITIZEN.get().create(helper.getLevel());
        if (citizen == null) throw new IllegalStateException("Missing Citizen");
        citizen.setPos(Vec3.atBottomCenterOf(start)); citizen.setOnGround(true);
        citizen.citizenState().setPopulation(CitizenPopulationState.DEFAULT.district(home.districtId(), 0));
        citizen.setBehavior(CitizenBehaviorState.walking()); helper.getLevel().addFreshEntity(citizen);
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 1000);
        return citizen;
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_walk_home")
    public static void districtAndRouteEditsCannotDemoteWalkersUsingAnOlderCapacitySnapshot(GameTestHelper helper) {
        var population = CitizenPopulationManager.get(helper.getLevel()); var routes = CitizenRouteManager.get(helper.getLevel());
        for (int slice = 0; slice < 100; slice++) {
            population.routeCapacity().tick();
            if (population.routeCapacity().ready()) break;
        }
        helper.assertTrue(population.routeCapacity().ready(), "Pre-edit capacity snapshot is complete");
        var home = home(helper); var outside = marker(helper, 20, 11); var next = marker(helper, 27, 11);
        routes.data().link(outside.id(), next.id(), false);
        var citizen = walker(helper, home, outside.pos());
        helper.assertTrue(!population.tryWandering(citizen), "District and route edits defer demotion until their capacity is rebuilt");
        helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK, "An older empty snapshot cannot replace WALK");
        for (int slice = 0; slice < 100; slice++) {
            population.routeCapacity().tick();
            if (population.routeCapacity().ready()) break;
        }
        helper.assertTrue(population.routeCapacity().ready() && population.routeCapacity().limit(home.districtId()) == 1,
                "Rebuilt internal route supports the walker");
        helper.assertTrue(!population.tryWandering(citizen) && citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK,
                "Current capacity also preserves the walker's mode");
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_walk_home", timeoutTicks = 440)
    public static void walkFollowsMarkersAcrossDistrictBoundaryAndReturnsWithoutChangingMembership(GameTestHelper helper) {
        var home = home(helper); var routes = CitizenRouteManager.get(helper.getLevel());
        var inside = marker(helper, 8, 11); var outside = marker(helper, 20, 11);
        routes.data().link(inside.id(), outside.id(), false);
        var citizen = walker(helper, home, inside.pos()); var population = CitizenPopulationManager.get(helper.getLevel());
        boolean[] left = {false}, returned = {false};
        for (int tick = 5; tick <= 400; tick += 5) helper.runAtTickTime(tick, () -> {
            helper.assertTrue(home.districtId().equals(citizen.citizenState().population().districtId()), "Walking across a boundary preserves home membership");
            if (!population.data().owns(home, citizen.position())) left[0] = true;
            else if (left[0]) returned[0] = true;
        });
        helper.runAtTickTime(405, () -> {
            helper.assertTrue(left[0] && returned[0] && citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK,
                    "WALK may leave its district and naturally return along markers: left=" + left[0] + " returned=" + returned[0]
                            + " mode=" + citizen.citizenState().behavior().mode() + " length=" + population.routeCapacity().length(home.districtId())
                            + " ready=" + population.routeCapacity().ready() + " walkers=" + population.data().walkingPopulation(home.districtId())
                            + " position=" + citizen.position() + " movement=" + citizen.routeMovement().state()); helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_walk_home", timeoutTicks = 650)
    public static void prolongedOutsideWalkRequestsReturnHomeAndResumesWalkAfterArrival(GameTestHelper helper) {
        var home = home(helper); var routes = CitizenRouteManager.get(helper.getLevel());
        marker(helper, 8, 11); var first = marker(helper, 20, 11); var second = marker(helper, 27, 11);
        routes.data().link(first.id(), second.id(), false);
        var citizen = walker(helper, home, first.pos()); var population = CitizenPopulationManager.get(helper.getLevel());
        boolean[] returning = {false}, arrived = {false};
        for (int tick = 5; tick <= 600; tick += 5) {
            int elapsed = tick;
            helper.runAtTickTime(tick, () -> {
                var intent = citizen.intents().active();
                if (elapsed <= 100) helper.assertTrue(intent == null, "Short outside walks are not immediately forced home");
                if (intent != null && intent.source() == CitizenIntent.Source.RETURN_HOME) returning[0] = true;
                if (returning[0] && intent == null && population.data().owns(home, citizen.position())) arrived[0] = true;
                helper.assertTrue(home.districtId().equals(citizen.citizenState().population().districtId())
                        && citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK, "Return is temporary and preserves WALK and membership: tick="
                                + elapsed + " mode=" + citizen.citizenState().behavior().mode() + " length=" + population.routeCapacity().length(home.districtId())
                                + " ready=" + population.routeCapacity().ready() + " walkers=" + population.data().walkingPopulation(home.districtId())
                                + " position=" + citizen.position() + " intent=" + citizen.intents().active());
            });
        }
        helper.runAtTickTime(605, () -> {
            helper.assertTrue(returning[0] && arrived[0], "Long outside walk returns through normal intent navigation and clears the intent"); helper.succeed();
        });
    }
}
