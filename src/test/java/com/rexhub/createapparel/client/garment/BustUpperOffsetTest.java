package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BustUpperOffsetTest {
    @Test
    void topBustAndFrontDecorationFollowUpperPlaneException() throws IOException {
        for (String name : List.of("t_shirt/bust", "shirt/bust", "shirt/bust_opened", "blouse/bust", "hoodie/bust")) {
            for (var element : part(name).elements()) {
                float expected = 23.4633F;
                assertEquals(expected, element.to().y(), 0.00001F, name + "/" + element.name());
                assertFalse(element.faces().containsKey(Face.TOP));
                assertEquals(30, element.rotation().angle());
            }
        }
    }

    @Test
    void outerBustAndCollarsFollowUpperPlaneWithoutMovingPlacket() throws IOException {
        for (var element : part("shirt_outer/bust").elements()) {
            float expected = element.name().contains("_placket") ? 21.5F : 23.3426F;
            assertEquals(expected, element.to().y(), 0.00001F, element.name());
            assertFalse(element.faces().containsKey(Face.TOP));
            assertEquals(30, element.rotation().angle());
        }
    }

    private static GarmentPartModel part(String name) throws IOException {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.fromNamespaceAndPath("createapparel", name),
                JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/assets/createapparel/garment_models/parts/" + name + ".json"))).getAsJsonObject());
    }
}
