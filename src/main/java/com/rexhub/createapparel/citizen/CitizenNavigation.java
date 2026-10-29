package com.rexhub.createapparel.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.Set;

public final class CitizenNavigation extends GroundPathNavigation {
    private boolean recomputePending;
    @Nullable private Path checkedPath;
    @Nullable private Path shortcutPath;
    private int checkedNode = -1;
    private int checkedAt;
    private net.minecraft.world.phys.Vec3 transportPosition = net.minecraft.world.phys.Vec3.ZERO;
    private long transportProgressAt;
    private boolean ridingTransport;
    @Nullable private CitizenEscalatedTransport.Surface lastTransport;
    private long lastTransportAt;
    @Nullable private Path transportCompletedPath;
    private boolean transportEpisode;
    private boolean transportExitPending;
    @Nullable private Path transportExitPath;
    public CitizenNavigation(Mob mob, Level level) { super(mob, level); }

    @Override
    protected net.minecraft.world.level.pathfinder.PathFinder createPathFinder(int maxVisitedNodes) {
        var evaluator = new CitizenWalkNodeEvaluator();
        nodeEvaluator = evaluator;
        nodeEvaluator.setCanPassDoors(true);
        return new CitizenPathFinder(evaluator, maxVisitedNodes);
    }

    public boolean transportProgress() { return ridingTransport && level.getGameTime() - transportProgressAt < 40; }
    public boolean transportPassedWaypoint() { return path != null && path == transportCompletedPath && path.isDone(); }
    public boolean canStartPath() { return canUpdatePath(); }
    public boolean consumeTransportExit() {
        boolean pending = transportExitPending;
        transportExitPending = false;
        return pending;
    }
    public boolean supportsTravel(BlockPos pos, net.minecraft.world.phys.Vec3 direction) {
        var surface = CitizenEscalatedTransport.support(mob, pos);
        return surface == null || surface.permits(direction, true);
    }

    public void resumeAfterInteraction() {
        lastTimeoutCheck = level.getGameTime();
        timeoutTimer = 0;
        timeoutLimit = 0;
        timeoutCachedNode = net.minecraft.core.Vec3i.ZERO;
        lastStuckCheck = tick;
        lastStuckCheckPos = mob.position();
    }

    @Override
    public void tick() {
        try (var diagnosticScope = com.rexhub.createapparel.citizen.population.CitizenSystemMetrics.measure("navigation")) {
            if (mob instanceof com.rexhub.createapparel.entity.Citizen citizen) {
                var mode = citizen.citizenState().behavior().mode();
                var surface = mode == com.rexhub.createapparel.entity.CitizenBehaviorMode.WALK
                        || mode == com.rexhub.createapparel.entity.CitizenBehaviorMode.WANDER || citizen.routeMovement().hasDestination()
                        ? CitizenEscalatedTransport.riding(mob) : null;
                if (surface == null) ridingTransport = false;
                else {
                    if (!ridingTransport || mob.position().distanceToSqr(transportPosition) > 0.0001) {
                        transportPosition = mob.position();
                        transportProgressAt = level.getGameTime();
                    }
                    ridingTransport = true;
                    lastTransport = surface;
                    lastTransportAt = level.getGameTime();
                    transportEpisode = true;
                }
                citizen.setStandingOnTransport(ridingTransport);
                if (!ridingTransport && transportEpisode && CitizenEscalatedTransport.support(mob,
                        BlockPos.containing(mob.getX(), mob.getY() - 0.05, mob.getZ())) == null) {
                    transportEpisode = false;
                    if (mode == com.rexhub.createapparel.entity.CitizenBehaviorMode.WALK || citizen.routeMovement().hasDestination()) {
                        transportExitPending = true;
                        stop();
                        holdPosition();
                    } else if (mode == com.rexhub.createapparel.entity.CitizenBehaviorMode.WANDER && path != null && !path.isDone()) {
                        com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler.get((net.minecraft.server.level.ServerLevel) level).cancel(citizen);
                        transportExitPath = path;
                    }
                }
            }
            if (replanTransportExit()) return;
            if (transportDirectionBlocked()) {
                stop();
                mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
            }
            super.tick();
        }
    }

