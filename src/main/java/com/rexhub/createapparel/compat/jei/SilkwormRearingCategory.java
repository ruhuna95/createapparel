package com.rexhub.createapparel.compat.jei;

import com.mojang.math.Axis;
import com.rexhub.createapparel.entity.Silkworm;
import com.rexhub.createapparel.init.ModEntities;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

final class SilkwormRearingCategory extends ApparelRecipeCategory<SilkwormRearingDisplay> {
    @Nullable
    private Silkworm silkworm;

    SilkwormRearingCategory(IGuiHelper helper, ItemStack catalyst) {
        super(helper, ApparelJeiRecipeTypes.SILKWORM_REARING, catalyst);
    }

    @Override
    public int getHeight() { return 75; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SilkwormRearingDisplay recipe, IFocusGroup focuses) {
        background(builder.addSlot(RecipeIngredientRole.INPUT, 27, 19).addItemStacks(recipe.inputs()), false);
        background(builder.addSlot(RecipeIngredientRole.OUTPUT, 134, 19).addItemStack(recipe.result()), false);
        if (!recipe.tray()) {
            background(builder.addSlot(RecipeIngredientRole.CATALYST, 134, 49).addItemStack(new ItemStack(Items.SHEARS)), true);
        }
    }

    @Override
    public void draw(SilkwormRearingDisplay recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        AllGuiTextures.JEI_ARROW.render(graphics, 71, 22);
        if (recipe.tray()) {
            getIcon().draw(graphics, 80, 46);
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (silkworm == null || silkworm.level() != level) silkworm = ModEntities.SILKWORM.get().create(level);
        if (silkworm != null) {
            silkworm.yBodyRot = 135;
            silkworm.setYRot(135);
            silkworm.yHeadRot = 135;
            var tilt = Axis.XP.rotationDegrees(25);
            InventoryScreen.renderEntityInInventory(graphics, 88, 67, 38,
                    Axis.ZP.rotationDegrees(180).mul(tilt), tilt, silkworm);
        }
    }

    @Override
    public ResourceLocation getRegistryName(SilkwormRearingDisplay recipe) { return recipe.id(); }
}
