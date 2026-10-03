package com.rexhub.createapparel.block.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class MachineGoggleTooltip {
    private MachineGoggleTooltip() {
    }

    static boolean addStack(List<Component> tooltip, String labelKey, ItemStack stack) {
        return addStack(tooltip, labelKey, stack, 0);
    }

    static boolean addStack(List<Component> tooltip, String labelKey, ItemStack stack, int requiredCount) {
        if (stack.isEmpty() && requiredCount <= 0) {
            return false;
        }
        Component name = stack.isEmpty() ? Component.translatable("tooltip.createapparel.machine.empty")
                : stack.getHoverName();
        String quantity = " x" + stack.getCount() + (requiredCount > 0 ? "/" + requiredCount : "");
        Component line = Component.translatable(labelKey, name).append(Component.literal(quantity));
        tooltip.add(line.copy().withStyle(ChatFormatting.GREEN));
        return true;
    }

    static boolean addValue(List<Component> tooltip, String labelKey, Component value) {
        tooltip.add(Component.translatable(labelKey, value).withStyle(ChatFormatting.GREEN));
        return true;
    }
}
