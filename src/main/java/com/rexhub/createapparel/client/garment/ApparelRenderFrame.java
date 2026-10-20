package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.client.model.HumanoidModel;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class ApparelRenderFrame {
    private final Map<GarmentSlot, ResolvedGarment> garments = new EnumMap<>(GarmentSlot.class);
    private final Map<GarmentPartModel, DualEnvelopeDeformer.Pose> envelopes = new IdentityHashMap<>();
    private final Optional<GarmentHemGeometry.Slope> skirtSlope;

    public ApparelRenderFrame(Function<GarmentSlot, Optional<ResolvedGarment>> resolver, HumanoidModel<?> player) {
        this(resolver, player, 0);
    }

    public ApparelRenderFrame(Function<GarmentSlot, Optional<ResolvedGarment>> resolver, HumanoidModel<?> player, int lod) {
        try (var diagnosticScope = GarmentDiagnostics.measure("apparelRenderFrame")) {
            LegEnvelopeGeometry legs = new LegEnvelopeGeometry(player);
            for (GarmentSlot slot : GarmentSlot.values()) {
                resolver.apply(slot).ifPresent(garment -> {
                    garments.put(slot, garment);
                    for (var part : garment.parts()) {
                        part.model().legEnvelope().ifPresent(motion -> envelopes.computeIfAbsent(part.model(),
                                ignored -> new DualEnvelopeDeformer.Pose(motion.geometry(), legs,
                                        motion.clearance() + (slot == GarmentSlot.OUTER || slot == GarmentSlot.COAT
                                                ? LegEnvelopeGeometry.layerClearance(slot) : 0),
                                        slot != GarmentSlot.OUTER && slot != GarmentSlot.COAT, lod)));
                    }
                });
            }
            Optional<GarmentHemGeometry.Slope> bottomSlope = envelopeSlope(GarmentSlot.BOTTOM).or(() ->
                    garment(GarmentSlot.BOTTOM).flatMap(bottom -> GarmentHemGeometry.bottomGeometry(bottom)
                            .map(geometry -> GarmentHemGeometry.dualSlope(new DualEnvelopeDeformer.Pose(geometry,
                                    new LegEnvelopeGeometry(player, bottom), 0)))));
            bottomSlope.ifPresent(slope -> garment(GarmentSlot.TOP).ifPresent(garment -> {
                for (var part : garment.parts()) {
                    var envelope = envelopes.get(part.model());
                    if (envelope != null) envelope.enclose(slope.envelope(), LegEnvelopeGeometry.layerClearance(GarmentSlot.TOP));
                }
            }));
            skirtSlope = envelopeSlope(GarmentSlot.TOP).or(() -> bottomSlope);
            skirtSlope.ifPresent(slope -> garment(GarmentSlot.OUTER).ifPresent(garment -> {
                for (var part : garment.parts()) {
                    var envelope = envelopes.get(part.model());
                    if (envelope != null) envelope.enclose(slope.envelope(), LegEnvelopeGeometry.layerClearance(GarmentSlot.OUTER));
                }
            }));
            Optional<GarmentHemGeometry.Slope> coatInner = envelopeSlope(GarmentSlot.OUTER).or(() -> skirtSlope);
            coatInner.ifPresent(slope -> garment(GarmentSlot.COAT).ifPresent(garment -> {
                for (var part : garment.parts()) {
                    var envelope = envelopes.get(part.model());
                    if (envelope != null) envelope.enclose(slope.envelope(), LegEnvelopeGeometry.layerClearance(GarmentSlot.COAT));
                }
            }));
        }
    }

    private Optional<GarmentHemGeometry.Slope> envelopeSlope(GarmentSlot slot) {
        return garment(slot).flatMap(garment -> garment.parts().stream()
                .map(part -> envelopes.get(part.model())).filter(java.util.Objects::nonNull).findFirst()
                .map(GarmentHemGeometry::dualSlope));
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
