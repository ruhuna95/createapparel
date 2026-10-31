package com.rexhub.createapparel.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class OldClothRecipeTest {
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path MAIN = Path.of("src/main/resources");

    @Test
    void recyclingUsesOneNativeCrushingRecipeWithoutNbtOrChance() throws Exception {
        var recipe = json(GENERATED.resolve("data/createapparel/recipe/crushing/garments_to_old_cloth.json"));
        assertEquals("create:crushing", recipe.get("type").getAsString());
        assertEquals(100, recipe.get("processing_time").getAsInt());
        assertEquals(1, recipe.getAsJsonArray("ingredients").size());
        assertEquals("createapparel:recyclable_garments", recipe.getAsJsonArray("ingredients")
                .get(0).getAsJsonObject().get("tag").getAsString());
        var outputs = recipe.getAsJsonArray("results");
        assertEquals(1, outputs.size());
        var result = outputs.get(0).getAsJsonObject();
        assertEquals(Set.of("id"), result.keySet());
        assertEquals("createapparel:old_cloth", result.get("id").getAsString());
        var tag = json(GENERATED.resolve("data/createapparel/tags/item/recyclable_garments.json"));
        var values = StreamSupport.stream(tag.getAsJsonArray("values").spliterator(), false)
                .map(value -> value.getAsString()).collect(Collectors.toSet());
        assertTrue(values.size() > 100);
        assertTrue(values.containsAll(List.of("createapparel:t_shirt", "createapparel:trench_coat", "createapparel:sneakers")));
        assertFalse(values.contains("createapparel:necklace"));
        assertFalse(values.contains("createapparel:bracelet"));
        assertFalse(values.contains("createapparel:glasses"));
        assertFalse(values.contains("createapparel:sunglasses"));
        assertFalse(values.contains("createapparel:watch"));
        assertFalse(tag.has("replace") && tag.get("replace").getAsBoolean());
    }

    @Test
    void jobSiteTagAddsTailorWithoutReplacingVanillaEntries() throws Exception {
        var tag = json(GENERATED.resolve("data/minecraft/tags/point_of_interest_type/acquirable_job_site.json"));
        assertEquals(1, tag.getAsJsonArray("values").size());
        assertEquals("createapparel:tailor_poi", tag.getAsJsonArray("values").get(0).getAsString());
        assertFalse(tag.has("replace") && tag.get("replace").getAsBoolean());
    }

    @Test
    void itemModelLanguagesAndBothProfessionOverlaysResolve() throws Exception {
        var model = json(GENERATED.resolve("assets/createapparel/models/item/old_cloth.json"));
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("createapparel:item/old_cloth", model.getAsJsonObject("textures").get("layer0").getAsString());
        for (var locale : List.of("en_us", "ja_jp")) {
            var lang = json(GENERATED.resolve("assets/createapparel/lang/" + locale + ".json"));
            assertEquals(locale.equals("ja_jp") ? "古布" : "Old Cloth", lang.get("item.createapparel.old_cloth").getAsString());
            assertEquals(locale.equals("ja_jp") ? "被服職人" : "Tailor", lang.get("entity.minecraft.villager.createapparel.tailor").getAsString());
        }
        var item = ImageIO.read(MAIN.resolve("assets/createapparel/textures/item/old_cloth.png").toFile());
        assertEquals(16, item.getWidth());
        assertEquals(16, item.getHeight());
        for (var entity : List.of("villager", "zombie_villager")) {
            var overlay = ImageIO.read(MAIN.resolve("assets/createapparel/textures/entity/" + entity + "/profession/tailor.png").toFile());
            assertEquals(64, overlay.getWidth());
            assertEquals(64, overlay.getHeight());
            assertTrue(overlay.getColorModel().hasAlpha());
        }
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
