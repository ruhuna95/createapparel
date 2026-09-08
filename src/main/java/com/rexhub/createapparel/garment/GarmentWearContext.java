package com.rexhub.createapparel.garment;

public record GarmentWearContext(GarmentSlot slot, EquippedStyleState styleState) {
    public static GarmentWearContext defaultsFor(GarmentDefinition definition) {
        return new GarmentWearContext(definition.defaultSlot(), EquippedStyleState.defaultsFor(definition));
    }
}
