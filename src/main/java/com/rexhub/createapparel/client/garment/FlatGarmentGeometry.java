package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Rotation;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;

import java.util.List;

public record FlatGarmentGeometry(Bounds originalBounds, Bounds flattenedBounds, float scaleZ) {
    public static FlatGarmentGeometry fromParts(List<GarmentPartModel> parts) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (var part : parts) {
            for (var element : part.elements()) {
                for (Vec3 corner : element.corners()) {
                    Vec3 point = rotate(corner, element.rotation());
                    minX = Math.min(minX, point.x());
                    minY = Math.min(minY, point.y());
                    minZ = Math.min(minZ, point.z());
                    maxX = Math.max(maxX, point.x());
                    maxY = Math.max(maxY, point.y());
                    maxZ = Math.max(maxZ, point.z());
                }
            }
        }
        if (!Float.isFinite(minX)) {
            Bounds empty = new Bounds(new Vec3(0, 0, 0), new Vec3(0, 0, 0));
            return new FlatGarmentGeometry(empty, empty, 1);
        }
        Bounds original = new Bounds(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
        float depth = maxZ - minZ;
        float scale = depth > 2 ? 2 / depth : 1;
        float centerZ = original.center().z();
        Bounds flat = new Bounds(new Vec3(minX, minY, centerZ + (minZ - centerZ) * scale),
                new Vec3(maxX, maxY, centerZ + (maxZ - centerZ) * scale));
        return new FlatGarmentGeometry(original, flat, scale);
    }

    public Vec3 transform(Vec3 point, Rotation rotation) {
        Vec3 rotated = rotate(point, rotation);
        float centerZ = originalBounds.center().z();
        return new Vec3(rotated.x(), rotated.y(), centerZ + (rotated.z() - centerZ) * scaleZ);
    }

    public static Vec3 rotate(Vec3 point, Rotation rotation) {
        if (rotation == null || rotation.angle() == 0) {
            return point;
        }
        Vec3 origin = rotation.origin();
        float x = point.x() - origin.x();
        float y = point.y() - origin.y();
        float z = point.z() - origin.z();
        float angle = (float) Math.toRadians(rotation.angle());
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        Vec3 rotated = switch (rotation.axis()) {
            case X -> new Vec3(x, y * cos - z * sin, y * sin + z * cos);
            case Y -> new Vec3(x * cos + z * sin, y, -x * sin + z * cos);
            case Z -> new Vec3(x * cos - y * sin, x * sin + y * cos, z);
        };
        return new Vec3(rotated.x() + origin.x(), rotated.y() + origin.y(), rotated.z() + origin.z());
    }

    public record Bounds(Vec3 min, Vec3 max) {
        public Vec3 center() {
            return new Vec3((min.x() + max.x()) / 2, (min.y() + max.y()) / 2, (min.z() + max.z()) / 2);
        }
    }
}
