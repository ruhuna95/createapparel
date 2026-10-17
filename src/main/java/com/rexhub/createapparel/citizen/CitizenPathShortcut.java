package com.rexhub.createapparel.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

final class CitizenPathShortcut {
    private static final double EPSILON = 1.0E-7;

    private CitizenPathShortcut() { }

    static boolean canTravel(Mob mob, Vec3 target) {
        var from = mob.position();
        var delta = target.subtract(from);
        if (!mob.onGround() || Math.abs(delta.y) > EPSILON || delta.horizontalDistanceSqr() > 144
                || mob.getPathfindingMalus(BlockPathTypes.WALKABLE) != 0) return false;
        var level = mob.level();
        var body = mob.getBoundingBox();
        var swept = body.expandTowards(delta);
        if (!level.getWorldBorder().isWithinBounds(swept)) return false;
        int minX = Mth.floor(swept.minX + EPSILON), maxX = Mth.floor(swept.maxX - EPSILON);
        int minZ = Mth.floor(swept.minZ + EPSILON), maxZ = Mth.floor(swept.maxZ - EPSILON);
        for (int x = Math.floorDiv(minX - 1, 16); x <= Math.floorDiv(maxX + 1, 16); x++) {
            for (int z = Math.floorDiv(minZ - 1, 16); z <= Math.floorDiv(maxZ + 1, 16); z++) {
                if (!level.getChunkSource().hasChunk(x, z)) return false;
            }
        }
        for (var shape : level.getBlockCollisions(mob, swept)) {
            for (var obstacle : shape.toAabbs()) if (hits(body, delta, obstacle)) return false;
        }
        int supportY = Mth.ceil(from.y) - 1;
        double halfX = body.getXsize() / 2, halfZ = body.getZsize() / 2;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            var cell = new AABB(x - halfX + EPSILON, from.y - 1, z - halfZ + EPSILON,
                    x + 1 + halfX - EPSILON, from.y + 1, z + 1 + halfZ - EPSILON);
            if (!cell.contains(from) && cell.clip(from, target).isEmpty()) continue;
            var support = new BlockPos(x, supportY, z);
            var boxes = level.getBlockState(support).getCollisionShape(level, support, CollisionContext.of(mob)).toAabbs();
            if (boxes.size() != 1) return false;
            var top = boxes.get(0);
            if (top.minX > EPSILON || top.minZ > EPSILON || top.maxX < 1 - EPSILON || top.maxZ < 1 - EPSILON
                    || Math.abs(supportY + top.maxY - from.y) > EPSILON
                    || WalkNodeEvaluator.getBlockPathTypeStatic(level, support.above().mutable()) != BlockPathTypes.WALKABLE) return false;
            for (int y = supportY; y < Mth.ceil(body.maxY); y++) {
                if (!level.getFluidState(new BlockPos(x, y, z)).isEmpty()) return false;
            }
        }
        return true;
    }

    static boolean hits(AABB body, Vec3 delta, AABB obstacle) {
        var expanded = obstacle.inflate(body.getXsize() / 2 - EPSILON,
                body.getYsize() / 2 - EPSILON, body.getZsize() / 2 - EPSILON);
        var center = body.getCenter();
        return expanded.contains(center) || expanded.clip(center, center.add(delta)).isPresent();
    }
}
