package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face.*;
import static org.junit.jupiter.api.Assertions.*;

class SlacksAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");

    @Test
    void definitionResolvesAllPartsAndTexturesForEveryProfile() throws IOException {
        var definition = json(Path.of("src/main/resources/data/createapparel/garments/slacks.json"));
        var templateId = ResourceLocation.parse(definition.get("model_template").getAsString());
        var template = json(ASSETS.resolve("garment_models/templates/" + templateId.getPath() + ".json"))
                .getAsJsonObject(definition.get("model_variant").getAsString());
        for (ArmProfile arms : ArmProfile.values()) {
            for (ChestProfile chest : ChestProfile.values()) {
                var selected = GarmentPartSelection.select(Map.of(), arms, chest, template.keySet());
                assertEquals(List.of("torso", "left_leg", "right_leg"), selected);
                for (String name : selected) {
                    var id = ResourceLocation.parse(template.getAsJsonObject(name).get("model").getAsString());
                    assertTrue(id.getPath().startsWith("slacks/"));
                    var part = ClientGarmentAssetLoader.parsePart(id,
                            json(ASSETS.resolve("garment_models/parts/" + id.getPath() + ".json")));
                    var texture = part.variants().get(definition.get("texture_variant").getAsString());
                    assertNotNull(texture);
                    assertTrue(texture.overlay().isEmpty());
                    assertTrue(texture.brightnessMask().isEmpty());
                    assertTrue(part.designs().isEmpty());
                    var base = ImageIO.read(ASSETS.resolve("textures/" + texture.base().getPath() + ".png").toFile());
                    assertEquals(part.textureWidth(), base.getWidth());
                    assertEquals(part.textureHeight(), base.getHeight());
                    var body = part.elements().get(0);
                    assertEquals(1, part.elements().size());
                    assertFalse(body.faces().containsKey(name.equals("torso") ? TOP : BOTTOM));
                    for (var uv : body.faces().values()) {
                        assertTrue(uv.u0() >= 0 && uv.v0() >= 0);
                        assertTrue(uv.u1() <= base.getWidth() && uv.v1() <= base.getHeight());
                        for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                            for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                                int pixel = base.getRGB(x, y);
                                assertEquals(255, pixel >>> 24, name + " has a hole in its visible surface");
                                assertEquals(pixel & 255, pixel >> 8 & 255);
                                assertEquals(pixel & 255, pixel >> 16 & 255);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void legCreasesAndClothAreMirroredWithoutIntroducingAnInnerSeam() throws IOException {
        var left = ImageIO.read(ASSETS.resolve("textures/garment/slacks/left_leg/base/default.png").toFile());
        var right = ImageIO.read(ASSETS.resolve("textures/garment/slacks/right_leg/base/default.png").toFile());
        for (int y = 4; y < 14; y++) {
            for (int x = 4; x < 8; x++) {
                assertEquals(left.getRGB(x, y), right.getRGB(11 - x, y));
            }
            assertTrue((left.getRGB(5, y) & 255) > (left.getRGB(4, y) & 255));
            assertTrue((left.getRGB(5, y) & 255) > (left.getRGB(6, y) & 255));
            for (int x = 0; x < 4; x++) {
                assertEquals(left.getRGB(x, y), right.getRGB(11 - x, y));
            }
        }
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
