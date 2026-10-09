package com.rexhub.createapparel.client.mirror;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import com.rexhub.createapparel.mirror.MirrorGeometry;
import java.util.ArrayList;
import java.util.List;

public final class MirrorReflectionMath {
    private MirrorReflectionMath() { }

    public static Vec3 reflectPoint(Vec3 point, Vec3 origin, Vec3 normal) {
        return point.subtract(normal.scale(2 * point.subtract(origin).dot(normal)));
    }

    public static Vec3 reflectDirection(Vec3 direction, Vec3 normal) {
        return direction.subtract(normal.scale(2 * direction.dot(normal)));
    }

    public static Matrix4f reflectedView(Matrix4f view, Vec3 normal) {
        float x = (float) normal.x, y = (float) normal.y, z = (float) normal.z;
        var reflection = new Matrix4f(
                1 - 2 * x * x, -2 * y * x, -2 * z * x, 0,
                -2 * x * y, 1 - 2 * y * y, -2 * z * y, 0,
                -2 * x * z, -2 * y * z, 1 - 2 * z * z, 0,
                0, 0, 0, 1);
        return new Matrix4f(view).mul(reflection);
    }

    public static Matrix4f croppedCullingProjection(Matrix4f projection, double[] bounds) {
        float left = (float) Math.max(-1, bounds[0] - 0.03), bottom = (float) Math.max(-1, bounds[1] - 0.03);
        float right = (float) Math.min(1, bounds[2] + 0.03), top = (float) Math.min(1, bounds[3] + 0.03);
        if (right - left < 0.001 || top - bottom < 0.001) return new Matrix4f(projection);
        return new Matrix4f().scaling(2 / (right - left), 2 / (top - bottom), 1)
                .translate(-(left + right) / 2, -(bottom + top) / 2, 0).mul(projection);
    }

    public static Matrix4f captureProjection(Matrix4f projection) {
        return new Matrix4f().scaling(1 / 1.15F, 1 / 1.15F, 1).mul(projection);
    }

    public static boolean coversVisibleMirror(List<MirrorGeometry> quads, Vec3 eye, Matrix4f mainProjectionView,
                                               Matrix4f textureMatrix, double[] coverage) {
        for (var quad : quads) {
            var polygon = visiblePolygon(quad, eye, mainProjectionView);
            for (var vertex : polygon) {
                var clip = textureMatrix.transform(new Vector4f((float) vertex.x, (float) vertex.y, (float) vertex.z, 1));
                if (clip.w <= 0) return false;
                double u = clip.x / clip.w * 0.5 + 0.5, v = clip.y / clip.w * 0.5 + 0.5;
                if (u < coverage[0] || v < coverage[1] || u > coverage[2] || v > coverage[3]) return false;
            }
        }
        return true;
    }

    public static boolean coversScreenCenter(List<MirrorGeometry> quads, Vec3 eye, Matrix4f projectionView) {
        for (var quad : quads) {
            var polygon = visiblePolygon(quad, eye, projectionView);
            if (polygon.size() < 3) continue;
            boolean positive = false, negative = false;
            var previous = project(polygon.get(polygon.size() - 1), projectionView);
            for (var vertex : polygon) {
                var current = project(vertex, projectionView);
                double cross = (double) previous.x * current.y - (double) previous.y * current.x;
                positive |= cross > 0;
                negative |= cross < 0;
                previous = current;
                if (positive && negative) break;
            }
            if (positive != negative) return true;
        }
        return false;
    }

    private static Vector4f project(Vec3 vertex, Matrix4f matrix) {
        return matrix.transform(new Vector4f((float) vertex.x, (float) vertex.y, (float) vertex.z, 1));
    }

    public static List<Vec3> visiblePolygon(MirrorGeometry quad, Vec3 eye, Matrix4f projectionView) {
        List<Vec3> polygon = quad.vertices().stream().map(vertex -> vertex.subtract(eye)).toList();
        for (int plane = 0; plane < 6 && !polygon.isEmpty(); plane++) {
            var clipped = new ArrayList<Vec3>();
            Vec3 previous = polygon.get(polygon.size() - 1);
            double previousDistance = clipDistance(projectionView, previous, plane);
            for (Vec3 current : polygon) {
                double distance = clipDistance(projectionView, current, plane);
                if ((distance >= 0) != (previousDistance >= 0)) {
                    double fraction = previousDistance / (previousDistance - distance);
                    clipped.add(previous.add(current.subtract(previous).scale(fraction)));
                }
                if (distance >= 0) clipped.add(current);
                previous = current;
                previousDistance = distance;
            }
            polygon = clipped;
        }
        return polygon;
    }

    private static double clipDistance(Matrix4f matrix, Vec3 point, int plane) {
        var clip = matrix.transform(new Vector4f((float) point.x, (float) point.y, (float) point.z, 1));
        return switch (plane) {
            case 0 -> clip.w + clip.x; case 1 -> clip.w - clip.x;
            case 2 -> clip.w + clip.y; case 3 -> clip.w - clip.y;
            case 4 -> clip.w + clip.z; default -> clip.w - clip.z;
        };
    }

    public static Matrix4f clippedProjection(Matrix4f projection, Matrix4f view, Vec3 originFromEye, Vec3 normal) {
        var plane = new Vector4f((float) normal.x, (float) normal.y, (float) normal.z,
                (float) -normal.dot(originFromEye) - 0.0001F);
        new Matrix4f(view).invert().transpose().transform(plane);
        var corner = new Matrix4f(projection).invert().transform(new Vector4f(Math.signum(plane.x), Math.signum(plane.y), 1, 1));
        float denominator = plane.dot(corner);
        if (Math.abs(denominator) < 1.0E-6) return new Matrix4f(projection);
        plane.mul(2 / denominator);
        return new Matrix4f(projection).m02(plane.x - projection.m03()).m12(plane.y - projection.m13())
                .m22(plane.z - projection.m23()).m32(plane.w - projection.m33());
    }
}
