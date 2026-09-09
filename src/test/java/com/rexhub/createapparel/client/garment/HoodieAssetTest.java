package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HoodieAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");

    @Test
    void everyHoodAndBodyProfileResolvesToLoadablePartsAndTextures() throws IOException {
        var template = json(ASSETS.resolve("garment_models/templates/hoodie.json"));
        for (String variant : List.of("classic", "slim")) {
            var mapping = template.getAsJsonObject(variant);
            assertTrue(mapping.keySet().containsAll(GarmentPartSelection.requiredStyleParts(
                    Map.of("hood", List.of("down", "up")), true)));
            for (ArmProfile arms : ArmProfile.values()) {
                for (ChestProfile chest : ChestProfile.values()) {
                    for (String hood : List.of("down", "up")) {
                        for (String semantic : GarmentPartSelection.select(Map.of("hood", hood), arms, chest, true)) {
                            var id = ResourceLocation.parse(mapping.getAsJsonObject(semantic).get("model").getAsString());
                            var part = ClientGarmentAssetLoader.parsePart(id,
                                    json(ASSETS.resolve("garment_models/parts/" + id.getPath() + ".json")));
                            var texture = part.variants().get("default");
                            assertTrue(texture.overlay().isEmpty());
                            assertTrue(part.designs().isEmpty());
                            var base = ImageIO.read(ASSETS.resolve("textures/" + texture.base().getPath() + ".png").toFile());
                            assertNotNull(base);
                            assertEquals(part.textureWidth(), base.getWidth());
                            assertEquals(base.getWidth(), base.getHeight());
                            assertEquals(0, base.getWidth() & (base.getWidth() - 1));
                            for (int y = 0; y < base.getHeight(); y++) {
                                for (int x = 0; x < base.getWidth(); x++) {
                                    int pixel = base.getRGB(x, y);
                                    assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
                                    assertEquals(pixel & 255, pixel >> 8 & 255);
                                    assertEquals(pixel & 255, pixel >> 16 & 255);
                                }
                            }
                            for (var element : part.elements()) {
                                for (var uv : element.faces().values()) {
                                    assertTrue(uv.u0() >= 0 && uv.v0() >= 0
                                            && uv.u1() <= base.getWidth() && uv.v1() <= base.getHeight());
                                }
                            }
                            if (semantic.contains("sleeve")) {
                                var sleeve = part.elements().get(0);
                                assertEquals(24.005F, sleeve.to().y());
                                assertEquals(12.75F, sleeve.from().y());
                                assertEquals(semantic.startsWith("left"), sleeve.from().x() > 0);
                                assertFalse(sleeve.faces().containsKey(Face.BOTTOM));
                                assertFalse(part.elements().get(1).faces().containsKey(Face.TOP));
                            }
                            if (semantic.equals("hood_down")) {
                                var hoodElement = part.elements().get(0);
                                assertFalse(hoodElement.faces().containsKey(Face.FRONT));
                                assertEquals(7.5F, hoodElement.to().x() - hoodElement.from().x());
                                assertEquals(4.5F, hoodElement.to().y() - hoodElement.from().y());
                                assertEquals(2.5F, hoodElement.to().z() - hoodElement.from().z());
                                assertEquals(2.25F, hoodElement.from().z());
                            }
                            if (semantic.equals("hood_up")) {
                                assertTrue(part.elements().get(0).faces().containsKey(Face.BOTTOM));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void bustDecorationUsesRaisedSurfaceAndAllowsAuthoredCutoutsAndBrightness() throws IOException {
        var part = json(ASSETS.resolve("garment_models/parts/hoodie/bust.json"));
        var elements = part.getAsJsonObject("geometry").getAsJsonArray("elements");
        var body = elements.get(0).getAsJsonObject();
        var decoration = elements.get(1).getAsJsonObject();
        assertEquals(0.15, body.getAsJsonArray("from").get(2).getAsDouble()
                - decoration.getAsJsonArray("from").get(2).getAsDouble(), 0.000001);
        assertEquals(body.get("rotation"), decoration.get("rotation"));
        for (int axis = 0; axis < 2; axis++) {
            assertEquals(body.getAsJsonArray("from").get(axis).getAsDouble() - 0.15,
                    decoration.getAsJsonArray("from").get(axis).getAsDouble(), 0.000001);
            assertEquals(body.getAsJsonArray("to").get(axis).getAsDouble() + (axis == 0 ? 0.15 : 0),
                    decoration.getAsJsonArray("to").get(axis).getAsDouble(), 0.000001);
        }
        assertEquals(Set.of("front", "bottom"), decoration.getAsJsonObject("faces").keySet());
        assertEquals("[3,10,10,14]", decoration.getAsJsonObject("faces")
                .getAsJsonObject("front").get("uv").toString());
        var base = ImageIO.read(ASSETS.resolve("textures/garment/hoodie/bust/base/default.png").toFile());
        var mask = ImageIO.read(ASSETS.resolve("textures/garment/hoodie/bust/masks/brightness/default.png").toFile());
        assertEquals(base.getWidth(), mask.getWidth());
        assertEquals(base.getHeight(), mask.getHeight());
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                int pixel = mask.getRGB(x, y);
                assertEquals(255, pixel >>> 24);
                assertEquals(pixel & 255, pixel >> 8 & 255);
                assertEquals(pixel & 255, pixel >> 16 & 255);
            }
        }
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
