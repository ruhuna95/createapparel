package com.rexhub.createapparel.compat.jei;

import com.rexhub.createapparel.photo.AdvertisingPhotoData;
import com.rexhub.createapparel.photo.PhotoRegistries;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class PhotoCopyRecipeExtension implements ICraftingCategoryExtension<com.rexhub.createapparel.photo.PhotoCopyRecipe> {
    private static final int CENTER_CRAFTING_SLOT = 4;

    @Override
    public void setRecipe(net.minecraft.world.item.crafting.RecipeHolder<com.rexhub.createapparel.photo.PhotoCopyRecipe> recipe,
                          IRecipeLayoutBuilder builder, ICraftingGridHelper helper, IFocusGroup focuses) {
        ItemStack photo = focuses.getItemStackFocuses()
                .map(focus -> focus.getTypedValue().getIngredient())
                .filter(stack -> stack.is(PhotoRegistries.ADVERTISING_PHOTO.get()) && AdvertisingPhotoData.read(stack) != null)
                .findFirst().map(stack -> stack.copyWithCount(1))
                .orElseGet(() -> new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()));
        builder.setShapeless();
        var inputs = helper.createAndSetInputs(builder, List.of(List.of(photo)), 1, 1);
        inputs.get(CENTER_CRAFTING_SLOT).addRichTooltipCallback((slot, tooltip) ->
                tooltip.add(Component.translatable("jei.createapparel.photo_copy.input")));
        helper.createAndSetOutputs(builder, List.of(photo.copyWithCount(2)))
                .addRichTooltipCallback((slot, tooltip) ->
                        tooltip.add(Component.translatable("jei.createapparel.photo_copy.output")));
    }

    @Override
    public int getWidth(net.minecraft.world.item.crafting.RecipeHolder<com.rexhub.createapparel.photo.PhotoCopyRecipe> recipe) { return 1; }

    @Override
    public int getHeight(net.minecraft.world.item.crafting.RecipeHolder<com.rexhub.createapparel.photo.PhotoCopyRecipe> recipe) { return 1; }
}
