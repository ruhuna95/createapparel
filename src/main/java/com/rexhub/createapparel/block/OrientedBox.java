package com.rexhub.createapparel.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OrientedBox {
    private static final double EPSILON = 1.0E-7;
    private static final List<Vec3> WORLD_AXES = List.of(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
    private final Vec3 center;
    private final Vec3 halfSize;
    private final List<Vec3> axes;
    private final List<Vec3> separatingAxes;
    private final AABB bounds;

    private OrientedBox(Vec3 center, Vec3 halfSize, List<Vec3> axes) {
        this.center = center;
        this.halfSize = halfSize;
        this.axes = List.copyOf(axes);
        var candidates = new ArrayList<>(WORLD_AXES);
        candidates.addAll(axes);
        for (var world : WORLD_AXES) for (var axis : axes) candidates.add(world.cross(axis));
        var unique = new ArrayList<Vec3>();
        for (var candidate : candidates) {
            if (candidate.lengthSqr() < EPSILON) continue;
            var normal = candidate.normalize();
            if (unique.stream().noneMatch(existing -> Math.abs(existing.dot(normal)) > 1 - EPSILON)) unique.add(normal);
        }
        separatingAxes = List.copyOf(unique);
        var radius = new Vec3(radius(WORLD_AXES.get(0)), radius(WORLD_AXES.get(1)), radius(WORLD_AXES.get(2)));
        bounds = new AABB(center.subtract(radius), center.add(radius));
    }

    public static OrientedBox pixels(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new OrientedBox(new Vec3((x1 + x2) / 32, (y1 + y2) / 32, (z1 + z2) / 32),
                new Vec3((x2 - x1) / 32, (y2 - y1) / 32, (z2 - z1) / 32), WORLD_AXES);
    }

    public static OrientedBox axisAligned(AABB box) {
        return new OrientedBox(box.getCenter(), new Vec3(box.getXsize(), box.getYsize(), box.getZsize()).scale(0.5), WORLD_AXES);
    }

    public AABB bounds() { return bounds; }

    public double volume() { return 8 * halfSize.x * halfSize.y * halfSize.z; }

    public Vec3 sample(java.util.random.RandomGenerator random) {
        return world(new Vec3((random.nextDouble() * 2 - 1) * halfSize.x,
                (random.nextDouble() * 2 - 1) * halfSize.y, (random.nextDouble() * 2 - 1) * halfSize.z));
    }

    private OrientedBox(OrientedBox source, Vec3 offset) {
        center = source.center.add(offset);
        halfSize = source.halfSize;
        axes = source.axes;
        separatingAxes = source.separatingAxes;
        bounds = source.bounds.move(offset);
    }

    public OrientedBox move(Vec3 offset) { return new OrientedBox(this, offset); }

    public OrientedBox rotateZ(Vec3 origin, double degrees) {
        double radians = Math.toRadians(degrees);
        return new OrientedBox(rotateVectorZ(center.subtract(origin), radians).add(origin), halfSize,
                axes.stream().map(axis -> rotateVectorZ(axis, radians)).toList());
    }

    private static Vec3 rotateVectorZ(Vec3 point, double radians) {
        double cosine = Math.cos(radians), sine = Math.sin(radians);
        return new Vec3(point.x * cosine - point.y * sine, point.x * sine + point.y * cosine, point.z);
    }

    public OrientedBox turnY(int turns, double extent) {
        Vec3 nextCenter = center;
        List<Vec3> nextAxes = axes;
        for (int turn = 0; turn < turns; turn++) {
            nextCenter = new Vec3(extent - nextCenter.z, nextCenter.y, nextCenter.x);
            nextAxes = nextAxes.stream().map(axis -> new Vec3(-axis.z, axis.y, axis.x)).toList();
        }
        return new OrientedBox(nextCenter, halfSize, nextAxes);
    }

    private Vec3 local(Vec3 point) {
        var offset = point.subtract(center);
        return new Vec3(offset.dot(axes.get(0)), offset.dot(axes.get(1)), offset.dot(axes.get(2)));
    }

    private Vec3 world(Vec3 point) {
        return center.add(axes.get(0).scale(point.x)).add(axes.get(1).scale(point.y)).add(axes.get(2).scale(point.z));
    }

    public boolean contains(Vec3 point, double tolerance) {
        var local = local(point);
        return Math.abs(local.x) <= halfSize.x + tolerance && Math.abs(local.y) <= halfSize.y + tolerance
                && Math.abs(local.z) <= halfSize.z + tolerance;
    }

    public Optional<Vec3> clip(Vec3 start, Vec3 end) {
        return new AABB(halfSize.scale(-1), halfSize).clip(local(start), local(end)).map(this::world);
    }

    public Direction hitDirection(Vec3 point) {
        var local = local(point);
        double x = Math.abs(Math.abs(local.x) - halfSize.x), y = Math.abs(Math.abs(local.y) - halfSize.y);
        double z = Math.abs(Math.abs(local.z) - halfSize.z);
        var normal = x <= y && x <= z ? axes.get(0).scale(Math.signum(local.x))
                : y <= z ? axes.get(1).scale(Math.signum(local.y)) : axes.get(2).scale(Math.signum(local.z));
        return Direction.getNearest(normal.x, normal.y, normal.z);
    }

    public void edges(net.minecraft.world.phys.shapes.Shapes.DoubleLineConsumer consumer) {
        for (int vertex = 0; vertex < 8; vertex++) for (int axis = 0; axis < 3; axis++) {
            if ((vertex & 1 << axis) != 0) continue;
            var start = vertex(vertex);
            var end = vertex(vertex | 1 << axis);
            consumer.consume(start.x, start.y, start.z, end.x, end.y, end.z);
        }
    }

    private Vec3 vertex(int bits) {
        return world(new Vec3((bits & 1) == 0 ? -halfSize.x : halfSize.x,
                (bits & 2) == 0 ? -halfSize.y : halfSize.y, (bits & 4) == 0 ? -halfSize.z : halfSize.z));
    }

    private double radius(Vec3 axis) {
        return halfSize.x * Math.abs(axis.dot(axes.get(0))) + halfSize.y * Math.abs(axis.dot(axes.get(1)))
                + halfSize.z * Math.abs(axis.dot(axes.get(2)));
    }

    public double collide(Direction.Axis movementAxis, AABB box, double distance) {
        var delta = box.getCenter().subtract(center);
        var moving = WORLD_AXES.get(movementAxis.ordinal()).scale(distance);
        double enter = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY;
        for (var axis : separatingAxes) {
            double radius = radius(axis) + (box.getXsize() * Math.abs(axis.x) + box.getYsize() * Math.abs(axis.y)
                    + box.getZsize() * Math.abs(axis.z)) * 0.5;
            double position = delta.dot(axis), velocity = moving.dot(axis);
            if (Math.abs(velocity) < EPSILON) {
                if (Math.abs(position) >= radius - EPSILON) return distance;
                continue;
            }
            double first = (-radius - position) / velocity, last = (radius - position) / velocity;
            enter = Math.max(enter, Math.min(first, last));
            exit = Math.min(exit, Math.max(first, last));
            if (enter > exit) return distance;
        }
        return enter >= -EPSILON && enter <= 1 && exit > EPSILON ? distance * Math.max(0, enter) : distance;
    }
}
