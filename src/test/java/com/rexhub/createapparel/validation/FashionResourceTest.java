package com.rexhub.createapparel.validation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FashionResourceTest {
    private static final Path MAIN = Path.of("src/main/resources/assets/createapparel");
    private static final Path GENERATED = Path.of("src/generated/resources/assets/createapparel");

    @Test
    void authoredFashionModelsAndStatesHaveNoGeneratedCopies() {
        for (String name : List.of("advertising_photo", "camera", "poster", "cash_register", "signage_broadcaster")) {
            assertHandwritten("models/item/" + name + ".json");
        }
        for (String name : List.of("poster", "cash_register", "signage_broadcaster")) {
            assertHandwritten("models/block/" + name + ".json");
            assertHandwritten("blockstates/" + name + ".json");
        }
    }

    @Test
    void photoAndPosterItemsUseAuthoredIcons() throws Exception {
        for (String name : List.of("advertising_photo", "poster")) {
            var model = ProjectResources.json(MAIN.resolve("models/item/" + name + ".json"));
            assertEquals("minecraft:item/generated", model.get("parent").getAsString());
            assertEquals("createapparel:item/" + name, model.getAsJsonObject("textures").get("layer0").getAsString());
        }
    }

    @Test
    void posterHasAPaintingThicknessAndWoodenBacking() throws Exception {
        var model = ProjectResources.json(MAIN.resolve("models/block/poster.json"));
        assertEquals("minecraft:painting/back", model.getAsJsonObject("textures").get("frame").getAsString());
        var element = model.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals(1, element.getAsJsonArray("to").get(2).getAsInt() - element.getAsJsonArray("from").get(2).getAsInt());
        var source = ProjectResources.json(Path.of("src/main/resources/assets/minecraft/atlases/blocks.json"))
                .getAsJsonArray("sources").get(0).getAsJsonObject();
        assertEquals("minecraft:single", source.get("type").getAsString());
        assertEquals("minecraft:painting/back", source.get("resource").getAsString());
        assertEquals(source.get("resource"), source.get("sprite"));
    }

    @Test
    void mediaRecipesUsePaperAndPosterWithGlassWithoutManualPrinting() throws Exception {
        var recipes = Path.of("src/generated/resources/data/createapparel/recipes");
        var poster = ProjectResources.json(recipes.resolve("poster.json"));
        assertEquals("minecraft:crafting_shaped", poster.get("type").getAsString());
        assertEquals(List.of("PP", "PP"), java.util.stream.StreamSupport.stream(poster.getAsJsonArray("pattern").spliterator(), false)
                .map(com.google.gson.JsonElement::getAsString).toList());
        assertEquals(1, poster.getAsJsonObject("key").size());
        assertEquals("minecraft:paper", poster.getAsJsonObject("key").getAsJsonObject("P").get("item").getAsString());
        var billboard = ProjectResources.json(recipes.resolve("billboard_panel.json"));
        assertEquals("minecraft:crafting_shapeless", billboard.get("type").getAsString());
        assertEquals(java.util.Set.of("createapparel:poster", "minecraft:glass_pane"), java.util.stream.StreamSupport
                .stream(billboard.getAsJsonArray("ingredients").spliterator(), false)
                .map(value -> value.getAsJsonObject().get("item").getAsString()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, billboard.getAsJsonObject("result").get("count").getAsInt());
        assertFalse(Files.exists(recipes.resolve("ad_print_manual_only.json")));
    }

    @Test
    void deployerPrintingHasTwoNonConsumingDataRecipes() throws Exception {
        for (var media : List.of("poster", "billboard_panel")) {
            var recipe = ProjectResources.json(Path.of("src/generated/resources/data/createapparel/recipes/deploying/" + media + "_printing.json"));
            assertEquals("createapparel:ad_printing", recipe.get("type").getAsString());
            assertTrue(recipe.get("keepHeldItem").getAsBoolean());
            var ingredients = recipe.getAsJsonArray("ingredients");
            assertEquals(2, ingredients.size());
            assertEquals("createapparel:" + media, ingredients.get(0).getAsJsonObject().get("item").getAsString());
            assertEquals("createapparel:advertising_photo", ingredients.get(1).getAsJsonObject().get("item").getAsString());
        }
    }

    @Test
    void broadcasterUsesAuthoredFacesAndTopForBottom() throws Exception {
        var model = ProjectResources.json(MAIN.resolve("models/block/signage_broadcaster.json"));
        assertEquals("minecraft:block/cube", model.get("parent").getAsString());
        var textures = model.getAsJsonObject("textures");
        assertEquals("createapparel:block/signage_broadcaster_top", textures.get("up").getAsString());
        assertEquals(textures.get("up"), textures.get("down"));
        assertEquals("createapparel:block/signage_broadcaster_front", textures.get("north").getAsString());
        assertEquals("createapparel:block/signage_broadcaster_back", textures.get("south").getAsString());
        assertEquals("createapparel:block/signage_broadcaster_side", textures.get("east").getAsString());
        assertEquals(textures.get("east"), textures.get("west"));
    }

    @Test
    void advertisementsUsePhotoDataInsteadOfLegacyFallbackTextures() throws Exception {
        for (String name : List.of("blank", "default_collection")) {
            String path = "textures/advertisement/" + name + ".png";
            assertFalse(Files.exists(MAIN.resolve(path)));
            assertFalse(Files.exists(GENERATED.resolve(path)));
        }
        for (String name : List.of("billboard_panel", "signage_panel")) {
            var image = ProjectResources.png(GENERATED.resolve("textures/item/" + name + ".png"));
            assertEquals(128, image.getWidth()); assertEquals(128, image.getHeight());
            assertEquals(0, image.getRGB(0, 0) >>> 24);
            var model = ProjectResources.json(GENERATED.resolve("models/item/" + name + ".json"));
            assertEquals("minecraft:item/generated", model.get("parent").getAsString());
            assertEquals("createapparel:item/" + name, model.getAsJsonObject("textures").get("layer0").getAsString());
        }
    }

    @Test
    void advertisementItemIconsPixelateOnlyThePhoto() throws Exception {
        for (String name : List.of("billboard_panel", "signage_panel")) {
            var image = ProjectResources.png(GENERATED.resolve("textures/item/" + name + ".png"));
            var colors = new java.util.HashSet<Integer>();
            for (int cellY = 0; cellY < 14; cellY++) for (int cellX = 0; cellX < 14; cellX++) {
                int x = 8 + cellX * 8, y = 8 + cellY * 8, color = image.getRGB(x, y); colors.add(color);
                assertEquals(255, color >>> 24);
                for (int dy = 0; dy < 8; dy++) for (int dx = 0; dx < 8; dx++) assertEquals(color, image.getRGB(x + dx, y + dy), "The photo uses a 14 by 14 logical grid");
            }
            assertTrue(colors.size() > 6, "The photograph still contains a visible figure rather than a placeholder");
            assertEquals(name.equals("billboard_panel") ? 0xFFE2E2E2 : 0xFF555555, image.getRGB(4, 64));
            assertEquals(name.equals("billboard_panel") ? 0xFFBBBBBB : 0xFF171717, image.getRGB(7, 64));
        }
    }

    @Test
    void cameraGuiTransformCentersTheProjectedModelBounds() throws Exception {
        var model = ProjectResources.json(MAIN.resolve("models/item/camera.json"));
        var gui = model.getAsJsonObject("display").getAsJsonObject("gui");
        var angles = gui.getAsJsonArray("rotation");
        var rotation = new org.joml.Quaternionf().rotationXYZ(
                (float) Math.toRadians(angles.get(0).getAsFloat()),
                (float) Math.toRadians(angles.get(1).getAsFloat()),
                (float) Math.toRadians(angles.get(2).getAsFloat()));
        var scale = gui.getAsJsonArray("scale");
        var translation = gui.getAsJsonArray("translation");
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (var entry : model.getAsJsonArray("elements")) {
            var element = entry.getAsJsonObject();
            var from = element.getAsJsonArray("from");
            var to = element.getAsJsonArray("to");
            for (int corner = 0; corner < 8; corner++) {
                var point = new org.joml.Vector3f();
                for (int axis = 0; axis < 3; axis++) {
                    float coordinate = ((corner & (1 << axis)) == 0 ? from : to).get(axis).getAsFloat() - 8;
                    point.setComponent(axis, coordinate * scale.get(axis).getAsFloat());
                }
                rotation.transform(point);
                point.add(translation.get(0).getAsFloat(), translation.get(1).getAsFloat(), translation.get(2).getAsFloat());
                minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
            }
        }
        assertEquals(0, (minX + maxX) / 2, 0.00001);
        assertEquals(0, (minY + maxY) / 2, 0.00001);
    }

    @Test
    void broadcasterBlockstatesRotateTheNorthFrontToEachFacing() throws Exception {
        var variants = ProjectResources.json(MAIN.resolve("blockstates/signage_broadcaster.json")).getAsJsonObject("variants");
        for (var entry : java.util.Map.of("north", 0, "east", 90, "south", 180, "west", 270).entrySet()) {
            var variant = variants.getAsJsonObject("facing=" + entry.getKey());
            assertEquals("createapparel:block/signage_broadcaster", variant.get("model").getAsString());
            assertEquals(entry.getValue().intValue(), variant.has("y") ? variant.get("y").getAsInt() : 0);
        }
    }

    @Test
    void removedPhotoCardHasNoGeneratedResources() {
        assertFalse(Files.exists(GENERATED.resolve("models/item/blank_photo_card.json")));
        var data = Path.of("src/generated/resources/data/createapparel");
        assertFalse(Files.exists(data.resolve("recipes/blank_photo_card.json")));
        assertFalse(Files.exists(data.resolve("advancements/recipes/misc/blank_photo_card.json")));
    }

    private static void assertHandwritten(String path) {
        assertTrue(Files.isRegularFile(MAIN.resolve(path)), "Missing handwritten asset: " + path);
        assertFalse(Files.exists(GENERATED.resolve(path)), "Generated asset shadows authored asset: " + path);
    }
}
