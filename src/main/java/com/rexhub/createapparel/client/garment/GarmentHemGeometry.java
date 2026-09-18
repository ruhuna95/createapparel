package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.TexturePoint;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Uv;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.client.model.PlayerModel;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class GarmentHemGeometry {
    private static final float HEM_HEIGHT = 2;

    private GarmentHemGeometry() {
    }

    public static Optional<Slope> skirtSlope(ResolvedGarment garment) {
        return garment.parts().stream().map(ClientGarmentAssets.ResolvedPart::model)
                .filter(part -> part.skirtMotion().isPresent()).flatMap(part -> part.elements().stream())
                .filter(element -> element.name().equals("front_panel")).findFirst().map(GarmentHemGeometry::slope);
    }

    public static Optional<Slope> skirtSlope(ResolvedGarment garment, PlayerModel<?> player) {
        return skirtSlope(garment, part -> SkirtPoseGeometry.animate(part, player));
    }

    public static Optional<Slope> skirtSlope(ResolvedGarment garment, UnaryOperator<GarmentPartModel> pose) {
        return garment.parts().stream().map(ClientGarmentAssets.ResolvedPart::model)
                .filter(part -> part.skirtMotion().isPresent()).findFirst().map(part -> {
                    var posed = pose.apply(part);
                    Element restPanel = part.elements().stream().filter(e -> e.name().equals("front_panel")).findFirst().orElseThrow();
                    Element posedPanel = posed.elements().stream().filter(e -> e.name().equals("front_panel")).findFirst().orElseThrow();
                    return animatedSlope(restPanel, posedPanel);
                });
    }

    static Slope animatedSlope(Element rest, Element posed) {
        var base = slope(rest);
        List<Vec3> movement = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Vec3 a = rest.corners().get(i);
            Vec3 b = posed.corners().get(i);
            movement.add(new Vec3(b.x() - a.x(), b.y() - a.y(), b.z() - a.z()));
        }
        return new Slope(base.min(), base.max(), base.west(), base.east(), base.north(), base.south(),
                rest.corners().get(4).y() - rest.corners().get(0).y(), List.copyOf(movement), 0);
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
        return new GarmentPartModel(part.id(), part.textureWidth(), part.textureHeight(), part.pivot(), List.copyOf(elements),
                part.variants(), part.designs(), part.materialOverrides(), part.skirtMotion(), part.hemGeometry());
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
                        float height, List<Vec3> movement, float clearance) {
        public Slope(Vec3 min, Vec3 max, float west, float east, float north, float south) {
            this(min, max, west, east, north, south, 1,
                    List.of(new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(0, 0, 0)), 0);
        }

        public Slope forSlot(GarmentSlot slot) {
            return new Slope(min, max, west, east, north, south, height, movement,
                    slot == GarmentSlot.OUTER ? 0.05F : 0.025F);
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
            Vec3 a = movement.get(0), b = movement.get(1), c = movement.get(3), d = movement.get(2);
            float dx = lerp(lerp(a.x(), b.x(), s), lerp(c.x(), d.x(), s), t);
            float dy = lerp(lerp(a.y(), b.y(), s), lerp(c.y(), d.y(), s), t);
            float dz = lerp(lerp(a.z(), b.z(), s), lerp(c.z(), d.z(), s), t);
            float ratio = depth / height;
            float nx = lerp(west, east, s) + dx / height;
            float nz = lerp(north, south, t) + dz / height;
            float drop = Math.max(0, 1 - dy / height);
            float side = 2 * s - 1;
            float front = 2 * t - 1;
            float normalX = side * drop;
            float normalZ = front * drop;
            float normalY = Math.abs(side * nx) + Math.abs(front * nz);
            float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            float gap = length > 0 ? clearance * Math.min(1, depth / HEM_HEIGHT) / length : 0;
            result.set(point.x() + lerp(west, east, x) * depth + dx * ratio + normalX * gap,
                    point.y() + dy * ratio + normalY * gap,
                    point.z() + lerp(north, south, z) * depth + dz * ratio + normalZ * gap);
        }
    }
}
