package com.rexhub.createapparel.recipe;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GarmentFabricRecipeTest {
    @Test
    void everyGarmentHasARecipeAcceptingSyntheticExactlyOnce() throws IOException {
        Path definitions = Path.of("src/main/resources/data/createapparel/garments");
        Path recipes = Path.of("src/generated/resources/data/createapparel/recipes");
        try (var paths = Files.list(definitions)) {
            var garments = paths.filter(path -> path.toString().endsWith(".json")).toList();
            assertFalse(garments.isEmpty());
            for (Path garment : garments) {
                var recipe = JsonParser.parseString(Files.readString(recipes.resolve(garment.getFileName())))
                        .getAsJsonObject();
                assertEquals("createapparel:sewing", recipe.get("type").getAsString(), garment.toString());
                int syntheticCount = 0;
                for (var tag : recipe.getAsJsonObject("fabric").getAsJsonArray("tag")) {
                    if (tag.getAsString().equals("createapparel:fabrics/synthetic")) {
                        syntheticCount++;
                    }
                }
                assertEquals(1, syntheticCount, garment.toString());
            }
        }
    }
}
