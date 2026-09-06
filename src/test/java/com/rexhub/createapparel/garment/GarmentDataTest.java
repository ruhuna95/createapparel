package com.rexhub.createapparel.garment;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GarmentDataTest {
    @Test
    void retiredBlouseSleevesPreserveTuckWithoutChangingStoredNbt() throws IOException {
        for (String id : List.of("blouse_round", "blouse_frill", "blouse_bow_tie")) {
            for (String sleeves : List.of("normal", "rolled")) {
                CompoundTag tag = data(sleeves);
                CompoundTag before = tag.copy();
                var styles = GarmentData.readStyles(tag.getCompound("style"), definition(id));
                assertEquals(Map.of("tuck", "tucked"), styles);
                assertEquals(before, tag);
                assertEquals(sleeves, tag.getCompound("style").getString("sleeves"));
            }
        }
    }

    @Test
    void otherItemsAndInvalidStylesRemainStrict() throws IOException {
        GarmentDefinition threeQuarter = definition("blouse_round_three_quarter_sleeve");
        GarmentDefinition shirt = definition("t_shirt");
        GarmentDefinition blouse = definition("blouse_round");
        assertThrows(IllegalArgumentException.class, () -> GarmentData.readStyles(data("rolled").getCompound("style"), threeQuarter));
        assertThrows(IllegalArgumentException.class, () -> GarmentData.readStyles(data("rolled").getCompound("style"), shirt));
        assertThrows(IllegalArgumentException.class, () -> GarmentData.readStyles(data("invalid").getCompound("style"), blouse));
        CompoundTag invalid = data("normal");
        invalid.getCompound("style").putString("other", "normal");
        assertThrows(IllegalArgumentException.class, () -> GarmentData.readStyles(invalid.getCompound("style"), blouse));
    }

    @Test
    void shirtSleeveStylesStillResolveNormally() throws IOException {
        var styles = GarmentData.readStyles(data("rolled").getCompound("style"), definition("shirt_longsleeves"));
        assertEquals("rolled", styles.get("sleeves"));
        assertEquals("tucked", styles.get("tuck"));
        assertEquals("closed", styles.get("open"));
    }

    private static CompoundTag data(String sleeves) {
        CompoundTag tag = new CompoundTag();
        tag.putString("material", "silk");
        tag.putString("design", "plain");
        tag.putString("primary_color", "blue");
        tag.putString("secondary_color", "red");
        tag.putString("accent_color", "yellow");
        CompoundTag style = new CompoundTag();
        style.putString("tuck", "tucked");
        style.putString("sleeves", sleeves);
        tag.put("style", style);
        return tag;
    }

    private static GarmentDefinition definition(String name) throws IOException {
        return GarmentDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("createapparel", name),
                JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/createapparel/garments/" + name + ".json")))
                        .getAsJsonObject());
    }
}
