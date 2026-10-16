package com.rexhub.createapparel.client.guide;

import com.rexhub.createapparel.guide.ItemGuide;
import com.rexhub.createapparel.init.ModRegistries;
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
                if ((!guide.hasDetails() || !Screen.hasShiftDown()) && !guide.hasExistingSummary()) {
                    var lines = TooltipHelper.cutStringTextComponent(
                            I18n.get(guide.translationBase() + ".tooltip.summary"), FontHelper.Palette.STANDARD_CREATE);
                    event.getToolTip().addAll(1, lines);
                }
            });
        }
    }
}
