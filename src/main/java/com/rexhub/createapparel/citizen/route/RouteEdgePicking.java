package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.block.CitizenRouteMarkerBlock;
import com.rexhub.createapparel.citizen.CitizenNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.UUID;

public final class RouteEdgePicking {
    public static final double PICK_RADIUS = 0.10;
    public record Candidate(UUID id, BlockPos a, BlockPos b, boolean control) {
        public Candidate(UUID id, BlockPos a, BlockPos b) { this(id, a, b, false); }
        public Vec3 start() { return Vec3.atBottomCenterOf(a).add(0, 0.15, 0); }
        public Vec3 end(net.minecraft.world.level.BlockGetter level) { return control ? CrossingCondition.connectionPoint(level, b) : Vec3.atBottomCenterOf(b).add(0, 0.15, 0); }
    }
    public record Hit(Vec3 point, double rayDistance, double separation) { }

    private RouteEdgePicking() { }

    @Nullable public static Candidate pick(Player player, Iterable<Candidate> candidates) {
        if (!player.isShiftKeyDown() || !RouteMarkerPermissions.holdingPhone(player)
                || !CitizenNavigation.hasLoadedRegion(player.level(), player.blockPosition(), 1)) return null;
        var nativeHit = RouteMarkerPermissions.pick(player);
        if (nativeHit == null) return null;
        if (nativeHit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockState(nativeHit.getBlockPos()).getBlock() instanceof CitizenRouteMarkerBlock) return null;
        var eye = player.getEyePosition();
        var end = nativeHit.getType() == HitResult.Type.MISS ? eye.add(player.getLookAngle().scale(player.getBlockReach())) : nativeHit.getLocation();
        Candidate selected = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (var edge : candidates) {
            if (!CitizenNavigation.hasLoadedRegion(player.level(), edge.a(), 0) || !CitizenNavigation.hasLoadedRegion(player.level(), edge.b(), 0)) continue;
            var hit = intersect(eye, end, edge.start(), edge.end(player.level()));
            if (hit == null || hit.rayDistance() >= nearest) continue;
            var obstruction = player.level().clip(new ClipContext(eye, hit.point(), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (obstruction.getType() != HitResult.Type.MISS && obstruction.getLocation().distanceToSqr(eye) + 0.000001 < hit.point().distanceToSqr(eye)) continue;
            selected = edge;
            nearest = hit.rayDistance();
        }
        return selected;
    }

    @Nullable public static Hit intersect(Vec3 start, Vec3 end, Vec3 a, Vec3 b) {
        var ray = end.subtract(start); var edge = b.subtract(a); var relative = start.subtract(a);
        double rr = ray.lengthSqr(), ee = edge.lengthSqr();
        if (rr < 0.000001 || ee < 0.000001) return null;
        double re = ray.dot(edge), rw = ray.dot(relative), ew = edge.dot(relative), determinant = rr * ee - re * re;
        double[][] pairs = {{0, clamp(ew / ee)}, {1, clamp((ew + re) / ee)},
                {clamp(-rw / rr), 0}, {clamp((re - rw) / rr), 1}};
        double best = Double.POSITIVE_INFINITY, bestRay = 0, bestEdge = 0;
        for (var pair : pairs) {
            double separation = start.add(ray.scale(pair[0])).distanceToSqr(a.add(edge.scale(pair[1])));
            if (separation < best) { best = separation; bestRay = pair[0]; bestEdge = pair[1]; }
        }
        if (determinant > 0.000001) {
            double s = (re * ew - ee * rw) / determinant, t = (rr * ew - re * rw) / determinant;
            if (s >= 0 && s <= 1 && t >= 0 && t <= 1) {
                double separation = start.add(ray.scale(s)).distanceToSqr(a.add(edge.scale(t)));
                if (separation < best) { best = separation; bestRay = s; bestEdge = t; }
            }
        }
        return best > PICK_RADIUS * PICK_RADIUS ? null : new Hit(a.add(edge.scale(bestEdge)), bestRay * Math.sqrt(rr), Math.sqrt(best));
    }

    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
