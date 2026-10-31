package com.rexhub.createapparel.garment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class GarmentItem extends Item {
    public GarmentItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            net.minecraft.world.item.Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag isAdvanced
    ) {
        var level = context.level();
        super.appendHoverText(stack, context, tooltipComponents, isAdvanced);
        tooltipComponents.add(Component.translatable("itemGroup.createapparel.garment")
                .withStyle(ChatFormatting.BLUE));
    }


}
