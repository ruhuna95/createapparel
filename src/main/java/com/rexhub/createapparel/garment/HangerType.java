package com.rexhub.createapparel.garment;

public enum HangerType {
    NORMAL, CLIP;

    public boolean accepts(GarmentDefinition definition) {
        return this == CLIP ? definition.allowedSlots().contains(GarmentSlot.BOTTOM)
                : definition.allowedSlots().contains(GarmentSlot.TOP)
                || definition.allowedSlots().contains(GarmentSlot.OUTER);
    }
}
