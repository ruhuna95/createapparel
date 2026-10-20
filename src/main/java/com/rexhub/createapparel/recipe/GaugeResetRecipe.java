package com.rexhub.createapparel.recipe;

import com.rexhub.createapparel.item.CitizenGaugeItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class GaugeResetRecipe extends CustomRecipe {
    public GaugeResetRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }

    public static ItemStack input(Container inventory) {
        var input = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!input.isEmpty() || !(stack.getItem() instanceof CitizenGaugeItem)) return ItemStack.EMPTY;
            var tag = stack.getTag();
            if (tag == null || !(tag.contains("GaugeCenter") || tag.contains("GaugeDimension")
                    || tag.contains("GaugeCenterReservation") || tag.contains("GaugeOutflow"))) return ItemStack.EMPTY;
            input = stack;
        }
        return input;
    }

    @Override public boolean matches(CraftingContainer inventory, Level level) { return !input(inventory).isEmpty(); }

    @Override public ItemStack assemble(CraftingContainer inventory, RegistryAccess registries) {
        var stack = input(inventory);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        var result = stack.copyWithCount(1);
        var tag = result.getTag();
        if (tag != null) {
            tag.remove("GaugeCenter");
            tag.remove("GaugeDimension");
            tag.remove("GaugeCenterReservation");
            tag.remove("GaugeOutflow");
            if (tag.isEmpty()) result.setTag(null);
        }
        return result;
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public RecipeSerializer<?> getSerializer() { return ModRecipeTypes.GAUGE_RESET.get(); }
}
