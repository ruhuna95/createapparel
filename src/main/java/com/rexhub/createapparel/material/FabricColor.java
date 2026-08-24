package com.rexhub.createapparel.material;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

import java.util.EnumSet;
import java.util.Optional;

public enum FabricColor implements StringRepresentable {
    WHITE(DyeColor.WHITE, 0xF1EEE8),
    ORANGE(DyeColor.ORANGE, 0xC88743),
    MAGENTA(DyeColor.MAGENTA, 0xB06EA5),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE, 0x7FA9D1),
    YELLOW(DyeColor.YELLOW, 0xD6B14F),
    LIME(DyeColor.LIME, 0x8FB955),
    PINK(DyeColor.PINK, 0xD7A0A7),
    GRAY(DyeColor.GRAY, 0x7C828A),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY, 0xC6CBD2),
    CYAN(DyeColor.CYAN, 0x4E98A0),
    PURPLE(DyeColor.PURPLE, 0x7D64A8),
    BLUE(DyeColor.BLUE, 0x5872AB),
    BROWN(DyeColor.BROWN, 0x7A5C43),
    GREEN(DyeColor.GREEN, 0x5F8B59),
    RED(DyeColor.RED, 0xA54B4B),
    BLACK(DyeColor.BLACK, 0x2E3138);

    private final DyeColor dyeColor;
    private final int tintRgb;

    static {
        EnumSet<DyeColor> colors = EnumSet.noneOf(DyeColor.class);
        for (FabricColor color : values()) {
            if (!colors.add(color.dyeColor)) {
                throw new IllegalStateException("Duplicate fabric dye color: " + color.dyeColor);
            }
        }
        if (!colors.equals(EnumSet.allOf(DyeColor.class))) {
            throw new IllegalStateException("Fabric color palette must cover every DyeColor");
        }
    }

    FabricColor(DyeColor dyeColor, int tintRgb) {
        this.dyeColor = dyeColor;
        this.tintRgb = tintRgb;
    }

    public DyeColor dyeColor() {
        return dyeColor;
    }

    public int tintRgb() {
        return tintRgb;
    }

    public static Optional<FabricColor> fromSerializedName(String name) {
        for (FabricColor color : values()) {
            if (color.getSerializedName().equals(name)) {
                return Optional.of(color);
            }
        }
        return Optional.empty();
    }

    @Override
    public String getSerializedName() {
        return dyeColor.getName();
    }
}
