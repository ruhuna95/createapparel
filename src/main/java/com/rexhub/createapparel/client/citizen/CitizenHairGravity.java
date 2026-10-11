package com.rexhub.createapparel.client.citizen;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CitizenHairGravity {
    public static final float CLEARANCE = 0.125F;

    private CitizenHairGravity() { }

    public static Vector3f point(CitizenHairLayers.Layer layer, float across, float drop, Quaternionf rotation,
                                 Vector3f origin, Torso torso) {
        var root = new Vector3f(layer.x0() + (layer.x1() - layer.x0()) * across, layer.y0(),
                layer.z0() + (layer.z1() - layer.z0()) * across).rotate(rotation).add(origin);
        boolean attachedSide = layer.x0() == layer.x1();
        if (!attachedSide && root.y <= torso.maxY() && root.y + layer.y1() - layer.y0() >= torso.minY()) {
            var normal = normal(layer).rotate(rotation);
            if (Math.abs(normal.x) >= Math.abs(normal.z)) {
                root.x = normal.x < 0 ? Math.min(root.x, torso.minX() - CLEARANCE) : Math.max(root.x, torso.maxX() + CLEARANCE);
            } else {
                root.z = normal.z < 0 ? Math.min(root.z, torso.minZ() - CLEARANCE) : Math.max(root.z, torso.maxZ() + CLEARANCE);
            }
        }
        return root.add(0, drop, 0);
    }

    public static Vector3f normal(CitizenHairLayers.Layer layer) {
        return layer.x0() == layer.x1() ? new Vector3f(layer.x0() < 0 ? -1 : 1, 0, 0)
                : new Vector3f(0, 0, layer.z0() < 0 ? -1 : 1);
    }

    public record Torso(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public static final Torso NONE = new Torso(0, Float.POSITIVE_INFINITY, 0, 0, Float.NEGATIVE_INFINITY, 0);

        public Torso relativeTo(Vector3f origin, float scale) {
            if (this == NONE) return NONE;
            return new Torso((minX - origin.x) / scale, (minY - origin.y) / scale, (minZ - origin.z) / scale,
                    (maxX - origin.x) / scale, (maxY - origin.y) / scale, (maxZ - origin.z) / scale);
        }
    }
}
