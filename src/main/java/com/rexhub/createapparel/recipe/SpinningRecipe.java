package com.rexhub.createapparel.recipe;

import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

public final class SpinningRecipe extends StandardProcessingRecipe<RecipeWrapper> {
    public static final int DEFAULT_PROCESSING_DURATION = 400;

    public SpinningRecipe(ProcessingRecipeParams params) {
        super(ModRecipeTypes.SPINNING, params);
    }

    @Override
    public boolean matches(RecipeWrapper inventory, Level level) {
        return !inventory.isEmpty() && ingredients.get(0).test(inventory.getItem(0));
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
