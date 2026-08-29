package com.rexhub.createapparel.apparel;

import net.minecraft.util.StringRepresentable;

public enum SkinTone implements StringRepresentable {
    SNOW("snow", 0xFFFFFF),
    VERY_FAIR("very_fair", 0xFFFFF6),
    FAIR("fair", 0xFFF4D7),
    NATURAL("natural", 0xFFE0C7),
    HEALTHY("healthy", 0xFDBD90),
    TANNED("tanned", 0xE7A876),
    LIGHT_BROWN("light_brown", 0xCC8C5A),
    BROWN("brown", 0xAF724F),
    DARK("dark", 0x7E604E);

    private final String serializedName;
    private final int tintRgb;

    SkinTone(String serializedName, int tintRgb) {
        this.serializedName = serializedName;
        this.tintRgb = tintRgb;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public int tintRgb() {
        return tintRgb;
    }

    public static SkinTone fromSerializedName(String name) {
        for (SkinTone tone : values()) {
            if (tone.serializedName.equals(name)) {
                return tone;
            }
        }
        throw new IllegalArgumentException("Unknown skin tone: " + name);
    }
}
