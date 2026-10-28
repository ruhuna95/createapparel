package com.rexhub.createapparel.client.guide;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.guide.ItemGuide;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipHelper;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ItemGuideTooltipChecks {
    private static final List<String> ITEMS = List.of("smart_phone", "citizen_gauge", "spinning_machine", "loom",
            "sewing_machine", "wardrobe", "wall_hanger", "clip_wall_hanger", "clothing_rack", "clip_clothing_rack",
            "cash_register", "poster", "billboard_panel", "signage_panel", "advertising_photo");

    private ItemGuideTooltipChecks() { }

    public static void check() {
        require(!Screen.hasShiftDown(), "Normal tooltip checks require Shift to be released");
        for (String id : ITEMS) {
            var stack = stack(id);
            var guide = ItemGuide.forItem(stack.getItem());
            var normal = stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.NORMAL);
            if (guide.hasExistingSummary()) {
                String key = guide == ItemGuide.WARDROBE ? "tooltip.createapparel.wardrobe.change_clothes"
                        : "tooltip.createapparel.hanger." + (id.startsWith("clip_") ? "clip" : "normal");
                checkFurniture(normal, key, false);
                var expanded = expandedFurniture(stack);
                checkFurniture(expanded, key, true);
                ApparelItemDescriptions.updateExistingSummary(expanded, true);
                checkFurniture(expanded, key, true);
            } else {
                String key = switch (guide) {
                    case PHONE, PHOTO -> guide.translationBase() + ".tooltip.short_summary";
                    case ADVERTISEMENT -> stack.getDescriptionId() + ".tooltip.summary";
                    default -> guide.translationBase() + ".tooltip.summary";
                };
                String source = I18n.get(key);
                var expected = TooltipHelper.cutStringTextComponent(source, FontHelper.Palette.STANDARD_CREATE);
                require(normal.subList(1, 1 + expected.size()).equals(expected), "Native summary precedes hints: " + id);
                if (List.of(ItemGuide.SPINNING, ItemGuide.LOOM, ItemGuide.SEWING).contains(guide)) {
                    checkHighlighting(normal.subList(1, 1 + expected.size()), source);
                }
                if (guide == ItemGuide.GAUGE) {
                    String shift = I18n.get("create.tooltip.keyShift");
                    require(normal.stream().skip(1 + expected.size()).anyMatch(line -> line.getString().contains(shift)),
                            "Gauge retains its Shift hint");
                    var description = ItemDescription.create(stack.getItem(), FontHelper.Palette.STANDARD_CREATE);
                    require(description != null && description.linesOnShift().size() > normal.size(), "Gauge retains its detailed instructions");
                    var recorded = stack.copy();
                    recorded.getOrCreateTag().putLong("GaugeCenter", new net.minecraft.core.BlockPos(4, 70, 6).asLong());
                    require(recorded.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.NORMAL).stream()
                            .anyMatch(line -> line.getString().contains("4, 70, 6")), "Recorded gauge center remains visible");
                }
            }
        }
        LogUtils.getLogger().info("ITEM_GUIDE_TOOLTIPS_PASSED language={}", Minecraft.getInstance().getLanguageManager().getSelected());
    }

    private static List<Component> expandedFurniture(ItemStack stack) {
        var description = ItemDescription.create(stack.getItem(), FontHelper.Palette.STANDARD_CREATE);
        require(description != null, "Furniture Shift details are present: " + stack);
        var lines = new ArrayList<Component>();
        lines.add(stack.getHoverName());
        lines.addAll(description.linesOnShift());
        stack.getItem().appendHoverText(stack, Minecraft.getInstance().level, lines, TooltipFlag.NORMAL);
        ApparelItemDescriptions.updateExistingSummary(lines, true);
        return lines;
    }

    private static void checkFurniture(List<Component> lines, String key, boolean expanded) {
        String text = I18n.get(key);
        require(lines.stream().filter(line -> line.getString().equals(text)).count() == 1, "Furniture summary appears once");
        int summaryIndex = -1;
        int shiftIndex = -1;
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).getString().equals(text)) summaryIndex = index;
            if (shiftIndex < 0 && lines.get(index).getString().contains(I18n.get("create.tooltip.keyShift"))) shiftIndex = index;
        }
        require(shiftIndex > 0, "Furniture retains its Shift hint");
        require(expanded ? summaryIndex > shiftIndex : summaryIndex == 1 && summaryIndex < shiftIndex,
                "Furniture summary moves inside Shift details and stays above the hint normally");
        lines.get(summaryIndex).<Void>visit((style, part) -> {
            if (!part.isEmpty()) require(FontHelper.Palette.STANDARD_CREATE.primary().getColor().equals(style.getColor()),
                    "Furniture summary uses the orange Create summary color");
            return Optional.empty();
        }, Style.EMPTY);
        if (expanded) require(lines.size() > summaryIndex + 2, "Furniture retains its detailed operating instructions");
    }

    private static void checkHighlighting(List<Component> lines, String source) {
        var text = new StringBuilder();
        var colors = new ArrayList<Integer>();
        for (var line : lines) line.<Void>visit((style, part) -> {
            text.append(part);
            for (int index = 0; index < part.length(); index++) colors.add(style.getColor() == null ? -1 : style.getColor().getValue());
            return Optional.empty();
        }, Style.EMPTY);
        require(text.toString().equals(source.replace("_", "")), "Highlight markers are not displayed");
        int offset = 0;
        var parts = source.split("_", -1);
        for (int index = 0; index < parts.length; index++) {
            int color = (index % 2 == 0 ? FontHelper.Palette.STANDARD_CREATE.primary() : FontHelper.Palette.STANDARD_CREATE.highlight())
                    .getColor().getValue();
            for (int character = 0; character < parts[index].length(); character++) {
                require(colors.get(offset++) == color, "Material and product words are yellow; other text stays orange");
            }
        }
    }

    private static ItemStack stack(String path) {
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("createapparel", path));
        require(item != null, "Missing tooltip fixture: " + path);
        var stack = new ItemStack(item);
        require(!stack.isEmpty(), "Empty tooltip fixture: " + path);
        return stack;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static final class PreviewScreen extends Screen {
        private final int page;

        public PreviewScreen(int page) {
            super(Component.literal("Item guide tooltips"));
            this.page = page;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            var items = page >= 2 ? List.of(List.of("wardrobe", "wall_hanger", "clothing_rack").get(page - 2)) : ITEMS;
            int start = page >= 2 ? 0 : page * 9;
            for (int index = start; index < Math.min(items.size(), start + 9); index++) {
                int slot = index - start;
                var item = stack(items.get(index));
                var lines = page >= 2 ? expandedFurniture(item) : item.getTooltipLines(minecraft.player, TooltipFlag.NORMAL);
                graphics.renderTooltip(font, lines, Optional.empty(), 8 + slot % 3 * 210, 24 + slot / 3 * 116);
            }
            graphics.flush();
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}
