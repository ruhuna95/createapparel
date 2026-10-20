package com.rexhub.createapparel.client.guide;

import com.rexhub.createapparel.guide.ItemGuide;
import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

public final class ApparelItemDescriptions {
    private ApparelItemDescriptions() { }

    public static void register() {
        for (var entry : ModRegistries.ITEMS.getEntries()) {
            var item = entry.get();
            var guide = ItemGuide.forItem(item);
            if (!guide.hasSummary()) continue;
            ItemDescription.useKey(item, guide.translationBase());
            var details = new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE);
            TooltipModifier.REGISTRY.register(item, event -> {
                if (guide.hasDetails()) details.modify(event);
                if (guide == ItemGuide.GAUGE && Screen.hasShiftDown()) appendGaugeState(event.getToolTip(), event.getItemStack());
                if (guide != ItemGuide.GAUGE && (!guide.hasDetails() || !Screen.hasShiftDown()) && !guide.hasExistingSummary()) {
                    var lines = TooltipHelper.cutStringTextComponent(
                            I18n.get(guide.translationBase() + ".tooltip.summary"), FontHelper.Palette.STANDARD_CREATE);
                    event.getToolTip().addAll(1, lines);
                }
            });
        }
    }
    static void appendGaugeState(java.util.List<Component> tooltip, net.minecraft.world.item.ItemStack stack) {
        var tag = stack.getTag();
        boolean recorded = tag != null && tag.contains("GaugeCenter", net.minecraft.nbt.Tag.TAG_LONG);
        tooltip.add(Component.translatable("screen.createapparel.gauge." + (recorded ? "district"
                : tag != null && tag.getBoolean("GaugeOutflow") ? "outflow" : "source")));
        if (recorded) {
            var center = Component.translatable("message.createapparel.gauge.center", BlockPos.of(tag.getLong("GaugeCenter")).toShortString());
            if (!tooltip.contains(center)) tooltip.add(center);
        }
    }
}
