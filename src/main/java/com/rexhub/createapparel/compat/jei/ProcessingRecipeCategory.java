package com.rexhub.createapparel.compat.jei;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class ProcessingRecipeCategory extends ApparelRecipeCategory<ProcessingRecipeDisplay> {
    ProcessingRecipeCategory(IGuiHelper helper, RecipeType<ProcessingRecipeDisplay> type,
                             ItemStack catalyst) {
        super(helper, type, catalyst);
    }

    @Override
    public int getHeight() { return 75; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ProcessingRecipeDisplay recipe, IFocusGroup focuses) {
        var input = builder.addSlot(RecipeIngredientRole.INPUT, 27, 19).addItemStacks(recipe.inputs());
        background(input, false);
        var output = builder.addSlot(RecipeIngredientRole.OUTPUT, 134, 19).addItemStack(recipe.result());
        background(output, false);
        if (recipe.chance() != 1) {
            output.addRichTooltipCallback((view, tooltip) ->
                    tooltip(tooltip, "jei.createapparel.chance", Math.round(recipe.chance() * 100)));
        }
    }

    @Override
    public void draw(ProcessingRecipeDisplay recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        AllGuiTextures.JEI_ARROW.render(graphics, 71, 22);
        getIcon().draw(graphics, 80, 46);
    }

    @Override
    public ResourceLocation getRegistryName(ProcessingRecipeDisplay recipe) { return recipe.recipe().getId(); }
}
