package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ZipupHoodieAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");

    @Test
    void slotStylesAndAllBodyProfilesResolveToLoadableAssets() throws IOException {
        var definition = GarmentDefinition.fromJson(ResourceLocation.parse("createapparel:zipup_hoodie"),
                json(Path.of("src/main/resources/data/createapparel/garments/zipup_hoodie.json")));
        assertEquals(GarmentSlot.TOP, definition.defaultSlot());
        assertFalse(definition.stylesFor(GarmentSlot.TOP).containsKey("open"));
        assertEquals(List.of("closed", "opened"), definition.stylesFor(GarmentSlot.OUTER).get("open"));
        var template = json(ASSETS.resolve("garment_models/templates/hoodie.json"));
        for (GarmentSlot slot : definition.allowedSlots()) {
            var mapping = template.getAsJsonObject(definition.modelVariantFor(slot));
            assertTrue(mapping.keySet().containsAll(GarmentPartSelection.requiredStyleParts(definition.stylesFor(slot), true)));
            for (ArmProfile arms : ArmProfile.values()) {
                for (ChestProfile chest : ChestProfile.values()) {
                    for (String hood : List.of("down", "up")) {
                        for (String open : definition.stylesFor(slot).getOrDefault("open", List.of("closed"))) {
                            for (String semantic : GarmentPartSelection.select(Map.of("hood", hood, "open", open), arms, chest, true)) {
                                var id = ResourceLocation.parse(mapping.getAsJsonObject(semantic).get("model").getAsString());
                                var part = ClientGarmentAssetLoader.parsePart(id, json(ASSETS.resolve("garment_models/parts/" + id.getPath() + ".json")));
                                var texture = part.variants().get("default");
                                var base = ImageIO.read(ASSETS.resolve("textures/" + texture.base().getPath() + ".png").toFile());
                                assertEquals(part.textureWidth(), base.getWidth());
                                assertEquals(part.textureHeight(), base.getHeight());
                                if (semantic.startsWith("torso") || semantic.startsWith("bust")) {
                                    var overlay = ImageIO.read(ASSETS.resolve("textures/" + texture.overlay().orElseThrow().getPath() + ".png").toFile());
                                    var mask = ImageIO.read(ASSETS.resolve("textures/" + texture.brightnessMask().orElseThrow().getPath() + ".png").toFile());
                                    assertEquals(base.getWidth(), overlay.getWidth());
                                    assertEquals(base.getHeight(), mask.getHeight());
                                    for (int y = 0; y < base.getHeight(); y++) {
                                        for (int x = 0; x < base.getWidth(); x++) {
                                            int pixel = base.getRGB(x, y);
                                            assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
                                            assertEquals(pixel & 255, pixel >> 8 & 255);
                                            assertEquals(pixel & 255, pixel >> 16 & 255);
                                            int brightness = mask.getRGB(x, y);
                                            assertEquals(255, brightness >>> 24);
                                            assertEquals(brightness & 255, brightness >> 8 & 255);
                                            assertEquals(brightness & 255, brightness >> 16 & 255);
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
    void openedFrontKeepsTwoPixelPanelsAndBothLayersLeaveTheOpeningClear() throws IOException {
        for (String name : List.of("torso_zipup_opened", "bust_zipup_opened")) {
            boolean bust = name.startsWith("bust");
            var base = ImageIO.read(ASSETS.resolve("textures/garment/hoodie/" + name + "/base/default.png").toFile());
            int u = bust ? 3 : 4, v = bust ? 3 : 4, decorationV = bust ? 10 : 16;
            int width = bust ? 7 : 8, height = bust ? 4 : 12;
            for (int y = 0; y < height; y++) {
                for (int x = 2; x < width - 2; x++) {
                    assertEquals(0, base.getRGB(u + x, v + y) >>> 24);
                    assertEquals(0, base.getRGB(u + x, decorationV + y) >>> 24);
                }
            }
        }
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test
    void actualOuterTorsoVariantsUseVisibleOnePixelHemSides() throws IOException {
        for (String semantic : List.of("torso", "torso_opened")) {
            var template = json(ASSETS.resolve("garment_models/templates/hoodie.json"));
            var id = ResourceLocation.parse(template.getAsJsonObject("zipup_outer")
                    .getAsJsonObject(semantic).get("model").getAsString());
            var model = json(ASSETS.resolve("garment_models/parts/" + id.getPath() + ".json"));
            var elements = model.getAsJsonObject("geometry").getAsJsonArray("elements");
            var body = elements.get(0).getAsJsonObject();
            var shell = elements.get(1).getAsJsonObject();
            assertEquals(body.getAsJsonArray("from").get(0).getAsDouble() - 0.0005,
                    shell.getAsJsonArray("from").get(0).getAsDouble(), 0.000001);
            assertEquals(body.getAsJsonArray("to").get(0).getAsDouble() + 0.0005,
                    shell.getAsJsonArray("to").get(0).getAsDouble(), 0.000001);
            var faces = shell.getAsJsonObject("faces");
            assertFalse(faces.has("top"));
            assertFalse(faces.has("bottom"));
            var baseId = ResourceLocation.parse(model.getAsJsonObject("textures").getAsJsonObject("variants")
                    .getAsJsonObject("default").get("base").getAsString());
            var texture = ImageIO.read(ASSETS.resolve("textures/" + baseId.getPath() + ".png").toFile());
            for (String face : List.of("left", "right")) {
                var uv = faces.getAsJsonObject(face).getAsJsonArray("uv");
                for (int x = uv.get(0).getAsInt(); x < uv.get(2).getAsInt(); x++) {
                    assertEquals(0, texture.getRGB(x, 26) >>> 24);
                    assertEquals(255, texture.getRGB(x, 27) >>> 24);
                    assertEquals(texture.getRGB(face.equals("left") ? 4 : 11, 27), texture.getRGB(x, 27));
                }
            }
        }
    }

    @Test
    void zipperContinuesAcrossBustDownAndOpenedCordsCoverTheZipper() throws IOException {
        var closed = ImageIO.read(ASSETS.resolve("textures/garment/hoodie/bust_zipup/base/default.png").toFile());
        var opened = ImageIO.read(ASSETS.resolve("textures/garment/hoodie/bust_zipup_opened/base/default.png").toFile());
        var torso = ImageIO.read(ASSETS.resolve("textures/garment/hoodie/torso_zipup_opened/base/default.png").toFile());
        var mask = ImageIO.read(ASSETS.resolve("textures/garment/hoodie/bust_zipup_opened/masks/brightness/default.png").toFile());
        for (int y = 7; y < 10; y++) {
            int expected = 126 + ((y - 7) % 2) * 20;
            assertEquals(255, closed.getRGB(13, y) >>> 24);
            assertEquals(expected, closed.getRGB(13, y) & 255);
            for (int x : List.of(11, 15)) {
                assertEquals(255, opened.getRGB(x, y) >>> 24);
                assertEquals(expected, opened.getRGB(x, y) & 255);
            }
        }
        for (int y = 16; y < 28; y++) {
            for (int x : List.of(5, 10)) {
                int expected = y < 21 ? 204 + (y % 2) * 12 : 126 + (y % 2) * 20;
                assertEquals(255, torso.getRGB(x, y) >>> 24);
                assertEquals(expected, torso.getRGB(x, y) & 255);
            }
        }
        for (int y = 10; y < 14; y++) {
            for (int x : List.of(4, 8)) {
                assertEquals(204 + (y % 2) * 12, opened.getRGB(x, y) & 255);
                assertEquals(130, mask.getRGB(x, y) & 255);
            }
            for (int x : List.of(3, 9)) {
                assertEquals(0, opened.getRGB(x, y) >>> 24);
                assertEquals(100, mask.getRGB(x, y) & 255);
            }
        }
    }
}
