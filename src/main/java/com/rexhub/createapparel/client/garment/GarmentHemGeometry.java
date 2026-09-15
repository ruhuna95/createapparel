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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        return garment.parts().stream().map(ClientGarmentAssets.ResolvedPart::model)
                .filter(part -> part.skirtMotion().isPresent()).findFirst().map(part -> {
                    var posed = SkirtPoseGeometry.animate(part, player);
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
        if ((slot != GarmentSlot.TOP && slot != GarmentSlot.OUTER) || !semantic.contains("torso")) {
            return part;
        }
        float hem = part.elements().stream().flatMap(element -> element.corners().stream()
                        .map(corner -> FlatGarmentGeometry.rotate(corner, element.rotation())))
                .map(Vec3::y).min(Float::compare).orElse(slope.min().y());
        if (hem >= slope.min().y()) {
            return part;
        }
        float cut = hem + HEM_HEIGHT;
        Slope surface = slope.forSlot(slot);
        List<Element> elements = new ArrayList<>();
        for (Element element : part.elements()) {
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
        return new GarmentPartModel(part.id(), part.textureWidth(), part.textureHeight(), part.pivot(), List.copyOf(elements),
                part.variants(), part.designs(), part.materialOverrides(), part.skirtMotion());
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

        Slope forSlot(GarmentSlot slot) {
            return new Slope(min, max, west, east, north, south, height, movement,
                    slot == GarmentSlot.OUTER ? 0.05F : 0.025F);
        }

        Vec3 shift(Vec3 point, float depth) {
            if (depth == 0) {
                return point;
            }
            float x = (point.x() - min.x()) / (max.x() - min.x());
            float z = (point.z() - min.z()) / (max.z() - min.z());
            float s = Math.max(0, Math.min(1, x));
            float t = Math.max(0, Math.min(1, z));
            Vec3 delta = blend(blend(movement.get(0), movement.get(1), s),
                    blend(movement.get(3), movement.get(2), s), t);
            float ratio = depth / height;
            float nx = lerp(west, east, s) + delta.x() / height;
            float nz = lerp(north, south, t) + delta.z() / height;
            float drop = Math.max(0, 1 - delta.y() / height);
            float side = 2 * s - 1;
            float front = 2 * t - 1;
            float normalX = side * drop;
            float normalZ = front * drop;
            float normalY = Math.abs(side * nx) + Math.abs(front * nz);
            float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            float gap = length > 0 ? clearance * Math.min(1, depth / HEM_HEIGHT) / length : 0;
            return new Vec3(point.x() + lerp(west, east, x) * depth + delta.x() * ratio + normalX * gap,
                    point.y() + delta.y() * ratio + normalY * gap,
                    point.z() + lerp(north, south, z) * depth + delta.z() * ratio + normalZ * gap);
        }

        private static Vec3 blend(Vec3 a, Vec3 b, float weight) {
            return new Vec3(lerp(a.x(), b.x(), weight), lerp(a.y(), b.y(), weight), lerp(a.z(), b.z(), weight));
        }
    }
}
