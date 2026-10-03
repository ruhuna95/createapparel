package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.BakedFace;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.BakedVertex;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class HudArmGeometry {
    private static final float CUFF_HEIGHT = 15;
    private static final Map<GarmentPartModel, List<BakedFace>> CACHE = new IdentityHashMap<>();

    private HudArmGeometry() {
    }

    public static List<BakedFace> rolledSleeve(GarmentPartModel model) {
        return CACHE.computeIfAbsent(model, HudArmGeometry::clipSleeve);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static List<BakedFace> clipSleeve(GarmentPartModel model) {
        List<BakedFace> result = new ArrayList<>();
        for (var element : model.elements()) {
            for (var face : element.bakedFaces()) {
                List<BakedVertex> polygon = face.vertices().stream().map(vertex -> {
                    Vec3 point = FlatGarmentGeometry.rotate(new Vec3(vertex.x(), vertex.y(), vertex.z()), element.rotation());
                    return new BakedVertex(point.x(), point.y(), point.z(), vertex.u(), vertex.v());
                }).toList();
                Vec3 normal = GarmentElementGeometry.normal(polygon.stream()
                        .map(vertex -> new Vec3(vertex.x(), vertex.y(), vertex.z())).toList());
                polygon = clip(polygon);
                if (polygon.size() < 3) continue;
                if (polygon.size() <= 4) {
                    result.add(new BakedFace(quad(polygon), normal));
                } else {
                    for (int index = 1; index < polygon.size() - 1; index++) {
                        result.add(new BakedFace(quad(List.of(polygon.get(0), polygon.get(index), polygon.get(index + 1))), normal));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<BakedVertex> clip(List<BakedVertex> polygon) {
        List<BakedVertex> clipped = new ArrayList<>();
        BakedVertex previous = polygon.get(polygon.size() - 1);
        for (BakedVertex current : polygon) {
            boolean before = previous.y() >= CUFF_HEIGHT;
            boolean after = current.y() >= CUFF_HEIGHT;
            if (before != after) {
                float fraction = (CUFF_HEIGHT - previous.y()) / (current.y() - previous.y());
                clipped.add(new BakedVertex(lerp(previous.x(), current.x(), fraction), CUFF_HEIGHT,
                        lerp(previous.z(), current.z(), fraction), lerp(previous.u(), current.u(), fraction),
                        lerp(previous.v(), current.v(), fraction)));
            }
            if (after) clipped.add(current);
            previous = current;
        }
        List<BakedVertex> unique = new ArrayList<>();
        for (BakedVertex point : clipped) {
            if (unique.isEmpty() || !unique.get(unique.size() - 1).equals(point)) unique.add(point);
        }
        if (unique.size() > 1 && unique.get(0).equals(unique.get(unique.size() - 1))) {
            unique.remove(unique.size() - 1);
        }
        return unique;
    }

    private static List<BakedVertex> quad(List<BakedVertex> polygon) {
        return polygon.size() == 3 ? List.of(polygon.get(0), polygon.get(1), polygon.get(2), polygon.get(2))
                : List.copyOf(polygon);
    }

    private static float lerp(float start, float end, float fraction) {
        return start + (end - start) * fraction;
    }
}
