package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShirtOuterAssetTest {
    @Test
    void everyWearContextResolvesAndOuterFrontStaysOpenForAllStyles() throws IOException {
        var template = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/createapparel/garment_models/templates/shirt.json"))).getAsJsonObject();
        for (String name : List.of("shirt_shortsleeves", "shirt_longsleeves")) {
            var definition = GarmentDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("createapparel", name),
                    JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/createapparel/garments/" + name + ".json"))).getAsJsonObject());
            for (GarmentSlot slot : definition.allowedSlots()) {
                var variant = template.getAsJsonObject(definition.modelVariantFor(slot));
                assertNotNull(variant);
                for (var arm : ArmProfile.values()) {
                    for (var chest : ChestProfile.values()) {
                        for (String tuck : List.of("untucked", "tucked")) {
                            for (String open : List.of("closed", "opened")) {
                                for (String sleeves : definition.styles().getOrDefault("sleeves", List.of("normal"))) {
                                    var styles = new EquippedStyleState(Map.of("tuck", tuck, "open", open, "sleeves", sleeves))
                                            .normalize(definition, slot).styles();
                                    for (String semantic : GarmentPartSelection.select(styles, arm, chest, true)) {
                                        var id = ResourceLocation.parse(variant.getAsJsonObject(semantic).get("model").getAsString());
                                        var model = ClientGarmentAssetLoader.parsePart(id, JsonParser.parseString(Files.readString(Path.of(
                                                "src/main/resources/assets/createapparel/garment_models/parts/" + id.getPath() + ".json"))).getAsJsonObject());
                                        if (slot == GarmentSlot.OUTER && !semantic.contains("sleeve")) {
                                            assertTrue(id.getPath().startsWith("shirt_outer/"));
                                            for (var element : model.elements()) {
                                                if (element.faces().containsKey(Face.FRONT)) {
                                                    assertTrue(element.to().x() <= -1.5 || element.from().x() >= 1.5,
                                                            id + "/" + element.name());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void outerPanelsCollarsAndPlacketsUseWholeTexturePixels() throws IOException {
        for (String part : List.of("torso", "bust")) {
            var json = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/createapparel/garment_models/parts/shirt_outer/" + part + ".json"))).getAsJsonObject();
            var id = ResourceLocation.fromNamespaceAndPath("createapparel", "shirt_outer/" + part);
            var model = ClientGarmentAssetLoader.parsePart(id, json);
            for (var element : json.getAsJsonObject("geometry").getAsJsonArray("elements")) {
                for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    for (var coordinate : face.getValue().getAsJsonObject().getAsJsonArray("uv")) {
                        assertEquals(Math.rint(coordinate.getAsDouble()), coordinate.getAsDouble(), id.toString());
                    }
                }
            }
            for (var element : model.elements()) {
                if (element.name().endsWith("_open") || element.name().endsWith("_collar")) {
                    var uv = element.faces().get(Face.FRONT);
                    assertEquals(2, uv.u1() - uv.u0(), 0.00001, element.name());
                    float outerOffset = part.equals("torso") ? 0.01F : 0.5F;
                    assertEquals(2 + outerOffset, element.to().x() - element.from().x(), 0.00001, element.name());
                }
                if (element.name().endsWith("_placket")) {
                    assertEquals(part.equals("torso") ? 2 : 1.5, element.from().x(), 0.00001);
                    assertEquals(1, element.to().x() - element.from().x(), 0.00001);
                    assertEquals(1, element.faces().get(Face.FRONT).u1() - element.faces().get(Face.FRONT).u0(), 0.00001);
                }
            }
        }
    }
}
