package com.rexhub.createapparel.garment;

import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record GarmentData(
        FabricMaterial material,
        String design,
        FabricColor primaryColor,
        Optional<FabricColor> secondaryColor,
        Optional<FabricColor> accentColor,
        Map<String, String> styles
) {
    public static final String TAG_NAME = "GarmentData";

    public GarmentData {
        secondaryColor = secondaryColor == null ? Optional.empty() : secondaryColor;
        accentColor = accentColor == null ? Optional.empty() : accentColor;
        styles = Collections.unmodifiableMap(new LinkedHashMap<>(styles));
    }

    public static GarmentData defaultFor(GarmentDefinition definition) {
        LinkedHashMap<String, String> styles = new LinkedHashMap<>();
        definition.styles().forEach((key, values) -> styles.put(key, values.get(0)));
        String design = definition.designs().contains("plain")
                ? "plain"
                : definition.designs().iterator().next();
        return new GarmentData(
                FabricMaterial.COTTON,
                design,
                FabricColor.WHITE,
                Optional.empty(),
                Optional.empty(),
                styles
        );
    }

    public static GarmentData fromStack(ItemStack stack, GarmentDefinition definition) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG_NAME, CompoundTag.TAG_COMPOUND)) {
            return defaultFor(definition);
        }
        CompoundTag tag = root.getCompound(TAG_NAME);
        FabricMaterial material = material(tag.getString("material"));
        String design = tag.getString("design");
        if (!definition.designs().contains(design)) {
            throw new IllegalArgumentException("Garment design is not allowed: " + definition.id() + " / " + design);
        }
        FabricColor primary = color(tag.getString("primary_color"));
        Optional<FabricColor> secondary = optionalColor(tag, "secondary_color");
        Optional<FabricColor> accent = optionalColor(tag, "accent_color");

        CompoundTag styleTag = tag.getCompound("style");
        for (String key : styleTag.getAllKeys()) {
            if (!definition.styles().containsKey(key)) {
                throw new IllegalArgumentException("Unknown garment style category: " + definition.id() + " / " + key);
            }
        }
        LinkedHashMap<String, String> styles = new LinkedHashMap<>();
        definition.styles().forEach((key, allowed) -> {
            String selected = styleTag.contains(key) ? styleTag.getString(key) : allowed.get(0);
            if (!allowed.contains(selected)) {
                throw new IllegalArgumentException(
                        "Garment style is not allowed: " + definition.id() + " / " + key + "=" + selected
                );
            }
            styles.put(key, selected);
        });
        return new GarmentData(material, design, primary, secondary, accent, styles);
    }

    public void write(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putString("material", material.getSerializedName());
        tag.putString("design", design);
        tag.putString("primary_color", primaryColor.getSerializedName());
        secondaryColor.ifPresent(color -> tag.putString("secondary_color", color.getSerializedName()));
        accentColor.ifPresent(color -> tag.putString("accent_color", color.getSerializedName()));
        CompoundTag styleTag = new CompoundTag();
        styles.forEach(styleTag::putString);
        tag.put("style", styleTag);
        stack.getOrCreateTag().put(TAG_NAME, tag);
    }

    private static Optional<FabricColor> optionalColor(CompoundTag tag, String key) {
        return tag.contains(key, CompoundTag.TAG_STRING)
                ? Optional.of(color(tag.getString(key)))
                : Optional.empty();
    }

    private static FabricMaterial material(String name) {
        return Arrays.stream(FabricMaterial.values())
                .filter(material -> material.getSerializedName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown garment material: " + name));
    }

    private static FabricColor color(String name) {
        return Arrays.stream(FabricColor.values())
                .filter(color -> color.getSerializedName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown garment color: " + name));
    }
}
