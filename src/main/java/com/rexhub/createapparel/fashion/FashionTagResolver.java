package com.rexhub.createapparel.fashion;

import com.rexhub.createapparel.garment.GarmentAppearance;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class FashionTagResolver {
    private static volatile Map<String, FashionDesignModifier> modifiers = Map.of();
    private FashionTagResolver() { }
    public static Map<String, FashionDesignModifier> modifiers() { return modifiers; }
    public static void acceptModifiers(Map<String, FashionDesignModifier> next) { modifiers = Map.copyOf(next); }
    public static Set<FashionTag> resolve(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var definition = id == null ? null : GarmentDefinitionManager.get(id).orElse(null);
        return definition == null ? Set.of() : resolve(definition, GarmentAppearance.fromStack(stack, definition).design());
    }
    public static Set<FashionTag> resolve(GarmentDefinition definition, String design) {
        if (!definition.designs().contains(design)) throw new IllegalArgumentException("Unsupported design: " + design);
        Set<FashionTag> result = EnumSet.noneOf(FashionTag.class);
        result.addAll(definition.fashionTags());
        var common = modifiers.get(design);
        if (common != null) common.apply(result);
        var local = definition.fashionDesignOverrides().get(design);
        if (local != null) local.apply(result);
        return Set.copyOf(result);
    }
}
