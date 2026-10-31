package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face.*;
import static org.junit.jupiter.api.Assertions.*;

class FootwearAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final List<String> GARMENTS = List.of("sneakers", "canvas_sneakers", "dress_shoes", "loafers",
            "ankle_boots", "work_boots", "knee_high_boots", "pumps", "sandals", "slippers");
    private static final Set<String> SOLES = Set.of("sneakers", "canvas_sneakers", "ankle_boots", "work_boots", "knee_high_boots");
    private static final Set<String> LACES = Set.of("sneakers", "canvas_sneakers", "dress_shoes", "ankle_boots", "work_boots", "knee_high_boots");
    private static final Map<String, Integer> LENGTHS = Map.of("sneakers", 3, "canvas_sneakers", 2,
            "dress_shoes", 2, "loafers", 2, "ankle_boots", 4, "work_boots", 3, "knee_high_boots", 6,
            "pumps", 1, "sandals", 1, "slippers", 1);

    @Test
    void everyFootwearResolvesOnlyLegsForEveryProfile() throws IOException {
        for (String garment : GARMENTS) {
            var definition = definition(garment);
            assertEquals("feet", definition.get("default_slot").getAsString());
            assertEquals(JsonParser.parseString("[\"feet\"]"), definition.get("allowed_slots"));
            assertEquals("default", definition.get("model_variant").getAsString());
            assertEquals(garment, definition.get("texture_variant").getAsString());
            DesignVariationAssetTest.assertDefinitionDesigns(definition, garment);
            assertEquals(0, definition.getAsJsonObject("styles").size());
            String group = group(garment);
            var template = json(ASSETS.resolve("garment_models/templates/" + group + ".json"));
            assertEquals(Set.of("default"), template.keySet());
            var parts = template.getAsJsonObject("default");
            assertEquals(Set.of("left_leg", "right_leg"), parts.keySet());
            for (var arms : ArmProfile.values()) {
                for (var chest : ChestProfile.values()) {
                    assertEquals(List.of("left_leg", "right_leg"),
                            GarmentPartSelection.select(Map.of(), arms, chest, parts.keySet()));
                }
            }
            for (String side : List.of("left", "right")) {
                assertEquals("createapparel:" + group + "/" + side + "_leg",
                        parts.getAsJsonObject(side + "_leg").get("model").getAsString());
                assertTrue(part(garment, side).variants().containsKey(garment));
            }
        }
    }

    @Test
    void feetUseSteppedFrontAndMinimalSidesWithJoinedDecoration() throws IOException {
        for (String garment : GARMENTS) {
            for (String side : List.of("left", "right")) {
                var part = part(garment, side);
                float x = side.equals("left") ? 0 : -4;
                var inner = side.equals("left") ? LEFT : RIGHT;
                int height = height(garment);
                assertEquals(height <= 4 ? 16 : 32, part.textureWidth());
                assertEquals(part.textureWidth(), part.textureHeight());
                for (var element : part.elements()) {
                    boolean decoration = element.name().endsWith("_decoration");
                    float offset = decoration ? 0.015F : 0.01F;
                    assertEquals(x - (decoration && inner == LEFT ? 0.01F : offset), element.from().x(), 0.00001);
                    assertEquals(x + 4 + (decoration && inner == RIGHT ? 0.01F : offset), element.to().x(), 0.00001);
                    if (decoration) assertFalse(element.faces().containsKey(inner));
                    if (element.faces().containsKey(BACK)) assertEquals(2 + offset, element.to().z(), 0.00001);
                    for (var entry : element.faces().entrySet()) {
                        var uv = entry.getValue();
                        assertTrue(uv.u0() >= 0 && uv.v0() >= 0 && uv.u1() <= part.textureWidth()
                                && uv.v1() <= part.textureHeight());
                        if (garment.equals("sandals")) continue;
                        assertEquals(4, uv.u1() - uv.u0());
                        if (entry.getKey() != TOP && entry.getKey() != BOTTOM) {
                            assertEquals(element.name().contains("_upper") ? height - 2 : Math.min(height, 2),
                                    uv.v1() - uv.v0());
                        }
                    }
                }
                if (garment.equals("sandals")) {
                    assertEquals(3, part.elements().size());
                    assertFalse(part.elements().get(0).faces().containsKey(TOP));
                    assertTrue(part.elements().get(1).faces().containsKey(TOP));
                    assertFalse(part.elements().get(2).faces().containsKey(TOP));
                    continue;
                }
                assertEquals(height == 1 ? 2 : 6, part.elements().size());
                for (boolean decoration : List.of(false, true)) {
                    String suffix = decoration ? "_decoration" : "";
                    float offset = decoration ? 0.015F : 0.01F;
                    var lower = element(part, side + "_leg" + suffix);
                    assertEquals(-2 - (decoration ? 0.75 : 0.5), lower.from().z(), 0.00001);
                    assertEquals(-0.01, lower.from().y(), 0.00001);
                    assertEquals(Math.min(height, 2) + offset, lower.to().y(), 0.00001);
                    assertEquals(!decoration || height > 1, lower.faces().containsKey(BOTTOM));
                    if (height == 1) {
                        assertTrue(lower.faces().containsKey(TOP));
                        assertEquals(4, lower.faces().get(TOP).v1() - lower.faces().get(TOP).v0());
                        continue;
                    }
                    var upper = element(part, side + "_leg_upper" + suffix);
                    var toe = element(part, side + "_leg_toe" + suffix);
                    assertFalse(lower.faces().containsKey(TOP));
                    assertFalse(upper.faces().containsKey(TOP));
                    assertFalse(upper.faces().containsKey(BOTTOM));
                    assertEquals(Set.of(TOP), toe.faces().keySet());
                    assertEquals(-2 - offset, upper.from().z(), 0.00001);
                    assertEquals(height + offset, upper.to().y(), 0.00001);
                    assertEquals(lower.to().y(), upper.from().y());
                    assertEquals(lower.to().y(), toe.to().y());
                    assertEquals(lower.from().z(), toe.from().z());
                    assertEquals(upper.from().z(), toe.to().z());
                    assertEquals(lower.from().x(), toe.from().x());
                    assertEquals(lower.to().x(), toe.to().x());
                    assertEquals(lower.to().z(), upper.to().z());
                    assertEquals(1, toe.faces().get(TOP).v1() - toe.faces().get(TOP).v0());
                    var lowerUv = lower.faces().get(FRONT);
                    var upperUv = upper.faces().get(FRONT);
                    assertEquals(upperUv.v1(), lowerUv.v0());
                    assertEquals(height, lowerUv.v1() - upperUv.v0());
                }
            }
        }
    }

    @Test
    void texturesKeepBinaryAlphaGrayscaleAndExactVisibleLengths() throws IOException {
        for (String garment : GARMENTS) {
            for (String side : List.of("left", "right")) {
                var part = part(garment, side);
                var texture = part.variants().get(garment);
                assertTrue(texture.brightnessMask().isEmpty());
                assertEquals(1, texture.brightness());
                assertTrue(texture.overlay().isEmpty());
                var image = image(texture.base());
                assertEquals(part.textureWidth(), image.getWidth());
                assertEquals(part.textureHeight(), image.getHeight());
                var shades = new HashSet<Integer>();
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = image.getRGB(x, y);
                        assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
                        assertEquals(pixel & 255, pixel >> 8 & 255);
                        assertEquals(pixel & 255, pixel >> 16 & 255);
                        if (pixel >>> 24 != 0) shades.add(pixel & 255);
                    }
                }
                assertTrue(shades.size() > 2);
                var body = part.elements().get(0);
                int height = height(garment);
                int first = 4 + height - LENGTHS.get(garment);
                for (int y = 4; y < first; y++) {
                    for (int x = 0; x < 16; x++) assertEquals(0, image.getRGB(x, y) >>> 24);
                }
                if (!Set.of("sandals", "slippers").contains(garment)) {
                    for (int y = first; y < 4 + height; y++) {
                        for (int x = 0; x < 16; x++) assertEquals(255, image.getRGB(x, y) >>> 24);
                    }
                }
                if (SOLES.contains(garment)) {
                    assertEquals(0, opaque(image, body.faces().get(BOTTOM)));
                    assertEquals(16, opaque(image, part.elements().get(1).faces().get(BOTTOM)));
                } else {
                    assertEquals(16, opaque(image, body.faces().get(BOTTOM)));
                }
            }
        }
    }

    @Test
    void accentBlendsLacesAndSecondaryColorsBothSneakerMidsoles() throws IOException {
        for (String garment : GARMENTS) {
            for (String side : List.of("left", "right")) {
                var part = part(garment, side);
                var base = image(part.variants().get(garment).base());
                var masks = part.designsFor(garment).get("plain");
                var channels = ClientGarmentAssets.colorChannels(Map.of(side + "_leg", part.id()),
                        garment, "plain", Map.of(part.id(), part));
                assertEquals(Set.of("sneakers", "canvas_sneakers").contains(garment), channels.secondary(), garment);
                assertEquals(LACES.contains(garment), channels.accent(), garment);
                if (!LACES.contains(garment)) {
                    assertNull(masks);
                    continue;
                }
                var mask = image(masks.accent().orElseThrow());
                var secondary = masks.secondary().isPresent() ? image(masks.secondary().orElseThrow()) : null;
                int laces = 0;
                int midsole = 0;
                int height = height(garment);
                for (int y = 0; y < mask.getHeight(); y++) {
                    for (int x = 0; x < mask.getWidth(); x++) {
                        int pixel = mask.getRGB(x, y);
                        int weight = pixel & 255;
                        assertEquals(255, pixel >>> 24);
                        assertEquals(weight, pixel >> 8 & 255);
                        assertEquals(weight, pixel >> 16 & 255);
                        if (secondary != null) {
                            int secondaryPixel = secondary.getRGB(x, y);
                            int secondaryWeight = secondaryPixel & 255;
                            assertEquals(255, secondaryPixel >>> 24);
                            assertEquals(secondaryWeight, secondaryPixel >> 8 & 255);
                            assertEquals(secondaryWeight, secondaryPixel >> 16 & 255);
                            if (base.getRGB(x, y) >>> 24 != 0 && secondaryWeight != 0) {
                                assertTrue(Set.of("sneakers", "canvas_sneakers").contains(garment));
                                assertTrue(y == 8 + 2 * height - 1 || y == 4 + height - 1);
                                assertEquals(255, secondaryWeight);
                                assertEquals(0, weight);
                                midsole++;
                            }
                        }
                        if (base.getRGB(x, y) >>> 24 != 0 && weight != 0) {
                            assertTrue(x == 5 || x == 6);
                            assertTrue((y >= 8 + height && y < 8 + 2 * height - 1) || y == height + 7);
                            assertEquals(128, weight);
                            laces++;
                        }
                    }
                }
                assertTrue(laces > 0, garment);
                assertEquals(Set.of("sneakers", "canvas_sneakers").contains(garment) ? 20 : 0, midsole);
            }
        }
        assertEquals(0x7F0080, GarmentTexturePixelOps.composeTint(0xFF0000,
                java.util.OptionalInt.empty(), java.util.OptionalInt.of(0x0000FF), 0, 128));
    }

    @Test
    void variantMaskOverrideAndEmptyOverrideKeepUiAndRenderingConsistent() throws IOException {
        var fixture = json(ASSETS.resolve("garment_models/parts/shoes/left_leg.json"));
        var textures = fixture.getAsJsonObject("textures");
        textures.add("designs", JsonParser.parseString(
                "{\"plain\":{\"accent_mask\":\"createapparel:legacy/accent\"}}"));
        textures.getAsJsonObject("variants").getAsJsonObject("default").remove("designs");
        var part = ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:fixture"), fixture);
        assertTrue(part.designsFor("default").get("plain").accent().isPresent());
        assertTrue(part.designsFor("unknown").get("plain").accent().isPresent());
        assertTrue(part.designsFor("sneakers").get("plain").accent().isPresent());
        assertTrue(part.designsFor("sneakers").get("plain").secondary().isPresent());
        assertFalse(part.designsFor("loafers").containsKey("plain"));
        for (String variant : List.of("default", "sneakers", "loafers")) {
            var channels = ClientGarmentAssets.colorChannels(Map.of("left_leg", part.id()), variant,
                    "plain", Map.of(part.id(), part));
            assertEquals(variant.equals("sneakers"), channels.secondary());
            assertEquals(!variant.equals("loafers"), channels.accent());
        }
    }

    @Test
    void lowShoesKeepOpenSkinAndPumpHeelDecoration() throws IOException {
        for (String side : List.of("left", "right")) {
            var sandals = part("sandals", side);
            var sandalBase = image(sandals.variants().get("sandals").base());
            assertEquals(3, sandals.elements().size());
            var sole = sandals.elements().get(0);
            var toe = sandals.elements().get(1);
            var heelBand = sandals.elements().get(2);
            assertEquals(Set.of(BOTTOM), sole.faces().keySet());
            assertEquals(1.01, sole.to().y(), 0.00001);
            assertEquals(1, sandals.elements().stream().filter(e -> e.faces().containsKey(TOP)).count());
            var topVertices = GarmentElementGeometry.faceVertices(toe, TOP);
            assertTrue(topVertices.stream().allMatch(vertex -> Math.abs(vertex.y() - 1.01F) < 0.00001F));
            assertTrue(topVertices.stream().allMatch(vertex -> vertex.z() >= -2.5F && vertex.z() <= -1F));
            var topUv = toe.faces().get(TOP).corners();
            for (int i = 0; i < 4; i++) {
                assertEquals(4 - (topVertices.get(i).z() + 2.5F) * 4 / 4.51F, topUv.get(i).v(), 0.00001);
            }
            assertEquals(1, GarmentElementGeometry.normal(topVertices).y(), 0.00001);
            assertEquals(Set.of(TOP, LEFT, FRONT, RIGHT), toe.faces().keySet());
            assertEquals(Set.of(LEFT, BACK, RIGHT), heelBand.faces().keySet());
            assertEquals(-1, toe.to().z());
            assertEquals(1, heelBand.from().z());
            assertEquals(sole.from().y(), toe.from().y());
            assertEquals(sole.from().y(), heelBand.from().y());
            assertEquals(sole.from().z(), toe.from().z());
            assertEquals(sole.to().z(), heelBand.to().z());
            for (var band : List.of(toe, heelBand)) {
                assertEquals(sole.from().x(), band.from().x());
                assertEquals(sole.to().x(), band.to().x());
                for (var face : band.faces().entrySet()) {
                    assertEquals(face.getKey() == TOP ? 8 : face.getKey() == BACK ? 4 : 1,
                            opaque(sandalBase, face.getValue()));
                }
            }
            for (int u = 4; u < 8; u++) {
                int expected = u == (side.equals("right") ? 6 : 5) ? 255 : 0;
                assertEquals(expected, sandalBase.getRGB(u, 4) >>> 24);
            }
            var slippers = part("slippers", side);
            var slipperBase = image(slippers.variants().get("slippers").base());
            assertEquals(0, opaque(slipperBase, slippers.elements().get(0).faces().get(BACK)));
            assertEquals(8, opaque(slipperBase, slippers.elements().get(0).faces().get(TOP)));
            for (var face : List.of(LEFT, RIGHT)) {
                int pixels = opaque(slipperBase, slippers.elements().get(0).faces().get(face));
                assertTrue(pixels < 4);
            }
            var pumps = part("pumps", side);
            var pumpBase = image(pumps.variants().get("pumps").base());
            var heel = pumps.elements().get(1);
            assertEquals(0, opaque(pumpBase, heel.faces().get(FRONT)));
            assertEquals(4, opaque(pumpBase, heel.faces().get(BACK)));
            assertFalse(heel.faces().containsKey(side.equals("left") ? LEFT : RIGHT));
            assertEquals(1, opaque(pumpBase, heel.faces().get(side.equals("left") ? RIGHT : LEFT)));
            assertEquals(8, opaque(pumpBase, pumps.elements().get(0).faces().get(TOP)));
            assertEquals(4, opaque(pumpBase, heel.faces().get(TOP)));
        }
    }

    @Test
    void bootDecorationSidesHaveOneArchCutoutWithoutChangingBodyOrSole() throws IOException {
        for (String garment : List.of("ankle_boots", "work_boots", "knee_high_boots")) {
            for (String side : List.of("left", "right")) {
                var part = part(garment, side);
                var base = image(part.variants().get(garment).base());
                var body = part.elements().get(0);
                var decoration = part.elements().get(1);
                for (var face : List.of(LEFT, RIGHT)) {
                    var uv = decoration.faces().get(face);
                    if (uv == null) continue;
                    int row = (int) uv.v1() - 1;
                    for (int u = (int) uv.u0(); u < uv.u1(); u++) {
                        assertEquals(u == (face == LEFT ? 1 : 10) ? 0 : 255, base.getRGB(u, row) >>> 24);
                    }
                    assertEquals(3, opaque(base, uv));
                    assertEquals(8, opaque(base, body.faces().get(face)));
                }
                assertEquals(16, opaque(base, decoration.faces().get(BOTTOM)));
            }
        }
    }

    @Test
    void innerDecorationAndTintMasksAreBakedAndToeTexturesFollowFront() throws IOException {
        for (String garment : GARMENTS) {
            if (garment.equals("sandals")) continue;
            for (String side : List.of("left", "right")) {
                var part = part(garment, side);
                var base = image(part.variants().get(garment).base());
                int height = height(garment);
                int innerU = side.equals("left") ? 0 : 8;
                var images = new java.util.ArrayList<BufferedImage>();
                images.add(base);
                var masks = part.designsFor(garment).get("plain");
                if (masks != null) {
                    if (masks.accent().isPresent()) images.add(image(masks.accent().orElseThrow()));
                    if (masks.secondary().isPresent()) images.add(image(masks.secondary().orElseThrow()));
                }
                for (var image : images) {
                    for (int row = 0; row < height; row++) {
                        for (int u = innerU; u < innerU + 4; u++) {
                            if (image == base && base.getRGB(u, 8 + height + row) >>> 24 == 0) continue;
                            assertEquals(image.getRGB(u, 8 + height + row), image.getRGB(u, 4 + row), garment);
                        }
                    }
                    if (height > 2) {
                        for (int u = 4; u < 8; u++) {
                            assertEquals(image.getRGB(u, height + 2), image.getRGB(u, 3), garment);
                            assertEquals(image.getRGB(u, 6 + 2 * height), image.getRGB(u, 7 + height), garment);
                        }
                    }
                }
                if (height > 2) assertEquals(4, opaque(base,
                        element(part, side + "_leg_toe").faces().get(TOP)));
            }
        }
    }

    @Test
    void recipesAndItemModelsMatchFootwearCatalog() throws IOException {
        for (String garment : GARMENTS) {
            var recipe = json(Path.of("src/generated/resources/data/createapparel/recipe/" + garment + ".json"));
            assertEquals("createapparel:sewing", recipe.get("type").getAsString());
            assertEquals("createapparel:" + garment, recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(2, recipe.getAsJsonObject("fabric").get("count").getAsInt());
            assertEquals(garment.endsWith("boots") ? 2 : 1, recipe.getAsJsonObject("thread").get("count").getAsInt());
            String materials = Set.of("sneakers", "canvas_sneakers", "slippers").contains(garment)
                    ? "[\"createapparel:fabrics/cotton\",\"createapparel:fabrics/hemp\",\"createapparel:fabrics/synthetic\"]"
                    : "[\"createapparel:fabrics/leather\",\"createapparel:fabrics/synthetic\"]";
            if (garment.equals("pumps")) materials = "[\"createapparel:fabrics/leather\",\"createapparel:fabrics/silk\",\"createapparel:fabrics/synthetic\"]";
            if (garment.equals("sneakers")) materials = "[\"createapparel:fabrics/cotton\",\"createapparel:fabrics/hemp\",\"createapparel:fabrics/leather\",\"createapparel:fabrics/synthetic\"]";
            if (garment.equals("slippers")) materials = "[\"createapparel:fabrics/cotton\",\"createapparel:fabrics/wool\",\"createapparel:fabrics/leather\",\"createapparel:fabrics/synthetic\"]";
            assertEquals(JsonParser.parseString(materials), recipe.getAsJsonObject("fabric").get("tag"));
            var model = json(Path.of("src/generated/resources/assets/createapparel/models/item/" + garment + ".json"));
            assertEquals("minecraft:builtin/entity", model.get("parent").getAsString());
        }
    }

    private static int height(String garment) throws IOException {
        return Map.of("shoes", 3, "boots", 4, "tall_boots", 6, "low_shoes", 1, "sandals", 1).get(group(garment));
    }

    private static ClientGarmentAssets.Element element(ClientGarmentAssets.GarmentPartModel part, String name) {
        return part.elements().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    private static int opaque(BufferedImage image, ClientGarmentAssets.Uv uv) {
        int count = 0;
        for (int y = (int) uv.v0(); y < uv.v1(); y++) {
            for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                if (image.getRGB(x, y) >>> 24 == 255) count++;
            }
        }
        return count;
    }

    private static BufferedImage image(ResourceLocation resource) throws IOException {
        return ImageIO.read(ASSETS.resolve("textures/" + resource.getPath() + ".png").toFile());
    }

    private static JsonObject definition(String garment) throws IOException {
        return json(Path.of("src/main/resources/data/createapparel/garments/" + garment + ".json"));
    }

    private static String group(String garment) throws IOException {
        return ResourceLocation.parse(definition(garment).get("model_template").getAsString()).getPath();
    }

    private static ClientGarmentAssets.GarmentPartModel part(String garment, String side) throws IOException {
        String path = group(garment) + "/" + side + "_leg";
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:" + path),
                json(ASSETS.resolve("garment_models/parts/" + path + ".json")));
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
