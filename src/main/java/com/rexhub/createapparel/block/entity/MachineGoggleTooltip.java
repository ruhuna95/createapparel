package com.rexhub.createapparel.block.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

public final class MachineGoggleTooltip {
    private MachineGoggleTooltip() {
    }

    public static boolean addContents(List<Component> tooltip, IItemHandler inventory) {
        boolean added = false;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            tooltip.add(Component.translatable(
                    "tooltip.createapparel.machine.contents",
                    stack.getHoverName(),
                    stack.getCount()
            ).withStyle(ChatFormatting.GREEN));
            added = true;
        }
        return added;
    }
}
