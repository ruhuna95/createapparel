package com.rexhub.createapparel.client.renderer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class CitizenGaugeAssetTest {
    private static final Path GENERATED = Path.of("src/generated/resources/assets/createapparel");
    private static final String TEXTURE = "createapparel:block/citizen_gauge/factory_panel";

    @Test void resetRecipeUsesTheSpecialSerializerAndManualOnlyAutomationGuard() throws IOException {
        var recipe = JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/data/createapparel/recipes/citizen_gauge_reset_manual_only.json"))).getAsJsonObject();
        assertEquals("createapparel:citizen_gauge_reset", recipe.get("type").getAsString());
        assertEquals("misc", recipe.get("category").getAsString());
    }

    @Test void creativeDiscardPrivateMembersHaveProductionMappings() throws IOException {
        var refmap = JsonParser.parseString(Files.readString(Path.of("build/generated/mixin/createapparel.refmap.json")))
                .getAsJsonObject().getAsJsonObject("mappings").getAsJsonObject("com/rexhub/createapparel/client/mixin/CreativeInventoryGaugeMixin");
        assertTrue(refmap.has("destroyItemSlot"));
        assertEquals("f_98512_:Lnet/minecraft/world/inventory/Slot;", refmap.get("destroyItemSlot").getAsString());
        assertEquals("m_98553_(Lnet/minecraft/world/inventory/Slot;)Z", refmap.get("isCreativeSlot").getAsString());
    }

    @Test
    void suppliedDistrictIconIsAnUntintedGrayscaleBaseWithTransparency() throws IOException {
        var image = ImageIO.read(Path.of("src/main/resources/assets/createapparel/textures/item/gauge_district_icon.png").toFile());
        assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
        for (int x : new int[]{0, 15}) for (int y : new int[]{0, 15}) assertEquals(0, image.getRGB(x, y));
        var shades = new java.util.HashSet<Integer>();
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int pixel = image.getRGB(x, y); if (pixel >>> 24 == 0) continue;
            assertEquals(255, pixel >>> 24);
            assertEquals(pixel >>> 16 & 255, pixel >>> 8 & 255);
            assertEquals(pixel >>> 8 & 255, pixel & 255);
            shades.add(pixel & 255);
        }
        assertTrue(shades.size() >= 2);
    }

    @Test void suppliedFlowIconsAreDistinctColoredTextures() throws IOException {
        var root = Path.of("src/main/resources/assets/createapparel/textures/item");
        var influx = ImageIO.read(root.resolve("gauge_influx_icon.png").toFile());
        var outflow = ImageIO.read(root.resolve("gauge_outflow_icon.png").toFile());
        assertEquals(16, influx.getWidth()); assertEquals(16, influx.getHeight());
        assertEquals(16, outflow.getWidth()); assertEquals(16, outflow.getHeight());
        assertFalse(java.util.Arrays.equals(influx.getRGB(0, 0, 16, 16, null, 0, 16), outflow.getRGB(0, 0, 16, 16, null, 0, 16)));
    }

    @Test
    void panelAndItemInheritFactoryGeometryUvsAndDisplayTransformsWithoutOverrides() throws IOException {
        for (String path : new String[]{"block/citizen_gauge/panel", "item/citizen_gauge"}) {
            JsonObject model = JsonParser.parseString(Files.readString(GENERATED.resolve("models/" + path + ".json"))).getAsJsonObject();
            assertEquals(Set.of("parent", "textures"), model.keySet());
            assertEquals("create:block/factory_gauge/" + (path.startsWith("block/") ? "panel" : "item"), model.get("parent").getAsString());
            var textures = model.getAsJsonObject("textures");
            assertEquals(Set.of("0", "particle"), textures.keySet());
            assertEquals(TEXTURE, textures.get("0").getAsString());
            assertEquals(TEXTURE, textures.get("particle").getAsString());
        }
    }

    @Test
    void textureKeepsFactoryPixelGridAlphaFaceBackplateAndIndicatorUnchanged() throws IOException {
        try (var input = getClass().getResourceAsStream("/assets/create/textures/block/factory_panel.png")) {
            assertNotNull(input, "Create's original texture must be available for pixel parity checks");
            var original = ImageIO.read(input);
            var actual = ImageIO.read(GENERATED.resolve("textures/block/citizen_gauge/factory_panel.png").toFile());
            assertNotNull(actual);
            assertEquals(16, original.getWidth());
            assertEquals(16, original.getHeight());
            assertEquals(original.getWidth(), actual.getWidth());
            assertEquals(original.getHeight(), actual.getHeight());
            int changed = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int before = original.getRGB(x, y);
                    int after = actual.getRGB(x, y);
                    String pixel = "Pixel " + x + "," + y;
                    assertEquals(before >>> 24, after >>> 24, pixel + " alpha");
                    boolean rim = x < 8 && y < 8 && (before >>> 24) != 0;
                    if (rim) {
                        assertNotEquals(before, after, pixel + " brass rim must be recolored");
                        assertTrue((after & 255) > ((after >>> 16) & 255), pixel + " rim must be diamond cyan");
                        changed++;
                    } else {
                        assertEquals(before, after, pixel + " outside rim must be identical");
                    }
                }
            }
            assertEquals(44, changed);
        }
    }
}
