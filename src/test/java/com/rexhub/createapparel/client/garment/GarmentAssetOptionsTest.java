package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarmentAssetOptionsTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "garment");

    @Test
    void oldJsonKeepsNeutralDefaults() {
        var part = ClientGarmentAssetLoader.parsePart(ID, model());
        assertEquals(false, part.translucent());
        var variant = part.variants().get("default");
        assertEquals(1.0F, variant.brightness());
        assertTrue(variant.brightnessMask().isEmpty());
        assertTrue(variant.overlay().isEmpty());
    }

    @Test
    void optionsAreGenericAndIndependent() {
        JsonObject json = model();
        JsonObject variant = variant(json);
        variant.addProperty("brightness", 1.15F);
        variant.addProperty("brightness_mask", "test:brightness");
        variant.addProperty("overlay", "test:overlay");
        var part = ClientGarmentAssetLoader.parsePart(ID, json);
        var parsed = part.variants().get("default");
        assertEquals(1.15F, parsed.brightness());
        assertEquals("test:brightness", parsed.brightnessMask().orElseThrow().toString());
        assertEquals("test:overlay", parsed.overlay().orElseThrow().toString());
    }

    @Test
    void brightnessDoesNotRequireAMaskOrOverlay() {
        JsonObject json = model();
        variant(json).addProperty("brightness", 1.5F);
        var parsed = ClientGarmentAssetLoader.parsePart(ID, json).variants().get("default");
        assertEquals(1.5F, parsed.brightness());
        assertTrue(parsed.brightnessMask().isEmpty());
        assertTrue(parsed.overlay().isEmpty());
    }

    @Test
    void invalidJsonSettingsFailBeforeRendering() {
        for (float value : new float[]{-0.1F, 4.01F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            JsonObject json = model();
            variant(json).addProperty("brightness", value);
            assertThrows(IllegalStateException.class, () -> ClientGarmentAssetLoader.parsePart(ID, json));
        }
        for (String renderType : new String[]{"unsupported"}) {
            JsonObject json = model();
            json.addProperty("render_type", renderType);
            assertThrows(IllegalStateException.class, () -> ClientGarmentAssetLoader.parsePart(ID, json));
        }
    }

    @Test
    void overlayOnlyDesignResolvesWithoutColorSelectorsAndKeepsCommonOverlay() {
        JsonObject json = model();
        variant(json).addProperty("overlay", "test:buttons");
        json.getAsJsonObject("textures").add("designs", JsonParser.parseString(
                "{\"logo_creeper\":{\"overlay\":\"test:print\"}}"));
        var part = ClientGarmentAssetLoader.parsePart(ID, json);
        var definition = com.rexhub.createapparel.garment.GarmentDefinition.fromJson(ID, JsonParser.parseString("""
                {"slot":"top", "model_template":"test:garment", "model_variant":"default",
                 "texture_variant":"default", "designs":["plain","logo_creeper"], "styles":{}}
                """).getAsJsonObject());
        var appearance = new com.rexhub.createapparel.garment.GarmentData(
                com.rexhub.createapparel.material.FabricMaterial.SYNTHETIC, "logo_creeper",
                com.rexhub.createapparel.material.FabricColor.WHITE, java.util.Optional.empty(), java.util.Optional.empty());
        var textures = part.resolveTextures(definition, appearance);
        assertEquals("test:print", textures.designOverlay().orElseThrow().toString());
        assertEquals("test:buttons", textures.overlay().orElseThrow().toString());
        assertEquals(new ClientGarmentAssets.ColorChannels(false, false), ClientGarmentAssets.colorChannels(
                java.util.Map.of("torso", ID), "default", "logo_creeper", java.util.Map.of(ID, part)));
        variant(json).add("designs", JsonParser.parseString("{\"logo_creeper\":{\"overlay\":\"test:variant_print\"}}"));
        var variantPart = ClientGarmentAssetLoader.parsePart(ID, json);
        assertEquals("test:variant_print", variantPart.resolveTextures(definition, appearance).designOverlay().orElseThrow().toString());
    }

    @Test
    void designWithoutAnyTextureFailsBeforeRendering() {
        JsonObject json = model();
        json.getAsJsonObject("textures").add("designs", JsonParser.parseString("{\"broken\":{}}"));
        var error = assertThrows(IllegalStateException.class, () -> ClientGarmentAssetLoader.parsePart(ID, json));
        assertTrue(error.getMessage().contains("no texture"));
    }

    @Test
    void designCanOverrideCommonOverlayAndBrightnessWithoutChangingPlainDefaults() {
        JsonObject json = model();
        variant(json).addProperty("overlay", "test:original_stitches");
        variant(json).addProperty("brightness_mask", "test:original_brightness");
        json.getAsJsonObject("textures").add("designs", JsonParser.parseString("""
                {"contrast_stitch":{"secondary_mask":"test:colored_stitches", "common_overlay":"test:transparent",
                                     "brightness_mask":"test:neutral_brightness"}}
                """));
        var part = ClientGarmentAssetLoader.parsePart(ID, json);
        var parsed = part.designs().get("contrast_stitch");
        assertEquals("test:transparent", parsed.commonOverlay().orElseThrow().toString());
        assertEquals("test:neutral_brightness", parsed.brightnessMask().orElseThrow().toString());
        assertEquals("test:original_stitches", part.variants().get("default").overlay().orElseThrow().toString());
        assertEquals("test:original_brightness", part.variants().get("default").brightnessMask().orElseThrow().toString());
    }

    private static JsonObject variant(JsonObject json) {
        return json.getAsJsonObject("textures").getAsJsonObject("variants").getAsJsonObject("default");
    }

    private static JsonObject model() {
        return JsonParser.parseString("""
                {
                  "render_type": "cutout", "texture_size": [1, 1],
                  "geometry": {"pivot": [0, 0, 0], "elements": [
                    {"name": "test", "from": [0, 0, 0], "to": [1, 1, 1],
                     "faces": {"front": {"uv": [0, 0, 1, 1]}}}
                  ]},
                  "textures": {"variants": {"default": {"base": "test:base"}}}
                }
                """).getAsJsonObject();
    }
}
