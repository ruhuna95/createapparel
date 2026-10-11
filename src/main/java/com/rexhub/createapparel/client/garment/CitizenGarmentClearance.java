package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class CitizenGarmentClearance {
    public static final float CLEARANCE = 0.03125F;
    private static final float MAX_PADDING = 0.05F;

    private CitizenGarmentClearance() { }

    public static GarmentPartModel separate(String semanticName, GarmentPartModel upper, List<GarmentPartModel> lower) {
        if (!semanticName.equals("torso")) return upper;
        if (upper.legEnvelope().isPresent() || lower.stream().anyMatch(part -> part.legEnvelope().isPresent())) return upper;
        List<Element> elements = new ArrayList<>();
        boolean changed = false;
        for (Element element : upper.elements()) {
            float padX = 0, padZ = 0;
            if (element.rotation() == null || element.rotation().angle() == 0) {
                float extentX = Math.max(Math.abs(element.from().x()), Math.abs(element.to().x()));
                float extentZ = Math.max(Math.abs(element.from().z()), Math.abs(element.to().z()));
                for (GarmentPartModel part : lower) for (Element inner : part.elements()) {
                    if (inner.to().y() <= element.from().y() || inner.from().y() >= element.to().y()) continue;
                    if (inner.rotation() != null && inner.rotation().angle() != 0) continue;
                    float needX = Math.max(Math.abs(inner.from().x()), Math.abs(inner.to().x())) + CLEARANCE - extentX;
                    float needZ = Math.max(Math.abs(inner.from().z()), Math.abs(inner.to().z())) + CLEARANCE - extentZ;
                    if (needX > 0 && needX <= MAX_PADDING) padX = Math.max(padX, needX);
                    if (needZ > 0 && needZ <= MAX_PADDING) padZ = Math.max(padZ, needZ);
                }
            }
            if (padX == 0 && padZ == 0) {
                elements.add(element);
            } else {
                float x = padX, z = padZ;
                var corners = element.corners().stream().map(point -> shift(point, x, z)).toList();
                elements.add(new Element(element.name(), shift(element.from(), x, z), shift(element.to(), x, z),
                        element.rotation(), element.faces(), corners));
                changed = true;
            }
        }
        return changed ? new GarmentPartModel(upper.id(), upper.translucent(), upper.textureWidth(), upper.textureHeight(),
                upper.pivot(), List.copyOf(elements), upper.variants(), upper.designs(), upper.materialOverrides(), upper.legEnvelope(),
                GarmentHemGeometry.prepare(elements)) : upper;
    }

    private static Vec3 shift(Vec3 point, float x, float z) {
        return new Vec3(point.x() + Math.signum(point.x()) * x, point.y(), point.z() + Math.signum(point.z()) * z);
    }
}