    private boolean replanTransportExit() {
        if (transportExitPath == null) return false;
        if (path != transportExitPath || !(mob instanceof com.rexhub.createapparel.entity.Citizen citizen)
                || citizen.citizenState().behavior().mode() != com.rexhub.createapparel.entity.CitizenBehaviorMode.WANDER
                || citizen.routeMovement().hasDestination()) { transportExitPath = null; return false; }
        holdPosition();
        if (!canStartPath()) return true;
        var expected = transportExitPath;
        var goal = expected.getTarget();
        com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler.get((net.minecraft.server.level.ServerLevel) level).submit(citizen,
                com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler.Priority.AUTONOMY,
                () -> path == expected && transportExitPath == expected
                        && citizen.citizenState().behavior().mode() == com.rexhub.createapparel.entity.CitizenBehaviorMode.WANDER
                        && !citizen.routeMovement().hasDestination(), () -> {
                    if (!canStartPath()) return;
                    stop();
                    var replacement = createPath(goal, 0);
                    if (replacement != null && replacement.canReach()) moveTo(replacement, 1);
                });
        return true;
    }

    private void holdPosition() {
        mob.setSpeed(0);
        mob.setZza(0);
        mob.setXxa(0);
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
    }

    private boolean transportDirectionBlocked() {
        if (path == null || path.isDone() || !CitizenWalkNodeEvaluator.shouldPreferTransport(mob)) return false;
        int last = Math.min(path.getNodeCount(), path.getNextNodeIndex() + 3);
        for (int index = Math.max(1, path.getNextNodeIndex()); index < last; index++) {
            var from = path.getNodePos(index - 1);
            var to = path.getNodePos(index);
            var delta = net.minecraft.world.phys.Vec3.atLowerCornerOf(to.subtract(from));
            if (!CitizenEscalatedTransport.permitsEntry(CitizenEscalatedTransport.supportAxis(mob, to), delta)
                    || !CitizenEscalatedTransport.permits(CitizenEscalatedTransport.support(mob, from),
                    CitizenEscalatedTransport.support(mob, to), delta)) return true;
        }
        return false;
    }

    @Override
    protected void doStuckDetection(net.minecraft.world.phys.Vec3 position) {
        if (transportProgress()) resumeAfterInteraction();
        else super.doStuckDetection(position);
    }

    @Override
    public void recomputePath() {
        if (!(mob instanceof com.rexhub.createapparel.entity.Citizen citizen)
                || !(level instanceof net.minecraft.server.level.ServerLevel server)) { super.recomputePath(); return; }
        if (recomputePending && !com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler.get(server).pending(citizen)) recomputePending = false;
        if (recomputePending || getTargetPos() == null || isDone()) return;
        var expected = getPath();
        recomputePending = com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler.get(server).submit(citizen,
                com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler.Priority.ROUTE,
                () -> expected == getPath() && !isDone(), () -> { recomputePending = false; super.recomputePath(); });
    }

    @Override
    public void stop() { super.stop(); recomputePending = false; checkedPath = shortcutPath = transportExitPath = null; checkedNode = -1; }

    @Override
    public boolean shouldRecomputePath(BlockPos pos) {
        if (path != null && path == shortcutPath && !path.isDone()) {
            var next = path.getNextEntityPos(mob);
            var swept = mob.getBoundingBox().expandTowards(next.subtract(mob.position())).inflate(1);
            if (swept.intersects(new net.minecraft.world.phys.AABB(pos))) return true;
        }
        return super.shouldRecomputePath(pos);
    }

    @Override
    protected void followThePath() {
        advanceTransportWaypoints();
        if (path == null || path.isDone()) return;
        if (mob instanceof com.rexhub.createapparel.entity.Citizen citizen && citizen.routeMovement().preciseArrival()
                && path != null && !path.isDone()) smoothPath();
        if (nearTransportWaypoint() || mob instanceof com.rexhub.createapparel.entity.Citizen citizen && (citizen.routeMovement().preciseArrival()
                || citizen.citizenState().behavior().mode() == com.rexhub.createapparel.entity.CitizenBehaviorMode.WANDER)
                && path != null && path.getNextNodeIndex() == path.getNodeCount() - 1) {
            var next = path.getNextEntityPos(mob);
            maxDistanceToWaypoint = 0.15F;
            if (Math.abs(mob.getX() - next.x) <= maxDistanceToWaypoint && Math.abs(mob.getZ() - next.z) <= maxDistanceToWaypoint
                    && Math.abs(mob.getY() - next.y) < 1) path.advance();
            doStuckDetection(getTempMobPos());
        } else super.followThePath();
    }

