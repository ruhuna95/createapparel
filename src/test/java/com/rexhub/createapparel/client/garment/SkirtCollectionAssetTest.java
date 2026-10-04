package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkirtCollectionAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final List<String> SHAPES = List.of("a_line", "pleated", "flared", "tight");

    private static List<String> lengths(String shape) {
        return shape.equals("a_line") || shape.equals("flared")
                ? List.of("mini", "knee", "midi", "long", "maxi")
                : List.of("micro", "mini", "knee", "midi", "long");
    }

    private static int height(String length) {
        return switch (length) {
            case "micro" -> 4;
            case "mini" -> 6;
            case "knee" -> 8;
            case "midi" -> 10;
            case "long" -> 12;
            case "maxi" -> 13;
            default -> throw new IllegalArgumentException(length);
        };
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static GarmentPartModel part(String id) throws Exception {
        String model = json(ASSETS.resolve("garment_models/templates/" + id + ".json"))
                .getAsJsonObject("default").getAsJsonObject("torso").get("model").getAsString();
        return DualEnvelopeDeformerTest.part(model.substring("createapparel:".length()));
    }

    @Test
    void allTwentySkirtsKeepTheWaistbandLengthAndOpenShellContract() throws Exception {
        var original = DualEnvelopeDeformerTest.part("a_line_skirt/torso");
        int count = 0;
        for (String shape : SHAPES) for (String length : lengths(shape)) {
            String id = shape + "_skirt_" + length;
            var model = part(id);
            assertEquals(original.elements().get(0), model.elements().get(0), id);
            var geometry = model.legEnvelope().orElseThrow().geometry();
            assertEquals(14, geometry.top());
            assertEquals(14 - height(length) - .004F, geometry.bottom(), .000002F, id);
            for (var element : model.elements()) {
                assertFalse(element.faces().containsKey(Face.TOP), id);
                assertFalse(element.faces().containsKey(Face.BOTTOM), id);
            }
            var definition = json(Path.of("src/main/resources/data/createapparel/garments/" + id + ".json"));
            assertEquals("bottom", definition.get("default_slot").getAsString());
            assertEquals(1, definition.getAsJsonArray("allowed_slots").size());
            assertEquals("plain", definition.getAsJsonArray("designs").get(0).getAsString());
            assertEquals(0, definition.getAsJsonObject("styles").size());
            count++;
        }
        assertEquals(20, count);
        var knee = part("a_line_skirt_knee");
        assertEquals(original.elements(), knee.elements());
        assertEquals(original.variants(), knee.variants());
    }

    @Test
    void staticShapesHaveConnectedSectionsAndPreserveMinimalDecorationCoordinates() throws Exception {
        for (String shape : SHAPES) for (String length : lengths(shape)) {
            var model = part(shape + "_skirt_" + length);
            var geometry = model.legEnvelope().orElseThrow().geometry();
            float growth = shape.equals("tight") ? 0 : shape.equals("flared") ? 1.5F : 1;
            float hemX = shape.equals("tight") ? -4.249F : -4.004F - growth;
            assertEquals(hemX, geometry.boundary(0, 1, true), .000001F);
            assertEquals(-hemX, geometry.boundary(1, 1, true), .000001F);
            assertEquals(-2.249F - growth, geometry.boundary(0, 1, false), .000001F);
            assertEquals(-4.004F, geometry.boundary(0, 0, true), .000001F);
            assertEquals(-2.249F, geometry.boundary(0, 0, false), .000001F);
            if (shape.equals("flared")) {
                float ring = (height(length) / 2F) / (height(length) + .004F);
                assertEquals(-4.504F, geometry.boundary(0, ring, true), .000002F);
                var upper = model.elements().get(1);
                var lower = model.elements().get(5);
                assertEquals(upper.corners().subList(0, 4), lower.corners().subList(4, 8));
                float topSlope = .5F / (upper.to().y() - upper.from().y());
                float bottomSlope = 1 / (lower.to().y() - lower.from().y());
                assertEquals(2, bottomSlope / topSlope, .003F);
            }
            if (shape.equals("pleated")) {
                assertEquals(9, model.elements().size());
                for (var deco : model.elements().subList(5, 9)) {
                    for (int corner = 4; corner < 8; corner++) {
                        Vec3 base = model.elements().get(1).corners().get(corner);
                        Vec3 decoration = deco.corners().get(corner);
                        assertEquals(base.x(), decoration.x(), .000001F);
                        assertEquals(base.y(), decoration.y(), .000001F);
                        assertEquals(base.z(), decoration.z(), .000001F);
                    }
                    assertEquals(-5.5F, deco.from().x());
                    assertEquals(5.5F, deco.to().x());
                    assertEquals(3.5F, deco.to().z(), .000001F);
                    assertTrue(deco.faces().keySet().stream().allMatch(face -> face != Face.TOP && face != Face.BOTTOM));
                }
            }
        }
    }

    @Test
    void atlasesAreGrayscaleWithBinaryAlphaAndPleatsAlternateOnePixelStrips() throws Exception {
        for (String shape : SHAPES) for (String length : lengths(shape)) {
            var model = part(shape + "_skirt_" + length);
            String resource = model.variants().get("default").base().getPath();
            var image = ImageIO.read(ASSETS.resolve("textures/" + resource + ".png").toFile());
            assertEquals(model.textureWidth(), image.getWidth());
            assertEquals(model.textureHeight(), image.getHeight());
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                assertEquals(pixel >> 16 & 255, pixel >> 8 & 255);
                assertEquals(pixel >> 8 & 255, pixel & 255);
                assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
            }
            for (var element : model.elements()) for (var uv : element.faces().values()) {
                assertTrue(uv.u0() >= 0 && uv.v0() >= 0 && uv.u1() <= image.getWidth() && uv.v1() <= image.getHeight());
            }
            if (shape.equals("pleated")) {
                for (int u0 : new int[]{0, 6, 16, 22}) for (int y = 0; y < height(length); y++)
                    for (int x = 0; x < (u0 == 0 || u0 == 16 ? 6 : 10); x++) {
                        int deco = image.getRGB(u0 + x, 1 + height(length) + y);
                        assertEquals(x % 2 == 0 ? 255 : 0, deco >>> 24);
                        if (x % 2 == 0) assertTrue((deco & 255) > (image.getRGB(u0 + x, 1 + y) & 255));
                    }
            }
        }
    }

    @Test
    void everySkirtRetainsWaistAnchorsAndLengthsAcrossWalkingAndSitting() throws Exception {
        for (String shape : SHAPES) for (String length : lengths(shape)) {
            var model = part(shape + "_skirt_" + length);
            for (float[] rotations : List.of(new float[]{0, 0}, new float[]{-.7F, .7F}, new float[]{-1.57F, -1.57F})) {
                var player = DualEnvelopeDeformerTest.player();
                player.leftLeg.xRot = rotations[0];
                player.rightLeg.xRot = rotations[1];
                var pose = DualEnvelopeDeformerTest.pose(model, player);
                var geometry = pose.geometry();
                var result = new Vector3f();
                for (var quad : geometry.quads()) for (Vec3 point : quad.points()) {
                    pose.shift(point, result);
                    assertTrue(Float.isFinite(result.x) && Float.isFinite(result.y) && Float.isFinite(result.z));
                    if (point.y() >= geometry.top()) {
                        assertEquals(new Vector3f(point.x(), point.y(), point.z()), result);
                        continue;
                    }
                    float ring = (geometry.top() - point.y()) / (geometry.top() - geometry.bottom());
                    float x = (point.x() - geometry.boundary(0, ring, true))
                            / (geometry.boundary(1, ring, true) - geometry.boundary(0, ring, true));
                    float z = (point.z() - geometry.boundary(0, ring, false))
                            / (geometry.boundary(2, ring, false) - geometry.boundary(0, ring, false));
                    var anchor = new Vector3f(-4.004F + 8.008F * x, geometry.top(), -2.249F + 4.498F * z);
                    assertEquals(anchor.distance(point.x(), point.y(), point.z()), anchor.distance(result), .00001F);
                    assertTrue(result.y >= point.y() - .00001F, model.id() + " p=" + point + " moved=" + result);
                }
            }
        }
    }

    @Test
    void layeredHemsFollowTheActualFlaredSurfaceAtItsMiddleAndHem() throws Exception {
        var model = part("flared_skirt_long");
        var geometry = model.legEnvelope().orElseThrow().geometry();
        for (boolean sitting : new boolean[]{false, true}) {
            var player = DualEnvelopeDeformerTest.player();
            if (sitting) player.leftLeg.xRot = player.rightLeg.xRot = -1.57F;
            var pose = DualEnvelopeDeformerTest.pose(model, player);
            var slope = GarmentHemGeometry.dualSlope(pose).forSlot(GarmentSlot.TOP);
            for (float depth : new float[]{3, 6, 9, 12.004F}) {
                float ring = depth / (geometry.top() - geometry.bottom());
                var sample = new Vec3(geometry.boundary(0, ring, true), geometry.top() - depth,
                        geometry.boundary(0, ring, false));
                var expected = new Vector3f();
                pose.shift(sample, expected);
                var actual = new Vector3f();
                slope.shift(new Vec3(-4.004F, geometry.top() - depth, -2.249F), depth, actual);
                assertEquals(expected.x - .025F, actual.x, .00001F);
                assertEquals(expected.y, actual.y, .00001F);
                assertEquals(expected.z - .025F, actual.z, .00001F);
            }
        }
    }

    @Test
    void segmentedEnvelopeRejectsGapsDuplicatesAndMissingPanels() throws Exception {
        Path path = ASSETS.resolve("garment_models/parts/flared_skirt_knee/torso.json");
        var missing = json(path);
        missing.getAsJsonObject("geometry").getAsJsonArray("elements").remove(5);
        assertThrows(IllegalStateException.class, () -> ClientGarmentAssetLoader.parsePart(
                ResourceLocation.parse("createapparel:invalid"), missing));
        var gap = json(path);
        gap.getAsJsonObject("geometry").getAsJsonArray("elements").get(5).getAsJsonObject()
                .getAsJsonArray("to").set(1, new com.google.gson.JsonPrimitive(9.9F));
        assertThrows(IllegalStateException.class, () -> ClientGarmentAssetLoader.parsePart(
                ResourceLocation.parse("createapparel:invalid"), gap));
        var duplicate = json(path);
        duplicate.getAsJsonObject("leg_envelope").getAsJsonArray("panel_sections").add("upper");
        assertThrows(IllegalStateException.class, () -> ClientGarmentAssetLoader.parsePart(
                ResourceLocation.parse("createapparel:invalid"), duplicate));
    }

    @Test
    void generatedRecipesModelsAndTranslationsCoverEverySkirt() throws Exception {
        var english = json(GENERATED.resolve("assets/createapparel/lang/en_us.json"));
        var japanese = json(GENERATED.resolve("assets/createapparel/lang/ja_jp.json"));
        for (String shape : SHAPES) for (String length : lengths(shape)) {
            String id = shape + "_skirt_" + length;
            var recipe = json(GENERATED.resolve("data/createapparel/recipes/" + id + ".json"));
            assertEquals("createapparel:sewing", recipe.get("type").getAsString());
            assertEquals("createapparel:" + id, recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(3, recipe.getAsJsonObject("fabric").get("count").getAsInt());
            assertEquals(1, recipe.getAsJsonObject("thread").get("count").getAsInt());
            assertEquals(5, recipe.getAsJsonObject("fabric").getAsJsonArray("tag").size());
            assertTrue(Files.exists(GENERATED.resolve("assets/createapparel/models/item/" + id + ".json")));
            String key = "item.createapparel." + id;
            assertTrue(english.has(key));
            assertTrue(japanese.has(key));
            if (length.equals("knee")) assertFalse(english.get(key).getAsString().contains("Knee"));
        }
        assertFalse(Files.exists(GENERATED.resolve("data/createapparel/recipes/a_line_skirt.json")));
        assertFalse(Files.exists(GENERATED.resolve("assets/createapparel/models/item/a_line_skirt.json")));
    }
}
