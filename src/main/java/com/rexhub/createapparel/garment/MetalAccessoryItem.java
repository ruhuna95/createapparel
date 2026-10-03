package com.rexhub.createapparel.garment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public final class MetalAccessoryItem extends GarmentItem {
    public MetalAccessoryItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        MetalAccessoryData data;
        try {
            data = MetalAccessoryData.fromStack(stack);
        } catch (IllegalArgumentException exception) {
            data = MetalAccessoryData.DEFAULT;
        }
        tooltip.add(Component.translatable("tooltip.createapparel.accessory.metal",
                Component.translatable("metal_color.createapparel." + data.metalColor().getSerializedName()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.createapparel.accessory.gem",
                Component.translatable("gem.createapparel." + data.gem().getSerializedName()))
                .withStyle(ChatFormatting.GRAY));
    }
}
