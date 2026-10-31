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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face.*;
import static org.junit.jupiter.api.Assertions.*;

class SocksAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final List<String> VARIANTS = List.of("ankle", "crew", "knee_high", "over_knee", "thigh_high");

    @Test
    void everySockResolvesOnlyLegsForEveryProfile() throws IOException {
        var template = json(ASSETS.resolve("garment_models/templates/socks.json")).getAsJsonObject("default");
        assertEquals(Set.of("left_leg", "right_leg"), template.keySet());
        for (String variant : VARIANTS) {
            var definition = json(Path.of("src/main/resources/data/createapparel/garments/" + variant + "_socks.json"));
            assertEquals("legwear", definition.get("default_slot").getAsString());
            assertEquals(JsonParser.parseString("[\"legwear\"]"), definition.get("allowed_slots"));
            assertEquals("createapparel:socks", definition.get("model_template").getAsString());
            assertEquals("default", definition.get("model_variant").getAsString());
            assertEquals(variant, definition.get("texture_variant").getAsString());
            DesignVariationAssetTest.assertDefinitionDesigns(definition, variant + "_socks");
            assertEquals(0, definition.getAsJsonObject("styles").size());
            for (var arms : ArmProfile.values()) {
                for (var chest : ChestProfile.values()) {
                    assertEquals(List.of("left_leg", "right_leg"),
                            GarmentPartSelection.select(Map.of(), arms, chest, template.keySet()));
                }
            }
            for (String name : template.keySet()) {
                assertEquals("createapparel:socks/" + name, template.getAsJsonObject(name).get("model").getAsString());
            }
        }
    }

    @Test
    void sharedModelsUseMinimalOffsetAndOnlyBottomAndSideFaces() throws IOException {
        for (String side : List.of("left", "right")) {
            var part = part(side);
            assertEquals(16, part.textureWidth());
            assertEquals(16, part.textureHeight());
            assertEquals(1, part.elements().size());
            var expectedVariants = new HashSet<>(VARIANTS);
            expectedVariants.add("default");
            assertEquals(expectedVariants, part.variants().keySet());
            assertEquals(part.variants().get("ankle"), part.variants().get("default"));
            assertTrue(part.designs().isEmpty());
            var element = part.elements().get(0);
            assertEquals(side.equals("left") ? -0.003 : -4.003, element.from().x(), 0.00001);
            assertEquals(side.equals("left") ? 4.003 : 0.003, element.to().x(), 0.00001);
            assertEquals(-0.003, element.from().y(), 0.00001);
            assertEquals(12.003, element.to().y(), 0.00001);
            assertEquals(-2.003, element.from().z(), 0.00001);
            assertEquals(2.003, element.to().z(), 0.00001);
            assertEquals(Set.of(BOTTOM, LEFT, FRONT, RIGHT, BACK), element.faces().keySet());
            for (var face : element.faces().entrySet()) {
                assertEquals(4, face.getValue().u1() - face.getValue().u0());
                assertEquals(face.getKey() == BOTTOM ? 4 : 12, face.getValue().v1() - face.getValue().v0());
            }
        }
    }

    @Test
    void texturesHaveExactLengthsOpaqueSolesAndMirroredPlainCloth() throws IOException {
        for (int index = 0; index < VARIANTS.size(); index++) {
            String variant = VARIANTS.get(index);
            int height = 2 * (index + 1);
            var left = ImageIO.read(ASSETS.resolve("textures/garment/socks/left_leg/base/" + variant + ".png").toFile());
            var right = ImageIO.read(ASSETS.resolve("textures/garment/socks/right_leg/base/" + variant + ".png").toFile());
            for (String side : List.of("left", "right")) {
                var texture = part(side).variants().get(variant);
                assertEquals("garment/socks/" + side + "_leg/base/" + variant, texture.base().getPath());
                assertTrue(texture.overlay().isEmpty());
                assertTrue(texture.brightnessMask().isEmpty());
            }
            for (var image : List.of(left, right)) {
                assertEquals(16, image.getWidth());
                assertEquals(16, image.getHeight());
                var shades = new HashSet<Integer>();
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        boolean sole = y < 4 && x >= 8 && x < 12;
                        boolean opaque = sole || y >= 16 - height;
                        int pixel = image.getRGB(x, y);
                        assertEquals(opaque ? 255 : 0, pixel >>> 24, variant + ": " + x + "," + y);
                        if (opaque) {
                            assertEquals(pixel & 255, pixel >> 8 & 255);
                            assertEquals(pixel & 255, pixel >> 16 & 255);
                            shades.add(pixel & 255);
                        }
                    }
                }
                assertTrue(shades.size() > 1);
            }
            for (int y = 4; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    assertEquals(left.getRGB(x < 12 ? 11 - x : 27 - x, y), right.getRGB(x, y));
                }
            }
            for (int y = 0; y < 4; y++) {
                for (int x = 8; x < 12; x++) {
                    assertEquals(left.getRGB(19 - x, y), right.getRGB(x, y));
                }
            }
        }
    }

    @Test
    void eachRecipeUsesOneFabricAndOneThread() throws IOException {
        for (String variant : VARIANTS) {
            var recipe = json(Path.of("src/generated/resources/data/createapparel/recipe/" + variant + "_socks.json"));
            assertEquals(1, recipe.getAsJsonObject("fabric").get("count").getAsInt());
            assertEquals(JsonParser.parseString("[\"createapparel:fabrics/cotton\",\"createapparel:fabrics/wool\",\"createapparel:fabrics/synthetic\"]"),
                    recipe.getAsJsonObject("fabric").get("tag"));
            assertEquals(1, recipe.getAsJsonObject("thread").get("count").getAsInt());
            assertEquals("createapparel:" + variant + "_socks", recipe.getAsJsonObject("result").get("id").getAsString());
        }
    }

    private static ClientGarmentAssets.GarmentPartModel part(String side) throws IOException {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:socks/" + side + "_leg"),
                json(ASSETS.resolve("garment_models/parts/socks/" + side + "_leg.json")));
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
