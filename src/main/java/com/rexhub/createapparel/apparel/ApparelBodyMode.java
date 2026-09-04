package com.rexhub.createapparel.apparel;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

public final class ApparelBodyMode {
    private ApparelBodyMode() {
    }

    public static boolean isActive(IItemHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isActive(List<ItemStack> contents) {
        for (ItemStack stack : contents) {
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
