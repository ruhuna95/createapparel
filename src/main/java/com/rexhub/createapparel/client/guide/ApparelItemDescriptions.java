package com.rexhub.createapparel.client.guide;

import com.rexhub.createapparel.guide.ItemGuide;
import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.Set;

public final class ApparelItemDescriptions {
    private static final Set<String> EXISTING_SUMMARIES = Set.of(
            "tooltip.createapparel.wardrobe.change_clothes",
            "tooltip.createapparel.hanger.normal", "tooltip.createapparel.hanger.clip");

    private ApparelItemDescriptions() { }

    public static void register() {
        for (var entry : ModRegistries.ITEMS.getEntries()) {
            var item = entry.get();
            var guide = ItemGuide.forItem(item);
            if (!guide.hasSummary()) continue;
            ItemDescription.useKey(item, guide.translationBase());
            var details = new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE);
            String summaryKey = switch (guide) {
                case PHONE, PHOTO -> guide.translationBase() + ".tooltip.short_summary";
                case ADVERTISEMENT -> item.getDescriptionId() + ".tooltip.summary";
                default -> guide.translationBase() + ".tooltip.summary";
            };
            TooltipModifier.REGISTRY.register(item, event -> {
                if (guide.hasDetails()) details.modify(event);
                if (guide == ItemGuide.GAUGE && Screen.hasShiftDown()) appendGaugeState(event.getToolTip(), event.getItemStack());
                if ((!guide.hasDetails() || !Screen.hasShiftDown()) && !guide.hasExistingSummary()) {
                    var lines = TooltipHelper.cutStringTextComponent(
                            I18n.get(summaryKey), FontHelper.Palette.STANDARD_CREATE);
                    event.getToolTip().addAll(1, lines);
                }
                if (guide.hasExistingSummary()) updateExistingSummary(event.getToolTip(), Screen.hasShiftDown());
            });
        }
    }

    static void updateExistingSummary(List<Component> tooltip, boolean expanded) {
        for (int index = 1; index < tooltip.size(); index++) {
            var line = tooltip.get(index);
            if (line.getContents() instanceof TranslatableContents contents && EXISTING_SUMMARIES.contains(contents.getKey())) {
                tooltip.remove(index);
                if (!expanded) tooltip.add(1, line.copy().withStyle(FontHelper.Palette.STANDARD_CREATE.primary()));
                return;
            }
        }
    }

    static void appendGaugeState(java.util.List<Component> tooltip, net.minecraft.world.item.ItemStack stack) {
        var tag = com.rexhub.createapparel.item.ItemData.read(stack);
        boolean recorded = tag != null && tag.contains("GaugeCenter", net.minecraft.nbt.Tag.TAG_LONG);
        tooltip.add(Component.translatable("screen.createapparel.gauge." + (recorded ? "district"
                : tag != null && tag.getBoolean("GaugeOutflow") ? "outflow" : "source")));
        if (recorded) {
            var center = Component.translatable("message.createapparel.gauge.center", BlockPos.of(tag.getLong("GaugeCenter")).toShortString());
            if (!tooltip.contains(center)) tooltip.add(center);
        }
    }
}
