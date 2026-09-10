package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.rexhub.createapparel.garment.GarmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GarmentTooltipEventsTest {
    @Test
    void creativeGroupIsRemovedWhileTheGroupAboveSlotsIsKept() {
        var name = Component.literal("Zip-up Hoodie");
        var otherGroup = Component.translatable("itemGroup.other");
        var group = Component.translatable("itemGroup.createapparel.garment");
        List<Component> lines = new ArrayList<>(List.of(name, otherGroup, group));
        GarmentTooltipEvents.appendSlots(lines, definition("\"top\",\"outer\""));
        List<Either<FormattedText, TooltipComponent>> elements = new ArrayList<>();
        elements.add(Either.left(name));
        elements.add(Either.left(group.copy()));
        lines.subList(1, lines.size()).forEach(line -> elements.add(Either.left(line)));

        GarmentTooltipEvents.removeDuplicateGroups(elements);

        assertEquals(lines, elements.stream().map(element -> element.left().orElseThrow()).toList());
        GarmentTooltipEvents.removeDuplicateGroups(elements);
        assertEquals(lines, elements.stream().map(element -> element.left().orElseThrow()).toList());
    }

    @Test
    void tooltipWithoutGroupAndCustomComponentsIsUnchanged() {
        TooltipComponent custom = new TooltipComponent() {};
        List<Either<FormattedText, TooltipComponent>> elements = new ArrayList<>(List.of(
                Either.left(Component.literal("Name")), Either.right(custom)));
        var original = List.copyOf(elements);
        GarmentTooltipEvents.removeDuplicateGroups(elements);
        assertEquals(original, elements);
    }

    @Test
    void multipleSlotsFollowTheGarmentGroupInStableOrderBeforeAdvancedDetails() {
        var definition = definition("\"outer\",\"top\"");
        var group = Component.translatable("itemGroup.createapparel.garment");
        var advanced = Component.literal("createapparel:zipup_hoodie");
        List<Component> tooltip = new ArrayList<>(List.of(Component.literal("Zip-up Hoodie"), group, advanced));
        GarmentTooltipEvents.appendSlots(tooltip, definition);
        assertEquals(5, tooltip.size());
        assertSame(group, tooltip.get(1));
        assertSlot(tooltip.get(2), "top");
        assertSlot(tooltip.get(3), "outer");
        assertSame(advanced, tooltip.get(4));
    }

    @Test
    void singleSlotAddsOnlyOneWearLocation() {
        List<Component> tooltip = new ArrayList<>(List.of(Component.translatable("itemGroup.createapparel.garment")));
        GarmentTooltipEvents.appendSlots(tooltip, definition("\"top\""));
        assertEquals(2, tooltip.size());
        assertSlot(tooltip.get(1), "top");
    }

    private static void assertSlot(Component component, String slot) {
        var text = assertInstanceOf(TranslatableContents.class, component.getContents());
        assertEquals("tooltip.createapparel.garment.slot", text.getKey());
        var name = assertInstanceOf(Component.class, text.getArgs()[0]);
        assertEquals("garment_slot.createapparel." + slot,
                assertInstanceOf(TranslatableContents.class, name.getContents()).getKey());
    }

    private static GarmentDefinition definition(String slots) {
        return GarmentDefinition.fromJson(ResourceLocation.parse("createapparel:test"), JsonParser.parseString(
                "{\"default_slot\":\"top\",\"allowed_slots\":[" + slots + "],"
                        + "\"model_template\":\"createapparel:hoodie\",\"model_variant\":\"classic\","
                        + "\"texture_variant\":\"default\",\"designs\":[\"plain\"]}").getAsJsonObject());
    }
}
