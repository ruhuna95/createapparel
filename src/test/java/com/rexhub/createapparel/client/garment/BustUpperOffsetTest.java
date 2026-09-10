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
    void bustSurfacesUseAbsoluteBodyBaseOffsets() throws IOException {
        var base = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/createapparel/body_models/parts/bust.json")))
                .getAsJsonObject().getAsJsonObject("geometry").getAsJsonArray("elements")
                .get(0).getAsJsonObject();
        var from = base.getAsJsonArray("from");
        var to = base.getAsJsonArray("to");
        for (String name : List.of("t_shirt/bust", "shirt/bust", "shirt/bust_opened", "blouse/bust", "hoodie/bust")) {
            var elements = part(name).elements();
            for (int i = 0; i < elements.size(); i++) {
                var element = elements.get(i);
                float offset = i == 0 ? 0.25F : 0.4F;
                assertEquals(from.get(2).getAsFloat() - offset, element.from().z(), 0.00001F, name);
                assertEquals(from.get(1).getAsFloat() - offset, element.from().y(), 0.00001F, name);
                assertEquals(from.get(0).getAsFloat() - offset, element.from().x(), 0.00001F, name);
                assertEquals(to.get(0).getAsFloat() + offset, element.to().x(), 0.00001F, name);
            }
        }
        var outer = part("shirt_outer/bust").elements();
        for (int i = 0; i < outer.size(); i++) {
            assertEquals(from.get(2).getAsFloat() - (i < 2 ? 0.5F : 0.75F),
                    outer.get(i).from().z(), 0.00001F, outer.get(i).name());
        }
        assertEquals(from.get(0).getAsFloat() - 0.5F, outer.get(0).from().x(), 0.00001F);
        assertEquals(to.get(0).getAsFloat() + 0.5F, outer.get(1).to().x(), 0.00001F);
        assertEquals(from.get(0).getAsFloat() - 0.75F, outer.get(2).from().x(), 0.00001F);
        assertEquals(to.get(0).getAsFloat() + 0.75F, outer.get(3).to().x(), 0.00001F);
    }

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
