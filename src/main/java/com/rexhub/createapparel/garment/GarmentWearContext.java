package com.rexhub.createapparel.garment;

public record GarmentWearContext(GarmentSlot slot, EquippedStyleState styleState, boolean coatPresent) {
    public GarmentWearContext(GarmentSlot slot, EquippedStyleState styleState) {
        this(slot, styleState, false);
    }
    public static GarmentWearContext defaultsFor(GarmentDefinition definition) {
        return new GarmentWearContext(definition.defaultSlot(), EquippedStyleState.defaultsFor(definition));
    }
}
