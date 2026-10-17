package com.rexhub.createapparel.garment;

public enum HangerType {
    NORMAL, CLIP;

    public String tooltipKey() {
        return this == CLIP ? "tooltip.createapparel.hanger.clip" : "tooltip.createapparel.hanger.normal";
    }

    public boolean accepts(GarmentDefinition definition) {
        return this == CLIP ? definition.allowedSlots().contains(GarmentSlot.BOTTOM)
                : definition.allowedSlots().contains(GarmentSlot.TOP)
                || definition.allowedSlots().contains(GarmentSlot.OUTER)
                || definition.allowedSlots().contains(GarmentSlot.COAT);
    }
}
