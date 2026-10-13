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

public final class CitizenBehaviorGoal extends Goal {
    public static final int FOLLOW_INTERVAL = 20;
    public static final int FAILURE_BACKOFF = 40;
    public static final int TELEPORT_INTERVAL = 10;
    public static final double START_DISTANCE_SQUARED = 16;
    public static final double STOP_DISTANCE_SQUARED = 6.25;
    private final Citizen citizen;
    private CitizenBehaviorState observed = CitizenBehaviorState.DEFAULT;
    @Nullable private Entity target;
    @Nullable private Vec3 lastTargetPosition;
    private int resolveDelay;
    private int pathDelay;
    private int teleportDelay;
    private boolean following;

    public CitizenBehaviorGoal(Citizen citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() { return !citizen.level().isClientSide && citizen.citizenState().behavior().mode().moves(); }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
        target = null;
        lastTargetPosition = null;
        following = false;
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
        if (state.mode() == CitizenBehaviorMode.FOLLOW) follow(state);
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
        Path path = citizen.getNavigation().createPath(BlockPos.containing(destination), 1);
        if (path == null || path.getNodeCount() < 2 || !citizen.getNavigation().moveTo(path, 1)) {
            citizen.getNavigation().stop();
            pathDelay = FAILURE_BACKOFF;
        } else lastTargetPosition = target.position();
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
        if (state.anchor() == null || !citizen.level().dimension().equals(state.anchorDimension())) {
            citizen.getNavigation().stop();
            return;
        }
        if (pathDelay > 0 || !citizen.getNavigation().isDone()) return;
        pathDelay = FAILURE_BACKOFF;
        if (!loadedSearchRegion()) return;
        var random = citizen.getRandom();
        int radius = state.wanderRadius();
        int stepRadius = Math.min(radius, 12);
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = citizen.blockPosition().getX() + random.nextInt(stepRadius * 2 + 1) - stepRadius;
            int z = citizen.blockPosition().getZ() + random.nextInt(stepRadius * 2 + 1) - stepRadius;
            var candidate = new BlockPos(x, citizen.blockPosition().getY(), z);
            if (!inside(state, candidate) || !CitizenNavigation.hasLoadedRegion(citizen.level(), candidate, 0)) continue;
            BlockPos walkable = null;
            for (int offset = 4; offset >= -4; offset--) {
                var pos = candidate.offset(0, offset, 0);
                if (citizen.level().isInWorldBounds(pos) && citizen.level().getBlockState(pos.below()).isSolidRender(citizen.level(), pos.below())
                        && citizen.level().getBlockState(pos).getCollisionShape(citizen.level(), pos).isEmpty()
                        && citizen.level().getBlockState(pos.above()).getCollisionShape(citizen.level(), pos.above()).isEmpty()
                        && citizen.level().getFluidState(pos).isEmpty()) {
                    walkable = pos;
                    break;
                }
            }
            if (walkable == null) continue;
            Path path = citizen.getNavigation().createPath(walkable, 0);
            if (path == null || !path.canReach() || path.getNodeCount() < 2) continue;
            boolean contained = true;
            for (int node = 0; node < path.getNodeCount(); node++) {
                if (!inside(state, path.getNodePos(node))) { contained = false; break; }
            }
            if (contained && citizen.getNavigation().moveTo(path, 1)) {
                pathDelay = 60 + random.nextInt(81);
                return;
            }
        }
    }

    private static boolean inside(CitizenBehaviorState state, BlockPos pos) {
        if (state.anchor() == null) return false;
        long x = (long) pos.getX() - state.anchor().getX();
        long z = (long) pos.getZ() - state.anchor().getZ();
        return x * x + z * z <= (long) state.wanderRadius() * state.wanderRadius();
    }

    private boolean loadedSearchRegion() {
        int extent = (int) Math.ceil(citizen.getAttributeValue(Attributes.FOLLOW_RANGE)) + 8;
        var pos = citizen.blockPosition();
        return CitizenNavigation.hasLoadedRegion(citizen.level(), pos, extent);
    }
}
