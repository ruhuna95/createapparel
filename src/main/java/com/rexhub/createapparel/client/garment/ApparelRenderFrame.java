package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.client.model.PlayerModel;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class ApparelRenderFrame {
    private final Map<GarmentSlot, ResolvedGarment> garments = new EnumMap<>(GarmentSlot.class);
    private final Map<GarmentPartModel, DualEnvelopeDeformer.Pose> envelopes = new IdentityHashMap<>();
    private final Optional<GarmentHemGeometry.Slope> skirtSlope;

    public ApparelRenderFrame(Function<GarmentSlot, Optional<ResolvedGarment>> resolver, PlayerModel<?> player) {
        LegEnvelopeGeometry legs = new LegEnvelopeGeometry(player);
        for (GarmentSlot slot : GarmentSlot.values()) {
            resolver.apply(slot).ifPresent(garment -> {
                garments.put(slot, garment);
                for (var part : garment.parts()) {
                    part.model().legEnvelope().ifPresent(motion -> envelopes.computeIfAbsent(part.model(),
                            ignored -> new DualEnvelopeDeformer.Pose(motion.geometry(), legs,
                                    motion.clearance() + (slot == GarmentSlot.OUTER
                                            ? LegEnvelopeGeometry.layerClearance(slot) : 0))));
                }
            });
        }
        skirtSlope = garment(GarmentSlot.BOTTOM).flatMap(garment -> garment.parts().stream()
                .map(part -> envelopes.get(part.model())).filter(java.util.Objects::nonNull).findFirst()
                .map(GarmentHemGeometry::dualSlope));
        skirtSlope.ifPresent(slope -> garment(GarmentSlot.OUTER).ifPresent(garment -> {
            for (var part : garment.parts()) {
                var envelope = envelopes.get(part.model());
                if (envelope != null) envelope.enclose(slope.envelope(), LegEnvelopeGeometry.layerClearance(GarmentSlot.OUTER));
            }
        }));
    }

    public Optional<DualEnvelopeDeformer.Pose> envelope(GarmentPartModel part) {
        return Optional.ofNullable(envelopes.get(part));
    }
    public Optional<ResolvedGarment> garment(GarmentSlot slot) {
        return Optional.ofNullable(garments.get(slot));
    }

    public Optional<GarmentHemGeometry.Slope> skirtSlope() {
        return skirtSlope;
    }
}
