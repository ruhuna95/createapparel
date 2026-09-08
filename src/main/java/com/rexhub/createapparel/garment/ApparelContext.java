package com.rexhub.createapparel.garment;

import java.util.Map;

public record ApparelContext(Map<GarmentSlot, EquippedGarmentView> equipped) {
    public ApparelContext {
        equipped = Map.copyOf(equipped);
    }

    public boolean has(GarmentSlot slot) {
        return equipped.containsKey(slot);
    }

    public record EquippedGarmentView(GarmentDefinition definition, EquippedStyleState styleState) {
    }
}
