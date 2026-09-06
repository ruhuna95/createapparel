package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class GarmentElementGeometry {
    private GarmentElementGeometry() {
    }

    static List<Vec3> corners(Vec3 from, Vec3 to, float[] topInset, float[] bottomInset) {
        List<Vec3> corners = new ArrayList<>(8);
        for (boolean top : new boolean[]{false, true}) {
            float[] inset = top ? topInset : bottomInset;
            for (float value : inset) {
                if (!Float.isFinite(value) || value < 0.0F) {
                    throw new IllegalStateException("Garment insets must be finite and nonnegative");
                }
            }
            float left = from.x() + inset[0];
            float front = from.z() + inset[1];
            float right = to.x() - inset[2];
            float back = to.z() - inset[3];
            if (!(left < right && front < back)) {
                throw new IllegalStateException("Garment insets must leave a positive rectangle");
            }
            float y = top ? to.y() : from.y();
            corners.add(new Vec3(left, y, front));
            corners.add(new Vec3(right, y, front));
            corners.add(new Vec3(right, y, back));
            corners.add(new Vec3(left, y, back));
        }
        return List.copyOf(corners);
    }

    public static List<Vec3> faceVertices(Element element, Face face) {
        List<Vec3> corners = element.corners();
        return switch (face) {
            case FRONT -> select(corners, 0, 4, 5, 1);
            case BACK -> select(corners, 2, 6, 7, 3);
            case LEFT -> select(corners, 3, 7, 4, 0);
            case RIGHT -> select(corners, 1, 5, 6, 2);
            case TOP -> select(corners, 4, 7, 6, 5);
            case BOTTOM -> select(corners, 3, 0, 1, 2);
        };
    }

    public static Vec3 normal(List<Vec3> vertices) {
        Vec3 a = vertices.get(0);
        Vec3 b = vertices.get(1);
        Vec3 c = vertices.get(2);
        float ux = b.x() - a.x();
        float uy = b.y() - a.y();
        float uz = b.z() - a.z();
        float vx = c.x() - a.x();
        float vy = c.y() - a.y();
        float vz = c.z() - a.z();
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new Vec3(nx / length, ny / length, nz / length);
    }

    private static List<Vec3> select(List<Vec3> corners, int a, int b, int c, int d) {
        return List.of(corners.get(a), corners.get(b), corners.get(c), corners.get(d));
    }
}
