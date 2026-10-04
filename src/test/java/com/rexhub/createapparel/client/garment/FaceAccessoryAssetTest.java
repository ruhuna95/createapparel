package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.GarmentDefinition;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face.*;
import static org.junit.jupiter.api.Assertions.*;

class FaceAccessoryAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final Path RECIPES = Path.of("src/generated/resources/data/createapparel/recipes");

    @Test
    void wearDefaultsDownAndOnlyMaskItemsSelectUp() throws Exception {
        for (String id : List.of("glasses", "sunglasses", "mask")) {
            var definition = GarmentDefinition.fromJson(resource(id),
                    json(Path.of("src/main/resources/data/createapparel/garments/" + id + ".json")));
            String templateId = id.equals("sunglasses") ? "glasses" : id;
            var template = ClientGarmentAssetLoader.parseTemplate(resource(templateId),
                    json(ASSETS.resolve("garment_models/templates/" + templateId + ".json")));
            assertEquals(Map.of("wear", "down"), EquippedStyleState.defaultsFor(definition).styles());
            assertEquals(Map.of("wear", "down"), new EquippedStyleState(Map.of("wear", "invalid")).normalize(definition).styles());
            assertEquals(Map.of("wear", id.equals("mask") ? "up" : "down"), template.stylesForItem(definition));
            assertEquals(Set.of("head", "head_up"), GarmentPartSelection.requiredStyleParts(definition.styles(), false));
            for (var arms : ArmProfile.values()) {
                for (var chest : ChestProfile.values()) {
                    for (String wear : List.of("down", "up")) {
                        assertEquals(List.of(wear.equals("up") ? "head_up" : "head"),
                                GarmentPartSelection.select(Map.of("wear", wear), arms, chest, template.variants().get("default").keySet()));
                    }
                }
            }
        }
        var invalid = ClientGarmentAssetLoader.parseTemplate(resource("invalid"), JsonParser.parseString("""
                {"item_styles":{"wear":"invalid"},"default":{"head":{"model":"createapparel:mask/face"}}}
                """).getAsJsonObject());
        var definition = GarmentDefinition.fromJson(resource("mask"),
                json(Path.of("src/main/resources/data/createapparel/garments/mask.json")));
        assertThrows(IllegalStateException.class, () -> invalid.stylesForItem(definition));
    }

    @Test
    void raisedGlassesTranslateOnePixelAndRaisedMaskExtendsTwoWithoutChangingDown() throws Exception {
        var glasses = part("glasses", "face");
        var raised = part("glasses", "face_up");
        assertEquals(glasses.variants(), raised.variants());
        var lower = glasses.elements().get(0);
        var upper = raised.elements().get(0);
        assertEquals(lower.faces(), upper.faces());
        for (int i = 0; i < 8; i++) {
            assertEquals(lower.corners().get(i).x(), upper.corners().get(i).x());
            assertEquals(lower.corners().get(i).y() + 1, upper.corners().get(i).y());
            assertEquals(lower.corners().get(i).z(), upper.corners().get(i).z());
        }
        var mask = part("mask", "face");
        var maskUp = part("mask", "face_up");
        assertEquals(mask.variants(), maskUp.variants());
        assertEquals(mask.elements().get(0), maskUp.elements().get(0));
        assertEquals(3, maskUp.elements().size());
        float lowerEdge = 24.8125F;
        for (int i = 1; i < 3; i++) {
            var band = maskUp.elements().get(i);
            assertEquals(Set.of(FRONT, LEFT, RIGHT), band.faces().keySet());
            assertEquals(lowerEdge, band.from().y());
            assertEquals(lowerEdge + 1, band.to().y());
            assertEquals(-4.25F, band.from().z());
            assertEquals(-3.1875F, band.to().z());
            lowerEdge = band.to().y();
        }
        assertEquals(26.8125F, lowerEdge);
    }

    private static ResourceLocation resource(String id) {
        return ResourceLocation.fromNamespaceAndPath("createapparel", id);
    }

    private static ClientGarmentAssets.GarmentPartModel part(String id, String name) throws Exception {
        return ClientGarmentAssetLoader.parsePart(resource(id + "/" + name),
                json(ASSETS.resolve("garment_models/parts/" + id + "/" + name + ".json")));
    }

    @Test
    void accessoriesResolveToHeadForAllProfilesAndHaveOnlyRequestedFaces() throws Exception {
        for (String id : List.of("glasses", "sunglasses", "mask")) {
            var definition = json(Path.of("src/main/resources/data/createapparel/garments/" + id + ".json"));
            assertEquals("face", definition.get("default_slot").getAsString());
            assertEquals(JsonParser.parseString("[\"face\"]"), definition.get("allowed_slots"));
            String templateId = id.equals("sunglasses") ? "glasses" : id;
            var template = json(ASSETS.resolve("garment_models/templates/" + templateId + ".json"));
            var names = template.getAsJsonObject("default").keySet();
            for (var arms : ArmProfile.values()) {
                for (var chest : ChestProfile.values()) {
                    assertEquals(List.of("head"), GarmentPartSelection.select(Map.of(), arms, chest, names));
                }
            }
            var part = ClientGarmentAssetLoader.parsePart(ResourceLocation.fromNamespaceAndPath("createapparel", templateId + "/face"),
                    json(ASSETS.resolve("garment_models/parts/" + templateId + "/face.json")));
            assertEquals(1, part.elements().size());
            assertEquals(!id.equals("mask"), part.translucent());
            var element = part.elements().get(0);
            assertEquals(id.equals("mask") ? Set.of(FRONT, BOTTOM, LEFT, RIGHT) : Set.of(FRONT), element.faces().keySet());
            assertEquals(-4.25F, element.from().z());
            assertEquals(23.75F, element.from().y());
            var variant = part.variants().get(definition.get("texture_variant").getAsString());
            assertNotNull(variant);
            assertTrue(variant.brightnessMask().isEmpty());
            var base = ImageIO.read(ASSETS.resolve("textures/" + variant.base().getPath() + ".png").toFile());
            assertEquals(part.textureWidth(), base.getWidth());
            assertEquals(part.textureHeight(), base.getHeight());
            int visible = 0;
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < base.getWidth(); x++) {
                    int pixel = base.getRGB(x, y);
                    assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
                    assertEquals(pixel >> 16 & 255, pixel >> 8 & 255);
                    assertEquals(pixel >> 8 & 255, pixel & 255);
                    if (pixel >>> 24 != 0) visible++;
                }
            }
            assertEquals(id.equals("mask") ? 38 : 10, visible);
        }
    }

    @Test
    void lensesOccupyExactlyEightSpecifiedPixelsWithoutTintedBase() throws Exception {
        var base = ImageIO.read(ASSETS.resolve("textures/garment/glasses/face/base/glasses.png").toFile());
        Set<Integer> lensPixels = Set.of(41, 42, 45, 46, 49, 50, 53, 54);
        Set<Integer> framePixels = Set.of(32, 33, 34, 37, 38, 39, 40, 43, 44, 47);
        for (String id : List.of("glasses", "sunglasses")) {
            var overlay = ImageIO.read(ASSETS.resolve("textures/garment/glasses/face/overlay/" + id + ".png").toFile());
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    assertEquals(framePixels.contains(y * 8 + x), base.getRGB(x, y) >>> 24 != 0);
                    int alpha = overlay.getRGB(x, y) >>> 24;
                    assertEquals(lensPixels.contains(y * 8 + x), alpha != 0);
                    if (alpha != 0) {
                        assertEquals(0, base.getRGB(x, y) >>> 24);
                        assertTrue(id.equals("glasses") ? alpha < 40 : alpha > 180 && alpha < 255);
                    }
                }
            }
        }
    }

    @Test
    void eyewearUsesVanillaCraftingWithDyeColoredFramesAndMaskUsesSewing() throws Exception {
        assertFalse(Files.exists(RECIPES.resolve("glasses.json")));
        for (String eyewear : List.of("glasses", "sunglasses")) {
            try (var paths = Files.list(RECIPES.resolve(eyewear))) {
                var recipes = paths.toList();
                assertEquals(com.rexhub.createapparel.material.FabricColor.values().length, recipes.size());
                for (var path : recipes) {
                    String color = path.getFileName().toString().replace(".json", "");
                    var recipe = json(path);
                    assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
                    assertEquals(JsonParser.parseString("[\" C \",\"GIG\"]"), recipe.get("pattern"));
                    var key = recipe.getAsJsonObject("key");
                    var fabricColor = com.rexhub.createapparel.material.FabricColor.fromSerializedName(color).orElseThrow();
                    assertEquals((fabricColor.isVanilla() ? "minecraft:" : "createapparel:")
                            + color + "_dye", key.getAsJsonObject("C").get("item").getAsString());
                    assertEquals(eyewear.equals("glasses") ? "minecraft:glass_pane" : "minecraft:black_stained_glass_pane",
                            key.getAsJsonObject("G").get("item").getAsString());
                    assertEquals("minecraft:iron_nugget", key.getAsJsonObject("I").get("item").getAsString());
                    var result = recipe.getAsJsonObject("result");
                    assertEquals("createapparel:" + eyewear, result.get("item").getAsString());
                    var data = TagParser.parseTag(result.get("nbt").getAsString()).getCompound("GarmentData");
                    assertEquals(color, data.getString("primary_color"));
                    assertEquals("synthetic", data.getString("material"));
                    assertEquals("plain", data.getString("design"));
                }
            }
        }
        var mask = json(RECIPES.resolve("mask.json"));
        assertEquals("createapparel:sewing", mask.get("type").getAsString());
        assertEquals(1, mask.getAsJsonObject("fabric").get("count").getAsInt());
        assertEquals(1, mask.getAsJsonObject("thread").get("count").getAsInt());
        assertEquals(4, mask.getAsJsonObject("fabric").getAsJsonArray("tag").size());
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
