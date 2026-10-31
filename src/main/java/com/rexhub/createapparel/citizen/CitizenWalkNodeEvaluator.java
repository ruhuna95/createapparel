package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

final class CitizenWalkNodeEvaluator extends WalkNodeEvaluator {
    private final Map<BlockPos, CitizenEscalatedTransport.Surface> surfaces = new HashMap<>();
    private final Map<BlockPos, net.minecraft.core.Direction.Axis> axes = new HashMap<>();
    private boolean preferTransport;
    private boolean directedTransport;
    private boolean transportCosts = true;

    void setTransportCosts(boolean enabled) { transportCosts = enabled; }

    static boolean shouldPreferTransport(Mob mob) {
        return CitizenEscalatedTransport.available() && mob instanceof Citizen citizen
                && (citizen.routeMovement().hasDestination() || citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WANDER
                || citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK);
    }

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        directedTransport = shouldPreferTransport(mob);
        preferTransport = transportCosts && directedTransport;
    }

    @Override
    public void done() {
        surfaces.clear();
        axes.clear();
        super.done();
    }

    @Override
    public PathType getPathType(net.minecraft.world.level.pathfinder.PathfindingContext context, int x, int y, int z) {
        if (!CitizenEscalatedTransport.available()) return super.getPathType(context, x, y, z);
        var pos = new BlockPos(x, y, z);
        if (CitizenEscalatedTransport.isWalkway(context.getBlockState(pos))) return PathType.BLOCKED;
        var type = super.getPathType(context, x, y, z);
        if (type == PathType.OPEN && CitizenEscalatedTransport.isWalkway(context.getBlockState(pos.below())))
            return checkNeighbourBlocks(context, x, y, z, PathType.WALKABLE);
        return type;
    }

    @Override
    public int getNeighbors(Node[] neighbors, Node node) {
        int count = super.getNeighbors(neighbors, node);
        if (!directedTransport) return count;
        var from = support(node.asBlockPos());
        int accepted = 0;
        for (int index = 0; index < count; index++) {
            var next = neighbors[index];
            var to = support(next.asBlockPos());
            var delta = new Vec3(next.x - node.x, next.y - node.y, next.z - node.z);
            if (!CitizenEscalatedTransport.permitsEntry(axis(next.asBlockPos()), delta)
                    || !CitizenEscalatedTransport.permits(from, to, delta)) continue;
            neighbors[accepted++] = next;
            if (!preferTransport || next.type != PathType.WALKABLE || mob.getPathfindingMalus(next.type) != 0) continue;
            boolean assisted = (from != null && from.along(delta) || to != null && to.along(delta));
            next.costMalus = assisted ? 0 : 0.6F;
        }
        return accepted;
    }

    @javax.annotation.Nullable
    private CitizenEscalatedTransport.Surface support(BlockPos feet) {
        if (!surfaces.containsKey(feet)) surfaces.put(feet, CitizenEscalatedTransport.support(mob, feet));
        return surfaces.get(feet);
    }

    @javax.annotation.Nullable
    private net.minecraft.core.Direction.Axis axis(BlockPos feet) {
        if (!axes.containsKey(feet)) axes.put(feet, CitizenEscalatedTransport.supportAxis(mob, feet));
        return axes.get(feet);
    }
}
