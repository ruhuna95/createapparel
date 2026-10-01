package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlouseAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final Path PARTS = ASSETS.resolve("garment_models/parts/blouse");
    private static final List<String> VARIANTS = List.of("round", "frill", "bow_tie");

    @Test
    void separateSleeveItemsResolveForEveryTuckAndBodyProfile() throws IOException {
        JsonObject template = json(ASSETS.resolve("garment_models/templates/blouse.json"));
        assertEquals(Set.of("classic", "slim", "three_quarter_sleeve_classic", "three_quarter_sleeve_slim"), template.keySet());
        for (String texture : VARIANTS) {
            for (boolean threeQuarter : List.of(false, true)) {
                String suffix = threeQuarter ? "_three_quarter_sleeve" : "";
                JsonObject definition = json(Path.of("src/main/resources/data/createapparel/garments/blouse_" + texture + suffix + ".json"));
                assertEquals("createapparel:blouse", definition.get("model_template").getAsString());
                assertEquals(texture, definition.get("texture_variant").getAsString());
                assertEquals("top", definition.get("slot").getAsString());
                assertEquals("[\"plain\"]", definition.get("designs").toString());
                assertEquals("[\"untucked\",\"tucked\"]", definition.getAsJsonObject("styles").get("tuck").toString());
                assertEquals(Set.of("tuck"), definition.getAsJsonObject("styles").keySet());
                assertEquals(threeQuarter ? "three_quarter_sleeve_classic" : "classic", definition.get("model_variant").getAsString());
                for (String profile : List.of("classic", "slim")) {
                    String model = threeQuarter ? "three_quarter_sleeve_" + profile : profile;
                    JsonObject parts = template.getAsJsonObject(model);
                    assertTrue(parts.keySet().stream().noneMatch(name -> name.endsWith("_rolled")));
                    for (ArmProfile arm : ArmProfile.values()) {
                        for (ChestProfile chest : ChestProfile.values()) {
                            for (String tuck : List.of("untucked", "tucked")) {
                                var selected = GarmentPartSelection.select(Map.of("tuck", tuck), arm, chest, true);
                                assertEquals(chest == ChestProfile.BUST ? 4 : 3, selected.size());
                                for (String semantic : selected) {
                                    String resource = parts.getAsJsonObject(semantic).get("model").getAsString();
                                    assertTrue(resource.startsWith("createapparel:blouse/"));
                                    GarmentPartModel part = part(resource.substring("createapparel:blouse/".length()));
                                    assertNotNull(part.variants().get(texture));
                                    if (semantic.contains("sleeve")) {
                                        boolean left = semantic.startsWith("left_");
                                        part.elements().forEach(element -> {
                                            assertTrue(element.name().startsWith(left ? "left_" : "right_"));
                                            assertEquals(left, element.from().x() > 0);
                                        });
                                        assertEquals(!threeQuarter, resource.contains("loose_sleeve"));
                                        assertEquals(threeQuarter, resource.contains("three_quarter_sleeve"));
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
    void tuckedHemBustAndCuffsFollowTheSpecifiedGeometry() throws IOException {
        var torso = part("torso").elements().get(0);
        var tucked = part("tucked_torso").elements().get(0);
        assertEquals(2.995, tucked.from().y() - torso.from().y(), 0.00001);
        assertEquals(-4.5F, tucked.corners().get(0).x(), 0.00001);
        assertEquals(4.5F, tucked.corners().get(1).x(), 0.00001);
        assertEquals(-4.005F, tucked.corners().get(4).x(), 0.00001);
        assertEquals(4.005F, tucked.corners().get(5).x(), 0.00001);
        var tuckedDecoration = part("tucked_torso").elements().get(1);
        for (int corner = 0; corner < 8; corner++) {
            assertEquals(tucked.corners().get(corner).x(), tuckedDecoration.corners().get(corner).x());
        }
        assertEquals(-2.5F, tucked.corners().get(0).z(), 0.00001);
        assertEquals(-2.25F, tucked.corners().get(4).z(), 0.00001);
        assertTrue(tucked.faces().containsKey(Face.BOTTOM));
        assertFalse(torso.faces().containsKey(Face.BOTTOM));
        var bust = part("bust");
        var frontDecoration = bust.elements().get(2);
        assertEquals(Set.of(Face.FRONT), frontDecoration.faces().keySet());
        assertEquals(-1.79F, frontDecoration.from().z());
        assertEquals(bust.elements().get(0).rotation(), frontDecoration.rotation());
        var uv = frontDecoration.faces().get(Face.FRONT);
        assertEquals(List.of(3F, 10F, 10F, 14F), List.of(uv.u0(), uv.v0(), uv.u1(), uv.v1()));
        for (var element : bust.elements()) {
            assertFalse(element.faces().containsKey(Face.TOP));
            assertFalse(element.faces().containsKey(Face.BACK));
        }
        for (String profile : List.of("classic", "slim")) {
            for (String side : List.of("left", "right")) {
                var sleeve = part(side + "_three_quarter_sleeve_" + profile);
                assertEquals(15.75F, sleeve.elements().get(0).from().y());
                assertFalse(sleeve.elements().get(0).faces().containsKey(Face.BOTTOM));
                var loose = part(side + "_loose_sleeve_" + profile);
                var cuff = loose.elements().get(1);
                assertEquals(11.75F, cuff.from().y());
                assertEquals(2, cuff.to().y() - cuff.from().y());
                assertFalse(cuff.faces().containsKey(Face.TOP));
                assertFalse(cuff.faces().containsKey(Face.BOTTOM));
                var upper = loose.elements().get(0);
                assertTrue(upper.faces().containsKey(Face.BOTTOM));
                assertEquals(13.74F, upper.from().y());
                assertEquals(24.005F, upper.to().y());
                assertEquals(-2.5F, upper.corners().get(0).z());
                assertEquals(-2.25F, upper.corners().get(4).z());
                assertEquals(0.25F, cuff.from().z() - upper.from().z(), 0.00001);
                assertEquals(0.25F, upper.to().z() - cuff.to().z(), 0.00001);
                if (side.equals("left")) {
                    assertEquals(0.25F, upper.to().x() - cuff.to().x(), 0.00001);
                    assertEquals(3.99F, upper.from().x());
                    assertEquals(cuff.to().x(), upper.corners().get(5).x(), 0.00001);
                    assertEquals(3.995F, upper.corners().get(4).x(), 0.00001);
                } else {
                    assertEquals(0.25F, cuff.from().x() - upper.from().x(), 0.00001);
                    assertEquals(-3.99F, upper.to().x());
                    assertEquals(cuff.from().x(), upper.corners().get(4).x(), 0.00001);
                    assertEquals(-3.995F, upper.corners().get(5).x(), 0.00001);
                }
            }
        }
    }

    @Test
    void everyTextureHasValidPixelsAndAuthoredBrightness() throws IOException {
        Set<Path> textures = new HashSet<>();
        try (var paths = Files.list(PARTS)) {
            for (Path path : paths.toList()) {
                var part = ClientGarmentAssetLoader.parsePart(id(path.getFileName().toString()), json(path));
                for (var variant : part.variants().values()) {
                    assertTrue(variant.overlay().isEmpty());
                    assertEquals(1, variant.brightness());
                    Path basePath = texturePath(variant.base());
                    Path maskPath = texturePath(variant.brightnessMask().orElseThrow());
                    textures.add(basePath);
                    textures.add(maskPath);
                    var base = ImageIO.read(basePath.toFile());
                    var mask = ImageIO.read(maskPath.toFile());
                    assertNotNull(base);
                    assertNotNull(mask);
                    assertEquals(part.textureWidth(), base.getWidth());
                    assertEquals(base.getWidth(), base.getHeight());
                    assertEquals(base.getWidth(), mask.getWidth());
                    assertEquals(base.getHeight(), mask.getHeight());
                    assertEquals(0, base.getWidth() & (base.getWidth() - 1));
                    for (int y = 0; y < base.getHeight(); y++) {
                        for (int x = 0; x < base.getWidth(); x++) {
                            int pixel = base.getRGB(x, y);
                            assertTrue((pixel >>> 24) == 0 || (pixel >>> 24) == 255);
                            assertEquals(pixel & 255, pixel >> 8 & 255);
                            assertEquals(pixel & 255, pixel >> 16 & 255);
                            int brightness = mask.getRGB(x, y);
                            assertEquals(255, brightness >>> 24);
                            assertEquals(brightness & 255, brightness >> 8 & 255);
                            assertEquals(brightness & 255, brightness >> 16 & 255);
                        }
                    }
                    assertTrue(part.designs().isEmpty());
                    int largestUv = 0;
                    for (var element : part.elements()) {
                        for (var uv : element.faces().values()) {
                            largestUv = Math.max(largestUv, (int) Math.max(uv.u1(), uv.v1()));
                            assertTrue(uv.u0() >= 0 && uv.v0() >= 0 && uv.u1() <= base.getWidth() && uv.v1() <= base.getHeight());
                        }
                    }
                    assertTrue(largestUv > base.getWidth() / 2);
                }
            }
        }
        assertEquals(26, textures.size());
    }

    @Test
    void existingAssetsKeepTheirOriginalBoxBoundsAndUvRectangles() throws IOException {
        try (var paths = Files.walk(ASSETS.resolve("garment_models/parts"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json") && !p.startsWith(PARTS)
                    && !p.startsWith(ASSETS.resolve("garment_models/parts/a_line_skirt"))
                    && !p.startsWith(ASSETS.resolve("garment_models/parts/jacket"))
                    && !p.startsWith(ASSETS.resolve("garment_models/parts/coat"))
                    && !p.startsWith(ASSETS.resolve("garment_models/parts/hat"))
                    && !p.startsWith(ASSETS.resolve("garment_models/parts/beret"))
                    && !p.startsWith(ASSETS.resolve("garment_models/parts/backpack"))
                    && !p.startsWith(ASSETS.resolve("garment_models/parts/daypack"))
                    && !p.startsWith(ASSETS.resolve("garment_models/parts/crossbody_bag"))
                    && !p.equals(ASSETS.resolve("garment_models/parts/cap/head_back.json"))
                    && !p.equals(ASSETS.resolve("garment_models/parts/shorts/left_leg.json"))
                    && !p.equals(ASSETS.resolve("garment_models/parts/shorts/right_leg.json"))).toList()) {
                var part = ClientGarmentAssetLoader.parsePart(id("legacy"), json(path));
                for (var element : part.elements()) {
                    for (Vec3 corner : element.corners()) {
                        assertTrue(corner.x() == element.from().x() || corner.x() == element.to().x());
                        assertTrue(corner.y() == element.from().y() || corner.y() == element.to().y());
                        assertTrue(corner.z() == element.from().z() || corner.z() == element.to().z());
                    }
                    for (var uv : element.faces().values()) {
                        assertEquals(uv.u0(), uv.corners().get(0).u());
                        assertEquals(uv.v1(), uv.corners().get(0).v());
                        assertEquals(uv.u0(), uv.corners().get(1).u());
                        assertEquals(uv.v0(), uv.corners().get(1).v());
                        assertEquals(uv.u1(), uv.corners().get(2).u());
                        assertEquals(uv.v0(), uv.corners().get(2).v());
                        assertEquals(uv.u1(), uv.corners().get(3).u());
                        assertEquals(uv.v1(), uv.corners().get(3).v());
                    }
                }
            }
        }
    }

    private static GarmentPartModel part(String name) throws IOException {
        return ClientGarmentAssetLoader.parsePart(id(name), json(PARTS.resolve(name + ".json")));
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("createapparel", name);
    }

    private static Path texturePath(ResourceLocation id) {
        return ASSETS.resolve("textures/" + id.getPath() + ".png");
    }
}
