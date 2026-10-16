package com.rexhub.createapparel.compat.jei;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

abstract class ApparelRecipeCategory<T> implements IRecipeCategory<T> {
    private final RecipeType<T> type;
    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable catalystSlot;

    ApparelRecipeCategory(IGuiHelper helper, RecipeType<T> type, ItemStack catalyst) {
        this.type = type;
        icon = helper.createDrawableItemStack(catalyst);
        slot = drawable(helper, AllGuiTextures.JEI_SLOT);
        catalystSlot = drawable(helper, AllGuiTextures.JEI_CATALYST_SLOT);
    }

    private static IDrawable drawable(IGuiHelper helper, AllGuiTextures texture) {
        return helper.createDrawable(texture.getLocation(), texture.getStartX(), texture.getStartY(),
                texture.getWidth(), texture.getHeight());
    }

    @Override
    public RecipeType<T> getRecipeType() { return type; }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.createapparel.category." + type.getUid().getPath());
    }

    @Override
    public IDrawable getIcon() { return icon; }

    @Override
    public int getWidth() { return 177; }

    protected void background(IRecipeSlotBuilder builder, boolean catalyst) {
        builder.setBackground(catalyst ? catalystSlot : slot, -1, -1);
    }

    protected void text(GuiGraphics graphics, String key, int x, int y, int width, Object... arguments) {
        graphics.drawWordWrap(Minecraft.getInstance().font, Component.translatable(key, arguments),
                x, y, width, 0x555555);
    }

    protected void tooltip(ITooltipBuilder tooltip, String key, Object... arguments) {
        tooltip.addAll(Minecraft.getInstance().font.getSplitter()
                .splitLines(Component.translatable(key, arguments), 190, Style.EMPTY));
    }
}
