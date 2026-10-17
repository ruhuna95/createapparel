package com.rexhub.createapparel.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OptionalIntegrationRecipeDataTest {
    @Test void syntheticFallbackUsesTheSpecifiedHeatedMixture() throws Exception {
        var recipe = read("mixing/synthetic_fiber");
        assertEquals("create:mixing", recipe.get("type").getAsString());
        assertEquals("heated", recipe.get("heatRequirement").getAsString());
        assertEquals("createapparel:synthetic_fiber_fallback", condition(recipe));
        var inputs = recipe.getAsJsonArray("ingredients");
        assertEquals(3, inputs.size());
        assertEquals("minecraft:sugar", inputs.get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("minecraft:slime_ball", inputs.get(1).getAsJsonObject().get("item").getAsString());
        assertEquals("minecraft:water", inputs.get(2).getAsJsonObject().get("fluid").getAsString());
        assertEquals(250, inputs.get(2).getAsJsonObject().get("amount").getAsInt());
        assertEquals("createapparel:synthetic_fiber", recipe.getAsJsonArray("results").get(0).getAsJsonObject().get("item").getAsString());
    }

    @Test void nylonUsesTheExactOptionalItemAndItsDestroyCondition() throws Exception {
        var recipe = read("synthetic_fiber_from_nylon");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals("createapparel:synthetic_fiber_destroy", condition(recipe));
        assertEquals(1, recipe.getAsJsonArray("ingredients").size());
        assertEquals("destroy:nylon", recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("createapparel:synthetic_fiber", recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(2, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test void mirrorPreservesItsExistingRecipeAndConditionIds() throws Exception {
        var recipe = read("mixing/mirror_coating_fluid");
        assertEquals("createapparel:mirror_coating_fallback", condition(recipe));
        assertEquals("heated", recipe.get("heatRequirement").getAsString());
        assertEquals(1000, recipe.getAsJsonArray("ingredients").get(2).getAsJsonObject().get("amount").getAsInt());
        assertEquals(1000, recipe.getAsJsonArray("results").get(0).getAsJsonObject().get("amount").getAsInt());
        assertEquals(250, read("filling/mirror_panel").getAsJsonArray("ingredients").get(1).getAsJsonObject().get("amount").getAsInt());
    }

    @Test void destroyMirrorRequiresNonEmptySilverAndTheApprovedMixture() throws Exception {
        var recipe = read("mixing/mirror_coating_fluid_from_destroy");
        assertEquals("create:mixing", recipe.get("type").getAsString());
        var conditions = recipe.getAsJsonArray("conditions");
        assertEquals(2, conditions.size());
        assertEquals("createapparel:mirror_coating_destroy", conditions.get(0).getAsJsonObject().get("type").getAsString());
        var nonEmpty = conditions.get(1).getAsJsonObject();
        assertEquals("forge:not", nonEmpty.get("type").getAsString());
        assertEquals("forge:tag_empty", nonEmpty.getAsJsonObject("value").get("type").getAsString());
        assertEquals("forge:ingots/silver", nonEmpty.getAsJsonObject("value").get("tag").getAsString());
        var ingredients = recipe.getAsJsonArray("ingredients");
        assertEquals(2, ingredients.size());
        var mixture = ingredients.get(0).getAsJsonObject();
        assertEquals("destroy:mixture", mixture.get("createapparel:mirror_mixture").getAsString());
        assertEquals("createapparel:diamminesilver_i", mixture.get("molecule").getAsString());
        assertEquals(1f, mixture.get("concentration").getAsFloat());
        assertEquals(900, mixture.get("amount").getAsInt());
        assertEquals("create:honey", ingredients.get(1).getAsJsonObject().get("fluid").getAsString());
        assertEquals(100, ingredients.get(1).getAsJsonObject().get("amount").getAsInt());
        assertEquals("createapparel:mirror_coating_fluid", recipe.getAsJsonArray("results").get(0).getAsJsonObject().get("fluid").getAsString());
        assertEquals(1000, recipe.getAsJsonArray("results").get(0).getAsJsonObject().get("amount").getAsInt());
    }

    private static String condition(JsonObject json) {
        assertEquals(1, json.getAsJsonArray("conditions").size());
        return json.getAsJsonArray("conditions").get(0).getAsJsonObject().get("type").getAsString();
    }

    private static JsonObject read(String path) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/data/createapparel/recipes/" + path + ".json"))).getAsJsonObject();
    }
}
