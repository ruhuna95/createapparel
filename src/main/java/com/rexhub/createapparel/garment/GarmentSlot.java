package com.rexhub.createapparel.garment;

import java.util.Arrays;

public enum GarmentSlot {
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
    NECK("neck");

    private final String serializedName;

    GarmentSlot(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static GarmentSlot fromSerializedName(String name) {
        return Arrays.stream(values())
                .filter(slot -> slot.serializedName.equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown garment slot: " + name));
    }
}
