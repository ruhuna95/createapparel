package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentModelTemplate;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DressCollectionAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final Map<String, List<String>> LENGTHS = new LinkedHashMap<>();
    static {
        LENGTHS.put("a_line_dress", List.of("knee", "midi"));
        LENGTHS.put("fit_and_flare_dress", List.of("knee", "midi"));
        LENGTHS.put("straight_dress", List.of("mini", "knee", "midi"));
        LENGTHS.put("shirt_dress", List.of("knee", "midi"));
        LENGTHS.put("jumper_dress", List.of("mini", "knee", "midi"));
        LENGTHS.put("camisole_dress", List.of("knee", "midi", "long", "maxi"));
        LENGTHS.put("bodycon_dress", List.of("mini", "knee", "midi"));
    }

    private static boolean layered(String kind) {
        return kind.equals("jumper_dress") || kind.equals("camisole_dress");
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static GarmentDefinition definition(String id) throws Exception {
        return GarmentDefinition.fromJson(ResourceLocation.parse("createapparel:" + id),
                json(Path.of("src/main/resources/data/createapparel/garments/" + id + ".json")));
    }

    private static GarmentModelTemplate template(String kind) throws Exception {
        return ClientGarmentAssetLoader.parseTemplate(ResourceLocation.parse("createapparel:" + kind),
                json(ASSETS.resolve("garment_models/templates/" + kind + ".json")));
    }

    private static ResolvedGarment resolve(String id, ArmProfile arm, ChestProfile chest) throws Exception {
        var definition = definition(id);
        var mapping = template(definition.modelTemplate().getPath()).variants().get(definition.modelVariant());
        var data = new GarmentData(FabricMaterial.SYNTHETIC, "plain", null, Optional.empty(), Optional.empty());
        List<ResolvedPart> parts = new ArrayList<>();
        for (String semantic : GarmentPartSelection.select(Map.of(), arm, chest, mapping.keySet())) {
            assertTrue(mapping.containsKey(semantic), id + " / " + semantic);
            var part = DualEnvelopeDeformerTest.part(mapping.get(semantic).getPath());
            parts.add(new ResolvedPart(semantic, part, part.resolveTextures(definition, data)));
        }
        return new ResolvedGarment(definition, data, List.copyOf(parts));
    }

    private static BufferedImage texture(ResourceLocation id) throws Exception {
        return ImageIO.read(ASSETS.resolve("textures/" + id.getPath() + ".png").toFile());
    }

    private static List<String> variants(String kind) {
        List<String> variants = new ArrayList<>();
        for (String length : LENGTHS.get(kind)) {
            if (layered(kind)) {
                variants.add(length + "_shortsleeves");
                variants.add(length + "_longsleeves");
            } else variants.add(length);
        }
        return variants;
    }

    @Test
    void allTwentySixItemsResolveAcrossBodyProfilesAndKeepLengthsInSevenTemplates() throws Exception {
        int count = 0;
        for (String kind : LENGTHS.keySet()) {
            assertEquals(variants(kind), List.copyOf(template(kind).variants().keySet()));
            for (String variant : variants(kind)) {
                String id = kind + "_" + variant;
                var definition = definition(id);
                assertEquals(GarmentSlot.TOP, definition.defaultSlot());
                assertEquals(Set.of(GarmentSlot.TOP), definition.allowedSlots());
                assertEquals(Set.of("plain"), definition.designs());
                assertTrue(definition.styles().isEmpty());
                for (var arm : ArmProfile.values()) for (var chest : ChestProfile.values()) {
                    var garment = resolve(id, arm, chest);
                    assertEquals(chest == ChestProfile.BUST ? 4 : 3, garment.parts().size(), id);
                    for (var part : garment.parts()) {
                        var base = texture(part.textures().base());
                        assertEquals(part.model().textureWidth(), base.getWidth(), id);
                        assertEquals(part.model().textureHeight(), base.getHeight(), id);
                        assertEquals(layered(kind), part.textures().secondary().isPresent(), id);
                        assertTrue(part.textures().accent().isEmpty(), id);
                        assertEquals(kind.equals("shirt_dress") && Set.of("torso", "bust").contains(part.semanticName()),
                                part.textures().brightnessMask().isPresent(), id);
                        assertEquals(1, part.textures().brightness());
                    }
                }
                count++;
            }
        }
        assertEquals(26, count);
    }

    @Test
    void skirtPanelBrightnessMatchesEachBodiceFaceWithoutFlatteningClothShading() throws Exception {
        for (String kind : LENGTHS.keySet()) for (String length : LENGTHS.get(kind)) {
            String id = kind + "/" + length + "/torso";
            var part = DualEnvelopeDeformerTest.part(id);
            var base = texture(part.defaultTexture());
            var bodice = part.elements().get(0);
            var panels = part.elements().stream()
                    .filter(e -> e.name().contains("_panel") && !e.name().equals("front_panel_decoration"))
                    .toList();
            for (var face : List.of(Face.FRONT, Face.BACK, Face.LEFT, Face.RIGHT)) {
                double upperSum = 0, lowerSum = 0;
                int upperCount = 0, lowerCount = 0, minimum = 255, maximum = 0;
                var uv = bodice.faces().get(face);
                for (int y = (int) uv.v0(); y < uv.v1(); y++) for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                    int pixel = base.getRGB(x, y);
                    if (pixel >>> 24 == 0) continue;
                    upperSum += pixel & 255;
                    upperCount++;
                }
                for (var panel : panels) {
                    uv = panel.faces().get(face);
                    if (uv == null) continue;
                    for (int y = (int) uv.v0(); y < uv.v1(); y++) for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                        int pixel = base.getRGB(x, y);
                        if (pixel >>> 24 == 0) continue;
                        assertEquals(255, pixel >>> 24, id);
                        int value = pixel & 255;
                        assertEquals(value, pixel >> 8 & 255, id);
                        assertEquals(value, pixel >> 16 & 255, id);
                        lowerSum += value;
                        lowerCount++;
                        minimum = Math.min(minimum, value);
                        maximum = Math.max(maximum, value);
                    }
                }
                assertTrue(upperCount > 0 && lowerCount > 0, id + " / " + face);
                assertEquals(upperSum / upperCount, lowerSum / lowerCount, 1, id + " / " + face);
                assertTrue(maximum > minimum, id + " / " + face);
            }
        }
    }

    @Test
    void skirtsJoinTheBodiceAtTopOffsetAndRetainAuthoredLengthsAndOpenHems() throws Exception {
        Map<String, Integer> heights = Map.of("mini", 6, "knee", 8, "midi", 10, "long", 12, "maxi", 13);
        for (String kind : LENGTHS.keySet()) for (String length : LENGTHS.get(kind)) {
            var part = DualEnvelopeDeformerTest.part(kind + "/" + length + "/torso");
            var geometry = part.legEnvelope().orElseThrow().geometry();
            assertEquals(14, geometry.top());
            assertEquals(14 - heights.get(length) - .004F, geometry.bottom(), .000002F);
            assertEquals(-4.005F, geometry.boundary(0, 0, true), .000002F);
            assertEquals(-2.25F, geometry.boundary(0, 0, false), .000002F);
            assertEquals(14, part.elements().get(0).from().y());
            assertTrue(part.elements().stream().noneMatch(e -> e.faces().containsKey(Face.BOTTOM)));
            if (kind.equals("straight_dress")) {
                assertEquals(geometry.boundary(0, 0, true), geometry.boundary(0, 1, true));
                assertEquals(geometry.boundary(0, 0, false), geometry.boundary(0, 1, false));
            }
            assertEquals(kind.equals("fit_and_flare_dress"), geometry.segmented());
        }
    }

    @Test
    void layeredBodicesHaveVisibleInnerColorWithoutPaintingTheOuterDressSecondary() throws Exception {
        for (String kind : List.of("jumper_dress", "camisole_dress")) {
            var garment = resolve(kind + "_knee_shortsleeves", ArmProfile.CLASSIC, ChestProfile.BUST);
            var torso = garment.parts().get(0);
            var outer = torso.model().elements().get(0);
            var inner = torso.model().elements().get(1);
            assertTrue(inner.from().x() > outer.from().x());
            assertTrue(inner.from().z() > outer.from().z());
            assertTrue(inner.to().z() < outer.to().z());
            assertTrue(inner.from().z() < -2);
            assertEquals(0, texture(torso.textures().base()).getRGB(6, 5) >>> 24);
            assertEquals(255, texture(torso.textures().base()).getRGB(6, 21) >>> 24);
            for (var part : garment.parts()) {
                var base = texture(part.textures().base());
                var mask = texture(part.textures().secondary().orElseThrow());
                assertEquals(base.getWidth(), mask.getWidth());
                assertEquals(base.getHeight(), mask.getHeight());
                for (int y = 0; y < base.getHeight(); y++) for (int x = 0; x < base.getWidth(); x++) {
                    int b = base.getRGB(x, y), m = mask.getRGB(x, y);
                    assertTrue(b >>> 24 == 0 || b >>> 24 == 255);
                    assertEquals(255, m >>> 24);
                    assertEquals(m >> 16 & 255, m >> 8 & 255);
                    assertEquals(m >> 16 & 255, m & 255);
                    assertEquals(b >> 16 & 255, b >> 8 & 255);
                    assertEquals(b >> 16 & 255, b & 255);
                }
            }
        }
    }

    @Test
    void topSlotDrivesCrouchingEnvelopeAndOuterHemWhileKeepingTheUpperBodyFixed() throws Exception {
        for (String kind : LENGTHS.keySet()) for (String variant : variants(kind)) {
            var garment = resolve(kind + "_" + variant, ArmProfile.SLIM, ChestProfile.BUST);
            var torso = garment.parts().get(0).model();
            for (boolean crouching : List.of(false, true)) {
                var player = DualEnvelopeDeformerTest.player();
                player.leftLeg.xRot = -.7F;
                player.rightLeg.xRot = .7F;
                if (crouching) {
                    player.crouching = true;
                    player.body.xRot = .5F;
                    player.body.y = 3.2F;
                    player.leftLeg.y = player.rightLeg.y = 12.2F;
                    player.leftLeg.z = player.rightLeg.z = 4;
                }
                var frame = new ApparelRenderFrame(slot -> slot == GarmentSlot.TOP
                        ? Optional.of(garment) : Optional.empty(), player);
                var pose = frame.envelope(torso).orElseThrow();
                assertSame(pose, frame.skirtSlope().orElseThrow().envelope());
                assertEquals(crouching, pose.usesBodyEdges());
                var moved = new Vector3f();
                for (var quad : pose.quads()) for (var point : quad.points()) {
                    pose.shift(point, moved);
                    assertTrue(Float.isFinite(moved.x) && Float.isFinite(moved.y) && Float.isFinite(moved.z));
                    if (point.y() >= 14) assertEquals(new Vector3f(point.x(), point.y(), point.z()), moved);
                }
                var outer = DualEnvelopeDeformerTest.part("jacket/torso");
                assertTrue(outer.hemGeometry().applies("torso", GarmentSlot.OUTER, frame.skirtSlope().orElseThrow()));
            }
        }
    }

    @Test
    void dressEnvelopeEnclosesAnUnderlyingSkirtAndDrivesTheOuterCoat() throws Exception {
        var dress = resolve("bodycon_dress_mini", ArmProfile.CLASSIC, ChestProfile.FLAT);
        var dressTorso = dress.parts().get(0).model();
        var skirt = DualEnvelopeDeformerTest.part("flared_skirt_maxi/torso");
        var coat = DualEnvelopeDeformerTest.part("coat/chesterfield/torso");
        var texture = dress.parts().get(0).textures();
        var bottom = new ResolvedGarment(dress.definition(), null, List.of(new ResolvedPart("torso", skirt, texture)));
        var outer = new ResolvedGarment(dress.definition(), null, List.of(new ResolvedPart("torso", coat, texture)));
        var frame = new ApparelRenderFrame(slot -> switch (slot) {
            case TOP -> Optional.of(dress);
            case BOTTOM -> Optional.of(bottom);
            case OUTER -> Optional.of(outer);
            default -> Optional.empty();
        }, DualEnvelopeDeformerTest.player());
        var pose = frame.envelope(dressTorso).orElseThrow();
        assertSame(pose, frame.skirtSlope().orElseThrow().envelope());
        assertTrue(pose.changed());
        assertTrue(frame.envelope(coat).orElseThrow().changed());
        var shifted = new Vector3f();
        pose.shift(0, pose.geometry().bottom(), -2.25F, shifted);
        assertTrue(shifted.z < -2.25F);
    }

    @Test
    void shirtCollarsMatchExistingShirtCutoutsAndBrightnessWithoutBrighteningOtherPixels() throws Exception {
        for (String name : List.of("knee/torso", "midi/torso", "bust")) {
            boolean bust = name.equals("bust");
            var original = DualEnvelopeDeformerTest.part("shirt/" + (bust ? "bust" : "torso"));
            var dress = DualEnvelopeDeformerTest.part("shirt_dress/" + name);
            var originalBase = texture(original.defaultTexture());
            var originalMask = texture(original.variants().get("default").brightnessMask().orElseThrow());
            var dressBase = texture(dress.defaultTexture());
            var dressMask = texture(dress.variants().get("default").brightnessMask().orElseThrow());
            assertEquals(dressBase.getWidth(), dressMask.getWidth());
            assertEquals(dressBase.getHeight(), dressMask.getHeight());
            var originalDecoration = original.elements().stream()
                    .filter(e -> e.name().equals(bust ? "bust_front_decoration" : "torso_decoration"))
                    .findFirst().orElseThrow();
            var dressDecoration = dress.elements().stream()
                    .filter(e -> e.name().equals(originalDecoration.name())).findFirst().orElseThrow();
            assertEquals(originalDecoration.to(), dressDecoration.to());
            assertEquals(originalDecoration.from().x(), dressDecoration.from().x());
            assertEquals(originalDecoration.from().z(), dressDecoration.from().z());
            assertEquals(originalDecoration.rotation(), dressDecoration.rotation());
            var uv = originalDecoration.faces().get(Face.FRONT);
            int brightened = 0;
            for (int y = 0; y < dressMask.getHeight(); y++) for (int x = 0; x < dressMask.getWidth(); x++) {
                boolean collar = x >= uv.u0() && x < uv.u1() && y >= uv.v0() && y < uv.v0() + 2
                        && originalBase.getRGB(x, y) >>> 24 != 0;
                int expected = collar ? originalMask.getRGB(x, y) & 255 : 100;
                assertEquals(0xFF000000 | expected << 16 | expected << 8 | expected,
                        dressMask.getRGB(x, y), name + " / " + x + "," + y);
                if (collar) {
                    assertEquals(originalBase.getRGB(x, y), dressBase.getRGB(x, y), name);
                    int tinted = 0xFF367094;
                    assertEquals(GarmentTexturePixelOps.applyBrightness(tinted, 1, originalMask.getRGB(x, y) & 255),
                            GarmentTexturePixelOps.applyBrightness(tinted, 1, expected), name);
                    brightened++;
                }
            }
            assertEquals(6, brightened, name);
        }
    }

    @Test
    void shirtPlacketButtonsStayOnDecorationAndLayeredBustDoesNotExposeAnInnerSeam() throws Exception {
        for (String length : List.of("knee", "midi")) {
            var torso = DualEnvelopeDeformerTest.part("shirt_dress/" + length + "/torso");
            var overlay = texture(torso.variants().get("default").overlay().orElseThrow());
            var placket = torso.elements().stream().filter(e -> e.name().equals("front_panel_decoration"))
                    .findFirst().orElseThrow();
            var uv = placket.faces().get(Face.FRONT);
            int alpha = texture(ResourceLocation.parse("createapparel:garment/shirt/torso/overlay/default"))
                    .getRGB(8, 18) >>> 24;
            assertEquals(alpha, overlay.getRGB((int) uv.u0() + 5, (int) uv.v0() + 1) >>> 24);
            assertEquals(0, overlay.getRGB((int) uv.u0() + 4, (int) uv.v0() + 1) >>> 24);
            var cam = DualEnvelopeDeformerTest.part("camisole_dress/" + length + "/torso");
            assertEquals(255, texture(cam.defaultTexture()).getRGB(8, 8) >>> 24);
        }
    }

    @Test
    void generatedRecipesModelsAndTranslationsCoverEveryVariantWithOneThreadAndExplicitSynthetic() throws Exception {
        var english = json(Path.of("src/generated/resources/assets/createapparel/lang/en_us.json"));
        var japanese = json(Path.of("src/generated/resources/assets/createapparel/lang/ja_jp.json"));
        for (String kind : LENGTHS.keySet()) for (String variant : variants(kind)) {
            String id = kind + "_" + variant;
            var recipe = json(Path.of("src/generated/resources/data/createapparel/recipes/" + id + ".json"));
            assertEquals("createapparel:" + id, recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(1, recipe.getAsJsonObject("thread").get("count").getAsInt());
            var fabrics = recipe.getAsJsonObject("fabric");
            assertTrue(fabrics.getAsJsonArray("tag").asList().stream()
                    .anyMatch(tag -> tag.getAsString().equals("createapparel:fabrics/synthetic")));
            int skirtCount = variant.startsWith("mini") ? 2 : variant.startsWith("knee") ? 3
                    : variant.startsWith("midi") ? 4 : 5;
            int expected = (kind.equals("shirt_dress") ? 4 : 3) + skirtCount
                    + (layered(kind) ? (variant.endsWith("shortsleeves") ? 3 : 4) : 0);
            assertEquals(expected, fabrics.get("count").getAsInt(), id);
            assertTrue(english.has("item.createapparel." + id));
            assertTrue(japanese.has("item.createapparel." + id));
            assertTrue(Files.exists(Path.of("src/generated/resources/assets/createapparel/models/item/" + id + ".json")));
        }
    }
}
