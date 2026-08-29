package com.rexhub.createapparel.apparel;

import net.minecraft.util.StringRepresentable;

public enum ChestProfile implements StringRepresentable {
    FLAT("flat"),
    BUST("bust");

    private final String serializedName;

    ChestProfile(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static ChestProfile fromSerializedName(String name) {
        for (ChestProfile profile : values()) {
            if (profile.serializedName.equals(name)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown chest profile: " + name);
    }
}
