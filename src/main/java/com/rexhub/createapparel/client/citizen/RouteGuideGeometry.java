package com.rexhub.createapparel.client.citizen;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

final class RouteGuideGeometry {
    static final double EDGE_WIDTH = 0.12;
    record Segment(Vec3 from, Vec3 to) { }

    private RouteGuideGeometry() { }

    static AABB markerBox(Vec3 center) {
        return new AABB(center.x - 0.25, center.y + 0.01, center.z - 0.25,
                center.x + 0.25, center.y + 0.25, center.z + 0.25);
    }

    @Nullable static Segment clipEdge(Vec3 a, Vec3 b, Vec3 origin, double distance) {
        var delta = b.subtract(a);
        double length = delta.lengthSqr();
        if (length < 0.000001) return null;
        var relative = a.subtract(origin);
        double projection = relative.dot(delta) / length;
        double discriminant = projection * projection - (relative.lengthSqr() - distance * distance) / length;
        if (discriminant < 0) return null;
        double start = Math.max(0, -projection - Math.sqrt(discriminant));
        double end = Math.min(1, -projection + Math.sqrt(discriminant));
        return start >= end ? null : new Segment(a.add(delta.scale(start)), a.add(delta.scale(end)));
    }

    static Vec3 ribbonSide(Segment segment, Vec3 camera) {
        var direction = segment.to().subtract(segment.from()).normalize();
        var side = direction.cross(camera.subtract(segment.from().lerp(segment.to(), 0.5))).normalize();
        if (side.lengthSqr() < 0.000001) side = direction.cross(Math.abs(direction.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        return side.scale(EDGE_WIDTH / 2);
    }

    static List<List<Vec3>> ribbonOutsideNodes(Segment segment, Vec3 camera, List<AABB> boxes) {
        var side = ribbonSide(segment, camera);
        List<List<Vec3>> pieces = List.of(List.of(segment.from().add(side), segment.to().add(side), segment.to().subtract(side), segment.from().subtract(side)));
        for (var box : boxes) {
            if (box.contains(camera)) return List.of();
            var planes = silhouettePlanes(box, camera);
            var remaining = new ArrayList<List<Vec3>>();
            for (var piece : pieces) {
                var inside = piece;
                for (var plane : planes) {
                    if (inside.size() < 3) break;
                    var outside = clipPolygon(inside, camera, plane, false);
                    if (outside.size() >= 3) remaining.add(outside);
                    inside = clipPolygon(inside, camera, plane, true);
                }
            }
            pieces = remaining;
        }
        return pieces;
    }

    private record Projected(double x, double y, Vec3 ray) { }

    private static List<Vec3> silhouettePlanes(AABB box, Vec3 camera) {
        Vec3 forward;
        if (camera.x <= box.minX) forward = new Vec3(1, 0, 0);
        else if (camera.x >= box.maxX) forward = new Vec3(-1, 0, 0);
        else if (camera.y <= box.minY) forward = new Vec3(0, 1, 0);
        else if (camera.y >= box.maxY) forward = new Vec3(0, -1, 0);
        else forward = new Vec3(0, 0, camera.z <= box.minZ ? 1 : -1);
        var right = forward.cross(Math.abs(forward.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1));
        var up = right.cross(forward);
        var points = new ArrayList<Projected>();
        for (double x : new double[]{box.minX, box.maxX}) for (double y : new double[]{box.minY, box.maxY}) for (double z : new double[]{box.minZ, box.maxZ}) {
            var ray = new Vec3(x, y, z).subtract(camera);
            double depth = Math.max(0.000000001, ray.dot(forward));
            points.add(new Projected(ray.dot(right) / depth, ray.dot(up) / depth, ray));
        }
        points.sort(java.util.Comparator.comparingDouble(Projected::x).thenComparingDouble(Projected::y));
        var lower = new ArrayList<Projected>(); var upper = new ArrayList<Projected>();
        for (var point : points) hullPoint(lower, point);
        for (int i = points.size() - 1; i >= 0; i--) hullPoint(upper, points.get(i));
        lower.remove(lower.size() - 1); upper.remove(upper.size() - 1); lower.addAll(upper);
        var center = box.getCenter().subtract(camera);
        var planes = new ArrayList<Vec3>();
        for (int i = 0; i < lower.size(); i++) {
            var normal = lower.get(i).ray().cross(lower.get((i + 1) % lower.size()).ray()).normalize();
            planes.add(normal.dot(center) >= 0 ? normal : normal.scale(-1));
        }
        return planes;
    }

    private static void hullPoint(List<Projected> hull, Projected point) {
        while (hull.size() >= 2) {
            var a = hull.get(hull.size() - 2); var b = hull.get(hull.size() - 1);
            if ((b.x() - a.x()) * (point.y() - a.y()) - (b.y() - a.y()) * (point.x() - a.x()) > 0.000000001) break;
            hull.remove(hull.size() - 1);
        }
        hull.add(point);
    }

    private static List<Vec3> clipPolygon(List<Vec3> polygon, Vec3 camera, Vec3 plane, boolean keepInside) {
        var result = new ArrayList<Vec3>();
        var previous = polygon.get(polygon.size() - 1);
        double previousDistance = previous.subtract(camera).dot(plane);
        boolean previousKept = keepInside ? previousDistance >= 0 : previousDistance < 0;
        for (var current : polygon) {
            double distance = current.subtract(camera).dot(plane);
            boolean kept = keepInside ? distance >= 0 : distance < 0;
            if (kept != previousKept) result.add(previous.lerp(current, previousDistance / (previousDistance - distance)));
            if (kept) result.add(current);
            previous = current; previousDistance = distance; previousKept = kept;
        }
        return result;
    }

}
