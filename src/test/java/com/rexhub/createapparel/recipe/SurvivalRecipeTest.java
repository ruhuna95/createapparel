package com.rexhub.createapparel.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SurvivalRecipeTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/createapparel/recipe");

    @Test
    void productionEquipmentAndPatternHaveCraftingRecipesAndUnlocks() throws IOException {
        for (String item : List.of("spinning_machine", "loom", "sewing_machine", "sewing_pattern", "wardrobe")) {
            var recipe = json(RECIPES.resolve(item + ".json"));
            assertTrue(Set.of("minecraft:crafting_shaped", "minecraft:crafting_shapeless")
                    .contains(recipe.get("type").getAsString()), item);
            assertEquals("createapparel:" + item, recipe.getAsJsonObject("result").get("id").getAsString());
            String category = item.equals("wardrobe") ? "decorations" : "misc";
            var advancement = json(Path.of("src/generated/resources/data/createapparel/advancement/recipes/"
                    + category + "/" + item + ".json"));
            assertTrue(advancement.getAsJsonObject("criteria").size() >= 2, item);
            assertEquals("createapparel:" + item, advancement.getAsJsonObject("rewards")
                    .getAsJsonArray("recipes").get(0).getAsString());
        }
    }

    @Test
    void clothingRackUsesIronIngotsAndSheetsInRequestedPositions() throws IOException {
        shapedRecipe("clothing_rack", List.of("III", "S S", "I I"),
                Map.of("I", "minecraft:iron_ingot", "S", "create:iron_sheet"));
    }

    @Test
    void sewingMachineUsesPoleCasingAndWoodenSlabs() throws IOException {
        shapedRecipe("sewing_machine", List.of(" IP", "SAN", "HHH"),
                Map.of("I", "create:iron_sheet", "P", "create:piston_extension_pole", "S", "create:shaft",
                        "A", "create:andesite_casing", "N", "minecraft:iron_nugget", "H", "#minecraft:wooden_slabs"));
        assertCasingUnlock("sewing_machine");
    }

    @Test
    void mirrorBlockUsesSixPanelsAndOneGlass() throws IOException {
        shapedRecipe("mirror_block", List.of("P P", "PGP", "P P"),
                Map.of("P", "createapparel:mirror_panel", "G", "minecraft:glass"));
    }

    @Test
    void rearingTrayUsesFiveWoodenSlabsTwoShaftsAndCasing() throws IOException {
        shapedRecipe("silkworm_rearing_tray", List.of("HHH", "SAS", "H H"),
                Map.of("H", "#minecraft:wooden_slabs", "S", "create:shaft", "A", "create:andesite_casing"));
        assertCasingUnlock("silkworm_rearing_tray");
    }

    @Test
    void tableMirrorsUseOnePanelAboveOneMatchingBlock() throws IOException {
        for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
                "cherry", "bamboo", "crimson", "warped")) {
            shapedRecipe(wood + "_table_mirror", List.of("P", "B"),
                    Map.of("P", "createapparel:mirror_panel", "B", "minecraft:" + wood + "_planks"));
        }
        shapedRecipe("iron_table_mirror", List.of("P", "B"),
                Map.of("P", "createapparel:mirror_panel", "B", "minecraft:iron_block"));
    }

    @Test
    void clipFurnitureConversionsAreReciprocalSingleItemCraftsWithUnlocks() throws IOException {
        for (String furniture : List.of("wall_hanger", "clothing_rack")) {
            for (boolean toClip : List.of(true, false)) {
                String source = toClip ? furniture : "clip_" + furniture;
                String result = toClip ? "clip_" + furniture : furniture;
                String name = toClip ? result : result + "_from_" + source;
                var recipe = json(RECIPES.resolve(name + ".json"));
                assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
                var ingredients = recipe.getAsJsonArray("ingredients");
                assertEquals(1, ingredients.size());
                assertEquals("createapparel:" + source, ingredients.get(0).getAsJsonObject().get("item").getAsString());
                assertEquals("createapparel:" + result, recipe.getAsJsonObject("result").get("id").getAsString());
                assertEquals(1, recipe.getAsJsonObject("result").has("count")
                        ? recipe.getAsJsonObject("result").get("count").getAsInt() : 1);
                var advancement = json(Path.of("src/generated/resources/data/createapparel/advancement/recipes/decorations/"
                        + name + ".json"));
                assertTrue(advancement.getAsJsonObject("criteria").has("has_" + source));
                assertEquals("createapparel:" + name, advancement.getAsJsonObject("rewards")
                        .getAsJsonArray("recipes").get(0).getAsString());
            }
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
    void revisedStandardRecipesUseRequestedCountsAndMaterials() throws IOException {
        counts("denim_pants", 5, 2);
        counts("necktie", 1, 1);
        counts("sneakers", 2, 1);
        counts("slippers", 2, 1);
        counts("cap", 1, 1);
        counts("canvas_sneakers", 2, 1);
        assertEquals(Set.of("cotton", "hemp", "synthetic"), materials("denim_pants"));
        assertEquals(Set.of("cotton", "wool", "silk", "synthetic"), materials("necktie"));
        assertEquals(Set.of("cotton", "hemp", "leather", "synthetic"), materials("sneakers"));
        assertEquals(Set.of("cotton", "wool", "leather", "synthetic"), materials("slippers"));
        assertEquals(Set.of("cotton", "linen", "hemp", "wool", "synthetic"), materials("cap"));
        assertEquals(Set.of("cotton", "hemp", "synthetic"), materials("canvas_sneakers"));
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

    private static void shapedRecipe(String name, List<String> pattern, Map<String, String> items) throws IOException {
        var recipe = json(RECIPES.resolve(name + ".json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals(pattern, recipe.getAsJsonArray("pattern").asList().stream()
                .map(value -> value.getAsString()).toList());
        var key = recipe.getAsJsonObject("key");
        assertEquals(items.keySet(), key.keySet());
        items.forEach((symbol, item) -> assertEquals(item.startsWith("#") ? item.substring(1) : item,
                key.getAsJsonObject(symbol).get(item.startsWith("#") ? "tag" : "item").getAsString()));
        var result = recipe.getAsJsonObject("result");
        assertEquals("createapparel:" + name, result.get("id").getAsString());
        assertEquals(1, result.has("count") ? result.get("count").getAsInt() : 1);
    }

    private static void assertCasingUnlock(String name) throws IOException {
        var advancement = json(Path.of("src/generated/resources/data/createapparel/advancement/recipes/misc/"
                + name + ".json"));
        var criteria = advancement.getAsJsonObject("criteria");
        assertFalse(criteria.has("has_andesite_alloy"));
        assertEquals("create:andesite_casing", criteria.getAsJsonObject("has_andesite_casing")
                .getAsJsonObject("conditions").getAsJsonArray("items").get(0).getAsJsonObject()
                .get("items").getAsString());
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
