package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.garment.GarmentSlot;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class ApparelRenderFrame {
    private final Map<GarmentSlot, ResolvedGarment> garments = new EnumMap<>(GarmentSlot.class);
    private final Map<GarmentPartModel, GarmentPartModel> poses = new IdentityHashMap<>();
    private final Optional<GarmentHemGeometry.Slope> skirtSlope;

    public ApparelRenderFrame(Function<GarmentSlot, Optional<ResolvedGarment>> resolver,
                              UnaryOperator<GarmentPartModel> animator) {
        for (GarmentSlot slot : GarmentSlot.values()) {
            resolver.apply(slot).ifPresent(garment -> {
                garments.put(slot, garment);
                for (var part : garment.parts()) {
                    if (part.model().skirtMotion().isPresent()) {
                        poses.computeIfAbsent(part.model(), animator::apply);
                    }
                }
            });
        }
        skirtSlope = garment(GarmentSlot.BOTTOM).flatMap(garment -> GarmentHemGeometry.skirtSlope(garment, this::posed));
    }

    public Optional<ResolvedGarment> garment(GarmentSlot slot) {
        return Optional.ofNullable(garments.get(slot));
    }

    public GarmentPartModel posed(GarmentPartModel part) {
        return poses.getOrDefault(part, part);
    }

    public Optional<GarmentHemGeometry.Slope> skirtSlope() {
        return skirtSlope;
    }
}
