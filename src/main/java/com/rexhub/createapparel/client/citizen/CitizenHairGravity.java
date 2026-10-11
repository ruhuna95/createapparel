package com.rexhub.createapparel.client.citizen;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CitizenHairGravity {
    public static final float CLEARANCE = 0.125F;

    private CitizenHairGravity() { }

    public static Vector3f point(CitizenHairLayers.Layer layer, float across, float drop, Quaternionf rotation,
                                 Vector3f origin, Torso torso) {
        var roots = roots(layer, rotation, origin, torso);
        return new Vector3f(roots.start()).lerp(roots.end(), across).add(0, drop, 0);
    }

    public static Roots roots(CitizenHairLayers.Layer layer, Quaternionf rotation, Vector3f origin, Torso torso) {
        var start = new Vector3f(layer.x0(), layer.y0(), layer.z0()).rotate(rotation).add(origin);
        var end = new Vector3f(layer.x1(), layer.y0(), layer.z1()).rotate(rotation).add(origin);
        boolean attachedSide = layer.x0() == layer.x1();
        if (!attachedSide && Math.min(start.y, end.y) <= torso.maxY()
                && Math.max(start.y, end.y) + layer.y1() - layer.y0() >= torso.minY()) {
            var normal = normal(layer).rotate(rotation);
            if (Math.abs(normal.x) >= Math.abs(normal.z)) {
                start.x = normal.x < 0 ? Math.min(start.x, torso.minX() - CLEARANCE) : Math.max(start.x, torso.maxX() + CLEARANCE);
                end.x = normal.x < 0 ? Math.min(end.x, torso.minX() - CLEARANCE) : Math.max(end.x, torso.maxX() + CLEARANCE);
            } else {
                start.z = normal.z < 0 ? Math.min(start.z, torso.minZ() - CLEARANCE) : Math.max(start.z, torso.maxZ() + CLEARANCE);
                end.z = normal.z < 0 ? Math.min(end.z, torso.minZ() - CLEARANCE) : Math.max(end.z, torso.maxZ() + CLEARANCE);
            }
        }
        return new Roots(start, end);
    }

    public record Roots(Vector3f start, Vector3f end) { }

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
