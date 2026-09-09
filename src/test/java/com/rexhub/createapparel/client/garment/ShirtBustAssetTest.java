package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Uv;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShirtBustAssetTest {
    @Test
    void northDecorationUsesDedicatedUvAndFollowsMainSurface() throws IOException {
        for (String name : new String[]{"bust", "bust_opened"}) {
            GarmentPartModel part = loadPart(name);
            assertEquals(3, part.elements().size());
            var main = part.elements().get(0);
            var decoration = part.elements().get(2);
            assertEquals(name + "_front_decoration", decoration.name());
            assertEquals(Set.of(Face.FRONT), decoration.faces().keySet());
            assertEquals(new Uv(3, 10, 10, 14), decoration.faces().get(Face.FRONT));
            assertEquals(main.rotation(), decoration.rotation());
            assertEquals(main.from().x() - 0.15F, decoration.from().x(), 0.000001F);
            assertEquals(main.from().y() - 0.15F, decoration.from().y(), 0.000001F);
            assertEquals(main.to().x() + 0.15F, decoration.to().x(), 0.000001F);
            assertEquals(main.to().y(), decoration.to().y());
            assertEquals(0.15F, main.from().z() - decoration.from().z(), 0.000001F);
            assertEquals(main.to().z(), decoration.to().z());
            assertEquals(part.elements().get(1).from(), decoration.from());
            assertEquals(part.elements().get(1).to(), decoration.to());
        }
    }

    @Test
    void northTextureContainsVisibleDecorationCutoutsAndBrightness() throws IOException {
        for (String name : new String[]{"bust", "bust_opened"}) {
            var variant = loadPart(name).variants().get("default");
            BufferedImage base = loadTexture(variant.base());
            BufferedImage brightness = loadTexture(variant.brightnessMask().orElseThrow());
            int opaque = 0;
            int brightened = 0;
            for (int v = 10; v < 14; v++) {
                for (int u = 3; u < 10; u++) {
                    if (base.getRGB(u, v) >>> 24 == 0) {
                        continue;
                    }
                    opaque++;
                    assertEquals(255, base.getRGB(u, v - 7) >>> 24);
                    if ((brightness.getRGB(u, v) & 0xFF) > 100) {
                        brightened++;
                    }
                }
            }
            assertTrue(opaque > 0 && opaque < 28);
            assertTrue(brightened > 0);
        }
    }

    @Test
    void allShirtButtonsShareOpaqueDecorationSurfaces() throws IOException {
        int overlayParts = 0;
        for (String name : loadShirtPartNames()) {
            GarmentPartModel part = loadPart(name);
            var variant = part.variants().get("default");
            if (variant.overlay().isEmpty()) {
                continue;
            }
            overlayParts++;
            BufferedImage base = loadTexture(variant.base());
            BufferedImage overlay = loadTexture(variant.overlay().orElseThrow());
            int buttons = 0;
            for (int v = 0; v < overlay.getHeight(); v++) {
                for (int u = 0; u < overlay.getWidth(); u++) {
                    if (overlay.getRGB(u, v) >>> 24 == 0) {
                        continue;
                    }
                    buttons++;
                    String location = name + " (" + u + "," + v + ")";
                    assertEquals(255, base.getRGB(u, v) >>> 24, location);
                    int surfaces = 0;
                    for (var element : part.elements()) {
                        for (Uv uv : element.faces().values()) {
                            if (u >= uv.u0() && u < uv.u1() && v >= uv.v0() && v < uv.v1()) {
                                assertTrue(element.name().endsWith("_decoration"), location);
                                surfaces++;
                            }
                        }
                    }
                    assertEquals(1, surfaces, location);
                }
            }
            assertTrue(buttons > 0, name);
        }
        assertEquals(6, overlayParts);
    }

    @Test
    void allShirtPartsUsePercentMasksWithoutDoubleBrightening() throws IOException {
        Set<ResourceLocation> masks = new LinkedHashSet<>();
        Set<String> names = loadShirtPartNames();
        assertEquals(18, names.size());
        for (String name : names) {
            var variant = loadPart(name).variants().get("default");
            assertEquals(1.0F, variant.brightness(), name);
            masks.add(variant.brightnessMask().orElseThrow());
        }
        assertEquals(12, masks.size());
        for (ResourceLocation mask : masks) {
            BufferedImage image = loadTexture(mask);
            boolean brightened = false;
            boolean neutral = false;
            for (int v = 0; v < image.getHeight(); v++) {
                for (int u = 0; u < image.getWidth(); u++) {
                    int pixel = image.getRGB(u, v);
                    int luminance = pixel & 0xFF;
                    assertEquals(255, pixel >>> 24);
                    assertEquals(luminance, pixel >>> 8 & 0xFF);
                    assertEquals(luminance, pixel >>> 16 & 0xFF);
                    brightened |= luminance > 100;
                    neutral |= luminance == 100;
                }
            }
            assertTrue(brightened && neutral, mask.toString());
        }
    }

    private static Set<String> loadShirtPartNames() throws IOException {
        try (InputStream stream = resource("garment_models/templates/shirt.json");
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Set<String> names = new LinkedHashSet<>();
            var variants = JsonParser.parseReader(reader).getAsJsonObject();
            for (var variant : variants.entrySet()) {
                for (var entry : variant.getValue().getAsJsonObject().entrySet()) {
                    String reference = entry.getValue().getAsJsonObject().get("model").getAsString();
                    names.add(reference.substring(reference.lastIndexOf('/') + 1));
                }
            }
            return names;
        }
    }

    private static GarmentPartModel loadPart(String name) throws IOException {
        try (InputStream stream = resource("garment_models/parts/shirt/" + name + ".json");
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return ClientGarmentAssetLoader.parsePart(
                    ResourceLocation.fromNamespaceAndPath("createapparel", "shirt/" + name),
                    JsonParser.parseReader(reader).getAsJsonObject()
            );
        }
    }

    private static BufferedImage loadTexture(ResourceLocation texture) throws IOException {
        try (InputStream stream = resource("textures/" + texture.getPath() + ".png")) {
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image);
            return image;
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = ShirtBustAssetTest.class.getResourceAsStream("/assets/createapparel/" + path);
        assertNotNull(stream, path);
        return stream;
    }
}
