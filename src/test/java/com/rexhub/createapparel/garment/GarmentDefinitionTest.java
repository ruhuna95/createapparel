package com.rexhub.createapparel.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GarmentDefinitionTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("createapparel", "test");

    private static JsonObject json(String slots) {
        return JsonParser.parseString("{" + slots + ",\"model_template\":\"createapparel:shirt\","
                + "\"model_variant\":\"top\",\"texture_variant\":\"default\",\"designs\":[\"plain\"]}").getAsJsonObject();
    }

    @Test
    void legacySlotRemainsTopOnly() {
        var definition = GarmentDefinition.fromJson(ID, json("\"slot\":\"top\""));
        assertEquals(GarmentSlot.TOP, definition.defaultSlot());
        assertEquals(Set.of(GarmentSlot.TOP), definition.allowedSlots());
    }

    @Test
    void slotVariantsAndNetworkRoundTrip() {
        var definition = GarmentDefinition.fromJson(ID, json("\"default_slot\":\"top\",\"allowed_slots\":[\"top\",\"outer\"],"
                + "\"slot_model_variants\":{\"outer\":\"outer_open\"},"
                + "\"slot_styles\":{\"outer\":{\"sleeves\":[\"normal\",\"rolled\"]}}"));
        assertEquals("top", definition.modelVariantFor(GarmentSlot.TOP));
        assertEquals("outer_open", definition.modelVariantFor(GarmentSlot.OUTER));
        assertEquals(Map.of("sleeves", List.of("normal", "rolled")), definition.stylesFor(GarmentSlot.OUTER));
        assertEquals(Map.of(), definition.stylesFor(GarmentSlot.TOP));
        assertEquals(GarmentSlot.TOP, GarmentWearContext.defaultsFor(definition).slot());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            definition.write(buffer);
            assertEquals(definition, GarmentDefinition.read(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void invalidSlotStylesAreRejected() {
        for (String styles : List.of(
                "{\"outer\":{\"sleeves\":[\"normal\"]}}",
                "{\"top\":{\"sleeves\":[]}}",
                "{\"top\":{\"\":[\"normal\"]}}",
                "{\"top\":{\"sleeves\":[\"\"]}}",
                "{\"top\":{\"sleeves\":[\"normal\",\"normal\"]}}")) {
            assertThrows(IllegalArgumentException.class, () -> GarmentDefinition.fromJson(ID,
                    json("\"slot\":\"top\",\"slot_styles\":" + styles)));
        }
    }

    @Test
    void onlyLongSleeveShirtOuterHasStylesWhileDefaultItemKeepsTopStyles() throws java.io.IOException {
        for (String name : List.of("shirt_shortsleeves", "shirt_longsleeves")) {
            var definition = GarmentDefinition.fromJson(ID, JsonParser.parseString(java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/resources/data/createapparel/garments/" + name + ".json"))).getAsJsonObject());
            var context = new ApparelContext(Map.of());
            boolean longSleeves = name.equals("shirt_longsleeves");
            assertEquals(longSleeves ? Set.of("sleeves") : Set.of(),
                    GarmentStyleResolver.allowedStyles(definition, GarmentSlot.OUTER, context).keySet());
            var old = new EquippedStyleState(Map.of("sleeves", "rolled", "open", "opened", "tuck", "tucked"));
            var normalized = GarmentStyleResolver.normalize(definition, GarmentSlot.OUTER, old, context);
            assertEquals(longSleeves ? Map.of("sleeves", "rolled") : Map.of(), normalized.styles());
            assertEquals(normalized, old.normalize(definition, GarmentSlot.OUTER));
            assertEquals("closed", GarmentWearContext.defaultsFor(definition).styleState().styles().get("open"));
            assertEquals("untucked", GarmentWearContext.defaultsFor(definition).styleState().styles().get("tuck"));
            assertEquals(definition.styles(), GarmentStyleResolver.allowedStyles(definition, GarmentSlot.TOP, context));
        }
    }

    @Test
    void invalidSlotsAreRejected() {
        for (String slots : List.of(
                "\"slot\":\"top\",\"default_slot\":\"top\",\"allowed_slots\":[\"top\"]",
                "\"default_slot\":\"top\",\"allowed_slots\":[]",
                "\"default_slot\":\"top\",\"allowed_slots\":[\"outer\"]",
                "\"default_slot\":\"top\",\"allowed_slots\":[\"top\",\"top\"]",
                "\"default_slot\":\"top\",\"allowed_slots\":[\"invalid\"]",
                "\"slot\":\"top\",\"slot_model_variants\":{\"outer\":\"outer\"}")) {
            assertThrows(IllegalArgumentException.class, () -> GarmentDefinition.fromJson(ID, json(slots)));
        }
    }

    @Test
    void outerRestrictionNormalizesPermanentlyAndLeavesUnrelatedStylesAlone() {
        var shirt = new GarmentDefinition(ID, GarmentSlot.TOP, ID, "default", "default", Set.of("plain"),
                Map.of("sleeves", List.of("normal", "rolled"), "tuck", List.of("untucked", "tucked")));
        var blouse = new GarmentDefinition(ID, GarmentSlot.TOP, ID, "default", "default", Set.of("plain"),
                Map.of("tuck", List.of("untucked", "tucked")));
        var empty = new ApparelContext(Map.of());
        var outer = new ApparelContext(Map.of(GarmentSlot.OUTER,
                new ApparelContext.EquippedGarmentView(shirt, EquippedStyleState.defaultsFor(shirt))));
        var rolled = EquippedStyleState.defaultsFor(shirt).with("sleeves", "rolled").with("tuck", "tucked");
        assertEquals(rolled, GarmentStyleResolver.normalize(shirt, GarmentSlot.TOP, rolled, empty));
        var normalized = GarmentStyleResolver.normalize(shirt, GarmentSlot.TOP, rolled, outer);
        assertEquals("normal", normalized.styles().get("sleeves"));
        assertEquals("tucked", normalized.styles().get("tuck"));
        assertEquals(normalized, GarmentStyleResolver.normalize(shirt, GarmentSlot.TOP, normalized, empty));
        assertEquals(List.of("normal"), GarmentStyleResolver.allowedStyles(shirt, GarmentSlot.TOP, outer).get("sleeves"));
        assertEquals(blouse.styles(), GarmentStyleResolver.allowedStyles(blouse, GarmentSlot.TOP, outer));
    }
}
