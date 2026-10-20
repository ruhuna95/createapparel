package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler;
import com.rexhub.createapparel.citizen.route.CitizenActivityScheduler;
import net.minecraft.server.level.ServerLevel;

public final class CitizenBehaviorGoal extends Goal {
    public static final int FOLLOW_INTERVAL = 20;
    public static final int FAILURE_BACKOFF = 40;
    public static final int TELEPORT_INTERVAL = 10;
    public static final double START_DISTANCE_SQUARED = 16;
    public static final double STOP_DISTANCE_SQUARED = 6.25;
    private static final int[] WANDER_HEIGHT_OFFSETS = {0, 1, -1, 2, -2, 3, -3, 4, -4};
    private final Citizen citizen;
    private CitizenBehaviorState observed = CitizenBehaviorState.DEFAULT;
    @Nullable private Entity target;
    @Nullable private Vec3 lastTargetPosition;
    private int resolveDelay;
    private int pathDelay;
    private int teleportDelay;
    private boolean following;
    @Nullable private CitizenWanderArea wanderArea;
    @Nullable private BlockPos wanderTarget;

    public CitizenBehaviorGoal(Citizen citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() { return !citizen.level().isClientSide && (citizen.citizenState().behavior().mode().moves() || citizen.routeMovement().hasDestination()); }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void stop() {
        if (citizen.level() instanceof ServerLevel level) CitizenPathfindingScheduler.get(level).cancel(citizen);
        citizen.routeMovement().reset();
        citizen.getNavigation().stop();
        target = null;
        lastTargetPosition = null;
        following = false;
        wanderArea = null;
        wanderTarget = null;
        pathDelay = resolveDelay = teleportDelay = 0;
    }

    @Override
    public void tick() {
        var state = citizen.citizenState().behavior();
        if (!state.equals(observed)) {
            stop();
            observed = state;
        }
        if (pathDelay > 0) pathDelay--;
        if (teleportDelay > 0) teleportDelay--;
        if (state.mode() == CitizenBehaviorMode.WALK || citizen.routeMovement().hasDestination()) citizen.routeMovement().tick();
        else if (state.mode() == CitizenBehaviorMode.FOLLOW) follow(state);
        else if (state.mode() == CitizenBehaviorMode.WANDER) wander(state);
    }

    private void follow(CitizenBehaviorState state) {
        if (target != null && !CitizenFollowTarget.valid(citizen, state, target)) {
            target = null;
            citizen.getNavigation().stop();
            following = false;
            lastTargetPosition = null;
        }
        if (--resolveDelay <= 0) {
            target = CitizenFollowTarget.resolve(citizen);
            resolveDelay = FOLLOW_INTERVAL;
        }
        if (target == null || citizen.isPassenger() || citizen.isLeashed()) {
            citizen.getNavigation().stop();
            following = false;
            return;
        }
        double distance = citizen.distanceToSqr(target);
        if (distance <= STOP_DISTANCE_SQUARED) {
            citizen.getNavigation().stop();
            following = false;
            lastTargetPosition = null;
            return;
        }
        if (!following && distance <= START_DISTANCE_SQUARED) return;
        following = true;
        if (distance >= FollowOwnerGoal.TELEPORT_WHEN_DISTANCE_IS * FollowOwnerGoal.TELEPORT_WHEN_DISTANCE_IS) {
            citizen.getNavigation().stop();
            lastTargetPosition = null;
            if (teleportDelay <= 0) {
                teleportDelay = TELEPORT_INTERVAL;
                if (!target.isSpectator()) teleportToTarget(target);
            }
            return;
        }
        if (pathDelay > 0) return;
        if (!citizen.getNavigation().isDone() && lastTargetPosition != null
                && lastTargetPosition.distanceToSqr(target.position()) < 1) return;
        pathDelay = FOLLOW_INTERVAL;
        if (!loadedSearchRegion() || !CitizenNavigation.hasLoadedRegion(citizen.level(), target.blockPosition(), 0)) {
            citizen.getNavigation().stop();
            pathDelay = FAILURE_BACKOFF;
            return;
        }
        Vec3 destination = target.position();
        Vec3 delta = destination.subtract(citizen.position());
        if (delta.lengthSqr() > 144) destination = citizen.position().add(delta.normalize().scale(12));
        var expectedTarget = target;
        var waypoint = BlockPos.containing(destination);
        var level = (ServerLevel) citizen.level();
        CitizenActivityScheduler.get(level).forceActive(citizen, CitizenActivityScheduler.Reason.PLAYER, 40);
        CitizenPathfindingScheduler.get(level).submit(citizen, CitizenPathfindingScheduler.Priority.PLAYER,
                () -> state.equals(citizen.citizenState().behavior()) && CitizenFollowTarget.valid(citizen, state, expectedTarget)
                        && !citizen.isPassenger() && !citizen.isLeashed() && citizen.distanceToSqr(expectedTarget) > STOP_DISTANCE_SQUARED
                        && citizen.distanceToSqr(expectedTarget) < 144, () -> {
                    Path path = citizen.getNavigation().createPath(waypoint, 1);
                    if (path == null || path.getNodeCount() < 2 || !citizen.getNavigation().moveTo(path, 1)) {
                        citizen.getNavigation().stop();
                        pathDelay = FAILURE_BACKOFF;
                    } else lastTargetPosition = expectedTarget.position();
                });
    }

    private void teleportToTarget(Entity followTarget) {
        var center = followTarget.blockPosition();
        var random = citizen.getRandom();
        for (int attempt = 0; attempt < 10; attempt++) {
            var pos = center.offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
            if (Math.abs(pos.getX() - followTarget.getX()) < 2 && Math.abs(pos.getZ() - followTarget.getZ()) < 2) continue;
            if (!canTeleportTo(pos)) continue;
            citizen.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, citizen.getYRot(), citizen.getXRot());
            citizen.getNavigation().stop();
            return;
        }
    }

