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

import static com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face.*;
import static org.junit.jupiter.api.Assertions.*;

class TightsAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");

    @Test
    void legwearDefinitionSelectsThreePartsForAllProfiles() throws IOException {
        var definition = json(Path.of("src/main/resources/data/createapparel/garments/tights.json"));
        assertEquals("legwear", definition.get("default_slot").getAsString());
        assertEquals(JsonParser.parseString("[\"legwear\"]"), definition.get("allowed_slots"));
        assertEquals(JsonParser.parseString("[\"plain\"]"), definition.get("designs"));
        assertEquals(0, definition.getAsJsonObject("styles").size());
        assertEquals("createapparel:tights", definition.get("model_template").getAsString());
        assertEquals("default", definition.get("model_variant").getAsString());
        assertEquals("default", definition.get("texture_variant").getAsString());
        var template = json(ASSETS.resolve("garment_models/templates/tights.json")).getAsJsonObject("default");
        for (var arms : ArmProfile.values()) {
            for (var chest : ChestProfile.values()) {
                assertEquals(List.of("torso", "left_leg", "right_leg"),
                        GarmentPartSelection.select(Map.of(), arms, chest, template.keySet()));
            }
        }
        for (String name : template.keySet()) {
            assertEquals("createapparel:tights/" + name, template.getAsJsonObject(name).get("model").getAsString());
        }
    }

    @Test
    void geometryUsesMinimalLegwearOffsetAndOpaquePixelPerfectFaces() throws IOException {
        for (String name : List.of("torso", "left_leg", "right_leg")) {
            boolean torso = name.equals("torso");
            var part = ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:tights/" + name),
                    json(ASSETS.resolve("garment_models/parts/tights/" + name + ".json")));
            assertEquals(1, part.elements().size());
            var element = part.elements().get(0);
            assertEquals(torso ? -4.003 : name.equals("left_leg") ? -0.003 : -4.003, element.from().x(), 0.00001);
            assertEquals(torso || name.equals("left_leg") ? 4.003 : 0.003, element.to().x(), 0.00001);
            assertEquals(torso ? 11.997 : -0.003, element.from().y(), 0.00001);
            assertEquals(torso ? 14.003 : 12.003, element.to().y(), 0.00001);
            assertEquals(-2.003, element.from().z(), 0.00001);
            assertEquals(2.003, element.to().z(), 0.00001);
            assertEquals(!torso, element.faces().containsKey(TOP));
            assertTrue(element.faces().containsKey(BOTTOM));
            assertEquals(torso ? 5 : 6, element.faces().size());
            var texture = part.variants().get("default");
            assertTrue(texture.overlay().isEmpty());
            assertTrue(texture.brightnessMask().isEmpty());
            assertTrue(part.designs().isEmpty());
            var base = ImageIO.read(ASSETS.resolve("textures/" + texture.base().getPath() + ".png").toFile());
            assertEquals(torso ? 32 : 16, base.getWidth());
            assertEquals(base.getWidth(), base.getHeight());
            assertEquals(part.textureWidth(), base.getWidth());
            boolean[][] used = new boolean[base.getWidth()][base.getHeight()];
            for (var entry : element.faces().entrySet()) {
                var face = entry.getKey();
                var uv = entry.getValue();
                boolean cap = face == TOP || face == BOTTOM;
                assertEquals(torso && face != LEFT && face != RIGHT ? 8 : 4, uv.u1() - uv.u0());
                assertEquals(cap ? 4 : torso ? 2 : 12, uv.v1() - uv.v0());
                assertTrue(uv.u0() >= 0 && uv.v0() >= 0 && uv.u1() <= base.getWidth() && uv.v1() <= base.getHeight());
                for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                    for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                        used[x][y] = true;
                    }
                }
            }
            var shades = new HashSet<Integer>();
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < base.getWidth(); x++) {
                    int pixel = base.getRGB(x, y);
                    assertEquals(used[x][y] ? 255 : 0, pixel >>> 24);
                    if (used[x][y]) {
                        assertEquals(pixel & 255, pixel >> 8 & 255);
                        assertEquals(pixel & 255, pixel >> 16 & 255);
                        shades.add(pixel & 255);
                    }
                }
            }
            assertTrue(shades.size() > 1);
        }
    }

    @Test
    void recipeUsesTwoFabricAndOneThread() throws IOException {
        var recipe = json(Path.of("src/generated/resources/data/createapparel/recipes/tights.json"));
        assertEquals(2, recipe.getAsJsonObject("fabric").get("count").getAsInt());
        assertEquals(JsonParser.parseString("[\"createapparel:fabrics/cotton\",\"createapparel:fabrics/wool\",\"createapparel:fabrics/synthetic\"]"),
                recipe.getAsJsonObject("fabric").get("tag"));
        assertEquals(1, recipe.getAsJsonObject("thread").get("count").getAsInt());
        assertEquals("createapparel:tights", recipe.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void defaultUnderwearIsStrictlyContainedByEachMatchingTightsPart() throws IOException {
        for (String name : List.of("torso", "left_leg", "right_leg")) {
            var tights = ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:tights/" + name),
                    json(ASSETS.resolve("garment_models/parts/tights/" + name + ".json"))).elements().get(0);
            var underwear = ClientGarmentAssetLoader.parsePart(
                    ResourceLocation.parse("createapparel:default_underwear_bottom/bottom_" + name),
                    json(ASSETS.resolve("garment_models/parts/default_underwear_bottom/bottom_" + name + ".json")))
                    .elements().get(0);
            assertEquals(-2.0025, underwear.from().z(), 0.000001);
            assertEquals(2.0025, underwear.to().z(), 0.000001);
            assertTrue(underwear.from().x() > tights.from().x(), name);
            assertTrue(underwear.from().y() > tights.from().y(), name);
            assertTrue(underwear.from().z() > tights.from().z(), name);
            assertTrue(underwear.to().x() < tights.to().x(), name);
            assertTrue(underwear.to().y() < tights.to().y(), name);
            assertTrue(underwear.to().z() < tights.to().z(), name);
            assertTrue(underwear.faces().containsKey(FRONT));
            assertTrue(underwear.faces().containsKey(BACK));
            for (String garment : List.of("denim_pants", "slacks")) {
                var bottom = ClientGarmentAssetLoader.parsePart(
                        ResourceLocation.parse("createapparel:" + garment + "/" + name),
                        json(ASSETS.resolve("garment_models/parts/" + garment + "/" + name + ".json")))
                        .elements().get(0);
                assertTrue(tights.from().x() > bottom.from().x(), garment + "/" + name);
                assertTrue(tights.to().x() < bottom.to().x(), garment + "/" + name);
                assertTrue(tights.from().z() > bottom.from().z(), garment + "/" + name);
                assertTrue(tights.to().z() < bottom.to().z(), garment + "/" + name);
            }
        }
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
