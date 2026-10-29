package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.citizen.population.CitizenGauge;
import com.rexhub.createapparel.citizen.population.CitizenIntent;
import com.rexhub.createapparel.citizen.population.CitizenPopulationManager;
import com.rexhub.createapparel.citizen.population.CitizenPopulationState;
import com.rexhub.createapparel.citizen.route.CitizenActivityScheduler;
import com.rexhub.createapparel.citizen.route.CitizenRouteManager;
import com.rexhub.createapparel.citizen.route.RouteMarker;
import com.rexhub.createapparel.citizen.route.RouteSettings;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenIntentRouteGameTests {
    private static final Map<ServerLevel, Set<ChunkPos>> TICKETS = new IdentityHashMap<>();
    private CitizenIntentRouteGameTests() { }

    private static void room(GameTestHelper helper) {
        var center = new ChunkPos(helper.absolutePos(new BlockPos(32, 1, 32)));
        var chunks = helper.getLevel().getChunkSource();
        chunks.addRegionTicket(TicketType.FORCED, center, 4, center);
        TICKETS.computeIfAbsent(helper.getLevel(), ignored -> new HashSet<>()).add(center);
        for (int x = center.x - 4; x <= center.x + 4; x++) for (int z = center.z - 4; z <= center.z + 4; z++) helper.getLevel().getChunk(x, z);
        GameTestFixtures.dryRoom(helper, 0, 0, 64);
    }

    @AfterBatch(batch = "citizen_intent_routes")
    public static void releaseTickets(ServerLevel level) {
        for (var chunk : TICKETS.getOrDefault(level, Set.of())) level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 4, chunk);
        TICKETS.remove(level);
    }

    private static CitizenGauge gauge(GameTestHelper helper, int x) {
        var pos = helper.absolutePos(new BlockPos(x, 1, 4));
        helper.getLevel().setBlockAndUpdate(pos.south(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState());
        var entity = (CitizenGaugeBlockEntity) Objects.requireNonNull(helper.getLevel().getBlockEntity(pos));
        entity.initialize(pos.south(4));
        var population = CitizenPopulationManager.get(helper.getLevel());
        var gauge = population.register(entity);
        population.settings(gauge.id(), 6, 0);
        return population.data().gauge(gauge.id());
    }

    private static RouteMarker marker(GameTestHelper helper, int x, int z, boolean gateway) {
        var pos = helper.absolutePos(new BlockPos(x, 1, z));
        helper.getLevel().setBlockAndUpdate(pos, (gateway ? ModBlocks.GATEWAY_ROUTE_MARKER.get() : ModBlocks.CITIZEN_ROUTE_MARKER.get()).defaultBlockState());
        var manager = CitizenRouteManager.get(helper.getLevel());
        var marker = manager.register((CitizenRouteMarkerBlockEntity) Objects.requireNonNull(helper.getLevel().getBlockEntity(pos))).withRegion(0.5, 0);
        manager.data().put(marker);
        return marker;
    }

    private static List<RouteMarker> road(GameTestHelper helper, int from, int to) {
        var nodes = List.of(marker(helper, from, 8, false), marker(helper, from, 22, false),
                marker(helper, to, 22, false), marker(helper, to, 8, false));
        var data = CitizenRouteManager.get(helper.getLevel()).data();
        for (int i = 1; i < nodes.size(); i++) data.link(nodes.get(i - 1).id(), nodes.get(i).id(), false);
        return nodes;
    }

    private static Citizen citizen(GameTestHelper helper, Vec3 start, boolean spawn) {
        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(helper.getLevel()));
        citizen.setPos(start);
        citizen.setOnGround(true);
        citizen.citizenState().setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.STAND,
                citizen.blockPosition(), helper.getLevel().dimension(), 0));
        if (spawn) {
            helper.getLevel().addFreshEntity(citizen);
            CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 1600);
        }
        return citizen;
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_intent_routes", timeoutTicks = 350)
    public static void isolatedMarkersDoNotDivertDestinationTravel(GameTestHelper helper) {
        room(helper);
        var first = marker(helper, 8, 32, false);
        marker(helper, 28, 36, false);
        var start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 1, 32)));
        var goal = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(36, 1, 32)));
        var citizen = citizen(helper, start, true);
        var planner = CitizenRouteManager.get(helper.getLevel()).planner();
        var midpoint = citizen(helper, start.add(14, 0, 0), false);
        var checked = new int[1];
        helper.runAtTickTime(5, () -> {
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(citizen.intents().request(new CitizenIntent(CitizenIntent.Source.PLAYER, CitizenIntent.DestinationType.POSITION,
                    goal, null, now, now + 350, 0)), "Destination intent is accepted");
        });
        helper.onEachTick(() -> {
            if (checked[0] == 0) {
                var middlePlan = planner.plan(midpoint, goal);
                if (!middlePlan.deferred()) {
                    helper.assertTrue(middlePlan.fallback(), "Overlapping endpoint candidates still require a real edge: " + middlePlan);
                    checked[0]++;
                }
            } else if (checked[0] == 1) {
                var sameMarker = planner.between(midpoint, first.id(), first.id());
                if (!sameMarker.deferred()) {
                    helper.assertTrue(!sameMarker.fallback() && sameMarker.nodes().equals(List.of(first.id()))
                            && sameMarker.edges().isEmpty(), "Explicit same-marker lookup remains valid");
                    checked[0]++;
                }
            }
            helper.assertTrue(citizen.routeMovement().currentMarker() == null,
                    "Isolated marker must not become a compulsory waypoint: " + first.pos());
            if (checked[0] == 2 && citizen.position().distanceToSqr(goal) <= 1) { citizen.discard(); helper.succeed(); }
        });
    }

    private static void walksInsteadOfGateway(GameTestHelper helper, CitizenIntent.Source source) {
        room(helper);
        var from = gauge(helper, 8);
        var to = gauge(helper, 30);
        var a = marker(helper, 8, 8, true);
        var b = marker(helper, 8, 22, false);
        var c = marker(helper, 30, 22, false);
        var d = marker(helper, 30, 8, true);
        var routes = CitizenRouteManager.get(helper.getLevel());
        routes.data().link(a.id(), b.id(), false);
        routes.data().link(b.id(), c.id(), false);
        routes.data().link(c.id(), d.id(), false);
        var population = CitizenPopulationManager.get(helper.getLevel());
        population.link(from.id(), to.id());
        var citizen = citizen(helper, a.center(), false);
        citizen.citizenState().setPopulation(CitizenPopulationState.DEFAULT.district(from.districtId(), 0));
        helper.getLevel().addFreshEntity(citizen);
        population.join(citizen);
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 1600);
        var behavior = citizen.citizenState().behavior();
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(citizen.intents().request(new CitizenIntent(source, CitizenIntent.DestinationType.POSITION,
                d.center(), to.districtId(), now, now + 1500, 0)), "Intent is accepted: " + source);
        helper.assertTrue(d.center().equals(citizen.routeMovement().currentDestination()), "Graph planning precedes Gateway selection: " + source);
        boolean[] detour = {false};
        Vec3[] previous = {citizen.position()};
        helper.onEachTick(() -> {
            helper.assertTrue(citizen.position().distanceToSqr(previous[0]) < 2.25, "Graph walk must not teleport: " + source);
            previous[0] = citizen.position();
            if (citizen.getZ() > a.center().z + 10) detour[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(detour[0] && citizen.position().distanceToSqr(d.center()) <= 1 && citizen.intents().active() == null,
                    "Intent physically follows the marked detour: " + source + " position=" + citizen.position() + " state=" + citizen.routeMovement().state());
            helper.assertTrue(behavior.equals(citizen.citizenState().behavior()), "Temporary movement preserves STAND: " + source);
            citizen.discard();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_intent_routes", timeoutTicks = 800)
    public static void playerIntentPrefersGraphBeforeGatewayFromStand(GameTestHelper helper) { walksInsteadOfGateway(helper, CitizenIntent.Source.PLAYER); }
    @GameTest(template = "citizen_route_resilience", batch = "citizen_intent_routes", timeoutTicks = 800)
    public static void fashionIntentPrefersGraphBeforeGatewayFromStand(GameTestHelper helper) { walksInsteadOfGateway(helper, CitizenIntent.Source.FASHION); }
    @GameTest(template = "citizen_route_resilience", batch = "citizen_intent_routes", timeoutTicks = 800)
    public static void returnHomeIntentPrefersGraphBeforeGatewayFromStand(GameTestHelper helper) { walksInsteadOfGateway(helper, CitizenIntent.Source.RETURN_HOME); }
    @GameTest(template = "citizen_route_resilience", batch = "citizen_intent_routes", timeoutTicks = 800)
    public static void transferIntentPrefersGraphBeforeGatewayFromStand(GameTestHelper helper) { walksInsteadOfGateway(helper, CitizenIntent.Source.GAUGE_TRANSFER); }
    @GameTest(template = "citizen_route_resilience", batch = "citizen_intent_routes", timeoutTicks = 800)
    public static void impulseVisitIntentPrefersGraphBeforeGatewayFromStand(GameTestHelper helper) { walksInsteadOfGateway(helper, CitizenIntent.Source.IMPULSE_VISIT); }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_intent_routes")
    public static void plannerKeepsPartialRoadsAndReversesDestinationSideSearch(GameTestHelper helper) {
        room(helper);
        var road = road(helper, 8, 20);
        var planner = CitizenRouteManager.get(helper.getLevel()).planner();
        var source = citizen(helper, road.get(0).center(), false);
        var destination = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(58, 1, 8)));
        var prefix = planner.plan(source, destination);
        helper.assertTrue(!prefix.deferred() && !prefix.fallback() && prefix.nodes().equals(road.stream().map(RouteMarker::id).toList()),
                "A missing destination graph must preserve the reachable source road: " + prefix);
        var reverse = citizen(helper, destination, false);
        var suffix = planner.plan(reverse, road.get(0).center());
        var reversed = new ArrayList<>(road.stream().map(RouteMarker::id).toList());
        java.util.Collections.reverse(reversed);
        helper.assertTrue(!suffix.deferred() && !suffix.fallback() && suffix.nodes().equals(reversed),
                "A missing source graph approaches the useful frontier of the destination road: " + suffix);
        var edge = CitizenRouteManager.get(helper.getLevel()).data().edge(prefix.edges().get(1));
        CitizenRouteManager.get(helper.getLevel()).data().removeEdge(edge.id());
        var changed = planner.plan(source, destination);
        helper.assertTrue(changed.fallback() || !changed.edges().contains(edge.id()), "Topology changes invalidate partial route caches");
        helper.succeed();
    }

    private static void mixedWalk(GameTestHelper helper, boolean prefix, boolean suffix) {
        room(helper);
        if (prefix) road(helper, 8, 20);
        if (suffix) road(helper, 44, 58);
        var start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 1, 8)));
        var goal = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(58, 1, 8)));
        var citizen = citizen(helper, start, true);
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(citizen.intents().request(new CitizenIntent(CitizenIntent.Source.PLAYER, CitizenIntent.DestinationType.POSITION,
                goal, null, now, now + 1500, 0)), "Mixed route starts");
        boolean[] used = {false, false};
        Vec3[] previous = {start};
        helper.onEachTick(() -> {
            helper.assertTrue(citizen.position().distanceToSqr(previous[0]) < 2.25, "Missing graph sections are walked, not teleported");
            previous[0] = citizen.position();
            if (citizen.getZ() > start.z + 10) used[citizen.getX() < start.x + 25 ? 0 : 1] = true;
            var stats = CitizenRouteManager.get(helper.getLevel()).planner().statistics();
            helper.assertTrue(stats.visitedNodes() <= RouteSettings.PLANNER_NODES_PER_TICK && stats.scannedEdges() <= RouteSettings.PLANNER_EDGES_PER_TICK,
                    "Partial searches share the existing per-tick planner budgets");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(citizen.position().distanceToSqr(goal) <= 1 && citizen.intents().active() == null,
                    "Mixed walk reaches the original destination: position=" + citizen.position() + " marker=" + citizen.routeMovement().currentMarker()
                            + " state=" + citizen.routeMovement().state() + " failures=" + citizen.routeMovement().consecutiveFailures());
            helper.assertTrue((!prefix || used[0]) && (!suffix || used[1]), "Every available road is used around the free-navigation section");
            helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.STAND, "Base behavior is unchanged");
            citizen.discard();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_intent_routes", timeoutTicks = 1300)
    public static void intentWalksSourceRoadThenFreeNavigation(GameTestHelper helper) { mixedWalk(helper, true, false); }
    @GameTest(template = "citizen_route_resilience", batch = "citizen_intent_routes", timeoutTicks = 1300)
    public static void intentWalksFreelyThenJoinsDestinationRoad(GameTestHelper helper) { mixedWalk(helper, false, true); }
    @GameTest(template = "citizen_route_resilience", batch = "citizen_intent_routes", timeoutTicks = 1300)
    public static void intentReconnectsDisconnectedRoadsAcrossAFreeSection(GameTestHelper helper) { mixedWalk(helper, true, true); }
}
