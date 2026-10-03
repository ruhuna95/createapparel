package com.rexhub.createapparel.garment;

import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.OptionalInt;

public record GarmentData(
        FabricMaterial material,
        String design,
        FabricColor primaryColor,
        Optional<FabricColor> secondaryColor,
        Optional<FabricColor> accentColor
) implements GarmentAppearance {
    public static final String TAG_NAME = "GarmentData";

    public GarmentData {
        secondaryColor = secondaryColor == null ? Optional.empty() : secondaryColor;
        accentColor = accentColor == null ? Optional.empty() : accentColor;
    }

    public static GarmentData defaultFor(GarmentDefinition definition) {
        String design = definition.designs().contains("plain")
                ? "plain"
                : definition.designs().iterator().next();
        return new GarmentData(
                FabricMaterial.SYNTHETIC,
                design,
                FabricColor.WHITE,
                Optional.empty(),
                Optional.empty()
        );
    }

    public static GarmentData fromStack(ItemStack stack, GarmentDefinition definition) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG_NAME, CompoundTag.TAG_COMPOUND)) {
            return defaultFor(definition);
        }
        CompoundTag tag = root.getCompound(TAG_NAME);
        FabricMaterial material = parseMaterial(tag.getString("material"));
        String design = tag.getString("design");
        if (!definition.designs().contains(design)) {
            throw new IllegalArgumentException("Garment design is not allowed: " + definition.id() + " / " + design);
        }
        FabricColor primary = parseColor(tag.getString("primary_color"));
        Optional<FabricColor> secondary = optionalColor(tag, "secondary_color");
        Optional<FabricColor> accent = optionalColor(tag, "accent_color");

        return new GarmentData(material, design, primary, secondary, accent);
    }

    public void write(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putString("material", material.getSerializedName());
        tag.putString("design", design);
        tag.putString("primary_color", primaryColor.getSerializedName());
        secondaryColor.ifPresent(color -> tag.putString("secondary_color", color.getSerializedName()));
        accentColor.ifPresent(color -> tag.putString("accent_color", color.getSerializedName()));
        stack.getOrCreateTag().put(TAG_NAME, tag);
    }

    @Override
    public int primaryTintRgb() {
        return primaryColor.tintRgb();
    }

    @Override
    public OptionalInt secondaryTintRgb() {
        return secondaryColor.isPresent() ? OptionalInt.of(secondaryColor.get().tintRgb()) : OptionalInt.empty();
    }

    @Override
    public OptionalInt accentTintRgb() {
        return accentColor.isPresent() ? OptionalInt.of(accentColor.get().tintRgb()) : OptionalInt.empty();
    }

    private static Optional<FabricColor> optionalColor(CompoundTag tag, String key) {
        return tag.contains(key, CompoundTag.TAG_STRING)
                ? Optional.of(parseColor(tag.getString(key)))
                : Optional.empty();
    }

    private static FabricMaterial parseMaterial(String name) {
        return FabricMaterial.fromSerializedName(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown garment material: " + name));
    }

    private static FabricColor parseColor(String name) {
        return FabricColor.fromSerializedName(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown garment color: " + name));
    }
}
