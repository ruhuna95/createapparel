package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
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
                                    for (String semantic : GarmentPartSelection.select(Map.of("tuck", tuck, "open", open, "sleeves", sleeves), arm, chest, true)) {
                                        var id = ResourceLocation.parse(variant.getAsJsonObject(semantic).get("model").getAsString());
                                        var model = ClientGarmentAssetLoader.parsePart(id, JsonParser.parseString(Files.readString(Path.of(
                                                "src/main/resources/assets/createapparel/garment_models/parts/" + id.getPath() + ".json"))).getAsJsonObject());
                                        if (slot == GarmentSlot.OUTER && !semantic.contains("sleeve")) {
                                            assertTrue(id.getPath().startsWith("shirt_outer/"));
                                            for (var element : model.elements()) {
                                                if (element.faces().containsKey(Face.FRONT)) {
                                                    assertTrue(element.to().x() <= -0.5 || element.from().x() >= 0.5,
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
}
