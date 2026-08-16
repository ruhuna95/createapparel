package com.rexhub.createapparel.material;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

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

    private static final Map<DyeColor, FabricColor> BY_DYE_COLOR = createLookup();

    private final DyeColor dyeColor;
    private final int tintRgb;

    FabricColor(DyeColor dyeColor, int tintRgb) {
        this.dyeColor = dyeColor;
        this.tintRgb = tintRgb;
    }

    private static Map<DyeColor, FabricColor> createLookup() {
        EnumMap<DyeColor, FabricColor> colors = new EnumMap<>(DyeColor.class);
        for (FabricColor color : values()) {
            if (colors.put(color.dyeColor, color) != null) {
                throw new IllegalStateException("Duplicate fabric dye color: " + color.dyeColor);
            }
        }
        if (colors.size() != DyeColor.values().length) {
            throw new IllegalStateException("Fabric color palette must cover every DyeColor");
        }
        return Collections.unmodifiableMap(colors);
    }

    public DyeColor dyeColor() {
        return dyeColor;
    }

    public int tintRgb() {
        return tintRgb;
    }

    @Override
    public String getSerializedName() {
        return dyeColor.getName();
    }

    public static FabricColor fromDyeColor(DyeColor dyeColor) {
        FabricColor color = BY_DYE_COLOR.get(dyeColor);
        if (color == null) {
            throw new IllegalArgumentException("Unsupported dye color: " + dyeColor);
        }
        return color;
    }
}
