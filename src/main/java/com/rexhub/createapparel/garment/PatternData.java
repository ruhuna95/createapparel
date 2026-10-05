package com.rexhub.createapparel.garment;

import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record PatternData(
        ResourceLocation recipe,
        String design,
        Optional<FabricColor> secondaryColor,
        Optional<FabricColor> accentColor
) {
    public static final String TAG_NAME = "PatternData";

    public PatternData {
        recipe = SkirtIdMigration.recipeId(recipe);
        design = DesignIdMigration.designId(design);
        if (design.isBlank()) {
            throw new IllegalArgumentException("Pattern design must not be blank");
        }
        secondaryColor = secondaryColor == null ? Optional.empty() : secondaryColor;
        accentColor = accentColor == null ? Optional.empty() : accentColor;
    }

    public static Optional<PatternData> fromStack(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG_NAME, CompoundTag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        CompoundTag tag = root.getCompound(TAG_NAME);
        ResourceLocation recipe = ResourceLocation.tryParse(tag.getString("recipe"));
        String design = tag.getString("design");
        if (recipe == null || design.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new PatternData(
                    recipe,
                    design,
                    optionalColor(tag, "secondary_color"),
                    optionalColor(tag, "accent_color")
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public void write(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", recipe.toString());
        tag.putString("design", design);
        secondaryColor.ifPresent(color -> tag.putString("secondary_color", color.getSerializedName()));
        accentColor.ifPresent(color -> tag.putString("accent_color", color.getSerializedName()));
        stack.getOrCreateTag().put(TAG_NAME, tag);
    }

    private static Optional<FabricColor> optionalColor(CompoundTag tag, String key) {
        if (!tag.contains(key, CompoundTag.TAG_STRING)) {
            return Optional.empty();
        }
        String name = tag.getString(key);
        FabricColor color = FabricColor.fromSerializedName(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown pattern color: " + name));
        return Optional.of(color);
    }
}
