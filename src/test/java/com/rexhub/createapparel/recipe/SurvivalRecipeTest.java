package com.rexhub.createapparel.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SurvivalRecipeTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/createapparel/recipes");

    @Test
    void productionEquipmentAndPatternHaveCraftingRecipesAndUnlocks() throws IOException {
        for (String item : List.of("spinning_machine", "loom", "sewing_machine", "sewing_pattern", "wardrobe")) {
            var recipe = json(RECIPES.resolve(item + ".json"));
            assertTrue(Set.of("minecraft:crafting_shaped", "minecraft:crafting_shapeless")
                    .contains(recipe.get("type").getAsString()), item);
            assertEquals("createapparel:" + item, recipe.getAsJsonObject("result").get("item").getAsString());
            String category = item.equals("wardrobe") ? "decorations" : "misc";
            var advancement = json(Path.of("src/generated/resources/data/createapparel/advancements/recipes/"
                    + category + "/" + item + ".json"));
            assertTrue(advancement.getAsJsonObject("criteria").size() >= 2, item);
            assertEquals("createapparel:" + item, advancement.getAsJsonObject("rewards")
                    .getAsJsonArray("recipes").get(0).getAsString());
        }
    }

    @Test
    void representativeGarmentsUseAppropriateFabricAndThreadCounts() throws IOException {
        for (String family : List.of("a_line", "pleated", "flared", "tight")) {
            for (String length : List.of("micro", "mini", "knee", "midi", "long", "maxi")) {
                String name = family + "_skirt_" + length;
                if (!Files.exists(RECIPES.resolve(name + ".json"))) continue;
                int fabrics = switch (length) {
                    case "micro", "mini" -> 2;
                    case "knee" -> 3;
                    case "midi" -> 4;
                    default -> 5;
                };
                counts(name, fabrics, 1);
            }
        }
        counts("trench_coat", 8, 2);
        counts("pullover_hoodie", 5, 2);
        counts("zipup_hoodie", 5, 2);
        counts("sweater", 5, 2);
        counts("t_shirt", 3, 1);
        counts("gloves", 1, 1);
        counts("pumps", 2, 1);
        assertEquals(Set.of("cotton", "linen", "hemp", "wool", "synthetic"), materials("trench_coat"));
        assertEquals(Set.of("cotton", "wool", "leather", "synthetic"), materials("gloves"));
        assertEquals(Set.of("leather", "silk", "synthetic"), materials("pumps"));
    }

    @Test
    void grassSeedChanceIsEightPercent() throws IOException {
        var modifier = json(Path.of("src/generated/resources/data/createapparel/loot_modifiers/grass_seeds.json"));
        assertEquals(0.08, modifier.get("chance").getAsDouble(), 1e-7);
    }

    @Test
    void creativeTabsAndAllMannequinNamesUseConsistentWording() throws IOException {
        var english = json(Path.of("src/generated/resources/assets/createapparel/lang/en_us.json"));
        var japanese = json(Path.of("src/generated/resources/assets/createapparel/lang/ja_jp.json"));
        assertEquals("Create: Apparel - 衣服", japanese.get("itemGroup.createapparel.garment").getAsString());
        assertTrue(english.get("itemGroup.createapparel.garment").getAsString().startsWith("Create: Apparel"));
        for (String color : List.of("white", "black")) {
            for (String form : List.of("full", "upper", "lower", "head")) {
                for (String profile : form.equals("full") || form.equals("upper")
                        ? List.of("classic", "slim") : List.of("")) {
                    String name = form + "_mannequin_" + color + (profile.isEmpty() ? "" : "_" + profile);
                    String expected = title(color) + " " + (profile.isEmpty() ? "" : title(profile) + " ")
                            + title(form) + " Mannequin";
                    assertEquals(expected, english.get("item.createapparel." + name).getAsString());
                    assertEquals(expected, english.get("entity.createapparel." + name).getAsString());
                }
            }
        }
    }

    private static String title(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static void counts(String name, int fabric, int thread) throws IOException {
        var recipe = json(RECIPES.resolve(name + ".json"));
        assertEquals(fabric, recipe.getAsJsonObject("fabric").get("count").getAsInt(), name);
        assertEquals(thread, recipe.getAsJsonObject("thread").get("count").getAsInt(), name);
    }

    private static Set<String> materials(String name) throws IOException {
        return json(RECIPES.resolve(name + ".json")).getAsJsonObject("fabric").getAsJsonArray("tag")
                .asList().stream().map(value -> value.getAsString().replace("createapparel:fabrics/", ""))
                .collect(Collectors.toSet());
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
