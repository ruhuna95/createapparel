package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Rotation;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record FlatGarmentGeometry(Bounds originalBounds, Bounds flattenedBounds, float scaleZ) {
    private static final Map<GeometryKey, FlatGarmentGeometry> CACHE = new LinkedHashMap<>(32, 0.75F, true);

    public static FlatGarmentGeometry cached(List<GarmentPartModel> parts) {
        return cached(parts, parts);
    }

    public static FlatGarmentGeometry cachedHanger(List<ResolvedPart> parts) {
        var models = parts.stream().map(ResolvedPart::model).toList();
        var depthParts = parts.stream()
                .filter(part -> !isHood(part))
                .map(ResolvedPart::model).toList();
        return cached(models, depthParts.isEmpty() ? models : depthParts);
    }

    public static Vec3 hangerAttachmentPoint(List<ResolvedPart> parts) {
        var attachmentParts = parts.stream().anyMatch(FlatGarmentGeometry::isHood)
                ? parts.stream().filter(part -> part.semanticName().equals("torso") || part.semanticName().startsWith("torso_")
                        || part.semanticName().equals("tucked_torso") || part.semanticName().startsWith("tucked_torso_"))
                        .map(ResolvedPart::model).toList()
                : List.<GarmentPartModel>of();
        if (attachmentParts.isEmpty()) attachmentParts = parts.stream().map(ResolvedPart::model).toList();
        var bounds = cached(attachmentParts).originalBounds();
        var center = bounds.center();
        return new Vec3(center.x(), bounds.max().y(), center.z());
    }

    private static boolean isHood(ResolvedPart part) {
        return part.semanticName().equals("hood") || part.semanticName().startsWith("hood_");
    }

    private static FlatGarmentGeometry cached(List<GarmentPartModel> parts, List<GarmentPartModel> depthParts) {
        GeometryKey key = new GeometryKey(new PartKey(parts), new PartKey(depthParts));
        FlatGarmentGeometry found = CACHE.get(key);
        if (found != null) return found;
        FlatGarmentGeometry geometry = fromParts(parts, depthParts);
        if (CACHE.size() >= 256) CACHE.remove(CACHE.keySet().iterator().next());
        CACHE.put(key, geometry);
        return geometry;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private record GeometryKey(PartKey parts, PartKey depthParts) { }

    private record PartKey(List<GarmentPartModel> parts) {
        private PartKey {
            parts = List.copyOf(parts);
        }

        @Override
        public int hashCode() {
            int result = 1;
            for (var part : parts) result = 31 * result + System.identityHashCode(part);
            return result;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PartKey key) || parts.size() != key.parts.size()) return false;
            for (int i = 0; i < parts.size(); i++) if (parts.get(i) != key.parts.get(i)) return false;
            return true;
        }
    }

    public static FlatGarmentGeometry fromParts(List<GarmentPartModel> parts) {
        return fromParts(parts, parts);
    }

    private static FlatGarmentGeometry fromParts(List<GarmentPartModel> parts, List<GarmentPartModel> depthParts) {
        Bounds original = bounds(parts);
        Bounds reference = parts == depthParts ? original : bounds(depthParts);
        float depth = reference.max().z() - reference.min().z();
        float scale = depth > 2 ? 2 / depth : 1;
        float centerZ = original.center().z();
        Bounds flat = new Bounds(new Vec3(original.min().x(), original.min().y(), centerZ + (original.min().z() - centerZ) * scale),
                new Vec3(original.max().x(), original.max().y(), centerZ + (original.max().z() - centerZ) * scale));
        return new FlatGarmentGeometry(original, flat, scale);
    }

    private static Bounds bounds(List<GarmentPartModel> parts) {
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
            return new Bounds(new Vec3(0, 0, 0), new Vec3(0, 0, 0));
        }
        return new Bounds(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
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
