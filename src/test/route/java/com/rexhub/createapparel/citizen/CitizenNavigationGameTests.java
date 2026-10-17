package com.rexhub.createapparel.citizen;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.citizen.route.CitizenActivityScheduler;
import com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler;
import com.rexhub.createapparel.citizen.route.CitizenRouteManager;
import com.rexhub.createapparel.citizen.route.RouteMarker;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenNavigationGameTests {
    private static final Map<ServerLevel, Set<ChunkPos>> TICKETS = new IdentityHashMap<>();

    private CitizenNavigationGameTests() { }

    private static void prepare(GameTestHelper helper) {
        var chunk = new ChunkPos(helper.absolutePos(new BlockPos(56, 1, 56)));
        helper.getLevel().getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 4, chunk);
        TICKETS.computeIfAbsent(helper.getLevel(), ignored -> new HashSet<>()).add(chunk);
        for (int x = chunk.x - 4; x <= chunk.x + 4; x++) for (int z = chunk.z - 4; z <= chunk.z + 4; z++) {
            helper.getLevel().getChunk(x, z);
        }
        for (int x = 37; x < 75; x++) for (int z = 37; z < 75; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    @AfterBatch(batch = "citizen_navigation")
    public static void releaseTickets(ServerLevel level) {
        for (var chunk : TICKETS.getOrDefault(level, Set.of())) level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 4, chunk);
        TICKETS.remove(level);
    }

    private static RouteMarker marker(GameTestHelper helper, int x, int z) {
        var pos = helper.absolutePos(new BlockPos(x, 1, z));
        helper.getLevel().setBlock(pos, ModBlocks.CITIZEN_ROUTE_MARKER.get().defaultBlockState(), 2);
        var manager = CitizenRouteManager.get(helper.getLevel());
        var marker = manager.register((CitizenRouteMarkerBlockEntity) Objects.requireNonNull(helper.getLevel().getBlockEntity(pos)))
                .withRegion(0.5, 1);
        manager.data().put(marker);
        return marker;
    }

    private static void logMovement(GameTestHelper helper, Citizen citizen, RouteMarker end) {
        if (helper.getTick() % 80 != 0) return;
        var path = citizen.getNavigation().getPath();
        LogUtils.getLogger().info("ROUTE_MOVEMENT child={} tick={} pos={} state={} current={} requests={} failures={} ground={} feet={} end={} path={}",
                citizen.isBaby(), helper.getTick(), citizen.position(), citizen.routeMovement().state(), citizen.routeMovement().currentMarker(),
                citizen.routeMovement().navigationRequests(), citizen.routeMovement().consecutiveFailures(), citizen.onGround(),
                helper.getLevel().getBlockState(citizen.blockPosition()), end.pos(),
                path == null ? null : path.getNextNodeIndex() + "/" + path.getNodeCount() + ":" + (path.isDone() ? "done" : path.getNextEntityPos(citizen)));
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_navigation", timeoutTicks = 400)
    public static void diagonalWalkDoesNotFollowAGridZigzag(GameTestHelper helper) {
        diagonalWalk(helper, 44, 44, 68, 56, false);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_navigation", timeoutTicks = 1100)
    public static void childWalkSmoothlyAcrossANegativeDiagonal(GameTestHelper helper) {
        diagonalWalk(helper, 68, 56, 44, 44, true);
    }

    private static void diagonalWalk(GameTestHelper helper, int fromX, int fromZ, int toX, int toZ, boolean child) {
        prepare(helper);
        var start = marker(helper, fromX, fromZ);
        var end = marker(helper, toX, toZ);
        CitizenRouteManager.get(helper.getLevel()).data().link(start.id(), end.id(), false);
        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(helper.getLevel()));
        citizen.setBaby(child);
        citizen.setPos(start.center());
        citizen.setOnGround(true);
        helper.getLevel().addFreshEntity(citizen);
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 1200);
        citizen.setBehavior(CitizenBehaviorState.walking());
        var previous = new Vec3[]{start.center()};
        var direction = new Vec3[]{Vec3.ZERO};
        var travelled = new double[]{0};
        var turns = new int[]{0};
        helper.onEachTick(() -> {
            logMovement(helper, citizen, end);
            if (end.id().equals(citizen.routeMovement().currentMarker())) {
                double direct = previous[0].subtract(start.center()).horizontalDistance();
                LogUtils.getLogger().info("ROUTE_DIAGONAL travelled={} direct={} excess={} sharpTurns={}", travelled[0], direct, travelled[0] - direct, turns[0]);
                citizen.discard();
                helper.assertTrue(travelled[0] - direct <= 0.25 && turns[0] <= 4,
                        "Open diagonal travel stays smooth: excess=" + (travelled[0] - direct) + ", sharpTurns=" + turns[0]);
                helper.succeed();
                return;
            }
            var movement = citizen.position().subtract(previous[0]).multiply(1, 0, 1);
            previous[0] = citizen.position();
            if (movement.lengthSqr() < 1.0E-6) return;
            travelled[0] += movement.length();
            var heading = movement.normalize();
            if (direction[0].lengthSqr() > 0 && Math.acos(Mth.clamp(direction[0].dot(heading), -1, 1)) > Math.toRadians(8)) turns[0]++;
            direction[0] = heading;
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_navigation")
    public static void shortcutsRejectUnsafeGroundAndBodyCollisions(GameTestHelper helper) {
        prepare(helper);
        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(helper.getLevel()));
        citizen.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(44, 1, 44))));
        citizen.setOnGround(true);
        var target = citizen.position().add(8, 0, 4);
        helper.assertTrue(CitizenPathShortcut.canTravel(citizen, target), "Flat supported diagonal is safe");
        var obstacle = new BlockPos(48, 1, 46);
        helper.setBlock(obstacle, Blocks.STONE);
        helper.assertTrue(!CitizenPathShortcut.canTravel(citizen, target), "Wall cannot be skipped");
        helper.setBlock(obstacle, Blocks.AIR);
        helper.setBlock(obstacle.above(), Blocks.STONE);
        helper.assertTrue(!CitizenPathShortcut.canTravel(citizen, target), "Head collision cannot be skipped");
        helper.setBlock(obstacle.above(), Blocks.AIR);
        helper.setBlock(obstacle.below(), Blocks.AIR);
        helper.assertTrue(!CitizenPathShortcut.canTravel(citizen, target), "Unsupported floor cannot be skipped");
        helper.setBlock(obstacle.below(), Blocks.STONE);
        helper.setBlock(obstacle, Blocks.WATER);
        helper.assertTrue(!CitizenPathShortcut.canTravel(citizen, target), "Water cannot be skipped");
        helper.setBlock(obstacle, Blocks.AIR);
        helper.setBlock(obstacle.below(), Blocks.STONE_SLAB);
        helper.assertTrue(!CitizenPathShortcut.canTravel(citizen, target), "A change in floor height cannot be skipped");
        helper.setBlock(obstacle.below(), Blocks.STONE);
        helper.setBlock(obstacle.offset(0, 0, 1), Blocks.CAMPFIRE);
        helper.assertTrue(!CitizenPathShortcut.canTravel(citizen, target), "Adjacent pathfinding hazards cannot be skipped");
        var unloaded = new BlockPos(1_000_000, 1, 1_000_000);
        var chunks = helper.getLevel().getChunkSource();
        helper.assertTrue(!chunks.hasChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4), "Fixture target starts unloaded");
        citizen.setPos(Vec3.atBottomCenterOf(unloaded));
        helper.assertTrue(!CitizenPathShortcut.canTravel(citizen, citizen.position().add(8, 0, 4)), "Unloaded terrain cannot be skipped");
        helper.assertTrue(!chunks.hasChunk(unloaded.getX() >> 4, unloaded.getZ() >> 4), "Shortcut checks do not load chunks");
        citizen.discard();
        helper.succeed();
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_navigation", timeoutTicks = 500)
    public static void anObstacleOnAShortcutReplansAroundTheWall(GameTestHelper helper) {
        prepare(helper);
        var start = marker(helper, 44, 44);
        var end = marker(helper, 68, 44);
        CitizenRouteManager.get(helper.getLevel()).data().link(start.id(), end.id(), false);
        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(helper.getLevel()));
        citizen.setPos(start.center());
        citizen.setOnGround(true);
        helper.getLevel().addFreshEntity(citizen);
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 600);
        citizen.setBehavior(CitizenBehaviorState.walking());
        var wall = helper.absolutePos(new BlockPos(52, 1, 44));
        var changed = new boolean[]{false};
        var replanned = new boolean[]{false};
        var original = new net.minecraft.world.level.pathfinder.Path[]{null};
        helper.onEachTick(() -> {
            logMovement(helper, citizen, end);
            if (!changed[0] && citizen.getX() > start.center().x + 1.5) {
                helper.assertTrue(citizen.getNavigation().shouldRecomputePath(wall), "A changed block on the shortcut requests recomputation");
                original[0] = citizen.getNavigation().getPath();
                for (int z = 43; z <= 45; z++) for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(52, y, z), Blocks.STONE);
                helper.assertTrue(CitizenPathfindingScheduler.get(helper.getLevel()).pending(citizen)
                        && citizen.getNavigation().getPath() == original[0], "Changed shortcut queues rather than immediately calculating a path");
                changed[0] = true;
            }
            if (changed[0] && citizen.getNavigation().getPath() != original[0]) replanned[0] = true;
            if (!end.id().equals(citizen.routeMovement().currentMarker())) return;
            helper.assertTrue(changed[0] && replanned[0], "Changed shortcut uses a new scheduled path");
            citizen.discard();
            helper.succeed();
        });
    }
}
