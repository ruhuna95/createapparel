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
        for (String renderType : new String[]{"translucent", "unsupported"}) {
            JsonObject json = model();
            json.addProperty("render_type", renderType);
            assertThrows(IllegalStateException.class, () -> ClientGarmentAssetLoader.parsePart(ID, json));
        }
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