    private boolean nearTransportWaypoint() {
        if (path == null || path.isDone() || !CitizenWalkNodeEvaluator.shouldPreferTransport(mob)) return false;
        var pos = path.getNextNodePos();
        if (!hasLoadedRegion(level, pos, 1)) return false;
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var adjacent = pos.relative(direction);
            if (CitizenEscalatedTransport.isWalkway(level.getBlockState(adjacent.below()))) return true;
        }
        return CitizenEscalatedTransport.isWalkway(level.getBlockState(pos.below()));
    }

    private void advanceTransportWaypoints() {
        if (path == null || path.isDone() || lastTransport == null || level.getGameTime() - lastTransportAt > 5) return;
        for (int skipped = 0; skipped < 16 && !path.isDone(); skipped++) {
            var next = path.getNextEntityPos(mob);
            var delta = mob.position().subtract(next);
            double forward = delta.x * lastTransport.direction().getStepX() + delta.z * lastTransport.direction().getStepZ();
            double lateral = lastTransport.direction().getAxis() == net.minecraft.core.Direction.Axis.X ? delta.z : delta.x;
            var support = CitizenEscalatedTransport.support(mob, path.getNextNodePos());
            if (forward <= 0 || Math.abs(lateral) > 0.75 || Math.abs(delta.y) > 1.25 || support == null
                    || support.controller() != lastTransport.controller() || support.direction() != lastTransport.direction()) break;
            path.advance();
            if (path.isDone()) transportCompletedPath = path;
        }
    }

    private void smoothPath() {
        if (path == null || path.getNextNodeIndex() + 1 >= path.getNodeCount()) return;
        int next = path.getNextNodeIndex();
        if (checkedPath == path && checkedNode == next && mob.tickCount - checkedAt < 5) return;
        checkedPath = path; checkedNode = next; checkedAt = mob.tickCount;
        int last = Math.min(path.getNodeCount() - 1, next + 16);
        for (int index = next; CitizenEscalatedTransport.available() && index <= last; index++) {
            var pos = path.getNodePos(index);
            if (!hasLoadedRegion(level, pos, 0)) { last = index - 1; break; }
            if (CitizenEscalatedTransport.isWalkway(level.getBlockState(pos))
                    || CitizenEscalatedTransport.isWalkway(level.getBlockState(pos.below()))
                    || CitizenEscalatedTransport.isWalkway(level.getBlockState(pos.below(2)))) {
                last = index - 1;
                break;
            }
        }
        for (int index = last; index > next; index--) {
            var position = path.getEntityPosAtNode(mob, index);
            if (!hasLoadedRegion(level, BlockPos.containing(position), 1)) continue;
            var target = new net.minecraft.world.phys.Vec3(position.x, getGroundY(position), position.z);
            if (!CitizenPathShortcut.canTravel(mob, target)) continue;
            path.setNextNodeIndex(index);
            shortcutPath = path;
            return;
        }
    }

    @Nullable
    @Override
    public Path createPath(BlockPos target, int accuracy) {
        if (!hasLoadedRegion(level, target, 0)) return null;
        return super.createPath(target, accuracy);
    }

    @Nullable
    @Override
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        try (var diagnosticScope = com.rexhub.createapparel.citizen.population.CitizenSystemMetrics.measure("vanillaPathSearch")) {
            var center = offsetUpward ? mob.blockPosition().above() : mob.blockPosition();
            int extent = (int) (followRange + regionOffset);
            if (!hasLoadedRegion(level, center, extent)) return null;
            return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
        }
    }

    public static boolean hasLoadedRegion(Level level, BlockPos center, int extent) {
        for (int x = Math.floorDiv(center.getX() - extent, 16); x <= Math.floorDiv(center.getX() + extent, 16); x++) {
            for (int z = Math.floorDiv(center.getZ() - extent, 16); z <= Math.floorDiv(center.getZ() + extent, 16); z++) {
                if (!level.getChunkSource().hasChunk(x, z)) return false;
            }
        }
        return true;
    }
}
