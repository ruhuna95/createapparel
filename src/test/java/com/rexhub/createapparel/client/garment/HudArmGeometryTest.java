package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudArmGeometryTest {
    @Test
    void cutsExactlyThreePixelsFromTheTipAndInterpolatesUvWithoutChangingTheAsset() {
        var model = model(12, 24, "");
        var original = model.elements().get(0).bakedFaces();
        var clipped = HudArmGeometry.rolledSleeve(model);
        assertEquals(1, clipped.size());
        assertSame(clipped, HudArmGeometry.rolledSleeve(model));
        for (var vertex : clipped.get(0).vertices()) {
            assertTrue(vertex.y() >= 15);
            assertEquals(24 - vertex.y(), vertex.v(), 0.00001);
        }
        assertTrue(clipped.get(0).vertices().stream().anyMatch(vertex -> vertex.y() == 15));
        assertTrue(original.get(0).vertices().stream().anyMatch(vertex -> vertex.y() == 12));
        assertSame(original, model.elements().get(0).bakedFaces());
        assertEquals(original.get(0).normal(), clipped.get(0).normal());
        HudArmGeometry.clearCache();
        assertNotSame(clipped, HudArmGeometry.rolledSleeve(model));
    }

    @Test
    void removesCuffDecorationsButLeavesShortSleevesUntouched() {
        assertTrue(HudArmGeometry.rolledSleeve(model(12, 14, "")).isEmpty());
        assertTrue(HudArmGeometry.rolledSleeve(model(12, 15, "")).isEmpty());
        var shortSleeve = model(17, 24, "");
        assertEquals(shortSleeve.elements().get(0).bakedFaces(), HudArmGeometry.rolledSleeve(shortSleeve));
    }

    @Test
    void cutsAfterElementRotationAndKeepsRotatedNormalsAndUvs() {
        var model = model(12, 24, ", \"rotation\": {\"axis\": \"z\", \"angle\": 30, \"origin\": [0,15,0]}");
        var clipped = HudArmGeometry.rolledSleeve(model);
        assertFalse(clipped.isEmpty());
        var normal = clipped.get(0).normal();
        assertEquals(-1, normal.z(), 0.00001);
        for (var face : clipped) {
            assertEquals(4, face.vertices().size());
            for (var vertex : face.vertices()) {
                assertTrue(vertex.y() >= 15);
                assertTrue(vertex.u() >= 0 && vertex.u() <= 4);
                assertTrue(vertex.v() >= 0 && vertex.v() <= 12);
            }
        }
    }

    private static GarmentPartModel model(int bottom, int top, String rotation) {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.fromNamespaceAndPath("test", "hud_arm"),
                JsonParser.parseString("""
                        {"render_type":"cutout", "texture_size":[16,16], "geometry":{"pivot":[0,0,0], "elements":[{
                          "name":"sleeve", "from":[0,%d,0], "to":[4,%d,4],
                          "faces":{"front":{"uv":[0,0,4,%d]}}%s
                        }]}, "textures":{"variants":{"default":{"base":"test:base"}}}}
                        """.formatted(bottom, top, top - bottom, rotation)).getAsJsonObject());
    }
}
