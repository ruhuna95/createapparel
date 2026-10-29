package com.rexhub.createapparel.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Node;

import javax.annotation.Nullable;
import java.util.*;

final class CitizenPathFinder extends PathFinder {
    private final CitizenWalkNodeEvaluator evaluator;
    private final int maxVisitedNodes;

    CitizenPathFinder(CitizenWalkNodeEvaluator evaluator, int maxVisitedNodes) {
        super(evaluator, maxVisitedNodes);
        this.evaluator = evaluator;
        this.maxVisitedNodes = maxVisitedNodes;
    }

    @Nullable
    @Override
    public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targets, float range, int accuracy, float depth) {
        if (!CitizenWalkNodeEvaluator.shouldPreferTransport(mob)) return super.findPath(region, mob, targets, range, accuracy, depth);
        Path preferred;
        evaluator.prepare(region, mob);
        try {
            preferred = search(targets, range, accuracy, Math.max(1, (int) (maxVisitedNodes * depth)));
        } finally {
            evaluator.done();
        }
        if (preferred != null && preferred.canReach()) return preferred;
        evaluator.setTransportCosts(false);
        try {
            var ordinary = super.findPath(region, mob, targets, range, accuracy, depth);
            return ordinary != null && (ordinary.canReach() || preferred == null || ordinary.getDistToTarget() < preferred.getDistToTarget())
                    ? ordinary : preferred;
        } finally {
            evaluator.setTransportCosts(true);
        }
    }

    @Nullable
    private Path search(Set<BlockPos> targets, float range, int accuracy, int budget) {
        var start = evaluator.getStart();
        if (start == null || targets.isEmpty()) return null;
        var frontier = new PriorityQueue<Entry>(Comparator.comparingDouble(Entry::estimate)
                .thenComparingDouble(entry -> entry.node().h));
        start.g = 0;
        start.h = heuristic(start, targets, accuracy);
        frontier.add(new Entry(start, 0, start.h));
        var nearest = start;
        var nearestTarget = closest(start, targets);
        double nearestDistance = distanceSquared(start, nearestTarget);
        var neighbors = new Node[32];
        int visited = 0;
        while (!frontier.isEmpty() && visited < budget) {
            var entry = frontier.remove();
            var node = entry.node();
            if (node.closed || entry.cost() != node.g) continue;
            node.closed = true;
            visited++;
            var target = closest(node, targets);
            double distance = distanceSquared(node, target);
            if (distance < nearestDistance) {
                nearest = node;
                nearestTarget = target;
                nearestDistance = distance;
            }
            for (var goal : targets) if (Math.abs(node.x - goal.getX()) + Math.abs(node.y - goal.getY())
                    + Math.abs(node.z - goal.getZ()) <= accuracy) return path(node, goal, true);
            if (node.distanceTo(start) >= range) continue;
            int count = evaluator.getNeighbors(neighbors, node);
            for (int index = 0; index < count; index++) {
                var next = neighbors[index];
                float step = node.distanceTo(next);
                float walked = node.walkedDistance + step;
                float cost = node.g + step + next.costMalus;
                if (next.closed || walked >= range || next.cameFrom != null && cost >= next.g) continue;
                next.cameFrom = node;
                next.walkedDistance = walked;
                next.g = cost;
                next.h = heuristic(next, targets, accuracy);
                frontier.add(new Entry(next, cost, cost + next.h));
            }
        }
        return path(nearest, nearestTarget, false);
    }

    private static BlockPos closest(Node node, Set<BlockPos> targets) {
        BlockPos closest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var target : targets) {
            double candidate = distanceSquared(node, target);
            if (candidate < distance) { closest = target; distance = candidate; }
        }
        return Objects.requireNonNull(closest);
    }

    private static float heuristic(Node node, Set<BlockPos> targets, int accuracy) {
        return Math.max(0, (float) Math.sqrt(distanceSquared(node, closest(node, targets))) - accuracy);
    }

    private static double distanceSquared(Node node, BlockPos target) {
        double x = node.x - target.getX(), y = node.y - target.getY(), z = node.z - target.getZ();
        return x * x + y * y + z * z;
    }

    private static Path path(Node end, BlockPos target, boolean reached) {
        var nodes = new ArrayList<Node>();
        for (var node = end; node != null; node = node.cameFrom) nodes.add(node);
        Collections.reverse(nodes);
        return new Path(nodes, target, reached);
    }

    private record Entry(Node node, float cost, float estimate) { }
}
