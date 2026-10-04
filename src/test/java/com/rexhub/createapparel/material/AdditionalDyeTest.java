package com.rexhub.createapparel.material;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdditionalDyeTest {
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path DATA = GENERATED.resolve("data/createapparel");
    private static final Path ASSETS = GENERATED.resolve("assets/createapparel");
    private static final Path SPECIFICATION = Path.of("src/test/resources/material/additional_dyes.json");

    @Test
    void paletteAndNamesMatchTheSpecificationAndPreserveVanillaOrdinals() throws Exception {
        var colors = json(SPECIFICATION).getAsJsonArray("colors");
        assertEquals(17, colors.size());
        assertEquals(33, FabricColor.values().length);
        var vanilla = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
        for (int index = 0; index < vanilla.size(); index++) {
            var color = FabricColor.values()[index];
            assertEquals(vanilla.get(index), color.getSerializedName());
            assertTrue(color.isVanilla());
            assertEquals("color.minecraft." + vanilla.get(index), color.translationKey());
        }
        var en = json(ASSETS.resolve("lang/en_us.json"));
        var ja = json(ASSETS.resolve("lang/ja_jp.json"));
        for (int index = 0; index < colors.size(); index++) {
            var expected = colors.get(index).getAsJsonObject();
            var color = FabricColor.values()[16 + index];
            String id = expected.get("id").getAsString();
            assertEquals(id, color.getSerializedName());
            assertEquals(Integer.parseInt(expected.get("hex").getAsString().substring(1), 16), color.tintRgb());
            assertFalse(color.isVanilla());
            assertEquals(color, FabricColor.fromSerializedName(id).orElseThrow());
            assertEquals(expected.get("display_name"), en.get(color.translationKey()));
            assertEquals(expected.get("display_name_ja"), ja.get(color.translationKey()));
            assertEquals(expected.get("display_name").getAsString() + " Dye", en.get("item.createapparel." + id + "_dye").getAsString());
            assertEquals(expected.get("display_name_ja").getAsString() + "色の染料", ja.get("item.createapparel." + id + "_dye").getAsString());
        }
    }

    @Test
    void craftingInputsAndCountsMatchEveryRecipeInTheSpecification() throws Exception {
        var colors = json(SPECIFICATION).getAsJsonArray("colors");
        for (var value : colors) {
            var expected = value.getAsJsonObject();
            String id = expected.get("id").getAsString() + "_dye";
            var actual = json(DATA.resolve("recipes/apparel_dyes/" + id + ".json"));
            assertEquals("minecraft:crafting_shapeless", actual.get("type").getAsString());
            var ingredients = actual.getAsJsonArray("ingredients");
            assertEquals(expected.getAsJsonArray("ingredients").size(), ingredients.size(), id);
            for (int index = 0; index < ingredients.size(); index++) {
                assertEquals(expected.getAsJsonArray("ingredients").get(index),
                        ingredients.get(index).getAsJsonObject().get("item"), id);
            }
            assertEquals("createapparel:" + id, actual.getAsJsonObject("result").get("item").getAsString());
            assertEquals(actual.getAsJsonArray("ingredients").size(), actual.getAsJsonObject("result").get("count").getAsInt());
            var advancement = json(DATA.resolve("advancements/recipes/misc/apparel_dyes/" + id + ".json"));
            assertTrue(advancement.getAsJsonObject("criteria").has("has_dye"));
        }
        assertEquals(17, colors.size());
    }

    @Test
    void allThirtyThreeFabricColorsCanBeDyedRedyedAndDecolorized() throws Exception {
        for (FabricMaterial material : FabricMaterial.values()) {
            String suffix = material.getSerializedName() + "_fabric";
            var ids = new HashSet<String>();
            ids.add("createapparel:" + suffix);
            for (FabricColor color : FabricColor.values()) ids.add("createapparel:" + color.getSerializedName() + "_" + suffix);
            for (FabricColor color : FabricColor.values()) {
                var recipe = json(DATA.resolve("recipes/mixing/dyeing/" + color.getSerializedName() + "_" + suffix + ".json"));
                var ingredients = recipe.getAsJsonArray("ingredients");
                var accepted = new HashSet<String>();
                ingredients.get(0).getAsJsonArray().forEach(value -> accepted.add(value.getAsJsonObject().get("item").getAsString()));
                assertEquals(ids, accepted);
                assertEquals((color.isVanilla() ? "minecraft:" : "createapparel:") + color.getSerializedName() + "_dye",
                        ingredients.get(1).getAsJsonObject().get("item").getAsString());
                assertEquals(250, ingredients.get(2).getAsJsonObject().get("amount").getAsInt());
                assertEquals("createapparel:" + color.getSerializedName() + "_" + suffix,
                        recipe.getAsJsonArray("results").get(0).getAsJsonObject().get("item").getAsString());
            }
            ids.remove("createapparel:" + suffix);
            var accepted = new HashSet<String>();
            json(DATA.resolve("recipes/mixing/decolorizing/" + suffix + ".json"))
                    .getAsJsonArray("ingredients").get(0).getAsJsonArray()
                    .forEach(value -> accepted.add(value.getAsJsonObject().get("item").getAsString()));
            assertEquals(ids, accepted);
        }
    }

    @Test
    void dyesShareWhitenedGrayDyeTextureAndOnlyOneTintLayer() throws Exception {
        var base = json(ASSETS.resolve("models/item/apparel_dye.json"));
        assertEquals("minecraft:item/generated", base.get("parent").getAsString());
        assertEquals("createapparel:item/apparel_dye_base", base.getAsJsonObject("textures").get("layer0").getAsString());
        var tag = json(DATA.resolve("tags/items/apparel_dyes.json")).getAsJsonArray("values");
        assertEquals(17, tag.size());
        for (int index = 16; index < 33; index++) {
            String id = FabricColor.values()[index].getSerializedName() + "_dye";
            assertEquals("createapparel:" + id, tag.get(index - 16).getAsString());
            assertEquals("createapparel:item/apparel_dye", json(ASSETS.resolve("models/item/" + id + ".json")).get("parent").getAsString());
        }
    }

    @Test
    void dyeBasePreservesWhiteInteriorWithDarkerGrayscaleRim() throws Exception {
        var image = ImageIO.read(Path.of("src/main/resources/assets/createapparel/textures/item/apparel_dye_base.png").toFile());
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        var shades = new HashSet<Integer>();
        int transparentPixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                int alpha = pixel >>> 24;
                assertTrue(alpha == 0 || alpha == 255);
                if (alpha == 0) {
                    transparentPixels++;
                    continue;
                }
                int red = pixel >> 16 & 255;
                assertEquals(red, pixel >> 8 & 255);
                assertEquals(red, pixel & 255);
                boolean edge = x == 0 || x == 15 || y == 0 || y == 15
                        || image.getRGB(x - 1, y) >>> 24 == 0
                        || image.getRGB(x + 1, y) >>> 24 == 0
                        || image.getRGB(x, y - 1) >>> 24 == 0
                        || image.getRGB(x, y + 1) >>> 24 == 0;
                assertTrue(edge ? red >= 64 && red <= 144 : red >= 160,
                        "Unexpected brightness at " + x + "," + y);
                shades.add(red);
            }
        }
        assertEquals(176, transparentPixels);
        assertEquals(8, shades.size());
        assertTrue(shades.contains(255));
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
