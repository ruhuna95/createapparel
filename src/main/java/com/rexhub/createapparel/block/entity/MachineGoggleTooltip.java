package com.rexhub.createapparel.block.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class MachineGoggleTooltip {
    private MachineGoggleTooltip() {
    }

    static boolean addStack(List<Component> tooltip, String labelKey, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Component line = Component.translatable(labelKey, stack.getHoverName())
                .append(Component.literal(" x" + stack.getCount()));
        tooltip.add(line.copy().withStyle(ChatFormatting.GREEN));
        return true;
    }

    static boolean addValue(List<Component> tooltip, String labelKey, Component value) {
        tooltip.add(Component.translatable(labelKey, value).withStyle(ChatFormatting.GREEN));
        return true;
    }
}
