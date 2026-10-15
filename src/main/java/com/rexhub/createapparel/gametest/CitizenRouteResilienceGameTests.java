package com.rexhub.createapparel.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenRouteResilienceGameTests {
    private static final Map<net.minecraft.server.level.ServerLevel, Set<net.minecraft.world.level.ChunkPos>> TICKETS = new IdentityHashMap<>();
    private CitizenRouteResilienceGameTests() { }
    private static CitizenRouteManager manager(GameTestHelper helper) { return CitizenRouteManager.get(helper.getLevel()); }
    private static void floor(GameTestHelper helper) {
        var chunk = new net.minecraft.world.level.ChunkPos(helper.absolutePos(new BlockPos(56, 1, 56)));
        var chunks = helper.getLevel().getChunkSource();
        chunks.addRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 4, chunk);
        TICKETS.computeIfAbsent(helper.getLevel(), ignored -> new HashSet<>()).add(chunk);
        for (int x = chunk.x - 4; x <= chunk.x + 4; x++) for (int z = chunk.z - 4; z <= chunk.z + 4; z++) helper.getLevel().getChunk(x, z);
        for (int x = 1; x < 38; x++) for (int z = 1; z < 38; z++) helper.setBlock(new BlockPos(x + 36, 0, z + 36), Blocks.STONE);
    }
    @net.minecraft.gametest.framework.AfterBatch(batch = "citizen_route_resilience")
    public static void releaseResilienceTickets(net.minecraft.server.level.ServerLevel level) { releaseTickets(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "citizen_route_load")
    public static void releaseLoadTickets(net.minecraft.server.level.ServerLevel level) { releaseTickets(level); }
    private static void releaseTickets(net.minecraft.server.level.ServerLevel level) {
        for (var chunk : TICKETS.getOrDefault(level, Set.of())) level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 4, chunk);
        TICKETS.remove(level);
    }
    private static RouteMarker marker(GameTestHelper helper, int x, int y, int z) {
        var pos = helper.absolutePos(new BlockPos(x + 36, y, z + 36));
        helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        helper.getLevel().setBlock(pos, ModBlocks.CITIZEN_ROUTE_MARKER.get().defaultBlockState(), 2);
        var node = manager(helper).register((CitizenRouteMarkerBlockEntity) Objects.requireNonNull(helper.getLevel().getBlockEntity(pos))).withRegion(0.5, 1);
        manager(helper).data().put(node); return node;
    }
    private static Citizen citizen(GameTestHelper helper, Vec3 pos, boolean spawn) {
        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(helper.getLevel()));
        citizen.setPos(pos); citizen.setOnGround(true);
        if (spawn) { helper.getLevel().addFreshEntity(citizen); CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 1600); }
        return citizen;
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience", timeoutTicks = 400)
    public static void incrementalPlannerBudgetsResumeAndDoNotStarveCitizens(GameTestHelper helper) {
        floor(helper); var data = manager(helper).data(); var nodes = new ArrayList<RouteMarker>();
        for (int z = 1; z <= 20; z++) for (int column = 1; column <= 30; column++) {
            int x = z % 2 == 1 ? column : 31 - column;
            var node = marker(helper, x, 1, z);
            if (!nodes.isEmpty()) data.link(nodes.get(nodes.size() - 1).id(), node.id(), false);
            nodes.add(node);
        }
        var citizens = new ArrayList<Citizen>(); var completed = new HashSet<UUID>(); var deferred = new boolean[]{false};
        for (int i = 0; i < 16; i++) citizens.add(citizen(helper, nodes.get(0).center(), false));
        var planner = manager(helper).planner(); var polls = new int[]{0};
        helper.onEachTick(() -> {
            polls[0]++;
            manager(helper).runtime().blockLocal(UUID.randomUUID(), UUID.randomUUID(), helper.getLevel().getGameTime());
            for (var citizen : citizens) if (!completed.contains(citizen.getUUID())) {
                if (citizen == citizens.get(citizens.size() - 1) && polls[0] % 80 != 1) continue;
                var plan = planner.between(citizen, nodes.get(0).id(), nodes.get(nodes.size() - 1).id());
                if (plan.deferred()) { deferred[0] = true; continue; }
                helper.assertTrue(!plan.fallback() && plan.nodes().size() == 600, "Every deferred search retains the reachable full route");
                completed.add(citizen.getUUID());
            }
            var stats = planner.statistics();
            helper.assertTrue(stats.searches() <= RouteSettings.PLANNER_SEARCHES_PER_TICK && stats.visitedNodes() <= RouteSettings.PLANNER_NODES_PER_TICK
                    && stats.scannedEdges() <= RouteSettings.PLANNER_EDGES_PER_TICK, "Graph work stays within shared per-tick budgets");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(completed.size() == 16 && deferred[0], "All sixteen Citizens complete multi-tick searches without starvation");
            for (var citizen : citizens) planner.cancel(citizen);
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience", timeoutTicks = 1040)
    public static void walkKeepsCirclingAndReturnsFromDeadEnds(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 8, 1, 8); var b = marker(helper, 16, 1, 8);
        var c = marker(helper, 16, 1, 16); var d = marker(helper, 8, 1, 16); var data = manager(helper).data();
        data.link(a.id(), b.id(), false); data.link(b.id(), c.id(), false); data.link(c.id(), d.id(), false); data.link(d.id(), a.id(), false);
        var walker = citizen(helper, a.center(), true); walker.setBehavior(CitizenBehaviorState.walking());
        var visited = new HashSet<UUID>(); var transitions = new int[]{0}; var previous = new UUID[]{null};
        helper.onEachTick(() -> {
            var current = walker.routeMovement().currentMarker();
            if (current != null) { visited.add(current); if (!current.equals(previous[0])) { transitions[0]++; previous[0] = current; } }
        });
        helper.runAtTickTime(700, () -> {
            helper.assertTrue(visited.size() == 4 && transitions[0] >= 5, "A ring remains a continuing patrol: visited=" + visited + ", transitions=" + transitions[0] + ", pos=" + walker.position() + ", state=" + walker.routeMovement().state() + ", requests=" + walker.routeMovement().navigationRequests());
            for (var edge : List.copyOf(data.connected(c.id()))) data.removeEdge(edge.id());
            for (var edge : List.copyOf(data.connected(d.id()))) data.removeEdge(edge.id());
            visited.clear(); transitions[0] = 0; previous[0] = null;
            walker.setPos(a.center()); walker.routeMovement().reset();
        });
        helper.runAtTickTime(1000, () -> {
            helper.assertTrue(visited.containsAll(List.of(a.id(), b.id())) && transitions[0] >= 3, "A dead end returns along the previous edge");
            walker.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience", timeoutTicks = 1220)
    public static void unreachableMarkerEntersLowFrequencyRetryAndRecovers(GameTestHelper helper) {
        floor(helper); var node = marker(helper, 12, 1, 12);
        helper.getLevel().setBlock(node.pos().above(), Blocks.STONE.defaultBlockState(), 2);
        var walker = citizen(helper, node.center().add(-4, 0, 0), true); walker.setBehavior(CitizenBehaviorState.walking());
        helper.runAtTickTime(650, () -> {
            helper.assertTrue(walker.routeMovement().consecutiveFailures() >= RouteSettings.FAILURE_WAIT_THRESHOLD
                    && walker.routeMovement().retryAt() > helper.getLevel().getGameTime(), "Repeated failures back off instead of spinning");
            helper.assertTrue(manager(helper).data().marker(node.id()) != null, "Failures do not delete the graph");
            helper.getLevel().removeBlock(node.pos().above(), false);
        });
        helper.runAtTickTime(1180, () -> {
            helper.assertTrue(node.id().equals(walker.routeMovement().currentMarker()) && walker.routeMovement().consecutiveFailures() == 0, "Successful movement resets failures");
            walker.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience", timeoutTicks = 620)
    public static void walkClimbsStairsAndChangesHeight(GameTestHelper helper) {
        floor(helper);
        for (int x = 11; x <= 20; x++) for (int z = 8; z <= 12; z++) {
            int rise = Math.min(3, (x - 10) / 2);
            for (int y = 1; y <= rise; y++) helper.setBlock(new BlockPos(x + 36, y, z + 36), Blocks.STONE);
            if (x < 16 && x % 2 == 1) helper.setBlock(new BlockPos(x + 36, rise + 1, z + 36),
                    Blocks.STONE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        }
        var a = marker(helper, 9, 1, 10); var b = marker(helper, 18, 4, 10); manager(helper).data().link(a.id(), b.id(), false);
        var walker = citizen(helper, a.center(), true); walker.setBehavior(CitizenBehaviorState.walking());
        helper.succeedWhen(() -> {
            helper.assertTrue(b.id().equals(walker.routeMovement().currentMarker()), "Standard Navigation traverses stairs and a three-block elevation change: pos=" + walker.position()
                    + ", from=" + a.center() + ", to=" + b.center() + ", current=" + walker.routeMovement().currentMarker() + ", target=" + walker.routeMovement().currentDestination()
                    + ", state=" + walker.routeMovement().state() + ", requests=" + walker.routeMovement().navigationRequests() + ", entityTicks=" + walker.tickCount
                    + ", failures=" + walker.routeMovement().consecutiveFailures() + ", retry=" + walker.routeMovement().retryAt() + ", loaded=" + manager(helper).loaded(b));
            walker.discard();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience")
    public static void phoneUnavailablePlacementLeavesChestDoorAndOtherBlocksAlone(GameTestHelper helper) {
        floor(helper); var pos = helper.absolutePos(new BlockPos(46, 1, 46));
        var p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "route-conflict"));
        p.setGameMode(GameType.SURVIVAL); p.setPos(Vec3.atBottomCenterOf(pos).add(0, 0, -2)); p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        for (var block : List.of(Blocks.CHEST, Blocks.OAK_DOOR, Blocks.LEVER)) {
            helper.getLevel().setBlock(pos, block.defaultBlockState(), 2);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
            var event = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos, hit);
            SmartPhoneRouteEditor.interact(event);
            helper.assertTrue(!event.isCanceled(), "Unavailable placement must not consume another block interaction: " + block);
        }
        helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2); helper.getLevel().setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 2);
        var occupied = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos, new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
        SmartPhoneRouteEditor.interact(occupied); helper.assertTrue(!occupied.isCanceled(), "Occupied placement falls through");
        helper.succeed();
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience")
    public static void supportLossLeavesOnlyBoundedTransientDiagnostics(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 1, 10); var b = marker(helper, 18, 1, 10); var manager = manager(helper);
        manager.data().link(a.id(), b.id(), false); var edge = manager.data().connected(a.id()).get(0);
        helper.getLevel().removeBlock(a.pos().below(), false);
        helper.assertTrue(manager.data().marker(a.id()) == null && manager.data().edge(edge.id()) == null, "Support loss cleans persistent references");
        helper.assertTrue(manager.removedEdges().stream().anyMatch(value -> value.id().equals(edge.id())
                && value.health() == com.rexhub.createapparel.network.RouteViewPacket.Health.REMOVED), "Cut connections remain identifiable as transient diagnostics");
        var restored = CitizenRouteSavedData.load(manager.data().save(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(restored.marker(a.id()) == null && restored.edge(edge.id()) == null, "Diagnostics are not persisted as broken edges");
        helper.succeed();
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience", timeoutTicks = 180)
    public static void unavailableBlockEntityAcrossChunkBoundaryWaitsAndReconciles(GameTestHelper helper) {
        floor(helper);
        var base = helper.absolutePos(new BlockPos(52, 1, 50));
        int x = ((base.getX() >> 4) << 4) + 15 - helper.absolutePos(BlockPos.ZERO).getX() - 36;
        var a = marker(helper, x, 1, 14); var b = marker(helper, x + 2, 1, 14); var manager = manager(helper);
        manager.data().link(a.id(), b.id(), false); var edge = manager.data().connected(a.id()).get(0);
        helper.assertTrue((a.pos().getX() >> 4) != (b.pos().getX() >> 4), "Fixture straddles a real chunk boundary");
        var walker = citizen(helper, a.center(), true); walker.setNoAi(true); walker.setBehavior(CitizenBehaviorState.walking());
        for (int i = 0; i < 4; i++) walker.routeMovement().tick();
        var identity = (CitizenRouteMarkerBlockEntity) helper.getLevel().getBlockEntity(b.pos());
        Objects.requireNonNull(identity).setMarkerId(UUID.randomUUID()); manager.loadChanged(); walker.routeMovement().tick();
        helper.assertTrue(walker.routeMovement().state() == CitizenRouteMovement.State.WAIT_ROUTE && walker.getNavigation().isDone(), "Unavailable identity stops the current route");
        helper.assertTrue(manager.data().edge(edge.id()) != null, "Availability changes retain persistent edges");
        identity.setMarkerId(b.id()); manager.reconcile(helper.getLevel().getChunkAt(b.pos()));
        var reached = new boolean[]{false};
        helper.onEachTick(() -> { if (b.id().equals(walker.routeMovement().currentMarker())) reached[0] = true; });
        helper.runAtTickTime(20, () -> walker.setNoAi(false));
        helper.runAtTickTime(160, () -> {
            helper.assertTrue(reached[0] && manager.loaded(manager.data().marker(b.id())) && manager.data().edge(edge.id()) != null, "Reconciliation restores walking with the original IDs");
            walker.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience")
    public static void ridingAnUnsupportedMovingEntityStopsNavigationWithoutTeleporting(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 1, 10); var b = marker(helper, 18, 1, 10); manager(helper).data().link(a.id(), b.id(), false);
        var walker = citizen(helper, a.center(), true); walker.setBehavior(CitizenBehaviorState.walking());
        var vehicle = Objects.requireNonNull(net.minecraft.world.entity.EntityType.BOAT.create(helper.getLevel()));
        vehicle.setPos(a.center()); helper.getLevel().addFreshEntity(vehicle);
        helper.assertTrue(walker.startRiding(vehicle, true), "Citizen can enter the moving-entity fixture");
        var before = walker.position(); walker.routeMovement().tick();
        helper.assertTrue(walker.getNavigation().isDone() && !CitizenPathfindingScheduler.get(helper.getLevel()).pending(walker)
                && walker.position().equals(before), "Unsupported riding does not start paths or teleport");
        walker.discard(); vehicle.discard(); helper.succeed();
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience", timeoutTicks = 1220)
    public static void closedCrossingWithoutDetourNeverFallsBackThroughTheSignal(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 10, 1, 10); var b = marker(helper, 16, 1, 10); var manager = manager(helper);
        manager.changeType(a.id(), RouteMarkerType.CROSSING); manager.changeType(b.id(), RouteMarkerType.CROSSING);
        manager.data().link(a.id(), b.id(), false);
        var walker = citizen(helper, a.center(), true); walker.routeMovement().requestDestination(b.center(), CitizenPathfindingScheduler.Priority.PLAYER);
        helper.runAtTickTime(630, () -> {
            helper.assertTrue(a.contains(walker.position()) && walker.routeMovement().hasDestination()
                    && walker.getNavigation().isDone(), "No detour must wait instead of bypassing the closed signal with Vanilla fallback");
        });
        helper.runAtTickTime(1000, () -> {
            helper.assertTrue(a.contains(walker.position()) && walker.getNavigation().isDone(), "The closed signal stays safe after local block expiry");
            for (var id : List.of(a.id(), b.id())) {
                var node = manager.data().marker(id); var signal = node.pos().north(2);
                helper.getLevel().setBlock(signal, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true), 2);
                manager.data().put(node.withCondition(new CrossingCondition(CrossingCondition.Mode.REDSTONE, signal, "", "", "", false)));
            }
        });
        helper.runAtTickTime(1160, () -> {
            helper.assertTrue(walker.position().distanceToSqr(b.center()) <= 1.1 && !walker.routeMovement().hasDestination(), "Opening the crossing promptly resumes the preserved intent");
            walker.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_load", timeoutTicks = 340)
    public static void twoHundredWalkingCitizensExposeBoundedWorkAndTiming(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 8, 1, 10).withRegion(8, 1); manager(helper).data().put(a); var b = marker(helper, 24, 1, 10); manager(helper).data().link(a.id(), b.id(), false);
        var citizens = new ArrayList<Citizen>(); var moved = new HashSet<UUID>(); var samples = new long[]{0, 0, 0, 0};
        for (int i = 0; i < 200; i++) {
            var walker = citizen(helper, a.center().add((i % 20) * 0.6 - 5.7, 0, (i / 20) * 0.6 - 2.7), true);
            walker.setBehavior(CitizenBehaviorState.walking()); citizens.add(walker);
        }
        helper.onEachTick(() -> {
            var scheduler = CitizenPathfindingScheduler.get(helper.getLevel()); var graph = manager(helper).planner().statistics();
            helper.assertTrue(scheduler.lastExecuted() <= 4 && graph.visitedNodes() <= RouteSettings.PLANNER_NODES_PER_TICK
                    && graph.scannedEdges() <= RouteSettings.PLANNER_EDGES_PER_TICK, "Load cannot bypass graph or Navigation budgets");
            samples[0]++; samples[1] += scheduler.lastNanos(); samples[2] = Math.max(samples[2], scheduler.lastNanos()); samples[3] = Math.max(samples[3], scheduler.queuedCount());
            for (var walker : citizens) if (walker.routeMovement().navigationRequests() > 0) moved.add(walker.getUUID());
        });
        helper.runAtTickTime(320, () -> {
            helper.assertTrue(moved.size() == 200, "All two hundred walkers receive Navigation service: served=" + moved.size() + ", sample=" + citizens.get(0).position() + ", state=" + citizens.get(0).routeMovement().state() + ", queued=" + CitizenPathfindingScheduler.get(helper.getLevel()).queuedCount());
            LogUtils.getLogger().info("ROUTE_LOAD citizens=200 serviced={} navigationQueueMax={} schedulerMeanMs={} schedulerMaxMs={}",
                    moved.size(), samples[3], samples[1] / (double) samples[0] / 1_000_000, samples[2] / 1_000_000.0);
            for (var citizen : citizens) citizen.discard(); helper.succeed();
        });
    }
}
