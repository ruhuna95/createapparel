package com.rexhub.createapparel.recipe;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class GarmentFabricRecipeTest {
    @Test
    void everyGarmentHasARecipeWithExplicitNonEmptyUniqueFabricTags() throws IOException {
        Path definitions = Path.of("src/main/resources/data/createapparel/garments");
        Path recipes = Path.of("src/generated/resources/data/createapparel/recipes");
        try (var paths = Files.list(definitions)) {
            var garments = paths.filter(path -> path.toString().endsWith(".json")).toList();
            assertFalse(garments.isEmpty());
            for (Path garment : garments) {
                var recipe = JsonParser.parseString(Files.readString(recipes.resolve(garment.getFileName())))
                        .getAsJsonObject();
                assertEquals("createapparel:sewing", recipe.get("type").getAsString(), garment.toString());
                var fabricTags = new HashSet<String>();
                for (var tag : recipe.getAsJsonObject("fabric").getAsJsonArray("tag")) {
                    assertFalse(tag.getAsString().isBlank(), garment.toString());
                    assertTrue(fabricTags.add(tag.getAsString()), garment.toString());
                }
                assertFalse(fabricTags.isEmpty(), garment.toString());
            }
        }
    }
}
