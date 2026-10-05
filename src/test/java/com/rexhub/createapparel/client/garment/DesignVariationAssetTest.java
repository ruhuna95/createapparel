package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DesignVariationAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final Path DEFINITIONS = Path.of("src/main/resources/data/createapparel/garments");
    private static final Set<String> RETIRED = Set.of("logo", "stripe_logo", "logo_diamond", "logo_redstone",
            "logo_wordmark", "logo_emblem", "plaid", "diagonal_stripe");

    static Set<String> expectedDesigns(String garment) throws IOException {
        var catalog = json(Path.of("src/test/resources/garment/design_variations.json"));
        String family = catalog.keySet().stream().filter(key -> garment.equals(key) || garment.startsWith(key + "_"))
                .max(Comparator.comparingInt(String::length)).orElseThrow();
        Set<String> result = new LinkedHashSet<>();
        catalog.getAsJsonArray(family).forEach(value -> result.add(value.getAsString()));
        return result;
    }

    static void assertDefinitionDesigns(JsonObject definition, String garment) throws IOException {
        Set<String> actual = new LinkedHashSet<>();
        definition.getAsJsonArray("designs").forEach(value -> actual.add(value.getAsString()));
        assertEquals(expectedDesigns(garment), actual, garment);
    }

    @Test
    void everyDefinitionMatchesItsDesignContractAndEverySlotResolvesVisibleTextures() throws IOException {
        Set<ResourceLocation> validated = new LinkedHashSet<>();
        for (var definition : definitions()) {
            assertEquals(expectedDesigns(definition.id().getPath()), definition.designs(), definition.id().toString());
            for (var slot : definition.allowedSlots()) {
                var parts = parts(definition, definition.modelVariantFor(slot));
                for (String design : definition.designs()) {
                    boolean visible = false;
                    var appearance = appearance(design);
                    for (var part : parts.values()) {
                        var textures = part.resolveTextures(definition, appearance);
                        for (var reference : List.of(textures.secondary(), textures.accent(), textures.designOverlay())) {
                            if (reference.isEmpty()) continue;
                            var resource = reference.get();
                            var image = image(resource);
                            assertEquals(part.textureWidth(), image.getWidth(), resource.toString());
                            assertEquals(part.textureHeight(), image.getHeight(), resource.toString());
                            boolean overlay = textures.designOverlay().equals(reference);
                            if (validated.add(resource)) validatePixels(image, overlay, resource);
                            visible |= hasVisibleWeight(part, image(textures.base()), image, overlay);
                        }
                    }
                    if (!design.equals("plain")) {
                        assertTrue(visible, "No reachable visible design texture: " + definition.id() + " / " + slot + " / " + design);
                    }
                }
            }
        }
        assertTrue(validated.size() > 900);
    }

    @Test
    void logosUseTintMasksAndOnlyRegisteredGarmentsExposeThem() throws IOException {
        for (var definition : definitions()) {
            for (String design : List.of("logo_creeper", "logo_word", "logo_round")) {
                String name = definition.id().getPath();
                boolean expected = name.startsWith("t_shirt") || Set.of("pullover_hoodie", "zipup_hoodie", "cap",
                        "backpack").contains(name);
                assertEquals(expected, definition.designs().contains(design), name + " / " + design);
                if (!expected) continue;
                for (var slot : definition.allowedSlots()) {
                    var parts = parts(definition, definition.modelVariantFor(slot));
                    assertEquals(new ClientGarmentAssets.ColorChannels(true, design.equals("logo_round")),
                            ClientGarmentAssets.colorChannels(mapping(parts), definition.textureVariant(), design, parts), name + " / " + design);
                    boolean partialWeight = false;
                    for (var part : parts.values()) {
                        var resolved = part.resolveTextures(definition, appearance(design));
                        if (!design.equals("logo_round")) assertTrue(resolved.designOverlay().isEmpty());
                        var mask = resolved.secondary();
                        if (mask.isEmpty()) continue;
                        String template = definition.modelTemplate().getPath();
                        assertTrue(mask.get().getPath().startsWith("garment/" + template + "/")
                                        || mask.get().getPath().startsWith("garment/" + template + "_outer/"),
                                "Logo must be authored for this garment: " + mask.get());
                        var image = image(mask.get());
                        for (int y = 0; y < image.getHeight(); y++) {
                            for (int x = 0; x < image.getWidth(); x++) {
                                int weight = image.getRGB(x, y) & 255;
                                partialWeight |= weight > 0 && weight < 255;
                            }
                        }
                    }
                    assertTrue(partialWeight, name + " / " + design);
                }
            }
        }
    }

    @Test
    void shirtLeftRightColorsFollowGeometryIncludingBustCollarAndSleeves() throws IOException {
        for (String name : List.of("shirt_shortsleeves", "shirt_longsleeves", "shirt_dress_knee", "shirt_dress_midi")) {
            var definition = definition(name);
            for (var part : parts(definition, definition.modelVariant()).values()) {
                var textures = part.resolveTextures(definition, appearance("two_tone_lr"));
                var base = image(textures.base());
                BufferedImage mask = textures.secondary().isPresent() ? image(textures.secondary().get()) : null;
                for (var element : part.elements()) {
                    for (var face : element.faces().entrySet()) {
                        var vertices = GarmentElementGeometry.faceVertices(element, face.getKey());
                        var uv = face.getValue();
                        for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                            for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                                if (base.getRGB(x, y) >>> 24 == 0) continue;
                                var sampledX = geometryX(vertices, uv, x + .5F, y + .5F);
                                if (sampledX.isEmpty()) continue;
                                float geometryX = (float) sampledX.getAsDouble();
                                if (Math.abs(geometryX) < .05F) continue;
                                assertEquals(geometryX > 0 ? 255 : 0, mask == null ? 0 : mask.getRGB(x, y) & 255,
                                        name + " / " + part.id() + " / " + element.name() + " / " + face.getKey() + " (" + x + "," + y + ")");
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void dressInnerShirtRemainsSecondaryAndPatternsOnlyUseAccentOnDress() throws IOException {
        for (var definition : definitions()) {
            if (!definition.id().getPath().startsWith("jumper_dress") && !definition.id().getPath().startsWith("camisole_dress")) continue;
            for (var part : parts(definition, definition.modelVariant()).values()) {
                var plain = part.resolveTextures(definition, appearance("plain"));
                for (String design : definition.designs()) {
                    var resolved = part.resolveTextures(definition, appearance(design));
                    assertEquals(plain.secondary(), resolved.secondary(), part.id() + " / " + design);
                    if (part.id().getPath().startsWith("dress_inner/")) {
                        assertTrue(resolved.accent().isEmpty());
                        assertTrue(resolved.designOverlay().isEmpty());
                    }
                }
            }
        }
    }

    @Test
    void retiredDesignsAndTranslationsAreAbsentFromResources() throws IOException {
        try (var paths = Files.walk(ASSETS.resolve("garment_models/parts"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var textures = json(path).getAsJsonObject("textures");
                for (String retired : RETIRED) {
                    assertFalse(textures.toString().contains("\"" + retired + "\""), path.toString());
                    assertFalse(textures.toString().contains("/masks/" + retired + "/"), path.toString());
                }
            }
        }
        for (String language : List.of("en_us", "ja_jp")) {
            var translations = json(Path.of("src/generated/resources/assets/createapparel/lang/" + language + ".json"));
            for (String retired : RETIRED) assertFalse(translations.has("design.createapparel." + retired));
            for (var definition : definitions()) {
                for (String design : definition.designs()) assertTrue(translations.has("design.createapparel." + design), design);
            }
        }
    }

    @Test
    void ankleSockCuffHasTwoAdjacentLinesAndTheSoleKeepsPrimary() throws IOException {
        var definition = definition("ankle_socks");
        for (var part : parts(definition, definition.modelVariant()).values()) {
            var mask = image(part.resolveTextures(definition, appearance("double_stripe")).secondary().orElseThrow());
            var front = part.elements().get(0).faces().get(ClientGarmentAssets.Face.FRONT);
            for (int x = (int) front.u0(); x < front.u1(); x++) {
                assertEquals(64, mask.getRGB(x, (int) front.v1() - 2) & 255);
                assertEquals(64, mask.getRGB(x, (int) front.v1() - 1) & 255);
            }
            var sole = part.elements().get(0).faces().get(ClientGarmentAssets.Face.BOTTOM);
            for (int y = (int) sole.v0(); y < sole.v1(); y++) {
                for (int x = (int) sole.u0(); x < sole.u1(); x++) assertEquals(0, mask.getRGB(x, y) & 255);
            }
        }
    }

    @Test
    void solidDressPanelsIncludeEveryNearestSampledTexelAlongSlopingUvEdges() throws IOException {
        int checked = 0;
        for (var definition : definitions()) {
            if (!definition.id().getPath().contains("dress")) continue;
            for (String design : List.of("two_tone", "two_tone_lr", "contrast_skirt")) {
                if (!definition.designs().contains(design)) continue;
                for (var part : parts(definition, definition.modelVariant()).values()) {
                    var textures = part.resolveTextures(definition, appearance(design));
                    var base = image(textures.base());
                    BufferedImage mask = textures.secondary().isPresent() ? image(textures.secondary().get()) : null;
                    for (var element : part.elements()) {
                        if (!element.name().contains("panel")) continue;
                        for (var face : element.faces().entrySet()) {
                            var uv = face.getValue().corners();
                            var vertices = GarmentElementGeometry.faceVertices(element, face.getKey());
                            for (var tri : List.of(new int[]{0, 1, 2}, new int[]{0, 2, 3})) {
                                for (int i = 0; i < 48; i++) {
                                    for (int j = 0; j < 48 - i; j++) {
                                        float a = (i + .2F) / 48, b = (j + .2F) / 48, c = 1 - a - b;
                                        int u = (int) (uv.get(tri[0]).u() * a + uv.get(tri[1]).u() * b + uv.get(tri[2]).u() * c);
                                        int v = (int) (uv.get(tri[0]).v() * a + uv.get(tri[1]).v() * b + uv.get(tri[2]).v() * c);
                                        if (base.getRGB(u, v) >>> 24 == 0) continue;
                                        float x = vertices.get(tri[0]).x() * a + vertices.get(tri[1]).x() * b + vertices.get(tri[2]).x() * c;
                                        if (design.equals("two_tone_lr") && Math.abs(x) < 1) continue;
                                        int expected = !design.equals("two_tone_lr") || x > 0 ? 255 : 0;
                                        assertEquals(expected, mask == null ? 0 : mask.getRGB(u, v) & 255,
                                                definition.id() + " / " + design + " / " + element.name() + " / " + face.getKey() + " (" + u + "," + v + ")");
                                        checked++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(checked > 100000);
    }

    @Test
    void vNeckReservesSecondaryAndHoodieColorBlocksExcludeSleeves() throws IOException {
        var vNeck = definition("v_neck_sweater");
        var vParts = parts(vNeck, vNeck.modelVariant());
        for (String design : List.of("horizontal_stripe", "two_tone")) {
            assertEquals(new ClientGarmentAssets.ColorChannels(true, true), ClientGarmentAssets.colorChannels(
                    mapping(vParts), vNeck.textureVariant(), design, vParts));
            for (var part : vParts.values()) assertEquals(part.resolveTextures(vNeck, appearance("plain")).secondary(),
                    part.resolveTextures(vNeck, appearance(design)).secondary());
        }
        for (String id : List.of("pullover_hoodie", "zipup_hoodie")) {
            var definition = definition(id);
            for (var part : parts(definition, definition.modelVariant()).values()) {
                if (!part.id().getPath().contains("sleeve")) continue;
                assertTrue(part.resolveTextures(definition, appearance("color_block")).secondary().isEmpty());
            }
        }
    }

    @Test
    void tintedBlouseDecorationsUseNeutralBrightnessWithoutChangingPlain() throws IOException {
        for (String id : List.of("blouse_frill", "blouse_bow_tie")) {
            var definition = definition(id);
            String design = id.endsWith("frill") ? "contrast_frill" : "contrast_bow";
            int selected = 0;
            for (var part : parts(definition, definition.modelVariant()).values()) {
                var plain = part.resolveTextures(definition, appearance("plain"));
                var colored = part.resolveTextures(definition, appearance(design));
                if (colored.secondary().isEmpty()) continue;
                assertNotEquals(plain.brightnessMask(), colored.brightnessMask());
                var mask = image(colored.secondary().get());
                var brightness = image(colored.brightnessMask().orElseThrow());
                var original = image(plain.brightnessMask().orElseThrow());
                for (int y = 0; y < mask.getHeight(); y++) for (int x = 0; x < mask.getWidth(); x++) {
                    if ((mask.getRGB(x, y) & 255) > 0) {
                        assertEquals(100, brightness.getRGB(x, y) & 255);
                        selected++;
                    } else assertEquals(original.getRGB(x, y), brightness.getRGB(x, y));
                }
            }
            assertTrue(selected > 0);
        }
    }

    @Test
    void denimStitchesFollowOriginalOverlayPixelsAndSuppressOnlyThatOverlay() throws IOException {
        for (String id : List.of("denim_pants", "denim_jacket")) {
            var definition = definition(id);
            for (var part : parts(definition, definition.modelVariant()).values()) {
                var plain = part.resolveTextures(definition, appearance("plain"));
                if (plain.overlay().isEmpty()) continue;
                var tinted = part.resolveTextures(definition, appearance("contrast_stitch"));
                var source = image(plain.overlay().get());
                var mask = image(tinted.secondary().orElseThrow());
                var replacement = image(tinted.overlay().orElseThrow());
                for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                    assertEquals(source.getRGB(x, y) >>> 24, mask.getRGB(x, y) & 255);
                    assertEquals(0, replacement.getRGB(x, y) >>> 24);
                }
            }
        }
    }

    @Test
    void horizontalStripesContinueOntoShoulders() throws IOException {
        for (String id : List.of("t_shirt", "sweater")) {
            var definition = definition(id);
            Set<Integer> shoulder = new LinkedHashSet<>();
            for (var part : parts(definition, definition.modelVariant()).values()) {
                var textures = part.resolveTextures(definition, appearance("horizontal_stripe"));
                if (textures.secondary().isEmpty()) continue;
                var mask = image(textures.secondary().get());
                var base = image(textures.base());
                for (var element : part.elements()) {
                    var top = element.faces().get(ClientGarmentAssets.Face.TOP);
                    if (top == null) continue;
                    for (int y = (int) top.v0(); y < top.v1(); y++) for (int x = (int) top.u0(); x < top.u1(); x++) {
                        if (base.getRGB(x, y) >>> 24 != 0) shoulder.add(mask.getRGB(x, y) & 255);
                    }
                }
            }
            assertEquals(Set.of(0, 255), shoulder, id);
        }
    }

    @Test
    void subtlePatternsMatchShirtStripeOpacityAndPinstripesAreLighter() throws IOException {
        for (String id : List.of("blouse_round", "blouse_frill", "blouse_bow_tie", "tights", "ankle_socks",
                "crew_socks", "knee_high_socks", "over_knee_socks", "thigh_high_socks", "beanie",
                "slacks", "suit_jacket", "blazer_jacket")) {
            var definition = definition(id);
            boolean pinstripe = definition.designs().contains("pinstripe");
            Set<Integer> weights = new LinkedHashSet<>();
            for (var part : parts(definition, definition.modelVariant()).values()) {
                var texture = part.resolveTextures(definition, appearance(pinstripe ? "pinstripe" : "stripe"));
                if (texture.secondary().isEmpty()) continue;
                var mask = image(texture.secondary().get());
                for (int y = 0; y < mask.getHeight(); y++) for (int x = 0; x < mask.getWidth(); x++) weights.add(mask.getRGB(x, y) & 255);
            }
            assertEquals(Set.of(0, pinstripe ? 32 : 64), weights, id);
        }
    }

    @Test
    void shortsSideLinesAreTwoTexelsWideAndHemDoesNotColorTheWaist() throws IOException {
        var definition = definition("shorts");
        for (var part : parts(definition, definition.modelVariant()).values()) {
            if (part.id().getPath().endsWith("/torso")) {
                assertTrue(part.resolveTextures(definition, appearance("hem_line")).secondary().isEmpty());
            }
            var textures = part.resolveTextures(definition, appearance("side_line"));
            var mask = image(textures.secondary().orElseThrow());
            var base = image(textures.base());
            for (var element : part.elements()) for (var face : List.of(ClientGarmentAssets.Face.LEFT, ClientGarmentAssets.Face.RIGHT)) {
                var uv = element.faces().get(face);
                if (uv == null) continue;
                for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                    int width = 0;
                    int visible = 0;
                    for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                        if ((mask.getRGB(x, y) & 255) > 0) width++;
                        if (base.getRGB(x, y) >>> 24 > 0) visible++;
                    }
                    assertEquals(visible == 0 ? 0 : 2, width, part.id() + " / " + face + " / " + y);
                }
            }
        }
    }

    @Test
    void braBustFrontStrapsAreSecondaryWithoutColoringTheCups() throws IOException {
        var definition = definition("bra");
        var part = parts(definition, definition.modelVariant()).get(ResourceLocation.parse("createapparel:inner_top/bust"));
        assertNotNull(part);
        var textures = part.resolveTextures(definition, appearance("two_tone"));
        var base = image(textures.base());
        var mask = image(textures.secondary().orElseThrow());
        for (int u : List.of(4, 8)) for (int v : List.of(0, 1)) {
            assertEquals(255, base.getRGB(u, v) >>> 24);
            assertEquals(255, mask.getRGB(u, v) & 255, u + "," + v);
        }
        assertEquals(0, mask.getRGB(5, 3) & 255);
        assertEquals(0, mask.getRGB(7, 3) & 255);
    }

    @Test
    void requestedPatternsAndHoodieLogosDoNotColorDecorations() throws IOException {
        for (String id : List.of("blouse_round", "blouse_frill", "blouse_bow_tie", "pullover_hoodie",
                "zipup_hoodie", "suit_jacket", "blazer_jacket")) {
            var definition = definition(id);
            for (String design : definition.designs()) {
                if (!Set.of("stripe", "polka_dot", "pinstripe", "tartan_check", "gingham_check",
                        "logo_creeper", "logo_word", "logo_round").contains(design)) continue;
                for (var part : parts(definition, definition.modelVariant()).values()) {
                    var textures = part.resolveTextures(definition, appearance(design));
                    for (var element : part.elements()) {
                        if (!element.name().contains("decoration")) continue;
                        for (var uv : element.faces().values()) {
                            assertUncoloredUv(textures, uv, part.id() + " / " + element.name() + " / " + design);
                        }
                    }
                }
            }
        }
    }

    @Test
    void skirtPatternsLeaveTheWaistbandPrimary() throws IOException {
        for (var definition : definitions()) {
            if (!definition.id().getPath().contains("skirt")) continue;
            for (String design : definition.designs()) {
                if (design.equals("waist_band")) continue;
                for (var part : parts(definition, definition.modelVariant()).values()) {
                    for (var element : part.elements()) {
                        if (!element.name().equals("waistband")) continue;
                        var textures = part.resolveTextures(definition, appearance(design));
                        for (var uv : element.faces().values()) assertUncoloredUv(textures, uv, definition.id() + " / " + design);
                    }
                }
            }
        }
    }

    @Test
    void sweaterArgyleAndFairIsleAreStrongerWithoutChangingSockOpacity() throws IOException {
        for (String id : List.of("sweater", "crew_socks")) {
            var definition = definition(id);
            for (String design : List.of("argyle", "fair_isle")) {
                if (!definition.designs().contains(design)) continue;
                Set<Integer> secondary = new LinkedHashSet<>(), accent = new LinkedHashSet<>();
                for (var part : parts(definition, definition.modelVariant()).values()) {
                    var textures = part.resolveTextures(definition, appearance(design));
                    textures.secondary().ifPresent(resource -> collectWeights(resource, secondary));
                    textures.accent().ifPresent(resource -> collectWeights(resource, accent));
                }
                assertEquals(Set.of(0, id.equals("sweater") ? 96 : 64), secondary);
                assertEquals(Set.of(0, id.equals("sweater") ? 64 : 48), accent);
            }
        }
    }

    @Test
    void blazerPipingContinuesDownClosedFrontAndOpenedEdges() throws IOException {
        var definition = definition("blazer_jacket");
        for (String name : List.of("jacket/torso", "jacket/torso_opened")) {
            var part = ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:" + name),
                    json(ASSETS.resolve("garment_models/parts/" + name + ".json")));
            var textures = part.resolveTextures(definition, appearance("piping"));
            var base = image(textures.base());
            var mask = image(textures.secondary().orElseThrow());
            for (int y = name.endsWith("opened") ? 4 : 10; y <= 15; y++) {
                for (int x : name.endsWith("opened") ? List.of(5, 10) : List.of(7)) {
                    assertEquals(255, base.getRGB(x, y) >>> 24);
                    assertEquals(96, mask.getRGB(x, y) & 255, name + " / " + x + "," + y);
                }
            }
        }
    }

    @Test
    void denimPatchIsOnTheWearersRightFrontOnly() throws IOException {
        var definition = definition("denim_pants");
        int visible = 0;
        double sumX = 0;
        for (var part : parts(definition, definition.modelVariant()).values()) {
            var textures = part.resolveTextures(definition, appearance("patched"));
            assertTrue(textures.designOverlay().isEmpty());
            if (textures.secondary().isEmpty()) continue;
            var mask = image(textures.secondary().get());
            for (var element : part.elements()) for (var face : element.faces().entrySet()) {
                var uv = face.getValue();
                for (int y = (int) uv.v0(); y < uv.v1(); y++) for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                    if ((mask.getRGB(x, y) & 255) == 0) continue;
                    assertEquals(ClientGarmentAssets.Face.FRONT, face.getKey());
                    double position = geometryX(GarmentElementGeometry.faceVertices(element, face.getKey()), uv, x + .5F, y + .5F).orElseThrow();
                    assertTrue(position < 0);
                    visible++;
                    sumX += position;
                }
            }
        }
        assertTrue(visible > 0);
        assertEquals(-1.6, sumX / visible, .5);
    }

    @Test
    void denimPantsPatchUsesSecondaryTintAndKeepsTheOriginalStitchOverlay() throws IOException {
        var definition = definition("denim_pants");
        var parts = parts(definition, definition.modelVariant());
        assertEquals(new ClientGarmentAssets.ColorChannels(true, false),
                ClientGarmentAssets.colorChannels(mapping(parts), definition.textureVariant(), "patched", parts));
        int colored = 0;
        for (var part : parts.values()) {
            var textures = part.resolveTextures(definition, appearance("patched"));
            var plain = part.resolveTextures(definition, appearance("plain"));
            assertEquals(plain.base(), textures.base());
            assertEquals(plain.overlay(), textures.overlay());
            assertTrue(textures.designOverlay().isEmpty());
            assertTrue(textures.accent().isEmpty());
            if (textures.secondary().isEmpty()) continue;
            var mask = image(textures.secondary().get());
            for (int y = 0; y < mask.getHeight(); y++) for (int x = 0; x < mask.getWidth(); x++) {
                int weight = mask.getRGB(x, y) & 255;
                int redTint = GarmentTexturePixelOps.composeTint(0xFFFFFF, java.util.OptionalInt.of(0xFF0000),
                        java.util.OptionalInt.empty(), weight, 0);
                int blueTint = GarmentTexturePixelOps.composeTint(0xFFFFFF, java.util.OptionalInt.of(0x0000FF),
                        java.util.OptionalInt.empty(), weight, 0);
                if (weight > 0) {
                    assertTrue(weight >= 90 && weight <= 154);
                    assertNotEquals(redTint, blueTint);
                    colored++;
                } else {
                    assertEquals(redTint, blueTint);
                }
            }
        }
        assertEquals(8, colored);
        var jacket = definition("denim_jacket");
        assertTrue(parts(jacket, jacket.modelVariant()).values().stream()
                .anyMatch(part -> part.resolveTextures(jacket, appearance("patched")).designOverlay().isPresent()));
    }

    @Test
    void tightsDiamondsAreFiveTexelCrosses() throws IOException {
        var definition = definition("tights");
        var part = parts(definition, definition.modelVariant()).get(ResourceLocation.parse("createapparel:tights/left_leg"));
        var mask = image(part.resolveTextures(definition, appearance("diamond")).secondary().orElseThrow());
        int colored = 0;
        for (int y = 4; y < 8; y++) for (int x = 4; x < 8; x++) {
            int expected = Math.abs(x - 5) + Math.abs(y - 6) <= 1 ? 64 : 0;
            assertEquals(expected, mask.getRGB(x, y) & 255, x + "," + y);
            if (expected > 0) colored++;
        }
        assertEquals(5, colored);
    }

    @Test
    void sneakerSideLinesAreSlantedRatherThanHorizontalBands() throws IOException {
        for (String id : List.of("sneakers", "canvas_sneakers")) {
            var definition = definition(id);
            for (var part : parts(definition, definition.modelVariant()).values()) {
                var textures = part.resolveTextures(definition, appearance("side_line"));
                var mask = image(textures.secondary().orElseThrow());
                var outer = part.id().getPath().endsWith("left_leg") ? ClientGarmentAssets.Face.RIGHT : ClientGarmentAssets.Face.LEFT;
                var upperUv = part.elements().stream().filter(e -> e.name().endsWith("_upper")).findFirst().orElseThrow().faces().get(outer);
                var lowerUv = part.elements().get(0).faces().get(outer);
                if (id.equals("canvas_sneakers")) {
                    upperUv = lowerUv;
                }
                int lowerY = (int) lowerUv.v0() + (id.equals("canvas_sneakers") ? 1 : 0);
                int upperX = -1, lowerX = -1, upperCount = 0, lowerCount = 0;
                for (int x = (int) upperUv.u0(); x < upperUv.u1(); x++) {
                    if ((mask.getRGB(x, (int) upperUv.v0()) & 255) != 0) {
                        upperX = x;
                        upperCount++;
                    }
                }
                for (int x = (int) lowerUv.u0(); x < lowerUv.u1(); x++) {
                    if ((mask.getRGB(x, lowerY) & 255) != 0) {
                        lowerX = x;
                        lowerCount++;
                    }
                    if (!id.equals("canvas_sneakers")) assertEquals(0, mask.getRGB(x, lowerY + 1) & 255);
                }
                assertEquals(1, upperCount);
                assertEquals(1, lowerCount);
                assertEquals(1, Math.abs(upperX - lowerX));
            }
        }
    }

    @Test
    void necktiePatternsShareOpacityAndStripesColorTheSmallerArea() throws IOException {
        var definition = definition("necktie");
        for (var part : parts(definition, definition.modelVariant()).values()) {
            for (String design : List.of("stripe", "dot", "check")) {
                var textures = part.resolveTextures(definition, appearance(design));
                var base = image(textures.base());
                var mask = image(textures.secondary().orElseThrow());
                int colored = 0, visible = 0;
                for (int y = 0; y < base.getHeight(); y++) for (int x = 0; x < base.getWidth(); x++) {
                    int weight = mask.getRGB(x, y) & 255;
                    assertTrue(weight == 0 || weight == 80);
                    if (base.getRGB(x, y) >>> 24 == 0) continue;
                    visible++;
                    if (weight > 0) colored++;
                }
                assertTrue(colored > 0);
                if (design.equals("stripe")) assertTrue(colored * 2 < visible, part.id().toString());
            }
        }
    }

    private static void collectWeights(ResourceLocation resource, Set<Integer> result) {
        try {
            var mask = image(resource);
            for (int y = 0; y < mask.getHeight(); y++) for (int x = 0; x < mask.getWidth(); x++) result.add(mask.getRGB(x, y) & 255);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static void assertUncoloredUv(ClientGarmentAssets.ResolvedTextures textures, ClientGarmentAssets.Uv uv, String context) throws IOException {
        var base = image(textures.base());
        for (var reference : List.of(textures.secondary(), textures.accent(), textures.designOverlay())) {
            if (reference.isEmpty()) continue;
            var layer = image(reference.get());
            for (int y = (int) uv.v0(); y < uv.v1(); y++) for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                if (base.getRGB(x, y) >>> 24 == 0) continue;
                assertEquals(0, textures.designOverlay().equals(reference) ? layer.getRGB(x, y) >>> 24 : layer.getRGB(x, y) & 255,
                        context + " / " + x + "," + y);
            }
        }
    }

    private static void validatePixels(BufferedImage image, boolean overlay, ResourceLocation resource) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if (!overlay) assertTrue(GarmentTexturePixelOps.isValidMaskPixel(pixel >> 16 & 255,
                        pixel >> 8 & 255, pixel & 255, pixel >>> 24), resource.toString());
            }
        }
    }

    private static java.util.OptionalDouble geometryX(List<ClientGarmentAssets.Vec3> vertices,
                                                      ClientGarmentAssets.Uv uv, float u, float v) {
        for (var triangle : List.of(new int[]{0, 1, 2}, new int[]{0, 2, 3})) {
            var a = uv.corners().get(triangle[0]);
            var b = uv.corners().get(triangle[1]);
            var c = uv.corners().get(triangle[2]);
            float denominator = (b.v() - c.v()) * (a.u() - c.u()) + (c.u() - b.u()) * (a.v() - c.v());
            if (Math.abs(denominator) < 0.000001F) continue;
            float p = ((b.v() - c.v()) * (u - c.u()) + (c.u() - b.u()) * (v - c.v())) / denominator;
            float q = ((c.v() - a.v()) * (u - c.u()) + (a.u() - c.u()) * (v - c.v())) / denominator;
            float r = 1 - p - q;
            if (p >= -0.000001F && q >= -0.000001F && r >= -0.000001F) {
                return java.util.OptionalDouble.of(vertices.get(triangle[0]).x() * p
                        + vertices.get(triangle[1]).x() * q + vertices.get(triangle[2]).x() * r);
            }
        }
        return java.util.OptionalDouble.empty();
    }

    private static boolean hasVisibleWeight(GarmentPartModel part, BufferedImage base, BufferedImage layer, boolean overlay) {
        for (var element : part.elements()) {
            for (var uv : element.faces().values()) {
                for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                    for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                        if (base.getRGB(x, y) >>> 24 != 0 && (overlay ? layer.getRGB(x, y) >>> 24 : layer.getRGB(x, y) & 255) != 0) return true;
                    }
                }
            }
        }
        return false;
    }

    private static GarmentData appearance(String design) {
        return new GarmentData(FabricMaterial.SYNTHETIC, design, FabricColor.WHITE,
                Optional.of(FabricColor.RED), Optional.of(FabricColor.BLUE));
    }

    private static Map<String, ResourceLocation> mapping(Map<ResourceLocation, GarmentPartModel> parts) {
        Map<String, ResourceLocation> result = new LinkedHashMap<>();
        parts.keySet().forEach(id -> result.put(id.getPath(), id));
        return result;
    }

    private static Map<ResourceLocation, GarmentPartModel> parts(GarmentDefinition definition, String variant) throws IOException {
        var template = json(ASSETS.resolve("garment_models/templates/" + definition.modelTemplate().getPath() + ".json"));
        Map<ResourceLocation, GarmentPartModel> result = new LinkedHashMap<>();
        for (var reference : template.getAsJsonObject(variant).entrySet()) {
            var id = ResourceLocation.parse(reference.getValue().getAsJsonObject().get("model").getAsString());
            result.put(id, ClientGarmentAssetLoader.parsePart(id, json(ASSETS.resolve("garment_models/parts/" + id.getPath() + ".json"))));
        }
        return result;
    }

    private static List<GarmentDefinition> definitions() throws IOException {
        List<GarmentDefinition> result = new ArrayList<>();
        try (var paths = Files.list(DEFINITIONS)) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                result.add(definition(path.getFileName().toString().replace(".json", "")));
            }
        }
        return result;
    }

    private static GarmentDefinition definition(String name) throws IOException {
        return GarmentDefinition.fromJson(ResourceLocation.parse("createapparel:" + name), json(DEFINITIONS.resolve(name + ".json")));
    }

    private static BufferedImage image(ResourceLocation resource) throws IOException {
        var image = ImageIO.read(ASSETS.resolve("textures/" + resource.getPath() + ".png").toFile());
        assertNotNull(image, resource.toString());
        return image;
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
