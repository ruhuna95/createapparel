package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.TexturePoint;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class DualEnvelopeDeformer {
    private DualEnvelopeDeformer() {
    }

    public record Quad(String panel, List<Vec3> points, List<TexturePoint> uv) {
    }

    public static final class Prepared {
        private final Element reference;
        private final int bands;
        private final float top;
        private final float bottom;
        private final List<Quad> quads;

        public Prepared(List<Element> elements, Element reference,
                        boolean frontBridge, boolean backBridge) {
            this.reference = reference;
            this.top = reference.to().y();
            this.bottom = reference.from().y();
            this.bands = Math.max(2, (int) Math.ceil((top - bottom) * 2));
            List<Quad> result = new ArrayList<>();
            for (Element element : elements) {
                element.faces().forEach((face, uv) -> {
                    List<Vec3> p = GarmentElementGeometry.faceVertices(element, face).stream()
                            .map(v -> FlatGarmentGeometry.rotate(v, element.rotation())).toList();
                    if (p.stream().allMatch(v -> v.y() >= top) || element.rotation() != null
                            || face == Face.TOP || face == Face.BOTTOM) {
                        result.add(new Quad(element.name(), p, uv.corners()));
                        return;
                    }
                    boolean front = face == Face.FRONT;
                    boolean back = face == Face.BACK;
                    float[] cuts = front || back ? new float[]{0, 0.375F, 0.4F, 0.6F, 0.625F, 1} : new float[]{0, 1};
                    for (int column = 0; column < cuts.length - 1; column++) {
                        boolean bridge = column > 0 && column < cuts.length - 2;
                        if (bridge && (front && !frontBridge || back && !backBridge)) continue;
                        String name = front ? switch (column) {
                            case 0 -> "front_right"; case 4 -> "front_left"; default -> "front_bridge";
                        } : back ? switch (column) {
                            case 0 -> "back_left"; case 4 -> "back_right"; default -> "back_bridge";
                        } : face == Face.RIGHT ? "left_side" : "right_side";
                        for (int row = 0; row < bands; row++) {
                            float lo = 1 - ring(bands - row), hi = 1 - ring(bands - row - 1);
                            float u0 = cuts[column], u1 = cuts[column + 1];
                            result.add(new Quad(name, List.of(point(p, u0, lo), point(p, u0, hi),
                                    point(p, u1, hi), point(p, u1, lo)),
                                    List.of(texture(uv.corners(), u0, lo), texture(uv.corners(), u0, hi),
                                            texture(uv.corners(), u1, hi), texture(uv.corners(), u1, lo))));
                        }
                    }
                });
            }
            quads = List.copyOf(result);
        }

        public List<Quad> quads() { return quads; }
        public Element reference() { return reference; }
        public float top() { return top; }
        public float bottom() { return bottom; }
        int bands() { return bands; }
        float height(int row) { return lerp(top, bottom, ring(row)); }

        private float ring(int row) {
            return (float) row / bands;
        }

        private float boundary(int index, float t, boolean x) {
            Vec3 upper = reference.corners().get(index + 4), lower = reference.corners().get(index);
            return x ? lerp(upper.x(), lower.x(), t) : lerp(upper.z(), lower.z(), t);
        }
    }

    public static final class Pose {
        private final Prepared geometry;
        private final float[] offsets;
        private boolean changed;

        public Pose(Prepared geometry, LegEnvelopeGeometry legs, float clearance) {
            this.geometry = geometry;
            offsets = new float[(geometry.bands + 1) * 6];
            float[] bounds = new float[4];
            boolean moved = false;
            for (int side = 0; side < 2; side++) {
                for (int row = 1; row <= geometry.bands; row++) {
                    float previous = geometry.ring(row - 1);
                    float next = geometry.ring(Math.min(geometry.bands, row + 1));
                    float lower = lerp(geometry.top, geometry.bottom, next);
                    float upper = lerp(geometry.top, geometry.bottom, previous);
                    if (!legs.extents(side, lower, upper, bounds)) continue;
                    int index = row * 6 + side * 3;
                    float minX = geometry.boundary(0, previous, true);
                    float maxX = geometry.boundary(1, previous, true);
                    float minZ = geometry.boundary(0, previous, false);
                    float maxZ = geometry.boundary(2, previous, false);
                    offsets[index] = Math.max(0, side == 0 ? minX - bounds[0] + clearance : bounds[1] + clearance - maxX);
                    offsets[index + 1] = Math.max(0, minZ - bounds[2] + clearance);
                    offsets[index + 2] = Math.max(0, bounds[3] + clearance - maxZ);
                    moved |= offsets[index] > 0 || offsets[index + 1] > 0 || offsets[index + 2] > 0;
                }
            }
            straighten();
            changed = moved;
        }

        private void straighten() {
            for (int direction = 0; direction < 6; direction++) {
                float extent = 0;
                for (int row = 1; row <= geometry.bands; row++) {
                    extent = Math.max(extent, offsets[row * 6 + direction] / geometry.ring(row));
                }
                for (int row = 1; row <= geometry.bands; row++) {
                    offsets[row * 6 + direction] = extent * geometry.ring(row);
                }
            }
        }

        public Prepared geometry() { return geometry; }
        public boolean changed() { return changed; }

        public void enclose(Pose inner, float clearance) {
            Vector3f point = new Vector3f();
            for (int row = 1; row <= geometry.bands; row++) {
                float t = geometry.ring(row);
                float y = lerp(geometry.top, geometry.bottom, t);
                if (y >= inner.geometry.top) continue;
                float sampleY = Math.max(y, inner.geometry.bottom);
                float innerT = (inner.geometry.top - sampleY) / (inner.geometry.top - inner.geometry.bottom);
                float extension = (inner.geometry.top - y) / (inner.geometry.top - sampleY);
                float gap = clearance * Math.min(1, (geometry.top - y) / 2);
                for (int side = 0; side < 2; side++) {
                    float x = inner.geometry.boundary(side, innerT, true);
                    float front = inner.geometry.boundary(0, innerT, false);
                    float back = inner.geometry.boundary(2, innerT, false);
                    inner.expand(x, sampleY, front, point);
                    float anchorX = inner.geometry.boundary(side, 0, true);
                    float anchorFront = inner.geometry.boundary(0, 0, false);
                    point.x = anchorX + (point.x - anchorX) * extension;
                    point.z = anchorFront + (point.z - anchorFront) * extension;
                    int index = row * 6 + side * 3;
                    float outX = geometry.boundary(side, t, true);
                    offsets[index] = Math.max(offsets[index], side == 0 ? outX - point.x + gap : point.x + gap - outX);
                    offsets[index + 1] = Math.max(offsets[index + 1], geometry.boundary(0, t, false) - point.z + gap);
                    inner.expand(x, sampleY, back, point);
                    float anchorBack = inner.geometry.boundary(2, 0, false);
                    point.z = anchorBack + (point.z - anchorBack) * extension;
                    offsets[index + 2] = Math.max(offsets[index + 2], point.z + gap - geometry.boundary(2, t, false));
                    changed |= offsets[index] > 0 || offsets[index + 1] > 0 || offsets[index + 2] > 0;
                }
            }
            straighten();
        }

        public void shift(Vec3 point, Vector3f result) {
            shift(point.x(), point.y(), point.z(), result);
        }

        public void shift(float px, float py, float pz, Vector3f result) {
            expand(px, py, pz, result);
            if (py >= geometry.top || !changed) return;
            float t = Math.min(1, (geometry.top - py) / (geometry.top - geometry.bottom));
            float x = (px - geometry.boundary(0, t, true))
                    / (geometry.boundary(1, t, true) - geometry.boundary(0, t, true));
            float z = (pz - geometry.boundary(0, t, false))
                    / (geometry.boundary(2, t, false) - geometry.boundary(0, t, false));
            float anchorX = lerp(geometry.boundary(0, 0, true), geometry.boundary(1, 0, true), x);
            float anchorZ = lerp(geometry.boundary(0, 0, false), geometry.boundary(2, 0, false), z);
            float dx = px - anchorX, dy = py - geometry.top, dz = pz - anchorZ;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            result.sub(anchorX, geometry.top, anchorZ).normalize(length).add(anchorX, geometry.top, anchorZ);
        }

        private void expand(float px, float py, float pz, Vector3f result) {
            float t = Math.max(0, Math.min(1, (geometry.top - py) / (geometry.top - geometry.bottom)));
            if (t == 0 || !changed) { result.set(px, py, pz); return; }
            float minX = geometry.boundary(0, t, true), maxX = geometry.boundary(1, t, true);
            float minZ = geometry.boundary(0, t, false), maxZ = geometry.boundary(2, t, false);
            float x = (px - minX) / (maxX - minX);
            float sideWeight = Math.max(0, Math.min(1, (x - 0.375F) / 0.25F));
            float z = (pz - minZ) / (maxZ - minZ);
            float right = offset(t, 0), left = offset(t, 3);
            float dx = x < 0.5F ? -right * (1 - 2 * x) : left * (2 * x - 1);
            float bridgeWeight = Math.max(0, Math.min(1, (0.125F - Math.abs(x - 0.5F)) / 0.025F));
            float front = Math.max(lerp(offset(t, 1), offset(t, 4), sideWeight),
                    Math.max(offset(t, 1), offset(t, 4)) * bridgeWeight);
            float back = Math.max(lerp(offset(t, 2), offset(t, 5), sideWeight),
                    Math.max(offset(t, 2), offset(t, 5)) * bridgeWeight);
            result.set(px + dx, py, pz + lerp(-front, back, z));
        }

        private float offset(float t, int direction) {
            float row = t * geometry.bands;
            int a = Math.min(geometry.bands, (int) row), b = Math.min(geometry.bands, a + 1);
            return lerp(offsets[a * 6 + direction], offsets[b * 6 + direction], row - a);
        }
    }

    static Vec3 point(List<Vec3> p, float u, float v) {
        return new Vec3(mix(p.get(0).x(), p.get(1).x(), p.get(2).x(), p.get(3).x(), u, v),
                mix(p.get(0).y(), p.get(1).y(), p.get(2).y(), p.get(3).y(), u, v),
                mix(p.get(0).z(), p.get(1).z(), p.get(2).z(), p.get(3).z(), u, v));
    }

    static TexturePoint texture(List<TexturePoint> p, float u, float v) {
        return new TexturePoint(mix(p.get(0).u(), p.get(1).u(), p.get(2).u(), p.get(3).u(), u, v),
                mix(p.get(0).v(), p.get(1).v(), p.get(2).v(), p.get(3).v(), u, v));
    }

    private static float mix(float a, float b, float c, float d, float u, float v) {
        return lerp(lerp(a, b, v), lerp(d, c, v), u);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
