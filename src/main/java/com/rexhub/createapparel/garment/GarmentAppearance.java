package com.rexhub.createapparel.garment;

import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

public sealed interface GarmentAppearance permits GarmentData, MetalAccessoryData {
    String design();

    int primaryTintRgb();

    OptionalInt secondaryTintRgb();

    OptionalInt accentTintRgb();

    void write(ItemStack stack);

    static GarmentAppearance fromStack(ItemStack stack, GarmentDefinition definition) {
        return stack.getItem() instanceof MetalAccessoryItem
                ? MetalAccessoryData.fromStack(stack)
                : GarmentData.fromStack(stack, definition);
    }

    static GarmentAppearance defaultFor(ItemStack stack, GarmentDefinition definition) {
        return stack.getItem() instanceof MetalAccessoryItem
                ? MetalAccessoryData.DEFAULT
                : GarmentData.defaultFor(definition);
    }

    static GarmentAppearance fromStackOrDefault(ItemStack stack, GarmentDefinition definition) {
        try {
            return fromStack(stack, definition);
        } catch (IllegalArgumentException exception) {
            return defaultFor(stack, definition);
        }
    }
}
