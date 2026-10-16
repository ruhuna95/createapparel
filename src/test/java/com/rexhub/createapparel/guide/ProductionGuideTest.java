package com.rexhub.createapparel.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductionGuideTest {
    private static JsonObject language(String language) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/assets/createapparel/lang/" + language + ".json"))).getAsJsonObject();
    }

    @Test
    void allGuideTextHasMatchingEnglishAndJapaneseEntries() throws IOException {
        var english = language("en_us");
        var japanese = language("ja_jp");
        assertEquals(english.keySet(), japanese.keySet());
        assertEquals(9, ProductionGuide.values().length);
        for (boolean ja : List.of(false, true)) {
            var lang = ja ? japanese : english;
            for (var scene : ProductionGuide.values()) {
                String prefix = "createapparel.ponder." + scene.id() + ".";
                assertEquals(scene.title(ja), lang.get(prefix + "header").getAsString());
                for (int index = 0; index < scene.textCount(); index++) {
                    assertEquals(scene.text(index, ja), lang.get(prefix + "text_" + (index + 1)).getAsString());
                }
                assertFalse(lang.has(prefix + "text_" + (scene.textCount() + 1)));
            }
            for (var guide : ItemGuide.values()) {
                String prefix = guide.translationBase() + ".tooltip.";
                if (!guide.hasSummary()) {
                    assertTrue(lang.keySet().stream().noneMatch(key -> key.startsWith(prefix)), guide.name());
                    continue;
                }
                assertFalse(lang.get(prefix + "summary").getAsString().isBlank(), guide.name());
                assertEquals(guide.hasDetails(), lang.has(prefix + "condition1"), guide.name());
                int count = 0;
                while (lang.has(prefix + "condition" + (count + 1))) {
                    count++;
                    assertTrue(lang.has(prefix + "behaviour" + count), prefix);
                }
                final int details = count;
                assertEquals(count, lang.keySet().stream().filter(key -> key.startsWith(prefix + "condition")).count());
                assertEquals(details, lang.keySet().stream().filter(key -> key.startsWith(prefix + "behaviour")).count());
            }
            assertFalse(lang.has("tooltip.createapparel.smart_phone.routes"));
            assertFalse(lang.has("tooltip.createapparel.smart_phone.convert_villager"));
            assertFalse(lang.has("tooltip.createapparel.smart_phone.route_health"));
        }
    }

    @Test
    void productionTemplateHasFiveByFiveBasePlateAndAirForExternalDrives() throws IOException {
        try (var stream = Files.newInputStream(Path.of("src/generated/resources/assets/createapparel/ponder/production.nbt"))) {
            var root = NbtIo.readCompressed(stream);
            assertEquals(7, root.getList("size", 3).getInt(0));
            assertEquals(6, root.getList("size", 3).getInt(1));
            assertEquals(7, root.getList("size", 3).getInt(2));
            var blocks = root.getList("blocks", 10);
            assertEquals(294, blocks.size());
            var palette = root.getList("palette", 10);
            assertEquals("minecraft:white_concrete", palette.getCompound(1).getString("Name"));
            assertEquals("minecraft:snow_block", palette.getCompound(2).getString("Name"));
            int floorBlocks = 0;
            for (var entry : blocks) {
                var block = (net.minecraft.nbt.CompoundTag) entry;
                var pos = block.getList("pos", 3);
                if (pos.getInt(1) == 0 && pos.getInt(0) >= 1 && pos.getInt(0) <= 5
                        && pos.getInt(2) >= 1 && pos.getInt(2) <= 5) {
                    assertEquals(1 + (pos.getInt(0) + pos.getInt(2)) % 2, block.getInt("state"));
                    floorBlocks++;
                } else assertEquals(0, block.getInt("state"));
            }
            assertEquals(25, floorBlocks);
        }
    }

    @Test
    void detailsAreReservedForInteractiveItemsAndMaterialsUseJei() throws IOException {
        for (var guide : ItemGuide.values()) {
            if (guide.hasPonder()) assertFalse(guide.hasDetails(), guide.name());
        }
        assertFalse(ItemGuide.GARMENT.hasDetails());
        assertTrue(ItemGuide.PATTERN.hasDetails());
        assertFalse(ItemGuide.PATTERN.hasPonder());
        for (var guide : List.of(ItemGuide.FABRIC, ItemGuide.THREAD, ItemGuide.FIBER, ItemGuide.CROP,
                ItemGuide.SEEDS, ItemGuide.COCOON, ItemGuide.DYE, ItemGuide.JEWELRY_BASE, ItemGuide.COATING,
                ItemGuide.CITIZEN_EGG, ItemGuide.SILKWORM_EGG)) {
            assertFalse(guide.hasSummary(), guide.name());
            assertFalse(guide.hasDetails(), guide.name());
            assertFalse(guide.hasPonder(), guide.name());
        }
        assertEquals("プレイヤーや市民が着ることが出来る衣服。",
                language("ja_jp").get("guide.createapparel.garment.tooltip.summary").getAsString());
    }

    @Test
    void showcasedRecipesKeepTheirDocumentedInputs() throws IOException {
        Path recipes = Path.of("src/generated/resources/data/createapparel/recipes");
        var shirt = JsonParser.parseString(Files.readString(recipes.resolve("t_shirt.json"))).getAsJsonObject();
        assertEquals(3, shirt.getAsJsonObject("fabric").get("count").getAsInt());
        assertEquals(1, shirt.getAsJsonObject("thread").get("count").getAsInt());
        for (String name : List.of("dyeing/blue_cotton_fabric", "dyeing/navy_cotton_fabric", "decolorizing/cotton_fabric")) {
            var recipe = JsonParser.parseString(Files.readString(recipes.resolve("mixing/" + name + ".json"))).getAsJsonObject();
            assertEquals("create:mixing", recipe.get("type").getAsString());
            assertFalse(recipe.has("heatRequirement"));
            var water = recipe.getAsJsonArray("ingredients").asList().stream()
                    .filter(element -> element.isJsonObject())
                    .map(element -> element.getAsJsonObject()).filter(element -> element.has("fluid")).findFirst().orElseThrow();
            assertEquals("minecraft:water", water.get("fluid").getAsString());
            assertEquals(250, water.get("amount").getAsInt());
        }
    }
}
