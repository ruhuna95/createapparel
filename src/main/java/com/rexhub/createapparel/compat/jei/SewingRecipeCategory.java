package com.rexhub.createapparel.compat.jei;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class SewingRecipeCategory extends ApparelRecipeCategory<SewingRecipeDisplay> {
    SewingRecipeCategory(IGuiHelper helper, ItemStack catalyst) {
        super(helper, ApparelJeiRecipeTypes.SEWING, catalyst);
    }

    @Override
    public int getHeight() { return 88; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SewingRecipeDisplay recipe, IFocusGroup focuses) {
        background(builder.addSlot(RecipeIngredientRole.INPUT, 16, 22).addItemStacks(recipe.fabrics()), false);
        background(builder.addSlot(RecipeIngredientRole.INPUT, 61, 22).addItemStacks(recipe.threads()), false);
        var pattern = builder.addSlot(RecipeIngredientRole.CATALYST, 16, 57).addItemStack(recipe.pattern());
        background(pattern, true);
        var output = builder.addSlot(RecipeIngredientRole.OUTPUT, 144, 22).addItemStack(recipe.result());
        background(output, false);
    }

    @Override
    public void draw(SewingRecipeDisplay recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        text(graphics, "jei.createapparel.fabric", 5, 5, 47);
        text(graphics, "jei.createapparel.thread", 57, 5, 45);
        AllGuiTextures.JEI_ARROW.render(graphics, 99, 25);
        text(graphics, "jei.createapparel.sewing.pattern", 41, 56, getWidth() - 45);
    }

    @Override
    public ResourceLocation getRegistryName(SewingRecipeDisplay recipe) { return recipe.recipe().getId(); }
}