    private boolean canTeleportTo(BlockPos pos) {
        var level = citizen.level();
        if (!level.isInWorldBounds(pos) || !level.isInWorldBounds(pos.above())
                || !CitizenNavigation.hasLoadedRegion(level, pos, 1)) return false;
        if (WalkNodeEvaluator.getBlockPathTypeStatic(level, pos.mutable()) != BlockPathTypes.WALKABLE
                || level.getBlockState(pos.below()).getBlock() instanceof LeavesBlock) return false;
        var destination = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        var bounds = citizen.getBoundingBox().move(destination.subtract(citizen.position()));
        var border = level.getWorldBorder();
        return bounds.minX >= border.getMinX() && bounds.maxX <= border.getMaxX()
                && bounds.minZ >= border.getMinZ() && bounds.maxZ <= border.getMaxZ()
                && level.noCollision(citizen, bounds);
    }

    private void wander(CitizenBehaviorState state) {
        if (citizen.intents().consumeWanderRetry()) { pathDelay = 0; wanderArea = null; wanderTarget = null; }
        if (!state.unlimitedWander() && (state.anchor() == null || !citizen.level().dimension().equals(state.anchorDimension()))) {
            citizen.getNavigation().stop();
            return;
        }
        if (state.unlimitedWander() && wanderArea != null && !wanderArea.valid(state, citizen.citizenState().population())) {
            CitizenPathfindingScheduler.get((ServerLevel) citizen.level()).cancel(citizen);
            citizen.getNavigation().stop(); wanderArea = null; pathDelay = 0;
            wanderTarget = null;
        }
        if (wanderTarget != null && citizen.getNavigation().isDone()) {
            if (citizen.position().distanceToSqr(Vec3.atBottomCenterOf(wanderTarget)) > 1) citizen.intents().wanderFailed();
            else citizen.intents().wanderSucceeded();
            wanderTarget = null;
        }
        if (pathDelay > 0 || !citizen.getNavigation().isDone()
                || CitizenActivityScheduler.get((ServerLevel) citizen.level()).state(citizen) == CitizenActivityScheduler.State.THROTTLED
                || CitizenPathfindingScheduler.get((ServerLevel) citizen.level()).pending(citizen)) return;
        pathDelay = FAILURE_BACKOFF;
        if (!loadedSearchRegion()) return;
        var random = citizen.getRandom();
        var population = com.rexhub.createapparel.citizen.population.CitizenPopulationManager.get((ServerLevel) citizen.level());
        if (state.unlimitedWander()) population.update(citizen);
        var area = new CitizenWanderArea(state, citizen.citizenState().population(), population.data(), citizen.blockPosition());
        wanderArea = area;
        var routes = com.rexhub.createapparel.citizen.route.CitizenRouteManager.get((ServerLevel) citizen.level());
        var destinations = new java.util.ArrayList<BlockPos>();
        for (int attempt = 0; attempt < 8; attempt++) {
            var candidate = area.candidate(random, attempt);
            if (!area.allowsDestination(candidate) || !CitizenNavigation.hasLoadedRegion(citizen.level(), candidate, 0)) continue;
            BlockPos walkable = null;
            for (int offset : WANDER_HEIGHT_OFFSETS) {
                var pos = candidate.offset(0, offset, 0);
                if (routes.safeEndpoint(citizen, pos)) {
                    walkable = pos;
                    break;
                }
            }
            if (walkable == null) continue;
            destinations.add(walkable);
        }
        requestWanderPath(area, destinations, 0, false);
    }

    private void requestWanderPath(CitizenWanderArea area, java.util.List<BlockPos> destinations, int index, boolean failed) {
        if (index >= destinations.size()) {
            pathDelay = FAILURE_BACKOFF;
            if (failed) citizen.intents().wanderFailed(); else citizen.intents().wanderIdle();
            return;
        }
        CitizenPathfindingScheduler.get((ServerLevel) citizen.level()).submit(citizen, CitizenPathfindingScheduler.Priority.AUTONOMY,
                () -> area.valid(citizen.citizenState().behavior(), citizen.citizenState().population()) && citizen.getNavigation().isDone(), () -> {
                    Path path = citizen.getNavigation().createPath(destinations.get(index), 0);
                    if (path != null && path.canReach() && path.getNodeCount() < 2
                            && citizen.position().distanceToSqr(Vec3.atBottomCenterOf(destinations.get(index))) <= 1) {
                        requestWanderPath(area, destinations, index + 1, failed); return;
                    }
                    boolean contained = path != null && path.canReach() && path.getNodeCount() >= 2;
                    if (contained) for (int node = 0; node < path.getNodeCount(); node++) {
                        if (!area.allowsPath(path.getNodePos(node))) { contained = false; break; }
                    }
                    if (contained && citizen.getNavigation().moveTo(path, 1)) {
                        wanderTarget = destinations.get(index); pathDelay = 60 + citizen.getRandom().nextInt(81);
                    }
                    else requestWanderPath(area, destinations, index + 1, true);
                });
    }

    private boolean loadedSearchRegion() {
        int extent = (int) Math.ceil(citizen.getAttributeValue(Attributes.FOLLOW_RANGE)) + 8;
        var pos = citizen.blockPosition();
        return CitizenNavigation.hasLoadedRegion(citizen.level(), pos, extent);
    }
}
