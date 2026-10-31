package com.rexhub.createapparel.compat.jei;

import com.rexhub.createapparel.recipe.SpinningRecipe;
import com.rexhub.createapparel.recipe.WeavingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Arrays;
import java.util.List;

public record ProcessingRecipeDisplay(net.minecraft.resources.ResourceLocation id, Recipe<?> recipe, List<ItemStack> inputs, ItemStack result,
                                      int duration, float chance) {
    public ProcessingRecipeDisplay {
        inputs = List.copyOf(inputs);
        result = result.copy();
    }

    public static ProcessingRecipeDisplay spinning(net.minecraft.resources.ResourceLocation id, SpinningRecipe recipe, RegistryAccess registries) {
        return processing(id, recipe, 1, registries);
    }

    public static ProcessingRecipeDisplay weaving(net.minecraft.resources.ResourceLocation id, WeavingRecipe recipe, RegistryAccess registries) {
        return processing(id, recipe, WeavingRecipe.REQUIRED_INPUT_COUNT, registries);
    }

    private static ProcessingRecipeDisplay processing(net.minecraft.resources.ResourceLocation id, ProcessingRecipe<?, ?> recipe, int count,
                                                      RegistryAccess registries) {
        List<ItemStack> inputs = recipe.getIngredients().isEmpty()
                ? List.of() : inputs(recipe.getIngredients().get(0), count);
        float chance = recipe.getRollableResults().isEmpty() ? 1 : recipe.getRollableResults().get(0).getChance();
        return new ProcessingRecipeDisplay(id, recipe, inputs, recipe.getResultItem(registries),
                recipe.getProcessingDuration(), chance);
    }

    private static List<ItemStack> inputs(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(count)).toList();
    }

    public boolean isValid() {
        return !inputs.isEmpty() && !result.isEmpty();
    }
}
