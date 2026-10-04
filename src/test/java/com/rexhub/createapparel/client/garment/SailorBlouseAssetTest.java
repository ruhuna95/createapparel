package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
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

import static org.junit.jupiter.api.Assertions.*;

class SailorBlouseAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final Path PARTS = ASSETS.resolve("garment_models/parts/sailor_blouse");

    @Test
    void bothSleeveItemsResolveForEveryBodyProfileWithTheirOwnColorChannels() throws IOException {
        var template = ClientGarmentAssetLoader.parseTemplate(id("sailor_blouse"),
                json(ASSETS.resolve("garment_models/templates/sailor_blouse.json")));
        assertEquals(Set.of("shortsleeves", "longsleeves"), template.variants().keySet());
        for (String variant : template.variants().keySet()) {
            var definition = GarmentDefinition.fromJson(id("sailor_blouse_" + variant), json(Path.of(
                    "src/main/resources/data/createapparel/garments/sailor_blouse_" + variant + ".json")));
            assertEquals(GarmentSlot.TOP, definition.defaultSlot());
            assertEquals(Set.of(GarmentSlot.TOP), definition.allowedSlots());
            assertEquals(Set.of("plain"), definition.designs());
            assertTrue(definition.styles().isEmpty());
            assertEquals(variant, definition.modelVariant());
            var parts = template.variants().get(variant);
            for (ArmProfile arm : ArmProfile.values()) {
                for (ChestProfile chest : ChestProfile.values()) {
                    var selected = GarmentPartSelection.select(Map.of(), arm, chest, parts.keySet());
                    assertEquals(chest == ChestProfile.BUST ? 4 : 3, selected.size());
                    for (String semantic : selected) {
                        var resource = parts.get(semantic);
                        assertNotNull(resource);
                        assertTrue(resource.getPath().startsWith("sailor_blouse/"));
                        var part = part(resource.getPath().substring("sailor_blouse/".length()));
                        assertNotNull(part.variants().get(definition.textureVariant()));
                        if (semantic.contains("sleeve")) {
                            boolean left = semantic.startsWith("left_");
                            for (var element : part.elements()) {
                                assertEquals(left, element.from().x() > 0);
                                assertTrue(element.name().startsWith(left ? "left_" : "right_"));
                            }
                            assertEquals(variant.equals("longsleeves"), part.designs().containsKey("plain"));
                        } else {
                            var masks = part.designs().get("plain");
                            assertTrue(masks.secondary().isPresent());
                            assertTrue(masks.accent().isPresent());
                        }
                    }
                }
            }
        }
    }

    @Test
    void cuffsHaveThreeOpaqueRowsIncludingTheirInnerFacesAndLinesStayFixedWhite() throws IOException {
        for (String profile : List.of("classic", "slim")) {
            for (String side : List.of("left", "right")) {
                var sleeve = part(side + "_long_sleeve_" + profile);
                var body = sleeve.elements().get(0);
                var cuff = sleeve.elements().get(1);
                Face inner = side.equals("left") ? Face.LEFT : Face.RIGHT;
                assertEquals(3.995F, side.equals("left") ? body.from().x() : -body.to().x());
                assertEquals(3.9945F, side.equals("left") ? cuff.from().x() : -cuff.to().x());
                assertFalse(body.faces().containsKey(Face.BOTTOM));
                assertEquals(Set.of(Face.LEFT, Face.FRONT, Face.RIGHT, Face.BACK), cuff.faces().keySet());
                var texture = sleeve.variants().get("default");
                var base = image(texture.base());
                var mask = image(sleeve.designs().get("plain").secondary().orElseThrow());
                var overlay = image(texture.overlay().orElseThrow());
                for (var uv : cuff.faces().values()) {
                    for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                        for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                            assertEquals(y >= 25 ? 255 : 0, base.getRGB(x, y) >>> 24);
                            assertEquals(y >= 25 ? 255 : 0, mask.getRGB(x, y) & 255);
                            assertEquals(y == 26 ? 255 : 0, overlay.getRGB(x, y) >>> 24);
                        }
                    }
                }
                assertNotNull(cuff.faces().get(inner));
                assertEquals(1, part(side + "_short_sleeve_" + profile).elements().size());
            }
        }
        var bust = part("bust");
        for (var element : bust.elements()) {
            assertFalse(element.faces().containsKey(Face.TOP));
            assertFalse(element.faces().containsKey(Face.BACK));
        }
        assertEquals(bust.elements().get(0).rotation(), bust.elements().get(1).rotation());
        assertEquals(-1.79F, bust.elements().get(1).from().z());
        var torso = part("torso");
        assertFalse(torso.elements().get(0).faces().containsKey(Face.BOTTOM));
        assertEquals(Set.of(Face.FRONT, Face.BACK, Face.TOP), torso.elements().get(1).faces().keySet());
        var base = image(torso.variants().get("default").base());
        var secondary = image(torso.designs().get("plain").secondary().orElseThrow());
        var accent = image(torso.designs().get("plain").accent().orElseThrow());
        assertEquals(255, base.getRGB(7, 20) >>> 24);
        assertEquals(0, secondary.getRGB(7, 20) & 255);
        assertEquals(0, accent.getRGB(7, 20) & 255);
        assertEquals(255, accent.getRGB(7, 24) & 255);
        assertEquals(255, secondary.getRGB(16, 18) & 255);
    }

    @Test
    void shorterTorsoHasAnOpenFrontNeckAndSharesItsDecorationWithTheForkedScarf() throws IOException {
        var torso = part("torso");
        assertEquals(2, torso.elements().size());
        var body = torso.elements().get(0);
        var collar = torso.elements().get(1);
        assertEquals("torso_decoration", collar.name());
        assertEquals(13.995F, body.from().y());
        assertEquals(24.005F, body.to().y());
        for (Face face : List.of(Face.FRONT, Face.BACK, Face.LEFT, Face.RIGHT)) {
            var uv = body.faces().get(face);
            assertEquals(10, uv.v1() - uv.v0());
        }
        assertEquals(body.to().y(), collar.to().y());
        assertEquals(collar.from().z(), -collar.to().z());
        assertEquals(-2.4F, collar.from().z());
        assertTrue(collar.from().z() < body.from().z());
        assertEquals(Set.of(Face.FRONT, Face.BACK, Face.TOP), collar.faces().keySet());
        var base = image(torso.variants().get("default").base());
        var secondary = image(torso.designs().get("plain").secondary().orElseThrow());
        var accent = image(torso.designs().get("plain").accent().orElseThrow());
        for (int x = 6; x < 10; x++) {
            for (int y = 1; y < 4; y++) {
                assertEquals(0, base.getRGB(x, y) >>> 24);
                assertEquals(0, base.getRGB(x, y + 14) >>> 24);
            }
        }
        for (int x : List.of(4, 5, 10, 11)) {
            for (int y = 0; y < 4; y++) {
                assertEquals(0, base.getRGB(x, y) >>> 24);
                assertEquals(255, base.getRGB(x, y + 14) >>> 24);
                assertEquals(255, secondary.getRGB(x, y + 14) & 255);
            }
        }
        for (int x : List.of(6, 8, 9)) {
            assertEquals(255, base.getRGB(x, 25) >>> 24);
            assertEquals(255, accent.getRGB(x, 25) & 255);
        }
        assertEquals(0, base.getRGB(7, 25) >>> 24);
        var bust = part("bust");
        assertEquals(2, bust.elements().size());
        var bustDecoration = bust.elements().get(1);
        assertEquals("bust_decoration", bustDecoration.name());
        assertEquals(bust.elements().get(0).rotation(), bustDecoration.rotation());
        assertEquals(-1.79F, bustDecoration.from().z());
        assertEquals(19.12F, bustDecoration.from().y());
        assertEquals(Set.of(Face.FRONT, Face.BOTTOM, Face.LEFT, Face.RIGHT), bustDecoration.faces().keySet());
        var bustBase = image(bust.variants().get("default").base());
        var bustAccent = image(bust.designs().get("plain").accent().orElseThrow());
        for (int y = 7; y < 10; y++) {
            for (int x = 12; x < 15; x++) {
                assertEquals(255, bustBase.getRGB(x, y) >>> 24);
                assertEquals(255, bustAccent.getRGB(x, y) & 255);
            }
        }
    }

    @Test
    void rowAboveScarfColorsItsOuterPixelsAccentAndItsCenterPixelsSecondary() throws IOException {
        var torso = part("torso");
        var base = image(torso.variants().get("default").base());
        var overlay = image(torso.variants().get("default").overlay().orElseThrow());
        var secondary = image(torso.designs().get("plain").secondary().orElseThrow());
        var accent = image(torso.designs().get("plain").accent().orElseThrow());
        for (int x : List.of(6, 9)) {
            assertEquals(255, base.getRGB(x, 21) >>> 24);
            assertEquals(0, secondary.getRGB(x, 21) & 255);
            assertEquals(255, accent.getRGB(x, 21) & 255);
            assertEquals(0, overlay.getRGB(x, 21) >>> 24);
        }
        for (int x : List.of(7, 8)) {
            assertEquals(255, base.getRGB(x, 21) >>> 24);
            assertEquals(255, secondary.getRGB(x, 21) & 255);
            assertEquals(0, accent.getRGB(x, 21) & 255);
            assertEquals(0, overlay.getRGB(x, 21) >>> 24);
        }
    }

    @Test
    void scarfUvIncludesTheAuthoredBottomPixelWithoutCompressingItsHeight() throws IOException {
        var torso = part("torso");
        var scarf = torso.elements().get(1);
        var uv = scarf.faces().get(Face.FRONT);
        assertEquals(15, scarf.from().y());
        assertEquals(24.005F, scarf.to().y());
        assertEquals(18, uv.v0());
        assertEquals(27, uv.v1());
        assertEquals(9, uv.v1() - uv.v0());
        assertEquals(uv.v1() - uv.v0(), scarf.to().y() - scarf.from().y(), 0.006F);
        var base = image(torso.variants().get("default").base());
        var accent = image(torso.designs().get("plain").accent().orElseThrow());
        assertEquals(255, base.getRGB(8, 26) >>> 24);
        assertEquals(255, accent.getRGB(8, 26) & 255);
        for (int y = (int) uv.v0(); y < base.getHeight(); y++) {
            for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                if ((base.getRGB(x, y) >>> 24) == 255) {
                    assertTrue(y < uv.v1(), "Authored scarf pixel outside UV: " + x + "," + y);
                }
            }
        }
    }

    @Test
    void everyCollarScarfAndLinePixelBelongsOnlyToTheSingleDecoration() throws IOException {
        for (String name : List.of("torso", "bust")) {
            var part = part(name);
            assertEquals(2, part.elements().size());
            var body = part.elements().get(0);
            var decoration = part.elements().get(1);
            var base = image(part.variants().get("default").base());
            var secondary = image(part.designs().get("plain").secondary().orElseThrow());
            var accent = image(part.designs().get("plain").accent().orElseThrow());
            var overlay = image(part.variants().get("default").overlay().orElseThrow());
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < base.getWidth(); x++) {
                    boolean colored = (base.getRGB(x, y) >>> 24) != 0
                            && ((secondary.getRGB(x, y) & 255) != 0 || (accent.getRGB(x, y) & 255) != 0);
                    if (!colored && (overlay.getRGB(x, y) >>> 24) == 0) continue;
                    boolean inBody = false;
                    for (var uv : body.faces().values()) {
                        inBody |= x >= uv.u0() && x < uv.u1() && y >= uv.v0() && y < uv.v1();
                    }
                    boolean inDecoration = false;
                    for (var uv : decoration.faces().values()) {
                        inDecoration |= x >= uv.u0() && x < uv.u1() && y >= uv.v0() && y < uv.v1();
                    }
                    String pixel = name + " / " + x + "," + y;
                    assertFalse(inBody, pixel);
                    assertTrue(inDecoration, pixel);
                }
            }
        }
    }

    @Test
    void texturesMatchUvSizesAndLayerFormatsAndEveryAuthoredFaceHasOpaquePixels() throws IOException {
        Set<ResourceLocation> checked = new HashSet<>();
        try (var paths = Files.list(PARTS)) {
            for (Path path : paths.toList()) {
                var part = part(path.getFileName().toString().replace(".json", ""));
                var texture = part.variants().get("default");
                assertTrue(texture.brightnessMask().isEmpty());
                var base = image(texture.base());
                assertEquals(part.textureWidth(), base.getWidth());
                assertEquals(part.textureHeight(), base.getHeight());
                assertEquals(base.getWidth(), base.getHeight());
                assertEquals(0, base.getWidth() & (base.getWidth() - 1));
                for (var element : part.elements()) {
                    for (var uv : element.faces().values()) {
                        assertTrue(uv.u0() >= 0 && uv.v0() >= 0);
                        assertTrue(uv.u1() <= base.getWidth() && uv.v1() <= base.getHeight());
                        boolean opaque = false;
                        for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                            for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                                opaque |= (base.getRGB(x, y) >>> 24) == 255;
                            }
                        }
                        assertTrue(opaque, path + " / " + element.name());
                    }
                }
                var resources = new HashSet<ResourceLocation>();
                resources.add(texture.base());
                texture.overlay().ifPresent(resources::add);
                for (var masks : part.designs().values()) {
                    masks.secondary().ifPresent(resources::add);
                    masks.accent().ifPresent(resources::add);
                }
                for (var resource : resources) {
                    if (!checked.add(resource)) continue;
                    var image = image(resource);
                    assertEquals(base.getWidth(), image.getWidth());
                    assertEquals(base.getHeight(), image.getHeight());
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            int pixel = image.getRGB(x, y);
                            int alpha = pixel >>> 24;
                            assertTrue(alpha == 0 || alpha == 255);
                            assertEquals(pixel & 255, pixel >>> 8 & 255);
                            assertEquals(pixel & 255, pixel >>> 16 & 255);
                            if (resource.getPath().contains("/masks/")) assertEquals(255, alpha);
                            if (resource.getPath().contains("/overlay/") && alpha != 0) {
                                assertTrue((pixel & 255) >= 240);
                            }
                        }
                    }
                }
            }
        }
        assertEquals(16, checked.size());
    }

    private static GarmentPartModel part(String name) throws IOException {
        return ClientGarmentAssetLoader.parsePart(id("sailor_blouse/" + name), json(PARTS.resolve(name + ".json")));
    }

    @Test
    void generatedRecipesModelsAndNamesUseTheRequestedItemsAndMaterials() throws IOException {
        Path generated = Path.of("src/generated/resources");
        for (String variant : List.of("shortsleeves", "longsleeves")) {
            String garment = "sailor_blouse_" + variant;
            var recipe = json(generated.resolve("data/createapparel/recipes/" + garment + ".json"));
            assertEquals("createapparel:sewing", recipe.get("type").getAsString());
            assertEquals("createapparel:" + garment, recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(4, recipe.getAsJsonObject("fabric").get("count").getAsInt());
            assertEquals(1, recipe.getAsJsonObject("thread").get("count").getAsInt());
            var tags = new HashSet<String>();
            recipe.getAsJsonObject("fabric").getAsJsonArray("tag").forEach(tag -> tags.add(tag.getAsString()));
            assertEquals(Set.of("createapparel:fabrics/cotton", "createapparel:fabrics/wool",
                    "createapparel:fabrics/synthetic"), tags);
            var model = json(generated.resolve("assets/createapparel/models/item/" + garment + ".json"));
            assertEquals("minecraft:builtin/entity", model.get("parent").getAsString());
            assertFalse(Files.exists(ASSETS.resolve("models/item/" + garment + ".json")));
            String nameKey = "item.createapparel." + garment;
            String japanese = variant.equals("shortsleeves") ? "半袖セーラー服" : "長袖セーラー服";
            String english = variant.equals("shortsleeves") ? "Short Sleeve Sailor Blouse" : "Long Sleeve Sailor Blouse";
            assertEquals(japanese, json(generated.resolve("assets/createapparel/lang/ja_jp.json")).get(nameKey).getAsString());
            assertEquals(english, json(generated.resolve("assets/createapparel/lang/en_us.json")).get(nameKey).getAsString());
        }
    }

    private static BufferedImage image(ResourceLocation resource) throws IOException {
        return ImageIO.read(ASSETS.resolve("textures/" + resource.getPath() + ".png").toFile());
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("createapparel", name);
    }
}
