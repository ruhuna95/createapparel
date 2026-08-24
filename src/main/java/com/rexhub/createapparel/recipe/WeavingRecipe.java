package com.rexhub.createapparel.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;

public final class WeavingRecipe extends ProcessingRecipe<RecipeWrapper> {
    public static final int DEFAULT_PROCESSING_DURATION = 1200;
    public static final int REQUIRED_INPUT_COUNT = 4;

    public WeavingRecipe(ProcessingRecipeParams params) {
        super(ModRecipeTypes.WEAVING, params);
    }

    @Override
    public boolean matches(RecipeWrapper inventory, Level level) {
        if (inventory.isEmpty() || ingredients.isEmpty()) {
            return false;
        }

        ItemStack input = inventory.getItem(0);
        return input.getCount() >= REQUIRED_INPUT_COUNT
                && ingredients.get(0).test(input);
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 1;
    }

    @Override
    protected boolean canSpecifyDuration() {
        return true;
    }
}
