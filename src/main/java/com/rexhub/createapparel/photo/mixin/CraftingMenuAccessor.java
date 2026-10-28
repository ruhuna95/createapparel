package com.rexhub.createapparel.photo.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TransientCraftingContainer.class)
public interface CraftingMenuAccessor {
    @Accessor("menu") AbstractContainerMenu createapparel$craftingMenu();
}
