package com.rexhub.createapparel.material;

import net.minecraft.util.StringRepresentable;

import java.util.Optional;

public enum FabricMaterial implements StringRepresentable {
    COTTON("cotton", "Cotton"),
    LINEN("linen", "Linen"),
    HEMP("hemp", "Hemp"),
    WOOL("wool", "Wool"),
    SILK("silk", "Silk"),
    LEATHER("leather", "Leather"),
    SYNTHETIC("synthetic", "Synthetic");

    private final String serializedName;
    private final String displayName;

    FabricMaterial(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<FabricMaterial> fromSerializedName(String name) {
        for (FabricMaterial material : values()) {
            if (material.getSerializedName().equals(name)) {
                return Optional.of(material);
            }
        }
        return Optional.empty();
    }
}
