package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
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

import static org.junit.jupiter.api.Assertions.*;

class InnerGarmentAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final List<String> GARMENTS = List.of("undershirt", "bra", "briefs", "boxer_briefs", "panties");

    @Test
    void definitionsResolveEveryBodyProfileAndEveryReferencedTexture() throws IOException {
        for (String garment : GARMENTS) {
            boolean top = garment.equals("undershirt") || garment.equals("bra");
            String slot = top ? "inner_top" : "inner_bottom";
            var definition = json(Path.of("src/main/resources/data/createapparel/garments/" + garment + ".json"));
            assertEquals(slot, definition.get("default_slot").getAsString());
            assertEquals(JsonParser.parseString("[\"" + slot + "\"]"), definition.get("allowed_slots"));
            assertEquals("createapparel:" + slot, definition.get("model_template").getAsString());
            assertEquals(JsonParser.parseString("[\"plain\"]"), definition.get("designs"));
            assertEquals(0, definition.getAsJsonObject("styles").size());
            assertEquals(garment, definition.get("texture_variant").getAsString());
            String variant = top ? "default" : garment.equals("boxer_briefs") ? "withlegs"
                    : garment.equals("briefs") ? "briefs" : "nolegs";
            assertEquals(variant, definition.get("model_variant").getAsString());
            var parts = json(ASSETS.resolve("garment_models/templates/" + slot + ".json")).getAsJsonObject(variant);
            for (var arms : ArmProfile.values()) {
                for (var chest : ChestProfile.values()) {
                    List<String> expected = top && chest == ChestProfile.BUST ? List.of("torso", "bust")
                            : garment.equals("boxer_briefs") ? List.of("torso", "left_leg", "right_leg") : List.of("torso");
                    assertEquals(expected, GarmentPartSelection.select(Map.of(), arms, chest, parts.keySet()));
                }
            }
            for (String name : parts.keySet()) {
                String model = parts.getAsJsonObject(name).get("model").getAsString().split(":")[1];
                var part = ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:" + model),
                        json(ASSETS.resolve("garment_models/parts/" + model + ".json")));
                var texture = part.variants().get(garment);
                assertNotNull(texture, model + " / " + garment);
                assertTrue(part.designs().isEmpty());
                assertTrue(texture.overlay().isEmpty());
                assertTrue(texture.brightnessMask().isEmpty());
                var image = ImageIO.read(ASSETS.resolve("textures/" + texture.base().getPath() + ".png").toFile());
                assertEquals(part.textureWidth(), image.getWidth());
                assertEquals(part.textureHeight(), image.getHeight());
                var shades = new HashSet<Integer>();
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = image.getRGB(x, y);
                        assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
                        assertEquals((pixel >> 16) & 255, (pixel >> 8) & 255);
                        assertEquals((pixel >> 8) & 255, pixel & 255);
                        if (pixel >>> 24 != 0) { shades.add(pixel & 255); }
                    }
                }
                assertTrue(shades.size() > 2, model + " / " + garment);
            }
        }
    }

    @Test
    void bodyGeometryKeepsTheCurrentSupportShapesAndRibbonIsPantiesOnly() throws IOException {
        for (var entry : Map.of("inner_top/torso", "default_underwear_top/top_torso",
                "inner_top/bust", "default_underwear_top/top_bust",
                "inner_bottom/torso", "default_underwear_bottom/bottom_torso",
                "inner_bottom/left_leg", "default_underwear_bottom/bottom_left_leg",
                "inner_bottom/right_leg", "default_underwear_bottom/bottom_right_leg").entrySet()) {
            var actual = json(ASSETS.resolve("garment_models/parts/" + entry.getKey() + ".json")).getAsJsonObject("geometry");
            var expected = json(ASSETS.resolve("garment_models/parts/" + entry.getValue() + ".json")).getAsJsonObject("geometry");
            assertEquals(expected.get("pivot"), actual.get("pivot"));
            var body = actual.getAsJsonArray("elements").get(0).getAsJsonObject();
            var support = expected.getAsJsonArray("elements").get(0).getAsJsonObject();
            body.remove("name");
            support.remove("name");
            assertEquals(support, body);
        }
        var ribbon = json(ASSETS.resolve("garment_models/parts/inner_bottom/torso.json"))
                .getAsJsonObject("geometry").getAsJsonArray("elements").get(1).getAsJsonObject();
        assertEquals(Set.of("front"), ribbon.getAsJsonObject("faces").keySet());
        assertEquals(-2.1, ribbon.getAsJsonArray("from").get(2).getAsDouble(), 0.00001);
        for (String garment : List.of("boxer_briefs", "panties")) {
            var image = ImageIO.read(ASSETS.resolve("textures/garment/inner_bottom/torso/base/" + garment + ".png").toFile());
            int pixels = 0;
            for (int x = 4; x < 12; x++) { if (image.getRGB(x, 6) >>> 24 != 0) { pixels++; } }
            assertEquals(garment.equals("panties") ? 3 : 0, pixels);
        }
    }

    @Test
    void sewingIncludesSyntheticWithOneFabricAndOneThread() throws IOException {
        for (String garment : GARMENTS) {
            var recipe = json(Path.of("src/generated/resources/data/createapparel/recipes/" + garment + ".json"));
            assertEquals("createapparel:sewing", recipe.get("type").getAsString());
            assertEquals("createapparel:" + garment, recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(JsonParser.parseString("[\"createapparel:fabrics/cotton\",\"createapparel:fabrics/silk\",\"createapparel:fabrics/synthetic\"]"),
                    recipe.getAsJsonObject("fabric").get("tag"));
            assertEquals(1, recipe.getAsJsonObject("fabric").get("count").getAsInt());
            assertEquals(1, recipe.getAsJsonObject("thread").get("count").getAsInt());
            var model = json(Path.of("src/generated/resources/assets/createapparel/models/item/" + garment + ".json"));
            assertEquals("minecraft:builtin/entity", model.get("parent").getAsString());
        }
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
