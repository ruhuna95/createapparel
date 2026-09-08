package com.rexhub.createapparel.garment;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GarmentDataTest {
    @Test
    void itemDataHasNoStyleComponent() {
        assertEquals(java.util.List.of("material", "design", "primaryColor", "secondaryColor", "accentColor"),
                java.util.Arrays.stream(GarmentData.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test
    void equippedStylesRoundTripAndNormalizeInvalidSavedValues() throws IOException {
        var definition = definition("shirt_longsleeves");
        var defaults = EquippedStyleState.defaultsFor(definition);
        var selected = defaults.with("sleeves", "rolled").with("tuck", "tucked");
        assertEquals(selected, EquippedStyleState.fromNBT(selected.serializeNBT()).normalize(definition));
        assertEquals("normal", defaults.styles().get("sleeves"));
        var invalid = selected.with("sleeves", "invalid").with("unknown", "value").normalize(definition);
        assertEquals("normal", invalid.styles().get("sleeves"));
        assertFalse(invalid.styles().containsKey("unknown"));
        assertEquals("tucked", invalid.styles().get("tuck"));
        assertThrows(UnsupportedOperationException.class, () -> selected.styles().put("tuck", "untucked"));
    }

    @Test
    void blouseDropsRetiredSleeveStateWithoutMutatingSavedTag() throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putString("tuck", "tucked");
        tag.putString("sleeves", "rolled");
        CompoundTag before = tag.copy();
        var state = EquippedStyleState.fromNBT(tag).normalize(definition("blouse_round"));
        assertEquals(Map.of("tuck", "tucked"), state.styles());
        assertEquals(before, tag);
    }

    private static GarmentDefinition definition(String name) throws IOException {
        return GarmentDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("createapparel", name),
                JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/createapparel/garments/" + name + ".json")))
                        .getAsJsonObject());
    }
}
