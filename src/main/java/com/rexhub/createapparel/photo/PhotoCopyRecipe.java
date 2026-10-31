package com.rexhub.createapparel.photo;

import com.rexhub.createapparel.advertisement.AdvertisementRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class PhotoCopyRecipe extends CustomRecipe {
    public PhotoCopyRecipe(CraftingBookCategory category) { super(category); }
    public static boolean manual(CraftingInput inventory) {
        return inventory instanceof ManualCraftingInput input && input.createapparel$isManual();
    }
    private static ItemStack photo(CraftingInput inventory) {
        ItemStack photo = ItemStack.EMPTY;
        for (int index = 0; index < inventory.size(); index++) {
            var stack = inventory.getItem(index); if (stack.isEmpty()) continue;
            if (stack.is(PhotoRegistries.ADVERTISING_PHOTO.get()) && photo.isEmpty() && AdvertisingPhotoData.read(stack) != null) photo = stack;
            else return ItemStack.EMPTY;
        }
        return photo;
    }
    @Override public boolean matches(CraftingInput inventory, Level level) { return manual(inventory) && !photo(inventory).isEmpty(); }
    @Override public ItemStack assemble(CraftingInput inventory, net.minecraft.core.HolderLookup.Provider access) {
        var input = photo(inventory); if (input.isEmpty()) return ItemStack.EMPTY;
        return input.copyWithCount(2);
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public RecipeSerializer<?> getSerializer() { return AdvertisementRegistries.PHOTO_COPY.get(); }
}
