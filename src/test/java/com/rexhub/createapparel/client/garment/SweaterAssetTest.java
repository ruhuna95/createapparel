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

import static org.junit.jupiter.api.Assertions.*;

class SweaterAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");

    @Test
    void bothGarmentsResolveForAllBodyProfiles() throws IOException {
        var template = json(ASSETS.resolve("garment_models/templates/sweater.json"));
        for (String garment : List.of("sweater", "v_neck_sweater")) {
            var definition = json(Path.of("src/main/resources/data/createapparel/garments/" + garment + ".json"));
            assertEquals("[\"top\"]", definition.get("allowed_slots").toString());
            assertEquals(0, definition.getAsJsonObject("styles").size());
            String variant = definition.get("texture_variant").getAsString();
            for (String modelVariant : List.of("classic", "slim")) {
                for (ArmProfile arms : ArmProfile.values()) {
                    for (ChestProfile chest : ChestProfile.values()) {
                        for (String semantic : GarmentPartSelection.select(Map.of(), arms, chest, true)) {
                            var reference = template.getAsJsonObject(modelVariant).getAsJsonObject(semantic);
                            var id = ResourceLocation.parse(reference.get("model").getAsString());
                            var part = ClientGarmentAssetLoader.parsePart(id,
                                    json(ASSETS.resolve("garment_models/parts/" + id.getPath() + ".json")));
                            var texture = part.variants().get(variant);
                            assertNotNull(texture);
                            assertTrue(texture.overlay().isEmpty());
                            assertTrue(texture.brightnessMask().isEmpty());
                            var image = ImageIO.read(ASSETS.resolve("textures/" + texture.base().getPath() + ".png").toFile());
                            assertEquals(part.textureWidth(), image.getWidth());
                            assertEquals(part.textureHeight(), image.getHeight());
                            for (int y = 0; y < image.getHeight(); y++) {
                                for (int x = 0; x < image.getWidth(); x++) {
                                    int pixel = image.getRGB(x, y);
                                    assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
                                    assertEquals(pixel & 255, pixel >> 8 & 255);
                                    assertEquals(pixel & 255, pixel >> 16 & 255);
                                }
                            }
                            for (var element : part.elements()) {
                                for (var uv : element.faces().values()) {
                                    assertTrue(uv.u0() >= 0 && uv.v0() >= 0
                                            && uv.u1() <= image.getWidth() && uv.v1() <= image.getHeight());
                                }
                            }
                            if (semantic.contains("sleeve")) {
                                assertEquals(semantic.startsWith("left"), part.elements().get(0).from().x() > 0);
                                assertFalse(part.elements().get(0).faces().containsKey(ClientGarmentAssets.Face.BOTTOM));
                                var inner = semantic.startsWith("left") ? ClientGarmentAssets.Face.LEFT : ClientGarmentAssets.Face.RIGHT;
                                var uv = part.elements().get(1).faces().get(inner);
                                assertNotNull(uv);
                                for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                                    assertEquals(255, image.getRGB(x, (int) uv.v1() - 1) >>> 24);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void sharedSecondaryMasksAreGrayscaleAndCoverVisibleVNeckPixels() throws IOException {
        for (String part : List.of("torso", "bust")) {
            Path textures = ASSETS.resolve("textures/garment/sweater/" + part);
            var vneck = ImageIO.read(textures.resolve("base/v_neck.png").toFile());
            var mask = ImageIO.read(textures.resolve("masks/plain/secondary.png").toFile());
            int shirtPixels = 0;
            for (int y = 0; y < mask.getHeight(); y++) {
                for (int x = 0; x < mask.getWidth(); x++) {
                    int pixel = mask.getRGB(x, y);
                    assertEquals(255, pixel >>> 24);
                    assertEquals(pixel & 255, pixel >> 8 & 255);
                    assertEquals(pixel & 255, pixel >> 16 & 255);
                    if ((pixel & 255) != 0) {
                        if (vneck.getRGB(x, y) >>> 24 == 255) shirtPixels++;
                    }
                }
            }
            assertTrue(shirtPixels >= 8);
        }
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
