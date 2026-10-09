package com.rexhub.createapparel.client.garment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JewelryBaseTextureTest {
    @ParameterizedTest
    @ValueSource(strings = {"gold_necklace_base", "silver_necklace_base", "gold_bracelet_base", "silver_bracelet_base"})
    void spritesHaveDarkOuterAndInnerOutlinesAndKeepMetalHighlights(String name) throws Exception {
        BufferedImage image = ImageIO.read(Path.of("src/main/resources/assets/createapparel/textures/item/" + name + ".png").toFile());
        Set<Integer> palette = name.startsWith("gold_")
                ? Set.of(0xFF65421C, 0xFF8A4A14, 0xFFB87820, 0xFFE8B638, 0xFFF8D86C, 0xFFFFF1A0)
                : Set.of(0xFF394A58, 0xFF465969, 0xFF768C9B, 0xFFA8BCC7, 0xFFD8E6EC, 0xFFF5FCFF);
        int outline = name.startsWith("gold_") ? 0xFF65421C : 0xFF394A58;
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        assertEquals(0, image.getRGB(7, 7) >>> 24);
        int contourPixels = 0;
        int coloredPixels = 0;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int pixel = image.getRGB(x, y);
            int alpha = pixel >>> 24;
            assertTrue(alpha == 0 || alpha == 255);
            if (x == 0 || x == 15 || y == 0 || y == 15) assertEquals(0, alpha);
            if (alpha == 0) continue;
            assertTrue(palette.contains(pixel));
            if (pixel != outline) coloredPixels++;
            if (transparent(image, x - 1, y) || transparent(image, x + 1, y)
                    || transparent(image, x, y - 1) || transparent(image, x, y + 1)) {
                assertEquals(outline, pixel, name + " contour " + x + "/" + y);
                contourPixels++;
            }
        }
        assertTrue(contourPixels >= 20);
        assertTrue(coloredPixels >= 12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"necklace", "bracelet"})
    void silverUsesTheGoldShapeAndCorrespondingMetalShades(String kind) throws Exception {
        var gold = ImageIO.read(Path.of("src/main/resources/assets/createapparel/textures/item/gold_" + kind + "_base.png").toFile());
        var silver = ImageIO.read(Path.of("src/main/resources/assets/createapparel/textures/item/silver_" + kind + "_base.png").toFile());
        var shades = java.util.Map.of(0xFF65421C, 0xFF394A58, 0xFF8A4A14, 0xFF465969,
                0xFFB87820, 0xFF768C9B, 0xFFE8B638, 0xFFA8BCC7, 0xFFF8D86C, 0xFFD8E6EC, 0xFFFFF1A0, 0xFFF5FCFF);
        try (var stream = getClass().getResourceAsStream("/texture_reference/gold_" + kind + "_base.png")) {
            assertNotNull(stream);
            var original = ImageIO.read(stream);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int pixel = gold.getRGB(x, y);
                assertEquals(original.getRGB(x, y) >>> 24, pixel >>> 24, "Gold silhouette must remain unchanged");
                assertEquals(pixel >>> 24, silver.getRGB(x, y) >>> 24);
                if (pixel >>> 24 != 0) assertEquals(shades.get(pixel).intValue(), silver.getRGB(x, y));
                int originalPixel = original.getRGB(x, y);
                assertEquals(originalPixel == 0xFF4E2E10 ? 0xFF65421C : originalPixel, pixel);
            }
        }
    }

    private static boolean transparent(BufferedImage image, int x, int y) {
        return x < 0 || x >= 16 || y < 0 || y >= 16 || image.getRGB(x, y) >>> 24 == 0;
    }
}
