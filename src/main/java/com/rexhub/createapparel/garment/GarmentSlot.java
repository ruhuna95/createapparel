package com.rexhub.createapparel.garment;

import net.minecraft.util.StringRepresentable;

public enum GarmentSlot implements StringRepresentable {
    INNER_TOP("inner_top"),
    TOP("top"),
    OUTER("outer"),
    INNER_BOTTOM("inner_bottom"),
    BOTTOM("bottom"),
    LEGWEAR("legwear"),
    FEET("feet"),
    HEAD("head"),
    FACE("face"),
    BACK("back"),
    HANDS("hands"),
    NECK("neck"),
    COAT("coat");

    private final String serializedName;

    GarmentSlot(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static GarmentSlot fromSerializedName(String name) {
        for (GarmentSlot slot : values()) {
            if (slot.serializedName.equals(name)) {
                return slot;
            }
        }
        throw new IllegalArgumentException("Unknown garment slot: " + name);
    }
}
