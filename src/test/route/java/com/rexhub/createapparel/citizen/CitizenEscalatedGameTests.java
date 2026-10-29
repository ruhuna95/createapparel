package com.rexhub.createapparel.citizen;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.citizen.route.CitizenActivityScheduler;
import com.rexhub.createapparel.citizen.route.CitizenRouteManager;
import com.rexhub.createapparel.citizen.route.RouteMarker;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenEscalatedGameTests {
    private static final Map<ServerLevel, Set<ChunkPos>> TICKETS = new HashMap<>();
    private static final int FLOOR = 80;

    private CitizenEscalatedGameTests() { }

    private static void prepare(GameTestHelper helper) {
        helper.assertTrue(CitizenEscalatedTransport.available(), "Real Escalated mod must be present");
        var chunk = new ChunkPos(helper.absolutePos(new BlockPos(56, FLOOR, 56)));
        helper.getLevel().getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 4, chunk);
        TICKETS.computeIfAbsent(helper.getLevel(), ignored -> new HashSet<>()).add(chunk);
        for (int x = chunk.x - 4; x <= chunk.x + 4; x++) for (int z = chunk.z - 4; z <= chunk.z + 4; z++)
            helper.getLevel().getChunk(x, z);
        for (int x = 38; x <= 70; x++) for (int z = 38; z <= 60; z++)
            helper.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE);
    }

    @AfterBatch(batch = "citizen_escalated")
    public static void releaseTickets(ServerLevel level) {
        for (var chunk : TICKETS.getOrDefault(level, Set.of()))
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 4, chunk);
        TICKETS.remove(level);
    }

    private static BlockState block(String id, Direction facing) {
        return Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("escalated", id)))
                .defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    private static BlockState slope(BlockState state, String value) {
        for (var property : state.getProperties()) if (property.getName().equals("slope"))
            return propertyValue(state, property, value);
        throw new AssertionError("Escalator slope property missing");
    }

    private static <T extends Comparable<T>> BlockState propertyValue(BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow());
    }

    private static CreativeMotorBlockEntity walkway(GameTestHelper helper, String material, boolean inclined, Direction facing, int rpm) {
        var origin = new BlockPos(46, FLOOR, 46);
        helper.setBlock(origin, block(material + "_walkway_terminal", facing));
        for (int i = 1; i <= 10; i++) {
            int rise = inclined ? Math.max(0, Math.min(5, i - 2)) : 0;
            var pos = origin.relative(facing, i).above(rise);
            var state = block(material + (inclined ? "_narrow_escalator" : "_narrow_walkway"), facing);
            if (inclined) state = slope(state, i <= 1 || i >= 8 ? "horizontal" : i == 2 ? "bottom" : i == 7 ? "top" : "middle");
            helper.setBlock(pos, state);
        }
        helper.setBlock(origin.relative(facing, 11).above(inclined ? 5 : 0),
                block(material + "_walkway_terminal", facing.getOpposite()));
        var drivePos = origin.relative(facing.getClockWise());
        helper.setBlock(drivePos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(CreativeMotorBlock.FACING, facing.getCounterClockWise()));
        var motor = (CreativeMotorBlockEntity) Objects.requireNonNull(helper.getLevel().getBlockEntity(helper.absolutePos(drivePos)));
        motor.generatedSpeed.setValue(rpm * facing.getCounterClockWise().getAxisDirection().getStep());
        motor.updateGeneratedRotation();
        return motor;
    }

    private static Citizen citizen(GameTestHelper helper, Vec3 position, boolean child) {
        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(helper.getLevel()));
        citizen.setBaby(child);
        citizen.setPos(position);
        citizen.setOnGround(true);
        helper.getLevel().addFreshEntity(citizen);
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(citizen, CitizenActivityScheduler.Reason.INTERACTION, 1500);
        return citizen;
    }

    private static Vec3 position(GameTestHelper helper, int x, int y, int z) {
        return Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, y, z)));
    }

    private static RouteMarker marker(GameTestHelper helper, int x, int y, int z) {
        var pos = helper.absolutePos(new BlockPos(x, y, z));
        helper.getLevel().setBlock(pos, ModBlocks.CITIZEN_ROUTE_MARKER.get().defaultBlockState(), 2);
        var manager = CitizenRouteManager.get(helper.getLevel());
        var marker = manager.register((CitizenRouteMarkerBlockEntity) Objects.requireNonNull(helper.getLevel().getBlockEntity(pos))).withRegion(0.5, 1);
        manager.data().put(marker);
        return marker;
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 650)
    public static void walkPrefersMovingWalkwayAndStandsUntilExit(GameTestHelper helper) {
        ride(helper, false, false);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 1000)
    public static void childWanderUsesWoodenWalkwayAndStandsUntilExit(GameTestHelper helper) {
        ride(helper, true, true);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 650)
    public static void powerLossReleasesStandingCitizenAndResumesWalking(GameTestHelper helper) {
        ride(helper, false, false, true);
    }

    private static void ride(GameTestHelper helper, boolean wander, boolean child) {
        ride(helper, wander, child, false);
    }

    private static void ride(GameTestHelper helper, boolean wander, boolean child, boolean powerLoss) {
        prepare(helper);
        var motor = walkway(helper, child ? "wooden" : "metal", false, Direction.EAST, -64);
        var start = marker(helper, 44, FLOOR + 1, 44);
        var end = marker(helper, wander ? 57 : 58, FLOOR + 1, 44);
        CitizenRouteManager.get(helper.getLevel()).data().link(start.id(), end.id(), false);
        var rider = citizen(helper, start.center(), child);
        helper.runAtTickTime(30, () -> {
            var powered = CitizenEscalatedTransport.surface(helper.getLevel(), helper.absolutePos(new BlockPos(50, FLOOR, 46)));
            helper.assertTrue(powered != null && powered.direction() == Direction.EAST, "Walkway must really move east");
            if (wander) {
                rider.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, rider.blockPosition(), helper.getLevel().dimension(), 24));
                helper.runAfterDelay(2, () -> {
                    var path = rider.getNavigation().createPath(BlockPos.containing(end.center()), 0);
                    helper.assertTrue(path != null && path.canReach(), "Wander destination is reachable: path=" + path
                            + ", end=" + (path == null ? null : path.getEndNode())
                            + ", loaded=" + CitizenNavigation.hasLoadedRegion(helper.getLevel(), rider.blockPosition(), 24));
                    helper.assertTrue(rider.getNavigation().moveTo(path, 1), "Start selected wander path");
                });
            } else rider.setBehavior(CitizenBehaviorState.walking());
        });
        var ridden = new int[]{0};
        var resumed = new boolean[]{false};
        var stopped = new boolean[]{false};
        helper.onEachTick(() -> {
            if (rider.standingOnTransport()) {
                ridden[0]++;
                helper.assertTrue(rider.zza == 0 && rider.xxa == 0 && rider.walkAnimation.speed() == 0,
                        "Transported citizen stands without walking inputs or animation");
            }
            if (powerLoss && ridden[0] >= 20 && !stopped[0]) {
                motor.generatedSpeed.setValue(0);
                motor.updateGeneratedRotation();
                stopped[0] = true;
            }
            if (ridden[0] > 0 && !rider.standingOnTransport() && rider.zza > 0) resumed[0] = true;
            if (helper.getTick() % 100 == 0) log(helper, rider, ridden[0]);
            boolean arrived = wander ? rider.position().distanceToSqr(end.center()) < 0.5 : end.id().equals(rider.routeMovement().currentMarker());
            if (!arrived) return;
            helper.assertTrue(ridden[0] >= 20 && resumed[0], "Citizen chooses the nearby walkway over a shorter ordinary floor and resumes walking: riding=" + ridden[0]);
            helper.assertTrue(!powerLoss || stopped[0], "Power loss occurs while the citizen is standing on the walkway");
            rider.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 650)
    public static void escalatorCarriesCitizenUpWithoutWalking(GameTestHelper helper) {
        escalator(helper, false);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 650)
    public static void escalatorCarriesCitizenDownWithoutWalking(GameTestHelper helper) {
        escalator(helper, true);
    }

    private static void escalator(GameTestHelper helper, boolean descending) {
        prepare(helper);
        for (int x = 54; x <= 60; x++) for (int z = 44; z <= 48; z++)
            for (int y = FLOOR + 1; y <= FLOOR + 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        walkway(helper, "metal", true, Direction.EAST, descending ? 64 : -64);
        var start = marker(helper, descending ? 59 : 44, FLOOR + (descending ? 6 : 1), 46);
        var end = marker(helper, descending ? 44 : 59, FLOOR + (descending ? 1 : 6), 46);
        CitizenRouteManager.get(helper.getLevel()).data().link(start.id(), end.id(), false);
        var rider = citizen(helper, start.center(), false);
        helper.runAtTickTime(30, () -> {
            rider.setBehavior(CitizenBehaviorState.walking());
            var delta = end.center().subtract(rider.position());
            var intermediate = BlockPos.containing(rider.position().add(delta.normalize().scale(12)));
            var direct = rider.getNavigation().createPath(intermediate, 0);
            var adjusted = rider.getNavigation().createPath(intermediate.above(descending ? -1 : 1), 0);
            LogUtils.getLogger().info("ESCALATED_WAYPOINT descending={} projected={} state={} direct={} adjusted={}", descending,
                    intermediate, helper.getLevel().getBlockState(intermediate),
                    direct == null ? null : direct.canReach() + ":" + direct.getEndNode(),
                    adjusted == null ? null : adjusted.canReach() + ":" + adjusted.getEndNode());
        });
        var ridden = new int[]{0};
        helper.onEachTick(() -> {
            double along = rider.getX() - helper.absolutePos(new BlockPos(46, FLOOR, 46)).getX();
            if (along > 3 && along < 6 && rider.onGround())
                helper.assertTrue(rider.standingOnTransport(), "Citizen stands throughout the middle of the escalator");
            if (rider.standingOnTransport()) {
                ridden[0]++;
                helper.assertTrue(rider.zza == 0 && rider.walkAnimation.speed() == 0, "No walking on escalator");
            }
            if (helper.getTick() % 100 == 0) log(helper, rider, ridden[0]);
            if (!end.id().equals(rider.routeMovement().currentMarker())) return;
            helper.assertTrue(ridden[0] >= 20, "Escalator must actually carry the citizen: " + ridden[0]);
            rider.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 200)
    public static void stoppedAndReverseWalkwaysAreNotPreferred(GameTestHelper helper) {
        prepare(helper);
        var motor = walkway(helper, "metal", false, Direction.SOUTH, 64);
        var rider = citizen(helper, position(helper, 44, FLOOR + 1, 44), false);
        rider.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, rider.blockPosition(), helper.getLevel().dimension(), 24));
        helper.runAtTickTime(30, () -> {
            var surface = CitizenEscalatedTransport.surface(helper.getLevel(), helper.absolutePos(new BlockPos(46, FLOOR, 50)));
            helper.assertTrue(surface != null && surface.direction() == Direction.SOUTH, "Positive Z-axis speed moves south");
            helper.assertTrue(surface.along(new Vec3(0, 0, 1)) && !surface.along(new Vec3(0, 0, -1)), "Priority follows actual rotation direction");
            motor.generatedSpeed.setValue(-64);
            motor.updateGeneratedRotation();
        });
        helper.runAtTickTime(50, () -> {
            var surface = CitizenEscalatedTransport.surface(helper.getLevel(), helper.absolutePos(new BlockPos(46, FLOOR, 50)));
            helper.assertTrue(surface != null && surface.direction() == Direction.NORTH, "Reversal changes direction without cached stale data");
            motor.generatedSpeed.setValue(0);
            motor.updateGeneratedRotation();
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(CitizenEscalatedTransport.surface(helper.getLevel(), helper.absolutePos(new BlockPos(46, FLOOR, 50))) == null,
                    "Stopped walkway does not receive a transport preference");
            helper.assertTrue(!rider.standingOnTransport(), "Stopped walkway never holds a citizen still");
            rider.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 400)
    public static void wanderAvoidsReverseEntryAndWalksAroundWalkway(GameTestHelper helper) {
        prepare(helper);
        walkway(helper, "metal", false, Direction.EAST, -64);
        var rider = citizen(helper, position(helper, 58, FLOOR + 1, 46), false);
        rider.goalSelector.removeAllGoals(goal -> goal instanceof CitizenBehaviorGoal);
        rider.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, rider.blockPosition(), helper.getLevel().dimension(), 24));
        var goal = position(helper, 45, FLOOR + 1, 46);
        helper.runAtTickTime(30, () -> {
            var path = rider.getNavigation().createPath(BlockPos.containing(goal), 0);
            helper.assertTrue(path != null && path.canReach(), "Ordinary floor provides a reverse-direction bypass: " + path
                    + " end=" + (path == null ? null : path.getEndNode()) + " pos=" + rider.position());
            assertForwardTransport(helper, rider, path);
            helper.assertTrue(rider.getNavigation().moveTo(path, 1), "Start bypass walk");
        });
        helper.onEachTick(() -> {
            helper.assertTrue(!rider.standingOnTransport(), "Reverse journey must not enter the powered walkway: pos=" + rider.position()
                    + " goal=" + goal + " path=" + rider.getNavigation().getPath());
            if (rider.position().distanceToSqr(goal) < 0.5) { rider.discard(); helper.succeed(); }
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 100)
    public static void routeCannotReverseEscalatorWithoutBypass(GameTestHelper helper) {
        reverseEscalator(helper, false);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 650)
    public static void walkApproachesWalkwayOnlyFromEntrance(GameTestHelper helper) {
        sideEntry(helper, Direction.EAST, false, -64, false);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 650)
    public static void childWanderApproachesWoodenWalkwayOnlyFromEntrance(GameTestHelper helper) {
        sideEntry(helper, Direction.SOUTH, false, 64, true);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 100)
    public static void ascendingEscalatorRejectsSideAndDiagonalEntry(GameTestHelper helper) {
        sideEntry(helper, Direction.EAST, true, -64, false);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 100)
    public static void descendingEscalatorRejectsSideAndDiagonalEntry(GameTestHelper helper) {
        sideEntry(helper, Direction.EAST, true, 64, false);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 100)
    public static void stoppedWalkwayAndTerminalsRejectSideAndDiagonalEntry(GameTestHelper helper) {
        sideEntry(helper, Direction.EAST, false, 0, false);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 100)
    public static void entranceDetourKeepsDefaultWanderRangeLimit(GameTestHelper helper) {
        prepare(helper);
        walkway(helper, "metal", false, Direction.EAST, -64);
        var rider = citizen(helper, position(helper, 44, FLOOR + 1, 44), false);
        rider.goalSelector.removeAllGoals(candidate -> candidate instanceof CitizenBehaviorGoal);
        rider.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, rider.blockPosition(), helper.getLevel().dimension(), 24));
        var goal = helper.absolutePos(new BlockPos(58, FLOOR + 1, 44));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(rider.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) == 16,
                    "The production follow range remains 16 blocks");
            var navigation = (CitizenNavigation) rider.getNavigation();
            var ordinary = navigation.createPath(goal, 0);
            helper.assertTrue(ordinary != null && ordinary.canReach(), "The shorter ordinary floor route remains reachable");
            for (int index = 0; index < ordinary.getNodeCount(); index++)
                helper.assertTrue(CitizenEscalatedTransport.support(rider, ordinary.getNodePos(index)) == null,
                        "The 16-block budget must not admit an overlong entrance detour");
            var probe = navigation.createPath(Set.of(goal), 8, false, 0, 32);
            helper.assertTrue(probe != null && probe.canReach(), "The entrance detour exists in the diagnostic larger range");
            double length = 0;
            boolean assisted = false;
            for (int index = 0; index < probe.getNodeCount(); index++) {
                if (index > 0) length += probe.getNode(index - 1).distanceTo(probe.getNode(index));
                assisted |= CitizenEscalatedTransport.support(rider, probe.getNodePos(index)) != null;
            }
            helper.assertTrue(assisted && length >= 16, "Without side entry, the preferred detour exceeds the default range: " + length);
            rider.discard(); helper.succeed();
        });
    }

    private static void sideEntry(GameTestHelper helper, Direction facing, boolean inclined, int rpm, boolean child) {
        prepare(helper);
        walkway(helper, child ? "wooden" : "metal", inclined, facing, rpm);
        var origin = helper.absolutePos(new BlockPos(46, FLOOR, 46));
        var side = facing.getCounterClockWise();
        var start = origin.relative(facing, 3).relative(side, 2).above();
        var goal = origin.relative(facing, 10).above(inclined ? 6 : 1);
        var rider = citizen(helper, Vec3.atBottomCenterOf(start), child);
        rider.goalSelector.removeAllGoals(candidate -> candidate instanceof CitizenBehaviorGoal);
        rider.setBehavior(child ? CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER,
                rider.blockPosition(), helper.getLevel().dimension(), 24) : CitizenBehaviorState.walking());
        helper.runAtTickTime(30, () -> {
            for (boolean costs : new boolean[]{true, false}) {
                var evaluator = new CitizenWalkNodeEvaluator();
                evaluator.setTransportCosts(costs);
                evaluator.prepare(new net.minecraft.world.level.PathNavigationRegion(helper.getLevel(), origin.offset(-16, -4, -16),
                        origin.offset(16, 12, 16)), rider);
                try {
                    for (int along : new int[]{0, 1, 2, 4, 7, 10, 11}) {
                        int rise = inclined ? Math.max(0, Math.min(5, along - 2)) : 0;
                        var feet = origin.relative(facing, along).above(rise + 1);
                        for (int lateral : new int[]{-1, 1}) for (int forward : new int[]{-1, 0, 1}) {
                            var from = feet.relative(side, lateral).relative(facing, -forward);
                            var node = new net.minecraft.world.level.pathfinder.Node(from.getX(), from.getY(), from.getZ());
                            var neighbors = new net.minecraft.world.level.pathfinder.Node[32];
                            int count = evaluator.getNeighbors(neighbors, node);
                            for (int index = 0; index < count; index++) {
                                var next = neighbors[index];
                                helper.assertTrue(next.x != feet.getX() || next.z != feet.getZ(),
                                        "No side or diagonal entry, including terminals and ordinary-cost fallback: costs=" + costs
                                                + " from=" + from + " next=" + next.asBlockPos());
                            }
                        }
                    }
                } finally { evaluator.done(); }
            }
            if (inclined || rpm == 0) { rider.discard(); helper.succeed(); return; }
            var path = rider.getNavigation().createPath(goal, 0);
            helper.assertTrue(path != null && path.canReach(), "Forward entrance remains reachable from beside the walkway");
            assertForwardTransport(helper, rider, path);
            rider.getNavigation().moveTo(path, 1);
        });
        var ridden = new boolean[1];
        if (!inclined && rpm != 0) helper.onEachTick(() -> {
            if (!ridden[0] && rider.standingOnTransport()) {
                var relative = rider.position().subtract(Vec3.atBottomCenterOf(origin));
                double along = relative.x * facing.getStepX() + relative.z * facing.getStepZ();
                helper.assertTrue(along <= 2, "First boarding must be at the entrance, not through the side: " + rider.position());
                ridden[0] = true;
            }
            if (rider.position().distanceToSqr(Vec3.atBottomCenterOf(goal)) > 0.25) return;
            helper.assertTrue(ridden[0], "Citizen really boards through the entrance and rides the walkway");
            rider.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 100)
    public static void routeCannotClimbDescendingEscalator(GameTestHelper helper) {
        reverseEscalator(helper, true);
    }

    private static void reverseEscalator(GameTestHelper helper, boolean descending) {
        prepare(helper);
        for (int x = 54; x <= 60; x++) for (int z = 44; z <= 48; z++)
            for (int y = FLOOR + 1; y <= FLOOR + 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        walkway(helper, "metal", true, Direction.EAST, descending ? 64 : -64);
        var rider = citizen(helper, position(helper, descending ? 45 : 58, FLOOR + (descending ? 1 : 6), 46), false);
        helper.runAtTickTime(30, () -> {
            rider.setBehavior(CitizenBehaviorState.walking());
            var path = rider.getNavigation().createPath(helper.absolutePos(new BlockPos(descending ? 58 : 45, FLOOR + (descending ? 6 : 1), 46)), 0);
            if (path != null) assertForwardTransport(helper, rider, path);
            rider.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 250)
    public static void liveReversalInvalidatesForwardWalkwayPath(GameTestHelper helper) {
        prepare(helper);
        var motor = walkway(helper, "metal", false, Direction.EAST, -64);
        var rider = citizen(helper, position(helper, 45, FLOOR + 1, 46), false);
        rider.goalSelector.removeAllGoals(goal -> goal instanceof CitizenBehaviorGoal);
        rider.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, rider.blockPosition(), helper.getLevel().dimension(), 24));
        helper.runAtTickTime(30, () -> {
            var path = rider.getNavigation().createPath(helper.absolutePos(new BlockPos(56, FLOOR + 1, 46)), 0);
            helper.assertTrue(path != null && path.canReach(), "Forward journey is reachable before reversal: pos=" + rider.position()
                    + " ground=" + rider.onGround() + " path=" + path + " end=" + (path == null ? null : path.getEndNode()));
            rider.getNavigation().moveTo(path, 1);
        });
        var reversedAt = new long[]{-1};
        helper.onEachTick(() -> {
            if (reversedAt[0] < 0 && rider.standingOnTransport()) {
                motor.generatedSpeed.setValue(64); motor.updateGeneratedRotation();
                reversedAt[0] = helper.getTick();
            }
            if (reversedAt[0] < 0 || helper.getTick() - reversedAt[0] < 12) return;
            var path = rider.getNavigation().getPath();
            if (path != null && !path.isDone()) assertForwardTransport(helper, rider, path);
            rider.discard(); helper.succeed();
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 450)
    public static void fastWalkwayExitDoesNotReturnToPassedWaypoint(GameTestHelper helper) {
        prepare(helper);
        walkway(helper, "metal", false, Direction.EAST, -256);
        var start = marker(helper, 44, FLOOR + 1, 46);
        var end = marker(helper, 60, FLOOR + 1, 46);
        CitizenRouteManager.get(helper.getLevel()).data().link(start.id(), end.id(), false);
        var rider = citizen(helper, start.center(), false);
        helper.runAtTickTime(30, () -> rider.setBehavior(CitizenBehaviorState.walking()));
        var ridden = new boolean[1];
        var furthest = new double[]{rider.getX()};
        helper.onEachTick(() -> {
            if (rider.standingOnTransport()) ridden[0] = true;
            if (ridden[0]) {
                if (helper.getTick() % 100 == 0) log(helper, rider, 1);
                furthest[0] = Math.max(furthest[0], rider.getX());
                helper.assertTrue(rider.getX() >= furthest[0] - 0.2,
                        "Passenger must not return to a waypoint passed during transport: pos=" + rider.position()
                                + " maximum=" + furthest[0] + " origin=" + start.pos() + " riding=" + rider.standingOnTransport()
                                + " path=" + rider.getNavigation().getPath()
                                + " next=" + (rider.getNavigation().isDone() ? null : rider.getNavigation().getPath().getNextEntityPos(rider))
                                + " state=" + rider.routeMovement().state() + " failures=" + rider.routeMovement().consecutiveFailures());
            }
            if (!end.id().equals(rider.routeMovement().currentMarker())) return;
            helper.assertTrue(ridden[0], "Fast walkway must actually carry the citizen");
            rider.discard(); helper.succeed();
        });
    }

    private static void assertForwardTransport(GameTestHelper helper, Citizen rider, net.minecraft.world.level.pathfinder.Path path) {
        for (int i = 1; i < path.getNodeCount(); i++) {
            var previous = path.getNodePos(i - 1);
            var next = path.getNodePos(i);
            var from = CitizenEscalatedTransport.support(rider, previous);
            var to = CitizenEscalatedTransport.support(rider, next);
            var delta = Vec3.atLowerCornerOf(next.subtract(previous));
            for (var surface : new CitizenEscalatedTransport.Surface[]{from, to}) if (surface != null) {
                double forward = delta.x * surface.direction().getStepX() + delta.z * surface.direction().getStepZ();
                helper.assertTrue(forward >= 0 && (surface != to || forward > 0),
                        "Path must not reverse or enter sideways: " + previous + " -> " + next + " direction=" + surface.direction());
            }
        }
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 650)
    public static void offsetWalkwayExitNeverBacktracksBeforeMarker(GameTestHelper helper) {
        offsetExit(helper, false, false);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 650)
    public static void offsetWanderExitNeverBacktracksBeforeDestination(GameTestHelper helper) {
        offsetExit(helper, true, false);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 650)
    public static void offsetChildWoodenWalkwayExitNeverBacktracksBeforeMarker(GameTestHelper helper) {
        offsetExit(helper, false, true);
    }

    private static void offsetExit(GameTestHelper helper, boolean wander, boolean child) {
        prepare(helper);
        walkway(helper, child ? "wooden" : "metal", false, Direction.EAST, -64);
        var start = marker(helper, 44, FLOOR + 1, 44);
        var end = marker(helper, wander ? 57 : 58, FLOOR + 1, 44);
        CitizenRouteManager.get(helper.getLevel()).data().link(start.id(), end.id(), false);
        var rider = citizen(helper, start.center(), child);
        helper.runAtTickTime(30, () -> {
            if (!wander) { rider.setBehavior(CitizenBehaviorState.walking()); return; }
            rider.goalSelector.removeAllGoals(goal -> goal instanceof CitizenBehaviorGoal);
            rider.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, rider.blockPosition(), helper.getLevel().dimension(), 24));
            var path = rider.getNavigation().createPath(BlockPos.containing(end.center()), 0);
            helper.assertTrue(path != null && path.canReach(), "Offset destination is reachable");
            rider.getNavigation().moveTo(path, 1);
        });
        var ridden = new boolean[1];
        var furthest = new double[]{rider.getX()};
        helper.onEachTick(() -> {
            boolean arrived = wander ? rider.position().distanceToSqr(end.center()) <= 0.25
                    : end.id().equals(rider.routeMovement().currentMarker());
            if (arrived) { helper.assertTrue(ridden[0], "The offset journey really uses the walkway"); rider.discard(); helper.succeed(); return; }
            if (rider.standingOnTransport()) ridden[0] = true;
            if (!ridden[0]) return;
            furthest[0] = Math.max(furthest[0], rider.getX());
            var path = rider.getNavigation().getPath();
            helper.assertTrue(rider.getX() >= furthest[0] - 0.03, "Offset exit must not return to the old lateral exit point: pos="
                    + rider.position() + " max=" + furthest[0] + " next=" + (path == null || path.isDone() ? null : path.getNextEntityPos(rider)));
        });
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 1800)
    public static void escalatorPatrolContinuesViaSideStairsAndUpdatesNextMarker(GameTestHelper helper) {
        escalatorPatrol(helper, false);
    }

    @GameTest(template = "citizen_route_resilience", batch = "citizen_escalated", timeoutTicks = 1800)
    public static void descendingEscalatorPatrolClimbsSideStairsAndReturnsOnTransport(GameTestHelper helper) {
        escalatorPatrol(helper, true);
    }

    private static void escalatorPatrol(GameTestHelper helper, boolean descending) {
        prepare(helper);
        for (int x = 54; x <= 63; x++) for (int z = 45; z <= 50; z++)
            for (int y = FLOOR + 1; y <= FLOOR + 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        for (int x = 49; x <= 53; x++) for (int z = 48; z <= 50; z++) {
            int rise = x - 48;
            for (int y = 1; y <= rise; y++) helper.setBlock(new BlockPos(x, FLOOR + y, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, FLOOR + rise, z), Blocks.STONE_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        }
        walkway(helper, "metal", true, Direction.EAST, descending ? 64 : -64);
        var lower = marker(helper, 44, FLOOR + 1, 46);
        var upper = marker(helper, 59, FLOOR + 6, 46);
        CitizenRouteManager.get(helper.getLevel()).data().link(lower.id(), upper.id(), false);
        var rider = citizen(helper, lower.center(), false);
        CitizenActivityScheduler.get(helper.getLevel()).forceActive(rider, CitizenActivityScheduler.Reason.INTERACTION, 2000);
        helper.runAtTickTime(30, () -> rider.setBehavior(CitizenBehaviorState.walking()));
        var phase = new int[1];
        var usedStairs = new boolean[1];
        var ridden = new int[1];
        helper.onEachTick(() -> {
            if (rider.standingOnTransport()) ridden[0]++;
            var current = rider.routeMovement().currentMarker();
            if (phase[0] == 0 && upper.id().equals(current)) phase[0] = 1;
            else if (phase[0] == 1 && lower.id().equals(current)) phase[0] = 2;
            else if (phase[0] == 2 && upper.id().equals(current)) {
                helper.assertTrue(usedStairs[0] && ridden[0] >= 20, "Patrol uses both the side stairs and real transport without reversing the escalator");
                rider.discard(); helper.succeed();
            }
            if (phase[0] == (descending ? 0 : 1) && rider.getZ() >= lower.center().z + 1.5) usedStairs[0] = true;
            if (helper.getTick() % 100 == 0) {
                log(helper, rider, phase[0]);
                LogUtils.getLogger().info("ESCALATOR_PATROL phase={} goal={} upper={} lower={}", phase[0],
                        rider.routeMovement().currentDestination(), upper.pos(), lower.pos());
            }
        });
    }

    private static void log(GameTestHelper helper, Citizen rider, int ridden) {
        var path = rider.getNavigation().getPath();
        LogUtils.getLogger().info("ESCALATED_CHECK tick={} pos={} ground={} riding={} inputs={} ridden={} state={} failures={} path={}",
                helper.getTick(), rider.position(), rider.onGround(), rider.standingOnTransport(), rider.zza, ridden,
                rider.routeMovement().state(), rider.routeMovement().consecutiveFailures(),
                path == null ? null : path.getNextNodeIndex() + "/" + path.getNodeCount() + ":" + (path.isDone() ? "done" : path.getNextEntityPos(rider)));
    }
}
