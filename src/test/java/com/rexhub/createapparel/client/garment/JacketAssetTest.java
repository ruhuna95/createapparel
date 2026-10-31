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

class JacketAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final List<String> PARTS = List.of("torso", "torso_opened", "bust", "bust_opened",
            "left_sleeve_classic", "right_sleeve_classic", "left_sleeve_slim", "right_sleeve_slim");

    @Test
    void jacketsResolveOpenAndClosedForAllBodyProfiles() throws IOException {
        var template = json(ASSETS.resolve("garment_models/templates/jacket.json")).getAsJsonObject("default");
        assertEquals(Set.copyOf(PARTS), template.keySet());
        for (String variant : List.of("suit", "blazer")) {
            var definition = GarmentDefinition.fromJson(ResourceLocation.parse("createapparel:" + variant + "_jacket"),
                    json(Path.of("src/main/resources/data/createapparel/garments/" + variant + "_jacket.json")));
            assertEquals(GarmentSlot.OUTER, definition.defaultSlot());
            assertEquals(Set.of(GarmentSlot.OUTER), definition.allowedSlots());
            assertEquals(Map.of("open", List.of("closed", "open")), definition.styles());
            assertEquals(variant, definition.textureVariant());
            assertEquals("default", definition.modelVariant());
            for (var arms : ArmProfile.values()) {
                for (var chest : ChestProfile.values()) {
                    for (String style : List.of("closed", "open")) {
                        var selected = GarmentPartSelection.select(Map.of("open", style), arms, chest, template.keySet());
                        assertEquals(style.equals("open") ? "torso_opened" : "torso", selected.get(0));
                        assertEquals(chest == ChestProfile.BUST, selected.contains(style.equals("open") ? "bust_opened" : "bust"));
                        assertEquals(chest == ChestProfile.BUST ? 4 : 3, selected.size());
                        for (String name : selected) {
                            assertEquals("createapparel:jacket/" + name, template.getAsJsonObject(name).get("model").getAsString());
                            assertTrue(part(name).variants().containsKey(variant));
                        }
                    }
                }
            }
        }
        assertEquals(Set.of("torso_opened", "bust_opened"), GarmentPartSelection.requiredStyleParts(
                Map.of("open", List.of("closed", "open")), true));
        assertEquals(GarmentPartSelection.select(Map.of("open", "opened"), ArmProfile.CLASSIC, ChestProfile.BUST, true),
                GarmentPartSelection.select(Map.of("open", "open"), ArmProfile.CLASSIC, ChestProfile.BUST, true));
    }

    @Test
    void partsRespectOffsetsSidesAndBustBottomException() throws IOException {
        for (String name : PARTS) {
            var model = part(name);
            var body = model.elements().get(0);
            var decoration = model.elements().get(1);
            assertTrue(model.designs().isEmpty());
            assertEquals(body.from().z() - 0.25F, decoration.from().z(), 0.00001F);
            assertEquals(body.to().y(), decoration.to().y());
            for (var element : model.elements()) {
                assertEquals(name.startsWith("bust"), element.faces().containsKey(BOTTOM), name);
                if (name.startsWith("bust")) {
                    assertFalse(element.faces().containsKey(TOP));
                    assertFalse(element.faces().containsKey(BACK));
                    assertEquals(23.3426F, element.to().y());
                }
                if (name.startsWith("left_")) { assertTrue(element.from().x() > 0); }
                if (name.startsWith("right_")) { assertTrue(element.to().x() < 0); }
            }
            if (name.startsWith("torso")) {
                assertEquals(16F, body.from().y());
                var hem = model.elements().stream().filter(e -> e.name().endsWith("_hem")).findFirst().orElseThrow();
                assertEquals(10.99F, hem.from().y());
                assertEquals(-4.1F, hem.from().x());
                assertEquals(4.1F, hem.to().x());
                var waist = model.elements().stream().filter(e -> e.name().endsWith("_waist")).findFirst().orElseThrow();
                for (int i = 0; i < 4; i++) {
                    assertEquals(body.corners().get(i).x(), waist.corners().get(i + 4).x(), 0.00001F);
                    assertEquals(body.corners().get(i).y(), waist.corners().get(i + 4).y());
                    assertEquals(body.corners().get(i).z(), waist.corners().get(i + 4).z());
                    assertEquals(waist.corners().get(i), hem.corners().get(i + 4));
                }
                for (var face : List.of(LEFT, FRONT, RIGHT, BACK)) {
                    assertEquals(body.faces().get(face).v1(), waist.faces().get(face).v0());
                    assertEquals(waist.faces().get(face).v1(), hem.faces().get(face).v0());
                }
                assertEquals(-4.01F, body.from().x());
                assertEquals(-2.5F, body.from().z());
                assertEquals(Set.of(FRONT, BACK), decoration.faces().keySet());
            } else if (name.contains("sleeve")) {
                assertEquals(11.5F, body.from().y());
                assertEquals(24.01F, body.to().y());
                assertEquals(Set.of(name.startsWith("left") ? RIGHT : LEFT), decoration.faces().keySet());
            }
        }
    }

    @Test
    void everyFaceHasPixelsAndTexturesAreGrayscaleWithBinaryAlpha() throws IOException {
        for (String name : PARTS) {
            var model = part(name);
            for (String variant : List.of("suit", "blazer")) {
                var texture = model.variants().get(variant);
                assertTrue(texture.overlay().isEmpty());
                assertTrue(texture.brightnessMask().isEmpty());
                var image = image(name, variant);
                assertEquals(model.textureWidth(), image.getWidth());
                assertEquals(model.textureHeight(), image.getHeight());
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = image.getRGB(x, y);
                        assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
                        assertEquals(pixel & 255, pixel >> 8 & 255);
                        assertEquals(pixel & 255, pixel >> 16 & 255);
                    }
                }
                for (var element : model.elements()) {
                    for (var uv : element.faces().values()) {
                        int opaque = 0;
                        for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                            for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                                if (image.getRGB(x, y) >>> 24 == 255) { opaque++; }
                            }
                        }
                        if (variant.equals("blazer") && element.name().endsWith("_waist_decoration")) {
                            assertEquals(0, opaque);
                        } else {
                            assertTrue(opaque > 0, name + ": " + variant + " / " + element.name());
                        }
                    }
                }
            }
        }
    }

    @Test
    void lengthsOpeningsButtonsAndPocketsDifferAsSpecified() throws IOException {
        for (String variant : List.of("suit", "blazer")) {
            var closed = image("torso", variant);
            var opened = image("torso_opened", variant);
            assertEquals(255, closed.getRGB(7, 10) >>> 24);
            for (int y = 4; y < 16; y++) {
                for (int x : List.of(6, 7, 8, 9)) {
                    assertEquals(0, opened.getRGB(x, y) >>> 24);
                    assertEquals(0, opened.getRGB(x, y + 13) >>> 24);
                }
            }
            assertEquals(118, closed.getRGB(8, 23) & 255);
            assertEquals(118, opened.getRGB(10, 23) & 255);
            assertEquals(0, closed.getRGB(9, 23) >>> 24);
            assertEquals(0, opened.getRGB(5, 23) >>> 24);
            for (int y = 6; y < 9; y++) {
                assertEquals(255, closed.getRGB(6, y) >>> 24);
                assertEquals(0, closed.getRGB(7, y) >>> 24);
                assertEquals(255, closed.getRGB(5, y) >>> 24);
                assertEquals(0, closed.getRGB(8, y) >>> 24);
                assertEquals(255, closed.getRGB(9, y) >>> 24);
            }
            assertEquals(0, closed.getRGB(7, 23) >>> 24);
            assertTrue((closed.getRGB(9, 20) & 255) >= 240);
            assertTrue((opened.getRGB(5, 20) & 255) >= 240);
            for (String name : List.of("torso", "torso_opened", "bust", "bust_opened")) {
                var pixels = image(name, variant);
                boolean torso = name.startsWith("torso");
                int u = torso ? 4 : 3;
                int v = torso ? 4 : 0;
                int decorationV = torso ? 17 : 4;
                for (int row = 0; row < (torso ? 5 : 4); row++) {
                    for (int x = 0; x < 4; x++) {
                        assertEquals(pixels.getRGB(u + x, v + row) >>> 24,
                                pixels.getRGB(u + 7 - x, v + row) >>> 24, name);
                        assertEquals(pixels.getRGB(u + x, decorationV + row),
                                pixels.getRGB(u + 7 - x, decorationV + row), name);
                    }
                }
            }
            for (int x = 0; x < 24; x++) {
                assertEquals(variant.equals("suit") ? 255 : 0, closed.getRGB(x, 16) >>> 24);
            }
            assertEquals(variant.equals("suit") ? 255 : 0, closed.getRGB(4, 25) >>> 24);
            var recipe = json(Path.of("src/generated/resources/data/createapparel/recipe/" + variant + "_jacket.json"));
            assertEquals(6, recipe.getAsJsonObject("fabric").get("count").getAsInt());
            assertEquals(2, recipe.getAsJsonObject("thread").get("count").getAsInt());
            String fabrics = variant.equals("suit") ? "linen,wool,silk,synthetic" : "cotton,linen,wool,synthetic";
            assertEquals(List.of(fabrics.split(",")).stream().map(s -> "createapparel:fabrics/" + s).toList(),
                    recipe.getAsJsonObject("fabric").getAsJsonArray("tag").asList().stream().map(e -> e.getAsString()).toList());
        }
    }

    @Test
    void bustUndersideContinuesTheOpeningAndLapelAndSleeveButtonsMoveInward() throws IOException {
        for (String variant : List.of("suit", "blazer")) {
            for (String name : List.of("bust", "bust_opened")) {
                var pixels = image(name, variant);
                for (int x = 0; x < 8; x++) {
                    for (int row = 0; row < 3; row++) {
                        assertEquals(pixels.getRGB(3 + x, 3), pixels.getRGB(x, 8 + row));
                        assertEquals(pixels.getRGB(3 + x, 7), pixels.getRGB(x, 11 + row));
                    }
                }
                if (name.endsWith("opened")) {
                    for (int x = 5; x < 9; x++) {
                        for (int row = 0; row < 8; row++) { assertEquals(0, pixels.getRGB(x, row) >>> 24); }
                    }
                }
                var model = part(name);
                var front = GarmentElementGeometry.faceVertices(model.elements().get(1), FRONT);
                var bottom = GarmentElementGeometry.faceVertices(model.elements().get(1), BOTTOM);
                assertEquals(front.get(0), bottom.get(1));
                assertEquals(front.get(3), bottom.get(2));
            }
            for (String name : PARTS.stream().filter(n -> n.contains("sleeve")).toList()) {
                var pixels = image(name, variant);
                var uv = part(name).elements().get(1).faces().values().iterator().next();
                int oldX = name.startsWith("left") ? (int) uv.u1() - 1 : (int) uv.u0();
                int newX = name.startsWith("left") ? oldX - 1 : oldX + 1;
                for (int y : List.of(24, 26)) {
                    assertEquals(0, pixels.getRGB(oldX, y) >>> 24);
                    assertEquals(126, pixels.getRGB(newX, y) & 255);
                    assertEquals(255, pixels.getRGB(newX, y) >>> 24);
                }
            }
        }
    }

    @Test
    void lowerJacketKeepsClearanceFromPantsSides() throws IOException {
        for (String pants : List.of("denim_pants", "slacks")) {
            var leg = ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:" + pants + "/left_leg"),
                    json(ASSETS.resolve("garment_models/parts/" + pants + "/left_leg.json"))).elements().get(0);
            for (float y : new float[]{10.99F, 11.5F, 12.005F}) {
                float t = (y - leg.from().y()) / (leg.to().y() - leg.from().y());
                float outer = leg.corners().get(1).x() + t * (leg.corners().get(5).x() - leg.corners().get(1).x());
                assertTrue(4.1F - outer > 0.025F, pants + " y=" + y);
            }
        }
    }

    @Test
    void widenedShortsKeepFrontClearanceButExtendPastLowerJacketSides() throws IOException {
        var shorts = ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:shorts/left_leg"),
                json(ASSETS.resolve("garment_models/parts/shorts/left_leg.json"))).elements().get(0);
        float y = 10.99F;
        float t = (y - shorts.from().y()) / (shorts.to().y() - shorts.from().y());
        float outer = shorts.corners().get(1).x() + t * (shorts.corners().get(5).x() - shorts.corners().get(1).x());
        assertEquals(4.1274F, outer, .0001F);
        assertTrue(outer > 4.1F);
        assertTrue(2.5F - Math.abs(shorts.from().z()) >= .25F);
    }

    private static BufferedImage image(String name, String variant) throws IOException {
        return ImageIO.read(ASSETS.resolve("textures/garment/jacket/" + name + "/base/" + variant + ".png").toFile());
    }

    private static ClientGarmentAssets.GarmentPartModel part(String name) throws IOException {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:jacket/" + name),
                json(ASSETS.resolve("garment_models/parts/jacket/" + name + ".json")));
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
