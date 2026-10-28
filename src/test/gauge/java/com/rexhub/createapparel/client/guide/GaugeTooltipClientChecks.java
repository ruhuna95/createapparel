package com.rexhub.createapparel.client.guide;

import com.rexhub.createapparel.init.ModItems;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipHelper;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.ArrayList;
import java.util.List;

public final class GaugeTooltipClientChecks {
    private GaugeTooltipClientChecks() { }
    public static void check() {
        var minecraft = Minecraft.getInstance();
        var plain = new ItemStack(ModItems.CITIZEN_GAUGE.get());
        var district = plain.copy(); district.getOrCreateTag().putLong("GaugeCenter", new net.minecraft.core.BlockPos(4, 70, 6).asLong());
        var outflow = plain.copy(); outflow.getOrCreateTag().putBoolean("GaugeOutflow", true);
        for (var stack : List.of(plain, district, outflow)) {
            var normal = stack.getTooltipLines(minecraft.player, TooltipFlag.NORMAL);
            var summary = TooltipHelper.cutStringTextComponent(net.minecraft.client.resources.language.I18n.get(
                    "guide.createapparel.gauge.tooltip.summary"), FontHelper.Palette.STANDARD_CREATE);
            check(normal.size() == summary.size() + (stack == district ? 3 : 2), "Normal gauge tooltip includes its summary and retains recorded coordinates: " + normal);
            check(normal.subList(1, 1 + summary.size()).equals(summary), "Gauge summary precedes the Shift hint");
            if (stack == district) check(normal.stream().anyMatch(line -> line.getString().contains("4, 70, 6")), "Recorded coordinates are visible without Shift");
            var expanded = expanded(stack);
            String text = expanded.stream().map(Component::getString).collect(java.util.stream.Collectors.joining(" "));
            check(text.contains("Influx") || text.contains("influx"), "Shift tooltip describes influx");
            check(text.contains("Outflow") || text.contains("outflow"), "Shift tooltip describes outflow");
            check(!text.contains("Right-click a connection") && !text.contains("for Gateways"), "Shift tooltip omits removed controls");
            if (stack == district) check(text.contains("4, 70, 6"), "Recorded coordinates are in Shift details");
        }
    }
    public static List<Component> expanded(ItemStack stack) {
        var description = ItemDescription.create(stack.getItem(), FontHelper.Palette.STANDARD_CREATE);
        if (description == null) throw new AssertionError("Gauge Shift description is registered");
        var lines = new ArrayList<Component>(); lines.add(stack.getHoverName()); lines.addAll(description.linesOnShift());
        stack.getItem().appendHoverText(stack, Minecraft.getInstance().level, lines, TooltipFlag.NORMAL);
        ApparelItemDescriptions.appendGaugeState(lines, stack);
        check(lines.stream().filter(line -> line.getString().contains("4, 70, 6")).count() <= 1, "Recorded coordinates are not duplicated in Shift details");
        return lines;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
