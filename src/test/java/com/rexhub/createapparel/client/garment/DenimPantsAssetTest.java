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
import java.util.Set;

import static com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face.*;
import static org.junit.jupiter.api.Assertions.*;

class DenimPantsAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");

    @Test
    void bottomSelectsTorsoAndBothLegsForEveryBodyProfile() throws IOException {
        var definition = json(Path.of("src/main/resources/data/createapparel/garments/denim_pants.json"));
        assertEquals("bottom", definition.get("default_slot").getAsString());
        assertEquals("[\"bottom\"]", definition.get("allowed_slots").toString());
        assertEquals("[\"plain\"]", definition.get("designs").toString());
        assertEquals(0, definition.getAsJsonObject("styles").size());
        var template = json(ASSETS.resolve("garment_models/templates/denim_pants.json"))
                .getAsJsonObject(definition.get("model_variant").getAsString());
        for (ArmProfile arms : ArmProfile.values()) {
            for (ChestProfile chest : ChestProfile.values()) {
                var selected = GarmentPartSelection.select(Map.of(), arms, chest, template.keySet());
                assertEquals(List.of("torso", "left_leg", "right_leg"), selected);
                for (String name : selected) {
                    var id = ResourceLocation.parse(template.getAsJsonObject(name).get("model").getAsString());
                    var part = ClientGarmentAssetLoader.parsePart(id,
                            json(ASSETS.resolve("garment_models/parts/" + id.getPath() + ".json")));
                    assertTrue(part.variants().containsKey(definition.get("texture_variant").getAsString()));
                    assertTrue(part.designs().isEmpty());
                }
            }
        }
    }

    @Test
    void templateAwareSelectionPreservesStyledTopsAndMissingStyleErrors() {
        for (ArmProfile arms : ArmProfile.values()) {
            for (ChestProfile chest : ChestProfile.values()) {
                var parts = Set.of("torso", "bust", "left_sleeve_classic", "right_sleeve_classic",
                        "left_sleeve_slim", "right_sleeve_slim");
                for (var styles : List.of(Map.<String, String>of(),
                        Map.of("tuck", "tucked", "sleeves", "rolled", "open", "opened"))) {
                    assertEquals(GarmentPartSelection.select(styles, arms, chest, true),
                            GarmentPartSelection.select(styles, arms, chest, parts));
                }
            }
        }
    }

    @Test
    void texturesAndGeometryKeepOpeningsVisibleDecorationAndOuterSeams() throws IOException {
        for (String name : List.of("torso", "left_leg", "right_leg")) {
            var id = ResourceLocation.parse("createapparel:denim_pants/" + name);
            var part = ClientGarmentAssetLoader.parsePart(id,
                    json(ASSETS.resolve("garment_models/parts/denim_pants/" + name + ".json")));
            var texture = part.variants().get("default");
            assertTrue(texture.brightnessMask().isEmpty());
            var base = ImageIO.read(ASSETS.resolve("textures/" + texture.base().getPath() + ".png").toFile());
            var overlay = ImageIO.read(ASSETS.resolve("textures/" + texture.overlay().orElseThrow().getPath() + ".png").toFile());
            assertEquals(part.textureWidth(), base.getWidth());
            assertEquals(part.textureHeight(), base.getHeight());
            assertEquals(base.getWidth(), overlay.getWidth());
            assertEquals(base.getHeight(), overlay.getHeight());
            boolean torso = name.equals("torso");
            int seamPixels = 0;
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < base.getWidth(); x++) {
                    int pixel = base.getRGB(x, y);
                    assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
                    assertEquals(pixel & 255, pixel >> 8 & 255);
                    assertEquals(pixel & 255, pixel >> 16 & 255);
                    int seam = overlay.getRGB(x, y);
                    if (seam >>> 24 != 0) {
                        assertEquals(255, pixel >>> 24);
                        assertTrue(seam >>> 24 < 255);
                        assertTrue((seam >> 16 & 255) > (seam & 255));
                        if (!torso) {
                            assertEquals(name.equals("left_leg") ? 9 : 2, x);
                            assertTrue(y >= 4 && y < 13);
                        }
                        seamPixels++;
                    }
                }
            }
            assertTrue(seamPixels > 0);
            var body = part.elements().get(0);
            assertEquals(torso ? 11.996 : 1.995, body.from().y(), 0.000001);
            assertEquals(torso ? 14.004 : 12.005, body.to().y(), 0.000001);
            assertEquals(torso ? -2.249 : -2.25, body.from().z(), 0.000001);
            assertEquals(torso ? 2.249 : 2.25, body.to().z(), 0.000001);
            assertFalse(body.faces().containsKey(torso ? TOP : BOTTOM));
            if (torso) {
                assertEquals(1, part.elements().size());
                assertEquals(-4.004, body.from().x(), 0.000001);
                assertEquals(4.004, body.to().x(), 0.000001);
                for (int start : List.of(4, 16)) {
                    for (int x = start; x < start + 8; x++) {
                        assertEquals(255, base.getRGB(x, 4) >>> 24);
                        assertEquals(0, base.getRGB(x, 6) >>> 24);
                        assertEquals(0, base.getRGB(x, 7) >>> 24);
                    }
                }
            } else {
                assertEquals(1, part.elements().size());
                assertEquals(name.equals("left_leg") ? -0.005 : -4.005, body.from().x(), 0.000001);
                assertEquals(name.equals("left_leg") ? 4.005 : 0.005, body.to().x(), 0.000001);
                assertTrue(body.faces().containsKey(LEFT));
                assertTrue(body.faces().containsKey(RIGHT));
                assertEquals(name.equals("left_leg"), (body.from().x() + body.to().x()) > 0);
                assertEquals(4.01, body.to().x() - body.from().x(), 0.000001);
                for (int x = 0; x < 16; x++) {
                    assertEquals(255, base.getRGB(x, 13) >>> 24);
                    for (int y = 14; y < 24; y++) {
                        assertEquals(0, base.getRGB(x, y) >>> 24);
                    }
                }
            }
            for (var element : part.elements()) {
                for (var uv : element.faces().values()) {
                    assertTrue(uv.u0() >= 0 && uv.v0() >= 0 && uv.u1() <= base.getWidth() && uv.v1() <= base.getHeight());
                    int visible = 0;
                    for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                        for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                            if (base.getRGB(x, y) >>> 24 == 255) visible++;
                        }
                    }
                    assertTrue(visible > 0);
                }
            }
            if (!torso) {
                assertTrue((base.getRGB(5, 7) & 255) > (base.getRGB(5, 9) & 255));
                assertTrue((base.getRGB(5, 11) & 255) > (base.getRGB(5, 9) & 255));
            }
        }
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test
    void underwearInnerAndOuterLegFacesStayInsideDenim() throws IOException {
        for (String side : List.of("left", "right")) {
            String name = side + "_leg";
            var underwearId = ResourceLocation.parse("createapparel:default_underwear_bottom/bottom_" + name);
            var underwear = ClientGarmentAssetLoader.parsePart(underwearId,
                    json(ASSETS.resolve("garment_models/parts/" + underwearId.getPath() + ".json")))
                    .elements().get(0);
            var pantsId = ResourceLocation.parse("createapparel:denim_pants/" + name);
            var pants = ClientGarmentAssetLoader.parsePart(pantsId,
                    json(ASSETS.resolve("garment_models/parts/" + pantsId.getPath() + ".json")))
                    .elements().get(0);
            if (side.equals("left")) {
                assertEquals(4.0025, underwear.to().x(), 0.000001);
                assertTrue(underwear.to().x() < pants.to().x());
                assertTrue(underwear.faces().containsKey(RIGHT));
                assertEquals(-0.0025, underwear.from().x(), 0.000001);
                assertTrue(underwear.from().x() > pants.from().x());
                assertTrue(underwear.faces().containsKey(LEFT));
            } else {
                assertEquals(-4.0025, underwear.from().x(), 0.000001);
                assertTrue(underwear.from().x() > pants.from().x());
                assertTrue(underwear.faces().containsKey(LEFT));
                assertEquals(0.0025, underwear.to().x(), 0.000001);
                assertTrue(underwear.to().x() < pants.to().x());
                assertTrue(underwear.faces().containsKey(RIGHT));
            }
            assertEquals(-2.05, underwear.from().z(), 0.000001);
            assertEquals(2.05, underwear.to().z(), 0.000001);
        }
    }
}
