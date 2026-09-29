package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.TexturePoint;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Uv;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.garment.GarmentSlot;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GarmentHemGeometry {
    private static final float HEM_HEIGHT = 2;

    private GarmentHemGeometry() {
    }

    static Slope dualSlope(DualEnvelopeDeformer.Pose pose) {
        Slope rest = slope(pose.geometry().reference());
        return new Slope(rest.min(), rest.max(), rest.west(), rest.east(), rest.north(), rest.south(),
                pose.geometry().top() - pose.geometry().bottom(), 0, pose);
    }

    public static Optional<Slope> skirtSlope(ResolvedGarment garment) {
        return garment.parts().stream().map(ClientGarmentAssets.ResolvedPart::model)
                .filter(part -> part.legEnvelope().isPresent()).flatMap(part -> part.elements().stream())
                .filter(element -> element.name().equals("front_panel")).findFirst().map(GarmentHemGeometry::slope);
    }


    static Slope slope(Element panel) {
        var c = panel.corners();
        float height = c.get(4).y() - c.get(0).y();
        return new Slope(c.get(4), c.get(6),
                (c.get(0).x() - c.get(4).x()) / height, (c.get(1).x() - c.get(5).x()) / height,
                (c.get(0).z() - c.get(4).z()) / height, (c.get(2).z() - c.get(6).z()) / height);
    }

    public static GarmentPartModel conform(GarmentPartModel part, String semantic, GarmentSlot slot, Slope slope) {
        if (!supportsHemConform(semantic, slot)) {
            return part;
        }
        float hem = part.elements().stream().flatMap(element -> element.corners().stream()
                        .map(corner -> FlatGarmentGeometry.rotate(corner, element.rotation())))
                .map(Vec3::y).min(Float::compare).orElse(slope.min().y());
        if (hem >= slope.min().y()) {
            return part;
        }
        float cut = Math.max(hem + HEM_HEIGHT, slope.min().y());
        Slope surface = slope.forSlot(slot);
        List<Element> elements = split(part.elements(), surface, cut);
        return new GarmentPartModel(part.id(), part.translucent(), part.textureWidth(), part.textureHeight(), part.pivot(), List.copyOf(elements),
                part.variants(), part.designs(), part.materialOverrides(), part.legEnvelope(), part.hemGeometry());
    }

    static Prepared prepare(List<Element> source) {
        float hem = source.stream().flatMap(element -> element.corners().stream()
                        .map(corner -> FlatGarmentGeometry.rotate(corner, element.rotation())))
                .map(Vec3::y).min(Float::compare).orElse(0F);
        return new Prepared(hem, List.copyOf(source));
    }

    private static List<Quad> prepareQuads(List<Element> source, float cut) {
        Slope identity = new Slope(new Vec3(-1, 0, -1), new Vec3(1, 0, 1), 0, 0, 0, 0);
        List<Quad> quads = new ArrayList<>();
        for (Element element : split(source, identity, cut)) {
            element.faces().forEach((face, uv) -> {
                var points = GarmentElementGeometry.faceVertices(element, face).stream()
                        .map(point -> FlatGarmentGeometry.rotate(point, element.rotation())).toList();
                quads.add(new Quad(points, uv.corners(), points.stream().map(p -> Math.max(0, cut - p.y())).toList()));
            });
        }
        return List.copyOf(quads);
    }

    public record Quad(List<Vec3> points, List<TexturePoint> uv, List<Float> depths) {
    }

    public static final class Prepared {
        private final float hem;
        private final List<Element> source;
        private final List<Quad> quads;
        private final Map<Float, List<Quad>> skirtQuads = new LinkedHashMap<>();
        private final Map<DualEnvelopeDeformer.Prepared, List<Quad>> dualQuads = new LinkedHashMap<>();

        private Prepared(float hem, List<Element> source) {
            this.hem = hem;
            this.source = source;
            this.quads = prepareQuads(source, hem + HEM_HEIGHT);
        }

        public float hem() {
            return hem;
        }

        public List<Quad> quads() {
            return quads;
        }

        public List<Quad> quads(Slope slope) {
            float cut = Math.max(hem + HEM_HEIGHT, slope.min().y());
            if (slope.envelope() != null) {
                var geometry = slope.envelope().geometry();
                if (!dualQuads.containsKey(geometry) && dualQuads.size() >= 8) {
                    dualQuads.remove(dualQuads.keySet().iterator().next());
                }
                return dualQuads.computeIfAbsent(geometry, ignored -> {
                    float height = cut;
                    List<Quad> result = new ArrayList<>();
                    Slope identity = new Slope(new Vec3(-1, 0, -1), new Vec3(1, 0, 1), 0, 0, 0, 0);
                    List<Element> divided = split(source, identity, height);
                    for (int row = 1; row < geometry.bands(); row++) {
                        float plane = geometry.height(row);
                        if (plane < height && plane > hem) divided = split(divided, identity, plane);
                    }
                    for (Quad quad : prepareQuads(divided, height)) {
                        if (quad.depths().stream().allMatch(depth -> depth == 0)) { result.add(quad); continue; }
                        int rows = 1;
                        float[] cuts = Math.abs(quad.points().get(0).x() - quad.points().get(3).x()) > 1
                                ? new float[]{0, .375F, .4F, .6F, .625F, 1} : new float[]{0, 1};
                        for (int row = 0; row < rows; row++) for (int col = 0; col < cuts.length - 1; col++) {
                            float a = row / (float) rows, b = (row + 1F) / rows;
                            float u = cuts[col], v = cuts[col + 1];
                            var points = List.of(DualEnvelopeDeformer.point(quad.points(), u, a),
                                    DualEnvelopeDeformer.point(quad.points(), u, b),
                                    DualEnvelopeDeformer.point(quad.points(), v, b),
                                    DualEnvelopeDeformer.point(quad.points(), v, a));
                            result.add(new Quad(points, List.of(DualEnvelopeDeformer.texture(quad.uv(), u, a),
                                    DualEnvelopeDeformer.texture(quad.uv(), u, b), DualEnvelopeDeformer.texture(quad.uv(), v, b),
                                    DualEnvelopeDeformer.texture(quad.uv(), v, a)),
                                    points.stream().map(p -> Math.max(0, height - p.y())).toList()));
                        }
                    }
                    return List.copyOf(result);
                });
            }
            if (cut == hem + HEM_HEIGHT) {
                return quads;
            }
            List<Quad> cached = skirtQuads.get(cut);
            if (cached == null) {
                cached = prepareQuads(source, cut);
                if (skirtQuads.size() >= 8) {
                    skirtQuads.remove(skirtQuads.keySet().iterator().next());
                }
                skirtQuads.put(cut, cached);
            }
            return cached;
        }

        public boolean applies(String semantic, GarmentSlot slot, Slope slope) {
            return supportsHemConform(semantic, slot) && hem < slope.min().y();
        }
    }

    private static boolean supportsHemConform(String semantic, GarmentSlot slot) {
        return (slot == GarmentSlot.TOP || slot == GarmentSlot.OUTER)
                && semantic.contains("torso") && !semantic.startsWith("tucked_");
    }

    private static List<Element> split(List<Element> source, Slope surface, float cut) {
        List<Element> elements = new ArrayList<>();
        for (Element element : source) {
            if (element.corners().stream().allMatch(c -> FlatGarmentGeometry.rotate(c, element.rotation()).y() >= cut)) {
                elements.add(element);
                continue;
            }
            element.faces().forEach((face, uv) -> {
                List<Vec3> positions = GarmentElementGeometry.faceVertices(element, face);
                List<Point> polygon = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    polygon.add(new Point(FlatGarmentGeometry.rotate(positions.get(i), element.rotation()), uv.corners().get(i)));
                }
                if (polygon.stream().anyMatch(p -> p.position().y() < cut)
                        && polygon.stream().anyMatch(p -> p.position().y() > cut)) {
                    emit(elements, element.name(), clip(polygon, cut, true), surface, cut);
                    emit(elements, element.name(), clip(polygon, cut, false), surface, cut);
                } else {
                    emit(elements, element.name(), polygon, surface, cut);
                }
            });
        }
        return elements;
    }

    private static List<Point> clip(List<Point> polygon, float height, boolean upper) {
        List<Point> result = new ArrayList<>();
        Point previous = polygon.get(polygon.size() - 1);
        for (Point current : polygon) {
            boolean a = upper ? previous.position().y() >= height : previous.position().y() <= height;
            boolean b = upper ? current.position().y() >= height : current.position().y() <= height;
            if (a != b) {
                float t = (height - previous.position().y()) / (current.position().y() - previous.position().y());
                result.add(new Point(new Vec3(
                        lerp(previous.position().x(), current.position().x(), t), height,
                        lerp(previous.position().z(), current.position().z(), t)),
                        new TexturePoint(lerp(previous.uv().u(), current.uv().u(), t),
                                lerp(previous.uv().v(), current.uv().v(), t))));
            }
            if (b) {
                result.add(current);
            }
            previous = current;
        }
        List<Point> unique = new ArrayList<>();
        for (Point point : result) {
            if (unique.isEmpty() || !unique.get(unique.size() - 1).equals(point)) {
                unique.add(point);
            }
        }
        if (unique.size() > 1 && unique.get(0).equals(unique.get(unique.size() - 1))) {
            unique.remove(unique.size() - 1);
        }
        return unique;
    }

    private static void emit(List<Element> elements, String name, List<Point> polygon, Slope slope, float cut) {
        if (polygon.size() < 3) {
            return;
        }
        if (polygon.size() > 4) {
            for (int i = 1; i < polygon.size() - 1; i++) {
                emit(elements, name, List.of(polygon.get(0), polygon.get(i), polygon.get(i + 1)), slope, cut);
            }
            return;
        }
        List<Vec3> points = new ArrayList<>();
        List<TexturePoint> uv = new ArrayList<>();
        for (Point point : polygon) {
            points.add(slope.shift(point.position(), Math.max(0, cut - point.position().y())));
            uv.add(point.uv());
        }
        if (points.size() == 3) {
            points.add(points.get(2));
            uv.add(uv.get(2));
        }
        Vec3 a = points.get(0), b = points.get(1), c = points.get(2), d = points.get(3);
        float minU = uv.stream().map(TexturePoint::u).min(Float::compare).orElseThrow();
        float maxU = uv.stream().map(TexturePoint::u).max(Float::compare).orElseThrow();
        float minV = uv.stream().map(TexturePoint::v).min(Float::compare).orElseThrow();
        float maxV = uv.stream().map(TexturePoint::v).max(Float::compare).orElseThrow();
        Vec3 min = new Vec3(points.stream().map(Vec3::x).min(Float::compare).orElseThrow(),
                points.stream().map(Vec3::y).min(Float::compare).orElseThrow(),
                points.stream().map(Vec3::z).min(Float::compare).orElseThrow());
        Vec3 max = new Vec3(points.stream().map(Vec3::x).max(Float::compare).orElseThrow(),
                points.stream().map(Vec3::y).max(Float::compare).orElseThrow(),
                points.stream().map(Vec3::z).max(Float::compare).orElseThrow());
        elements.add(new Element(name, min, max, null,
                Map.of(Face.FRONT, new Uv(minU, minV, maxU, maxV, List.copyOf(uv))),
                List.of(a, d, d, a, b, c, c, b)));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private record Point(Vec3 position, TexturePoint uv) {
    }

    public record Slope(Vec3 min, Vec3 max, float west, float east, float north, float south,
                        float height, float clearance, DualEnvelopeDeformer.Pose envelope) {
        public Slope(Vec3 min, Vec3 max, float west, float east, float north, float south) {
            this(min, max, west, east, north, south, 1, 0, null);
        }

        public Slope forSlot(GarmentSlot slot) {
            return new Slope(min, max, west, east, north, south, height,
                    LegEnvelopeGeometry.layerClearance(slot), envelope);
        }

        Vec3 shift(Vec3 point, float depth) {
            Vector3f result = new Vector3f();
            shift(point, depth, result);
            return depth == 0 ? point : new Vec3(result.x, result.y, result.z);
        }

        public void shift(Vec3 point, float depth, Vector3f result) {
            if (depth == 0) {
                result.set(point.x(), point.y(), point.z());
                return;
            }
            float x = (point.x() - min.x()) / (max.x() - min.x());
            float z = (point.z() - min.z()) / (max.z() - min.z());
            float s = Math.max(0, Math.min(1, x));
            float t = Math.max(0, Math.min(1, z));
            if (envelope != null) {
                float sampleDepth = Math.min(height, depth);
                float sampleX = lerp(min.x(), max.x(), s) + lerp(west, east, s) * sampleDepth;
                float sampleZ = lerp(min.z(), max.z(), t) + lerp(north, south, t) * sampleDepth;
                envelope.shift(sampleX, min.y() - sampleDepth, sampleZ, result);
                float gap = clearance * Math.min(1, depth / HEM_HEIGHT);
                result.set(point.x() + lerp(west, east, x) * depth + result.x - sampleX + (2 * s - 1) * gap,
                        point.y() + result.y - (min.y() - sampleDepth),
                        point.z() + lerp(north, south, z) * depth + result.z - sampleZ + (2 * t - 1) * gap);
                return;
            }
            float nx = lerp(west, east, s);
            float nz = lerp(north, south, t);
            float side = 2 * s - 1;
            float front = 2 * t - 1;
            float normalX = side;
            float normalZ = front;
            float normalY = Math.abs(side * nx) + Math.abs(front * nz);
            float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            float gap = length > 0 ? clearance * Math.min(1, depth / HEM_HEIGHT) / length : 0;
            result.set(point.x() + lerp(west, east, x) * depth + normalX * gap,
                    point.y() + normalY * gap,
                    point.z() + lerp(north, south, z) * depth + normalZ * gap);
        }
    }
}
