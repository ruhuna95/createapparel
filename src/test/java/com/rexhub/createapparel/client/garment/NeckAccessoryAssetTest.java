package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.MetalAccessoryData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NeckAccessoryAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final Path GENERATED = Path.of("src/generated/resources/data/createapparel");

    @Test
    void necktieHasNoUpperGapsAndUsesCenteredTwoPixelBustAheadOfTopDecorations() throws Exception {
        var torso = part("necktie", "torso");
        var element = torso.elements().get(0);
        assertEquals(Set.of(Face.FRONT), element.faces().keySet());
        var shirtTorso = part("shirt", "torso");
        var shirtDecoration = shirtTorso.elements().stream()
                .filter(value -> value.name().equals("torso_decoration")).findFirst().orElseThrow();
        assertEquals(shirtDecoration.from().z() - 0.01f, element.from().z(), 0.00001f);
        var base = texture(torso.variants().get("necktie").base());
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                boolean opaque = (y >= 4 && y <= 12 && (x == 7 || x == 8))
                        || (y == 13 && x == 8);
                assertEquals(opaque ? 255 : 0, base.getRGB(x, y) >>> 24, x + "/" + y);
            }
        }
        var bustPart = part("necktie", "bust");
        var bust = bustPart.elements().get(0);
        assertEquals(Set.of(Face.FRONT, Face.BOTTOM), bust.faces().keySet());
        var shirtBust = part("shirt", "bust").elements().stream()
                .filter(value -> value.name().equals("bust_front_decoration")).findFirst().orElseThrow();
        assertEquals(shirtBust.from().z() - 0.01f, bust.from().z(), 0.00001f);
        assertEquals(shirtBust.from().y() - 0.01f, bust.from().y(), 0.00001f);
        assertEquals(-1, bust.from().x());
        assertEquals(1, bust.to().x());
        assertEquals(2, bust.to().x() - bust.from().x());
        assertEquals(0, (bust.to().x() + bust.from().x()) / 2);
        for (var uv : bust.faces().values()) {
            assertEquals(2, uv.u1() - uv.u0());
        }
        var bustBase = texture(bustPart.variants().get("necktie").base());
        for (int y = 0; y < bustBase.getHeight(); y++) {
            for (int x = 0; x < bustBase.getWidth(); x++) {
                assertEquals(x < 2 && y < 7 ? 255 : 0, bustBase.getRGB(x, y) >>> 24);
            }
        }
        assertEquals(shirtBust.rotation(), bust.rotation());
    }

    @Test
    void necklaceSharesTieOffsetsAndUsesBrightMetalWithoutRecoloringGems() throws Exception {
        var tieTorso = part("necktie", "torso").elements().get(0);
        var necklace = part("necklace", "torso");
        for (var element : necklace.elements()) {
            assertEquals(tieTorso.from().z(), element.from().z());
        }
        assertEquals(-tieTorso.from().z(), necklace.elements().get(0).to().z());
        var tieBust = part("necktie", "bust").elements().get(0);
        var necklaceBust = part("necklace", "bust").elements().get(0);
        assertEquals(tieBust.from().z(), necklaceBust.from().z());
        assertEquals(tieBust.from().y(), necklaceBust.from().y());
        assertEquals(tieBust.to().y(), necklaceBust.to().y());
        assertEquals(tieBust.rotation(), necklaceBust.rotation());
        for (String semantic : List.of("torso", "bust")) {
            var model = part("necklace", semantic);
            var base = texture(model.variants().get("necklace").base());
            var mask = texture(model.designsFor("necklace").get("plain").secondary().orElseThrow());
            Set<Integer> shades = new java.util.HashSet<>();
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < base.getWidth(); x++) {
                    int pixel = base.getRGB(x, y);
                    if ((pixel >>> 24) == 255 && (mask.getRGB(x, y) & 255) == 0) {
                        int shade = pixel & 255;
                        assertTrue(shade >= 232, "Chain must retain bright metal highlights");
                        shades.add(shade);
                    }
                }
            }
            assertTrue(shades.size() > 1, "Keep chain shading instead of flat white");
        }
    }

    @Test
    void bothAccessoriesResolveEveryProfileWithNoDecorationsAndVisibleFaces() throws Exception {
        for (String id : List.of("necklace", "necktie")) {
            var definition = GarmentDefinition.fromJson(resource(id), json(Path.of(
                    "src/main/resources/data/createapparel/garments/" + id + ".json")));
            assertEquals(Set.of(GarmentSlot.NECK), definition.allowedSlots());
            assertEquals(DesignVariationAssetTest.expectedDesigns(id), definition.designs());
            assertTrue(definition.styles().isEmpty());
            var mapping = json(ASSETS.resolve("garment_models/templates/" + id + ".json")).getAsJsonObject("default");
            for (var arm : ArmProfile.values()) {
                for (var chest : ChestProfile.values()) {
                    assertEquals(chest == ChestProfile.BUST ? List.of("torso", "bust") : List.of("torso"),
                            GarmentPartSelection.select(Map.of(), arm, chest, mapping.keySet()));
                }
            }
            for (String semantic : mapping.keySet()) {
                var model = part(id, semantic);
                var variant = model.variants().get(id);
                assertFalse(model.translucent());
                assertTrue(variant.brightnessMask().isEmpty());
                assertTrue(variant.overlay().isEmpty());
                var base = texture(variant.base());
                assertEquals(model.textureWidth(), base.getWidth());
                assertEquals(model.textureHeight(), base.getHeight());
                Set<Integer> shades = new java.util.HashSet<>();
                for (int y = 0; y < base.getHeight(); y++) {
                    for (int x = 0; x < base.getWidth(); x++) {
                        int p = base.getRGB(x, y);
                        assertTrue((p >>> 24) == 0 || (p >>> 24) == 255);
                        assertEquals(p & 255, (p >> 8) & 255);
                        assertEquals(p & 255, (p >> 16) & 255);
                        if ((p >>> 24) != 0) shades.add(p & 255);
                    }
                }
                assertTrue(shades.size() > 1, id + "/" + semantic);
                for (var element : model.elements()) {
                    assertFalse(element.name().contains("decoration"));
                    if (semantic.equals("torso")) assertFalse(element.faces().containsKey(Face.BOTTOM));
                    if (semantic.equals("bust")) {
                        assertFalse(element.faces().containsKey(Face.TOP));
                        assertFalse(element.faces().containsKey(Face.BACK));
                    }
                    for (var uv : element.faces().values()) {
                        boolean visible = false;
                        for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                            for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                                visible |= (base.getRGB(x, y) >>> 24) != 0;
                            }
                        }
                        assertTrue(visible, id + "/" + semantic + " / " + element.name());
                    }
                }
            }
        }
    }

    @Test
    void metalAndGemMasksResolveIndependentColorsForEveryCombination() throws Exception {
        for (String semantic : List.of("torso", "bust")) {
            var part = part("necklace", semantic);
            var masks = part.designsFor("necklace").get("plain");
            var mask = texture(masks.secondary().orElseThrow());
            var base = texture(part.variants().get("necklace").base());
            int gemCount = 0;
            int metalCount = 0;
            for (int y = 0; y < mask.getHeight(); y++) {
                for (int x = 0; x < mask.getWidth(); x++) {
                    int p = mask.getRGB(x, y);
                    assertEquals(255, p >>> 24);
                    assertEquals(p & 255, (p >> 8) & 255);
                    assertEquals(p & 255, (p >> 16) & 255);
                    if (p == -1) {
                        gemCount++;
                        assertEquals(255, base.getRGB(x, y) >>> 24);
                    } else {
                        assertEquals(0, p & 255);
                        if ((base.getRGB(x, y) >>> 24) == 255) metalCount++;
                    }
                    for (var metal : MetalAccessoryData.MetalColor.values()) {
                        for (var gem : MetalAccessoryData.Gem.values()) {
                            var data = new MetalAccessoryData(metal, gem);
                            int tint = GarmentTexturePixelOps.composeTint(data.primaryTintRgb(),
                                    data.secondaryTintRgb(), data.accentTintRgb(), p & 255, 0);
                            assertEquals(p == -1 ? gem.tintRgb() : metal.tintRgb(), tint);
                        }
                    }
                }
            }
            assertEquals(1, gemCount);
            assertTrue(metalCount > 0);
        }
    }

    @Test
    void necklaceManufacturingUsesThreeNuggetsThenTaggedGemsAndOnlyAppearanceNbt() throws Exception {
        for (var metal : MetalAccessoryData.MetalColor.values()) {
            String name = metal.getSerializedName();
            var compacting = json(GENERATED.resolve("recipes/compacting/" + name + "_necklace_base.json"));
            assertEquals("create:compacting", compacting.get("type").getAsString());
            assertEquals(3, compacting.getAsJsonArray("ingredients").size());
            for (var ingredient : compacting.getAsJsonArray("ingredients")) {
                assertEquals(metal == MetalAccessoryData.MetalColor.GOLD ? "minecraft:gold_nugget" : "minecraft:iron_nugget",
                        ingredient.getAsJsonObject().get("item").getAsString());
            }
            assertFalse(compacting.has("heatRequirement"));
            for (var gem : MetalAccessoryData.Gem.values()) {
                var recipe = json(GENERATED.resolve("recipes/deploying/necklace/" + name + "_" + gem.getSerializedName() + ".json"));
                assertEquals("create:deploying", recipe.get("type").getAsString());
                assertEquals("createapparel:" + name + "_necklace_base",
                        recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
                assertEquals("createapparel:gems/" + gem.getSerializedName(),
                        recipe.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("tag").getAsString());
                var result = recipe.getAsJsonArray("results").get(0).getAsJsonObject();
                assertEquals("createapparel:necklace", result.get("item").getAsString());
                var root = result.getAsJsonObject("nbt");
                assertEquals(Set.of(MetalAccessoryData.TAG_NAME), root.keySet());
                var data = root.getAsJsonObject(MetalAccessoryData.TAG_NAME);
                assertEquals(Set.of("metal_color", "gem"), data.keySet());
                assertEquals(name, data.get("metal_color").getAsString());
                assertEquals(gem.getSerializedName(), data.get("gem").getAsString());
            }
        }
        for (var gem : MetalAccessoryData.Gem.values()) {
            var tag = json(GENERATED.resolve("tags/items/gems/" + gem.getSerializedName() + ".json"));
            String fallback = switch (gem) {
                case DIAMOND -> "diamond";
                case EMERALD -> "emerald";
                case AMETHYST -> "amethyst_shard";
                case RUBY -> "redstone";
                case SAPPHIRE -> "lapis_lazuli";
                case PEARL -> "quartz";
            };
            assertEquals("minecraft:" + fallback, tag.getAsJsonArray("values").get(0).getAsString());
        }
        var tie = json(GENERATED.resolve("recipes/necktie.json"));
        assertEquals("createapparel:sewing", tie.get("type").getAsString());
        assertEquals(1, tie.getAsJsonObject("fabric").get("count").getAsInt());
        assertEquals(1, tie.getAsJsonObject("thread").get("count").getAsInt());
        assertEquals(JsonParser.parseString("[\"createapparel:fabrics/silk\",\"createapparel:fabrics/synthetic\"]"),
                tie.getAsJsonObject("fabric").get("tag"));
        assertFalse(Files.exists(GENERATED.resolve("recipes/necklace.json")));
    }

    private static GarmentPartModel part(String id, String semantic) throws Exception {
        return ClientGarmentAssetLoader.parsePart(resource(id + "/" + semantic),
                json(ASSETS.resolve("garment_models/parts/" + id + "/" + semantic + ".json")));
    }

    private static BufferedImage texture(ResourceLocation id) throws Exception {
        return ImageIO.read(ASSETS.resolve("textures/" + id.getPath() + ".png").toFile());
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath("createapparel", path);
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
