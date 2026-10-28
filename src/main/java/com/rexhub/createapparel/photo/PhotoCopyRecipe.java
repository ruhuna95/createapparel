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
    public PhotoCopyRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }
    public static boolean manual(CraftingContainer inventory) {
        return inventory instanceof com.rexhub.createapparel.photo.mixin.CraftingMenuAccessor access && (access.createapparel$craftingMenu() instanceof CraftingMenu
                || access.createapparel$craftingMenu() instanceof InventoryMenu);
    }
    private static ItemStack photo(CraftingContainer inventory) {
        ItemStack photo = ItemStack.EMPTY;
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            var stack = inventory.getItem(index); if (stack.isEmpty()) continue;
            if (stack.is(PhotoRegistries.ADVERTISING_PHOTO.get()) && photo.isEmpty() && AdvertisingPhotoData.read(stack) != null) photo = stack;
            else return ItemStack.EMPTY;
        }
        return photo;
    }
    @Override public boolean matches(CraftingContainer inventory, Level level) { return manual(inventory) && !photo(inventory).isEmpty(); }
    @Override public ItemStack assemble(CraftingContainer inventory, RegistryAccess access) {
        var input = photo(inventory); if (input.isEmpty()) return ItemStack.EMPTY;
        return input.copyWithCount(2);
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public RecipeSerializer<?> getSerializer() { return AdvertisementRegistries.PHOTO_COPY.get(); }
}
