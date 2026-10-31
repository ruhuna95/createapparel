package com.rexhub.createapparel.photo.mixin;

import com.rexhub.createapparel.photo.ManualCraftingInput;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.CraftingInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TransientCraftingContainer.class)
abstract class CraftingMenuAccessor implements CraftingContainer {
    @Shadow @Final private AbstractContainerMenu menu;

    @Override
    public CraftingInput.Positioned asPositionedCraftInput() {
        var positioned = CraftingContainer.super.asPositionedCraftInput();
        if (!positioned.input().isEmpty()) {
            ((ManualCraftingInput) positioned.input()).createapparel$setManual(
                    menu instanceof CraftingMenu || menu instanceof InventoryMenu);
        }
        return positioned;
    }
}
