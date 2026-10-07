package com.rexhub.createapparel.garment;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class HangerGarments {
    private HangerGarments() { }

    public static double displayLift(GarmentDefinition definition) {
        return definition.allowedSlots().contains(GarmentSlot.BOTTOM) ? 0 : 0.125;
    }

    public static boolean accepts(ItemStack stack) {
        return accepts(stack, HangerType.NORMAL);
    }

    public static boolean accepts(ItemStack stack, HangerType type) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GarmentItem)) return false;
        return GarmentDefinitionManager.get(ForgeRegistries.ITEMS.getKey(stack.getItem()))
                .map(type::accepts).orElse(false);
    }

    public static boolean accepts(GarmentDefinition definition) {
        return HangerType.NORMAL.accepts(definition);
    }
}
