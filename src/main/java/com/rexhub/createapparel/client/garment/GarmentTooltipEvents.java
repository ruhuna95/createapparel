package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.CreateApparel;
import com.mojang.datafixers.util.Either;
import com.rexhub.createapparel.garment.GarmentAppearance;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.MetalAccessoryData;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class GarmentTooltipEvents {
    private static final ResourceLocation WATCH_ID = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "watch");

    private GarmentTooltipEvents() {
    }

    @SubscribeEvent
    public static void addGarmentSlots(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof GarmentItem)) {
            return;
        }
        var id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id != null) {
            ClientGarmentAssets.getDefinition(id).ifPresent(definition -> {
                appendSlots(event.getToolTip(), definition);
                GarmentAppearance appearance = GarmentAppearance.fromStackOrDefault(event.getItemStack(), definition);
                appendAppearance(event.getToolTip(), appearance);
                var tags = com.rexhub.createapparel.fashion.FashionTagResolver.resolve(definition, appearance.design());
                if (!tags.isEmpty()) event.getToolTip().add(Component.translatable("tooltip.createapparel.garment.fashion_tags",
                        Component.literal(String.join(", ", tags.stream().sorted().map(tag ->
                                Component.translatable("fashion_tag.createapparel." + tag.id()).getString()).toList())))
                        .withStyle(ChatFormatting.GRAY));
            });
        }
    }

    static void appendAppearance(List<Component> tooltip, GarmentAppearance appearance) {
        if (appearance instanceof GarmentData data) {
            appendFabricAppearance(tooltip, data.material().getSerializedName(), data.primaryColor().getSerializedName(),
                    data.design(), data.secondaryColor().map(color -> color.getSerializedName()),
                    data.accentColor().map(color -> color.getSerializedName()));
        } else if (appearance instanceof MetalAccessoryData data) {
            insertAppearance(tooltip, List.of(
                    Component.translatable("tooltip.createapparel.accessory.metal",
                            Component.translatable("metal_color.createapparel." + data.metalColor().getSerializedName()))
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("tooltip.createapparel.accessory.gem",
                            Component.translatable("gem.createapparel." + data.gem().getSerializedName()))
                            .withStyle(ChatFormatting.GRAY)));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void addWatchUsage(ItemTooltipEvent event) {
        var id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id != null) appendWatchUsage(event.getToolTip(), id);
    }

    static void appendWatchUsage(List<Component> tooltip, ResourceLocation itemId) {
        if (WATCH_ID.equals(itemId)) {
            tooltip.add(Component.translatable("tooltip.createapparel.watch.check_time", Component.keybind("key.sneak"))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    static void appendFabricAppearance(List<Component> tooltip, String material, String primaryColor, String design,
                                       Optional<String> secondaryColor, Optional<String> accentColor) {
        List<Component> appearance = new ArrayList<>(List.of(
                appearanceLine("material", Component.translatable(
                        "fabric_material.createapparel." + material)),
                appearanceLine("primary_color", colorName(primaryColor)),
                appearanceLine("design", Component.translatable("design.createapparel." + design))));
        secondaryColor.ifPresent(color -> appearance.add(appearanceLine("secondary_color", colorName(color))));
        accentColor.ifPresent(color -> appearance.add(appearanceLine("accent_color", colorName(color))));
        insertAppearance(tooltip, appearance);
    }

    private static void insertAppearance(List<Component> tooltip, List<Component> appearance) {
        int insertion = tooltip.size();
        for (int index = 0; index < tooltip.size(); index++) {
            if (tooltip.get(index).getContents() instanceof TranslatableContents text
                    && (text.getKey().equals("itemGroup.createapparel.garment")
                    || text.getKey().equals("tooltip.createapparel.garment.slot"))) {
                insertion = index + 1;
            }
        }
        tooltip.addAll(insertion, appearance);
    }

    private static Component appearanceLine(String field, Component value) {
        return Component.translatable("tooltip.createapparel.garment." + field, value)
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component colorName(String color) {
        return Component.translatable(FabricColor.fromSerializedName(color)
                .map(value -> value.translationKey()).orElse("color.minecraft." + color));
    }

    static void appendSlots(List<Component> tooltip, GarmentDefinition definition) {
        int insertion = tooltip.size();
        for (int index = 0; index < tooltip.size(); index++) {
            if (tooltip.get(index).getContents() instanceof TranslatableContents text
                    && text.getKey().equals("itemGroup.createapparel.garment")) {
                insertion = index + 1;
                break;
            }
        }
        for (GarmentSlot slot : GarmentSlot.values()) {
            if (definition.allowedSlots().contains(slot)) {
                tooltip.add(insertion++, Component.translatable("tooltip.createapparel.garment.slot",
                        Component.translatable("garment_slot.createapparel." + slot.getSerializedName()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @SubscribeEvent
    public static void removeDuplicateGarmentGroups(RenderTooltipEvent.GatherComponents event) {
        if (event.getItemStack().getItem() instanceof GarmentItem) {
            removeDuplicateGroups(event.getTooltipElements());
        }
    }

    static void removeDuplicateGroups(List<Either<FormattedText, TooltipComponent>> tooltip) {
        boolean foundGroup = false;
        for (int index = tooltip.size() - 1; index >= 0; index--) {
            var text = tooltip.get(index).left().orElse(null);
            if (text instanceof Component component
                    && component.getContents() instanceof TranslatableContents translation
                    && translation.getKey().equals("itemGroup.createapparel.garment")) {
                if (foundGroup) {
                    tooltip.remove(index);
                }
                foundGroup = true;
            }
        }
    }
}
