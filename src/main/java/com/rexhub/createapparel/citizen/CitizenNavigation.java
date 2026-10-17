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
    public CitizenNavigation(Mob mob, Level level) { super(mob, level); }

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
    public void stop() { super.stop(); recomputePending = false; checkedPath = shortcutPath = null; checkedNode = -1; }

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
        if (mob instanceof com.rexhub.createapparel.entity.Citizen citizen && citizen.routeMovement().preciseArrival()
                && path != null && !path.isDone()) smoothPath();
        if (mob instanceof com.rexhub.createapparel.entity.Citizen citizen && (citizen.routeMovement().preciseArrival()
                || citizen.citizenState().behavior().mode() == com.rexhub.createapparel.entity.CitizenBehaviorMode.WANDER)
                && path != null && path.getNextNodeIndex() == path.getNodeCount() - 1) {
            var next = path.getNextEntityPos(mob);
            maxDistanceToWaypoint = 0.15F;
            if (Math.abs(mob.getX() - next.x) <= maxDistanceToWaypoint && Math.abs(mob.getZ() - next.z) <= maxDistanceToWaypoint
                    && Math.abs(mob.getY() - next.y) < 1) path.advance();
            doStuckDetection(getTempMobPos());
        } else super.followThePath();
    }

    private void smoothPath() {
        if (path == null || path.getNextNodeIndex() + 1 >= path.getNodeCount()) return;
        int next = path.getNextNodeIndex();
        if (checkedPath == path && checkedNode == next && mob.tickCount - checkedAt < 5) return;
        checkedPath = path; checkedNode = next; checkedAt = mob.tickCount;
        for (int index = Math.min(path.getNodeCount() - 1, next + 16); index > next; index--) {
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
        var center = offsetUpward ? mob.blockPosition().above() : mob.blockPosition();
        int extent = (int) (followRange + regionOffset);
        if (!hasLoadedRegion(level, center, extent)) return null;
        return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
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
