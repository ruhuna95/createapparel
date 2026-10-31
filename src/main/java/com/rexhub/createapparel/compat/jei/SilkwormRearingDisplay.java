package com.rexhub.createapparel.compat.jei;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModTags;
import com.rexhub.createapparel.recipe.SilkwormRearingRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Arrays;
import java.util.List;

public record SilkwormRearingDisplay(ResourceLocation id, List<ItemStack> inputs, ItemStack result, boolean tray) {
    public SilkwormRearingDisplay {
        inputs = List.copyOf(inputs);
        result = result.copy();
    }

    public static SilkwormRearingDisplay tray(ResourceLocation id, SilkwormRearingRecipe recipe, RegistryAccess registries) {
        return new SilkwormRearingDisplay(id, inputs(recipe.getIngredients().get(0), recipe.foodCount()),
                recipe.getResultItem(registries), true);
    }

    public static SilkwormRearingDisplay silkworm() {
        return new SilkwormRearingDisplay(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "jei/silkworm_rearing"),
                inputs(Ingredient.of(ModTags.Items.SILKWORM_FOOD), 1), new ItemStack(ModItems.COCOON.get()), false);
    }

    private static List<ItemStack> inputs(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(count)).toList();
    }

    public boolean isValid() { return !inputs.isEmpty() && !result.isEmpty(); }
}
