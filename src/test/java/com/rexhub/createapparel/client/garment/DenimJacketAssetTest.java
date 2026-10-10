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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face.*;
import static org.junit.jupiter.api.Assertions.*;

class DenimJacketAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final List<String> PARTS = List.of("torso", "torso_opened", "bust", "bust_opened",
            "left_sleeve_classic", "right_sleeve_classic", "left_sleeve_slim", "right_sleeve_slim");

    @Test
    void resolvesBothStylesForEveryBodyProfileWithIndependentResources() throws IOException {
        var definition = GarmentDefinition.fromJson(ResourceLocation.parse("createapparel:denim_jacket"),
                json(Path.of("src/main/resources/data/createapparel/garments/denim_jacket.json")));
        assertEquals(GarmentSlot.OUTER, definition.defaultSlot());
        assertEquals(Set.of(GarmentSlot.OUTER), definition.allowedSlots());
        assertEquals("default", definition.textureVariant());
        assertEquals("default", definition.modelVariant());
        assertEquals(Map.of("open", List.of("closed", "open")), definition.styles());
        var template = json(ASSETS.resolve("garment_models/templates/denim_jacket.json")).getAsJsonObject("default");
        assertEquals(Set.copyOf(PARTS), template.keySet());
        for (var arms : ArmProfile.values()) {
            for (var chest : ChestProfile.values()) {
                for (String style : List.of("closed", "open")) {
                    var selected = GarmentPartSelection.select(Map.of("open", style), arms, chest, template.keySet());
                    String suffix = style.equals("open") ? "_opened" : "";
                    assertEquals("torso" + suffix, selected.get(0));
                    assertEquals(chest == ChestProfile.BUST, selected.contains("bust" + suffix));
                    assertEquals(chest == ChestProfile.BUST ? 4 : 3, selected.size());
                    for (String name : selected) {
                        assertEquals("createapparel:denim_jacket/" + name,
                                template.getAsJsonObject(name).get("model").getAsString());
                        var model = part(name);
                        assertEquals(Set.of("default"), model.variants().keySet());
                        assertTrue(model.designs().isEmpty());
                        assertTrue(model.variants().get("default").brightnessMask().isEmpty());
                        assertTrue(model.variants().get("default").base().getPath().startsWith("garment/denim_jacket/"));
                    }
                }
            }
        }
    }

    @Test
    void modelsRespectStandardOuterOffsetsLengthAndDecorationShells() throws IOException {
        for (String name : PARTS) {
            var model = part(name);
            assertEquals(2, model.elements().size());
            var body = model.elements().get(0);
            var decoration = model.elements().get(1);
            assertEquals(body.from().z() - 0.25F, decoration.from().z(), 0.00001F);
            assertEquals(body.to().y(), decoration.to().y());
            for (var element : model.elements()) {
                assertEquals(name.startsWith("bust"), element.faces().containsKey(BOTTOM));
                if (name.startsWith("left")) { assertTrue(element.from().x() > 0); }
                if (name.startsWith("right")) { assertTrue(element.to().x() < 0); }
            }
            if (name.startsWith("torso")) {
                assertEquals(new ClientGarmentAssets.Vec3(-4.01F, 11.99F, -2.5F), body.from());
                assertEquals(new ClientGarmentAssets.Vec3(4.01F, 24.01F, 2.5F), body.to());
                assertEquals(12, body.faces().get(FRONT).v1() - body.faces().get(FRONT).v0());
                assertEquals(Set.of(FRONT, BACK), decoration.faces().keySet());
            } else if (name.startsWith("bust")) {
                assertEquals(23.3426F, body.to().y());
                assertEquals(19F, body.from().y());
                assertEquals(body.rotation(), decoration.rotation());
                assertEquals(Set.of(LEFT, FRONT, RIGHT, BOTTOM), body.faces().keySet());
                assertEquals(Set.of(FRONT, BOTTOM), decoration.faces().keySet());
                for (var element : model.elements()) {
                    var front = GarmentElementGeometry.faceVertices(element, FRONT);
                    var bottom = GarmentElementGeometry.faceVertices(element, BOTTOM);
                    assertEquals(front.get(0), bottom.get(1));
                    assertEquals(front.get(3), bottom.get(2));
                }
            } else {
                assertEquals(11.5F, body.from().y());
                assertEquals(24.01F, body.to().y());
                assertEquals(12, body.faces().get(FRONT).v1() - body.faces().get(FRONT).v0());
                assertEquals(name.endsWith("slim") ? 3 : 4,
                        body.faces().get(FRONT).u1() - body.faces().get(FRONT).u0());
                assertEquals(Set.of(name.startsWith("left") ? RIGHT : LEFT), decoration.faces().keySet());
            }
        }
    }

    @Test
    void allFacesHavePixelsAndSeamsAreFixedColorOverOpaqueGrayscale() throws IOException {
        for (String name : PARTS) {
            var model = part(name);
            var base = image(name, "base");
            var overlay = image(name, "overlay");
            assertEquals(model.textureWidth(), base.getWidth());
            assertEquals(model.textureHeight(), base.getHeight());
            assertEquals(base.getWidth(), overlay.getWidth());
            assertEquals(base.getHeight(), overlay.getHeight());
            int stitches = 0;
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < base.getWidth(); x++) {
                    int pixel = base.getRGB(x, y);
                    assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
                    assertEquals(pixel & 255, pixel >> 8 & 255);
                    assertEquals(pixel & 255, pixel >> 16 & 255);
                    int stitch = overlay.getRGB(x, y);
                    if (stitch >>> 24 != 0) {
                        stitches++;
                        assertEquals(255, pixel >>> 24, name);
                        assertEquals(0xC09E74, stitch & 0xFFFFFF);
                        assertTrue(stitch >>> 24 == 39 || stitch >>> 24 == 55);
                    }
                }
            }
            assertTrue(stitches > 0, name);
            for (var element : model.elements()) {
                for (var uv : element.faces().values()) {
                    int opaque = 0;
                    for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                        for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                            if (base.getRGB(x, y) >>> 24 == 255) { opaque++; }
                        }
                    }
                    assertTrue(opaque > 0, name + " / " + element.name());
                }
            }
        }
    }

    @Test
    void openingStaysClearAndBustBottomContinuesBothLayers() throws IOException {
        var opened = image("torso_opened", "base");
        var closed = image("torso", "base");
        for (int y = 4; y < 16; y++) {
            for (int x = 6; x < 10; x++) {
                assertEquals(0, opened.getRGB(x, y) >>> 24);
                assertEquals(0, opened.getRGB(x, y + 12) >>> 24);
            }
        }
        for (int y : List.of(20, 23, 26)) {
            assertEquals(124, closed.getRGB(8, y) & 255);
            assertEquals(124, opened.getRGB(10, y) & 255);
        }
        for (int y : List.of(21, 24)) {
            assertEquals(0, closed.getRGB(8, y) >>> 24);
            assertEquals(0, opened.getRGB(10, y) >>> 24);
        }
        for (String name : List.of("bust", "bust_opened")) {
            for (String layer : List.of("base", "overlay")) {
                var pixels = image(name, layer);
                for (int x = 0; x < 7; x++) {
                    for (int row = 0; row < 3; row++) {
                        assertEquals(pixels.getRGB(3 + x, 3), pixels.getRGB(x, 8 + row));
                        assertEquals(pixels.getRGB(3 + x, 7), pixels.getRGB(x, 11 + row));
                    }
                }
            }
        }
        for (String name : PARTS.stream().filter(n -> n.contains("sleeve")).toList()) {
            var uv = part(name).elements().get(1).faces().values().iterator().next();
            int x = name.startsWith("left") ? (int) uv.u1() - 2 : (int) uv.u0() + 1;
            assertEquals(124, image(name, "base").getRGB(x, 26) & 255);
        }
    }

    @Test
    void denimGrainDoesNotRepeatEveryThreePixelsAndMatchesPantsBrightness() throws IOException {
        var jacket = image("torso", "base");
        var pants = ImageIO.read(ASSETS.resolve("textures/garment/denim_pants/left_leg/base/default.png").toFile());
        int horizontalDifferences = 0;
        int verticalDifferences = 0;
        double jacketSum = 0;
        double pantsSum = 0;
        for (int y = 4; y < 13; y++) {
            for (int x = 16; x < 24; x++) {
                jacketSum += jacket.getRGB(x, y) & 255;
                if (x < 21 && jacket.getRGB(x, y) != jacket.getRGB(x + 3, y)) { horizontalDifferences++; }
                if (y < 10 && jacket.getRGB(x, y) != jacket.getRGB(x, y + 3)) { verticalDifferences++; }
            }
            for (int x = 12; x < 16; x++) { pantsSum += pants.getRGB(x, y) & 255; }
        }
        assertTrue(horizontalDifferences >= 15);
        assertTrue(verticalDifferences >= 15);
        assertTrue(Math.abs(jacketSum / 72 - pantsSum / 36) < 12);
    }

    @Test
    void recipeUsesSixFabricAndTwoThreadsWithExplicitSynthetic() throws IOException {
        var recipe = json(Path.of("src/generated/resources/data/createapparel/recipes/denim_jacket.json"));
        assertEquals(6, recipe.getAsJsonObject("fabric").get("count").getAsInt());
        assertEquals(2, recipe.getAsJsonObject("thread").get("count").getAsInt());
        assertEquals(List.of("createapparel:fabrics/cotton", "createapparel:fabrics/hemp", "createapparel:fabrics/synthetic"),
                recipe.getAsJsonObject("fabric").getAsJsonArray("tag").asList().stream().map(e -> e.getAsString()).toList());
    }

    private static BufferedImage image(String name, String layer) throws IOException {
        return ImageIO.read(ASSETS.resolve("textures/garment/denim_jacket/" + name + "/" + layer + "/default.png").toFile());
    }

    private static ClientGarmentAssets.GarmentPartModel part(String name) throws IOException {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:denim_jacket/" + name),
                json(ASSETS.resolve("garment_models/parts/denim_jacket/" + name + ".json")));
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
