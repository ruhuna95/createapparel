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
    @net.minecraft.gametest.framework.AfterBatch(batch = "citizen_route_view_load")
    public static void releaseViewTickets(net.minecraft.server.level.ServerLevel level) { releaseTickets(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "citizen_route_cache_runtime")
    public static void releaseCacheRuntimeTickets(net.minecraft.server.level.ServerLevel level) { releaseTickets(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "citizen_route_cache_crossing")
    public static void releaseCacheCrossingTickets(net.minecraft.server.level.ServerLevel level) { releaseTickets(level); }
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
    private static String markerState(GameTestHelper helper, RouteMarker marker) {
        return marker.pos() + ":saved=" + (manager(helper).data().marker(marker.id()) != null)
                + ":block=" + helper.getLevel().getBlockState(marker.pos())
                + ":support=" + helper.getLevel().getBlockState(marker.pos().below());
    }
    private static void logRoute(GameTestHelper helper, Citizen walker, List<RouteMarker> nodes) {
        if (helper.getTick() % 100 != 0) return;
        var m = manager(helper);
        LogUtils.getLogger().info("ROUTE_DETOUR_CHECK tick={} pos={} state={} current={} activeDetour={} nav={} failures={} planner={} nodes={}",
                helper.getTick(), walker.position(), walker.routeMovement().state(), walker.routeMovement().currentMarker(), walker.routeMovement().detouring(),
                walker.routeMovement().navigationRequests(), walker.routeMovement().consecutiveFailures(), m.planner().statistics(),
                nodes.stream().map(n -> n.pos() + ":saved=" + (m.data().marker(n.id()) != null) + ":loaded=" + m.loaded(m.data().marker(n.id()))).toList());
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
        var signalA = marker(helper, 34, 1, 24); var signalB = marker(helper, 35, 1, 24); var m = manager(helper);
        m.changeType(signalA.id(), RouteMarkerType.CROSSING); m.changeType(signalB.id(), RouteMarkerType.CROSSING);
        var signal = signalA.pos().north(2);
        helper.getLevel().setBlock(signal, Blocks.LEVER.defaultBlockState(), 2);
        for (var node : List.of(signalA, signalB)) data.put(data.marker(node.id()).withCondition(new CrossingCondition(CrossingCondition.Mode.REDSTONE, signal, "", "", "", false)));
        data.link(signalA.id(), signalB.id(), false);
        var citizens = new ArrayList<Citizen>(); var completed = new HashSet<UUID>(); var deferred = new boolean[]{false};
        for (int i = 0; i < 16; i++) citizens.add(citizen(helper, nodes.get(0).center(), false));
        var planner = manager(helper).planner(); var polls = new int[]{0};
        var generations = new long[]{data.version(), m.loadRevision(), 0, 0};
        helper.onEachTick(() -> {
            polls[0]++;
            if (data.version() != generations[0]) { generations[0] = data.version(); generations[2]++; }
            if (m.loadRevision() != generations[1]) { generations[1] = m.loadRevision(); generations[3]++; }
            helper.getLevel().setBlock(signal, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, polls[0] % 2 == 0), 2);
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
            helper.assertTrue(completed.size() == 16 && deferred[0], "All sixteen Citizens complete multi-tick searches without starvation: completed="
                    + completed.size() + ", deferred=" + deferred[0] + ", planner=" + planner.statistics() + ", graphChanges=" + generations[2]
                    + ", loadChanges=" + generations[3] + ", invalid=" + nodes.stream().filter(node -> !m.loaded(data.marker(node.id())))
                    .limit(4).map(node -> markerState(helper, node)).toList());
            for (var citizen : citizens) planner.cancel(citizen);
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_cache_runtime", timeoutTicks = 200)
    public static void undeliveredCacheRechecksRuntimeAndMarkerValidity(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 8, 1, 8); var b = marker(helper, 16, 1, 8); var c = marker(helper, 12, 1, 16);
        var m = manager(helper); var data = m.data(); data.link(a.id(), b.id(), false); data.link(a.id(), c.id(), false); data.link(c.id(), b.id(), false);
        var blocked = data.connected(a.id()).stream().filter(e -> e.other(a.id()).equals(b.id())).findFirst().orElseThrow();
        var walker = citizen(helper, a.center(), false); var planner = m.planner();
        var reference = citizen(helper, a.center(), false); var readyAt = new long[]{Long.MAX_VALUE};
        helper.onEachTick(() -> {
            if (readyAt[0] != Long.MAX_VALUE) return;
            var initial = planner.between(reference, a.id(), b.id());
            if (initial.deferred()) return;
            helper.assertTrue(initial.edges().contains(blocked.id()), "Fixture initially chooses the direct route");
            for (int i = 0; i < RouteSettings.PLANNER_SEARCHES_PER_TICK; i++) planner.between(citizen(helper, a.center(), false), a.id(), b.id());
            helper.assertTrue(planner.between(walker, a.id(), b.id()).deferred(), "Cache is produced asynchronously before its first delivery");
            readyAt[0] = helper.getTick() + 9;
            helper.runAfterDelay(8, () -> m.runtime().blockLocal(walker.getUUID(), blocked.id(), helper.getLevel().getGameTime()));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() >= readyAt[0], "Wait until the cached edge is blocked");
            var plan = planner.between(walker, a.id(), b.id());
            helper.assertTrue(!plan.deferred() && !plan.fallback() && plan.nodes().equals(List.of(a.id(), c.id(), b.id())), "Budgeted recomputation finds the actual alternative");
            var entity = (CitizenRouteMarkerBlockEntity) helper.getLevel().getBlockEntity(c.pos());
            Objects.requireNonNull(entity).setMarkerId(UUID.randomUUID());
            m.loadChanged();
            var unavailable = planner.between(walker, a.id(), b.id());
            helper.assertTrue(unavailable.deferred() || unavailable.fallback(), "Unavailable marker is not handed off from cache");
            entity.setMarkerId(c.id()); m.reconcile(helper.getLevel().getChunkAt(c.pos()));
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_cache_crossing", timeoutTicks = 120)
    public static void asynchronousFirstDeliveryRechecksCrossingChanges(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 8, 1, 8); var b = marker(helper, 16, 1, 8); var m = manager(helper);
        m.changeType(a.id(), RouteMarkerType.CROSSING); m.changeType(b.id(), RouteMarkerType.CROSSING);
        var signal = a.pos().north(2);
        helper.getLevel().setBlock(signal, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true), 2);
        for (var node : List.of(a, b)) m.data().put(m.data().marker(node.id()).withCondition(new CrossingCondition(CrossingCondition.Mode.REDSTONE, signal, "", "", "", false)));
        m.data().link(a.id(), b.id(), false);
        var walker = citizen(helper, a.center(), false); var planner = m.planner(); var avoided = UUID.randomUUID();
        var synchronous = citizen(helper, a.center(), false);
        var immediate = planner.openDetour(synchronous, a.id(), b.id(), avoided);
        if (!immediate.deferred()) {
            helper.getLevel().setBlock(signal, Blocks.LEVER.defaultBlockState(), 2);
            var closed = planner.openDetour(synchronous, a.id(), b.id(), avoided);
            helper.assertTrue(closed.deferred() || closed.fallback(), "Even same-tick signal changes invalidate an open-only cache before the periodic revision poll");
            helper.getLevel().setBlock(signal, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true), 2);
        }
        for (int i = 0; i < RouteSettings.PLANNER_SEARCHES_PER_TICK; i++) {
            var filler = citizen(helper, a.center(), false); planner.between(filler, a.id(), b.id());
        }
        helper.assertTrue(planner.openDetour(walker, a.id(), b.id(), avoided).deferred(), "Request begins behind the shared search budget");
        helper.runAtTickTime(8, () -> helper.getLevel().setBlock(signal, Blocks.LEVER.defaultBlockState(), 2));
        helper.runAtTickTime(9, () -> {
            var plan = planner.openDetour(walker, a.id(), b.id(), avoided);
            helper.assertTrue(plan.deferred() || plan.fallback(), "First delivery cannot reuse an open-only route after its signal closed");
        });
        helper.runAtTickTime(20, () -> helper.getLevel().setBlock(signal, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true), 2));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() >= 21, "Wait for reopening");
            var plan = planner.openDetour(walker, a.id(), b.id(), avoided);
            helper.assertTrue(!plan.deferred() && !plan.fallback() && plan.edges().size() == 1, "Opening permits budgeted replanning rather than caching no-route indefinitely");
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience", timeoutTicks = 1220)
    public static void acceptedDetourIsFollowedAndSurvivesSignalOpening(GameTestHelper helper) {
        GameTestFixtures.dryRoom(helper, 35, 35, 40);
        floor(helper); var a = marker(helper, 8, 1, 8); var b = marker(helper, 16, 1, 8); var c = marker(helper, 8, 1, 29); var m = manager(helper);
        m.changeType(a.id(), RouteMarkerType.CROSSING); m.changeType(b.id(), RouteMarkerType.CROSSING);
        for (var node : List.of(a, b)) {
            var signal = node.pos().north(2);
            helper.getLevel().setBlock(signal, Blocks.LEVER.defaultBlockState(), 2);
            m.data().put(m.data().marker(node.id()).withCondition(new CrossingCondition(CrossingCondition.Mode.REDSTONE, signal, "", "", "", false)));
        }
        m.data().link(a.id(), b.id(), false); m.data().link(a.id(), c.id(), false); m.data().link(c.id(), b.id(), false);
        var unrelated = marker(helper, 32, 1, 32);
        var walker = citizen(helper, a.center(), true); walker.routeMovement().requestDestination(b.center(), CitizenPathfindingScheduler.Priority.PLAYER);
        var adopted = new boolean[]{false}; var visited = new boolean[]{false};
        helper.onEachTick(() -> {
            logRoute(helper, walker, List.of(a, b, c));
            if (walker.routeMovement().detouring() && !adopted[0]) {
                adopted[0] = true;
                m.changeType(unrelated.id(), RouteMarkerType.GATEWAY);
                for (var node : List.of(a, b)) {
                    var signal = node.pos().north(2);
                    helper.getLevel().setBlock(signal, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true), 2);
                }
            }
            if (c.id().equals(walker.routeMovement().currentMarker())) visited[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(adopted[0] && visited[0] && !walker.routeMovement().hasDestination() && walker.position().distanceToSqr(b.center()) <= 1.1,
                    "Accepted detour is traversed without switching back when the signal opens: adopted=" + adopted[0] + ", visited=" + visited[0] + ", pos=" + walker.position());
            walker.discard();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience", timeoutTicks = 1220)
    public static void deletingAFutureDetourEdgeStopsSafelyWithoutReturningToTheClosedCrossing(GameTestHelper helper) {
        GameTestFixtures.dryRoom(helper, 35, 35, 40);
        floor(helper); var a = marker(helper, 8, 1, 8); var b = marker(helper, 16, 1, 8); var c = marker(helper, 8, 1, 29); var m = manager(helper);
        m.changeType(a.id(), RouteMarkerType.CROSSING); m.changeType(b.id(), RouteMarkerType.CROSSING);
        m.data().link(a.id(), b.id(), false); m.data().link(a.id(), c.id(), false); m.data().link(c.id(), b.id(), false);
        var removed = m.data().connected(c.id()).stream().filter(e -> e.other(c.id()).equals(b.id())).findFirst().orElseThrow();
        var walker = citizen(helper, a.center(), true); walker.routeMovement().requestDestination(b.center(), CitizenPathfindingScheduler.Priority.PLAYER);
        var changed = new boolean[]{false}; var arrivedAt = new long[]{Long.MAX_VALUE};
        helper.onEachTick(() -> {
            logRoute(helper, walker, List.of(a, b, c));
            if (!changed[0] && walker.routeMovement().detouring()) { changed[0] = true; m.data().removeEdge(removed.id()); }
            if (c.id().equals(walker.routeMovement().currentMarker()) && arrivedAt[0] == Long.MAX_VALUE) arrivedAt[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(changed[0] && arrivedAt[0] != Long.MAX_VALUE && helper.getTick() > arrivedAt[0] + 100 && c.contains(walker.position()) && walker.routeMovement().hasDestination()
                    && walker.routeMovement().state() == CitizenRouteMovement.State.WAIT_ROUTE && walker.getNavigation().isDone(),
                    "Changed detour must wait safely: adopted=" + changed[0] + ", arrived=" + arrivedAt[0] + ", pos=" + walker.position()
                            + ", current=" + walker.routeMovement().currentMarker() + ", target=" + walker.routeMovement().currentDestination()
                            + ", state=" + walker.routeMovement().state() + ", failures=" + walker.routeMovement().consecutiveFailures() + ", requests=" + walker.routeMovement().navigationRequests());
            walker.discard();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_view_load", timeoutTicks = 280)
    public static void routeViewMeasuresSharedSnapshotsAndFrequentEditDeltas(GameTestHelper helper) {
        floor(helper); var m = manager(helper); var nodes = new ArrayList<RouteMarker>();
        for (int x = 36; x <= 74; x++) for (int z = 36; z <= 74; z++) {
            helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(x, y, z), x == 36 || x == 74 || z == 36 || z == 74 ? Blocks.STONE : Blocks.AIR);
        }
        var players = new ArrayList<ServerPlayer>(); var receivers = new ArrayList<RouteViewTransfer.Receiver>();
        for (int i = 0; i < 16; i++) {
            var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "route-view-" + i));
            player.setPos(helper.absoluteVec(new Vec3(52, 1, 52))); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
            players.add(player); receivers.add(new RouteViewTransfer.Receiver());
        }
        var stage = new int[]{0}; var tick = new int[]{0}; var bytes = new long[2][16]; var nanos = new long[2][16]; var packets = new long[2][16];
        helper.onEachTick(() -> {
            int offset = tick[0] % 60;
            int count = new int[]{128, 600, 1024, 1024}[stage[0]];
            if (offset == 0) {
                while (nodes.size() < count) {
                    int i = nodes.size(); var node = marker(helper, i % 32 + 1, 1, i / 32 + 1);
                    if (!nodes.isEmpty()) m.data().link(nodes.get(nodes.size() - 1).id(), node.id(), false);
                    nodes.add(node);
                }
                RouteViewSynchronization.clear();
                for (int i = 0; i < receivers.size(); i++) receivers.set(i, new RouteViewTransfer.Receiver());
                if (stage[0] == 3) for (int i = 0; i < players.size(); i++) players.get(i).setPos(helper.absoluteVec(new Vec3(50 + i % 4, 1, 50 + i / 4)));
                for (int phase = 0; phase < 2; phase++) { Arrays.fill(bytes[phase], 0); Arrays.fill(nanos[phase], 0); Arrays.fill(packets[phase], 0); }
            }
            if (offset == 20) m.data().put(m.data().marker(nodes.get(0).id()).withRegion(stage[0] + 4, 2));
            if (offset >= 40 && offset <= 45) m.data().put(m.data().marker(nodes.get(1).id()).withRegion(offset == 45 ? 7 : 7 + offset % 2, 2));
            int phase = offset < 20 ? 0 : 1;
            for (int i = 0; i < players.size(); i++) {
                final int index = i;
                long started = System.nanoTime();
                RouteViewSynchronization.update(players.get(i), packet -> {
                    packets[phase][index]++;
                    var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                    try { packet.encode(buffer); bytes[phase][index] += buffer.readableBytes(); }
                    finally { buffer.release(); }
                    receivers.get(index).accept(packet);
                });
                nanos[phase][index] += System.nanoTime() - started;
                if (i == 0 || i == 3 || i == 15) {
                    if (offset == 19 || offset == 39) LogUtils.getLogger().info("ROUTE_VIEW_SERVER nodes={} edges={} players={} spread={} phase={} packets={} bytes={} cpuMs={}",
                            receivers.get(0).nodes().size(), receivers.get(0).edges().size(), i + 1, stage[0] == 3, phase,
                            Arrays.stream(packets[phase], 0, i + 1).sum(), Arrays.stream(bytes[phase], 0, i + 1).sum(), Arrays.stream(nanos[phase], 0, i + 1).sum() / 1_000_000.0);
                }
            }
            helper.assertTrue(RouteViewSynchronization.statistics(helper.getLevel()).captures() <= 2, "Distinct view reconstruction stays within the shared per-tick budget");
            if (offset == 39) {
                for (var node : nodes) if (!m.loaded(m.data().marker(node.id()))) {
                    boolean loaded = com.rexhub.createapparel.citizen.CitizenNavigation.hasLoadedRegion(helper.getLevel(), node.pos(), 0);
                    LogUtils.getLogger().warn("ROUTE_VIEW_MISSING stage={} pos={} chunkLoaded={} saved={} state={} entity={}", stage[0], node.pos(), loaded,
                            m.data().marker(node.id()), loaded ? helper.getLevel().getBlockState(node.pos()) : null, loaded ? helper.getLevel().getBlockEntity(node.pos()) : null);
                    break;
                }
                for (var receiver : receivers) helper.assertTrue(receiver.nodes().stream().anyMatch(n -> n.id().equals(nodes.get(0).id()) && n.radius() == stage[0] + 4), "Every independent view commits the node update");
                helper.assertTrue(Arrays.stream(bytes[1]).sum() < Arrays.stream(bytes[0]).sum() / 10 && Arrays.stream(packets[1]).sum() == 16, "A single edit sends one small delta per player, not a full view");
            }
            tick[0]++;
            if (offset == 59) {
                for (var receiver : receivers) helper.assertTrue(receiver.nodes().stream().anyMatch(n -> n.id().equals(nodes.get(1).id()) && n.radius() == 7), "Continuous edits coalesce and retain their final value");
                helper.assertTrue(Arrays.stream(packets[1]).sum() <= 16 * 4, "Frequent edits do not restart a full snapshot on every tick");
                stage[0]++;
                if (stage[0] == 4) {
                    for (var player : players) RouteViewSynchronization.logout(player.getUUID());
                    helper.succeed();
                }
            }
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
            helper.assertTrue(visited.containsAll(List.of(a.id(), b.id())) && transitions[0] >= 3, "A dead end returns along the previous edge: visited="
                    + visited + ", transitions=" + transitions[0] + ", pos=" + walker.position() + ", state=" + walker.routeMovement().state()
                    + ", requests=" + walker.routeMovement().navigationRequests() + ", ground=" + helper.getLevel().getBlockState(walker.blockPosition())
                    + ", markers=" + List.of(markerState(helper, a), markerState(helper, b)));
            walker.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience", timeoutTicks = 1220)
    public static void unreachableMarkerEntersLowFrequencyRetryAndRecovers(GameTestHelper helper) {
        retryAndRecover(helper, 4);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_route_resilience", timeoutTicks = 1220)
    public static void shortRecoveryWalkClearsPreviousNavigationFailures(GameTestHelper helper) {
        retryAndRecover(helper, 0.8);
    }

    private static void retryAndRecover(GameTestHelper helper, double startDistance) {
        floor(helper); var node = marker(helper, 12, 1, 12);
        helper.getLevel().setBlock(node.pos().above(), Blocks.STONE.defaultBlockState(), 2);
        var walker = citizen(helper, node.center().add(-startDistance, 0, 0), true); walker.setBehavior(CitizenBehaviorState.walking());
        var initialPosition = walker.position();
        var entered = new boolean[1];
        var lastState = new String[]{""};
        helper.onEachTick(() -> {
            var movement = walker.routeMovement();
            var state = movement.state() + ":" + movement.currentMarker() + ":" + movement.consecutiveFailures() + ":" + movement.navigationRequests();
            if (!state.equals(lastState[0])) {
                lastState[0] = state;
                LogUtils.getLogger().info("ROUTE_RECOVERY_TRACE tick={} short={} pos={} state={} attempt={} nav={}",
                        helper.getTick(), startDistance < 1, walker.position(), state, recoveryAttempt(movement),
                        walker.getNavigation().getPath());
            }
            if (entered[0] || !node.id().equals(walker.routeMovement().currentMarker())) return;
            entered[0] = true;
            if (startDistance < 1) helper.assertTrue(walker.position().distanceToSqr(initialPosition) < 0.25,
                    "Recovery really succeeds after less than half a block of movement");
        });
        helper.runAtTickTime(650, () -> {
            helper.assertTrue(walker.routeMovement().consecutiveFailures() >= RouteSettings.FAILURE_WAIT_THRESHOLD
                    && walker.routeMovement().retryAt() > helper.getLevel().getGameTime(), "Repeated failures back off instead of spinning");
            helper.assertTrue(manager(helper).data().marker(node.id()) != null, "Failures do not delete the graph: " + markerState(helper, node));
            helper.getLevel().removeBlock(node.pos().above(), false);
        });
        helper.runAtTickTime(1180, () -> {
            helper.assertTrue(walker.routeMovement().navigationRequests() == 1,
                    "Actual recovery progress must not be cancelled by stale failure counts: requests=" + walker.routeMovement().navigationRequests());
            helper.assertTrue(node.id().equals(walker.routeMovement().currentMarker()) && walker.routeMovement().consecutiveFailures() == 0,
                    "Successful movement resets failures: pos=" + walker.position() + ", state=" + walker.routeMovement().state()
                            + ", current=" + walker.routeMovement().currentMarker() + ", failures=" + walker.routeMovement().consecutiveFailures()
                            + ", target=" + walker.routeMovement().currentDestination() + ", marker=" + markerState(helper, node)
                            + ", nearby=" + manager(helper).nearby(walker.position(), RouteSettings.DISCOVERY_RADIUS).stream().map(RouteMarker::id).toList());
            walker.discard(); helper.succeed();
        });
    }

    private static String recoveryAttempt(CitizenRouteMovement movement) {
        var values = new ArrayList<String>();
        for (var name : List.of("attemptOrigin", "pathStart", "pathIssued")) {
            try {
                var field = CitizenRouteMovement.class.getDeclaredField(name);
                field.setAccessible(true);
                values.add(name + "=" + field.get(movement));
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        }
        return values.toString();
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
    public static void serverRechecksPredictedTargetsBeforeConsumingOrDeleting(GameTestHelper helper) {
        floor(helper); var a = marker(helper, 8, 1, 8); var b = marker(helper, 16, 1, 8); var m = manager(helper);
        m.data().link(a.id(), b.id(), false); var edge = m.data().connected(a.id()).get(0);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "route-prediction"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(a.center().add(0, 0, -3)); player.setYRot(180); player.setXRot(0); player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        var pos = a.pos().north(1); var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        SmartPhoneRouteEditor.interact(event, true, new RouteEdgePicking.Candidate(edge.id(), a.pos(), b.pos()));
        helper.assertTrue(!event.isCanceled() && m.data().edge(edge.id()) != null, "Client prediction cannot consume an unrelated block or remove an edge outside the authoritative ray");
        SmartPhoneRouteEditor.logout(player.getUUID()); helper.succeed();
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
        var metrics = com.rexhub.createapparel.citizen.population.CitizenPopulationManager.get(helper.getLevel()).metrics(); metrics.start();
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
            metrics.stop(); for (var citizen : citizens) citizen.discard(); helper.succeed();
        });
    }
}
