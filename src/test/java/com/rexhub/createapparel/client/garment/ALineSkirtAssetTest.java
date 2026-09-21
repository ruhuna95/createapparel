package com.rexhub.createapparel.client.garment;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ALineSkirtAssetTest {
    @Test
    void textureAndOpenShellMatchAssetContract() throws Exception {
        var part = DualEnvelopeDeformerTest.part("a_line_skirt/torso");
        assertEquals(5, part.elements().size());
        assertEquals(14, part.elements().get(0).from().y());
        assertEquals(15.004F, part.elements().get(0).to().y());
        for (var panel : part.elements().subList(1, 5)) {
            assertEquals(14, panel.to().y());
            assertEquals(5.996F, panel.from().y());
            assertEquals(8, panel.faces().values().iterator().next().v1()
                    - panel.faces().values().iterator().next().v0());
        }
        var png = ImageIO.read(Path.of(
                "src/main/resources/assets/createapparel/textures/garment/a_line_skirt/torso/base/default.png").toFile());
        assertEquals(32, png.getWidth());
        assertEquals(32, png.getHeight());
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int pixel = png.getRGB(x, y);
                assertEquals(pixel >> 16 & 255, pixel >> 8 & 255);
                assertEquals(pixel >> 8 & 255, pixel & 255);
                assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
            }
        }
        for (var element : part.elements()) {
            assertFalse(element.faces().containsKey(ClientGarmentAssets.Face.TOP));
            assertFalse(element.faces().containsKey(ClientGarmentAssets.Face.BOTTOM));
            for (var uv : element.faces().values()) {
                assertEquals(255, png.getRGB((int) ((uv.u0() + uv.u1()) / 2),
                        (int) ((uv.v0() + uv.v1()) / 2)) >>> 24);
            }
        }
    }

}
