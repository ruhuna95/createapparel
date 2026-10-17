package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrouserFootwearAssetTest {
    private static final float CLEARANCE = 0.03125F;
    private static final float EPSILON = 0.00001F;

    @Test
    void sharedTrouserSidesEncloseEveryOverlappingFootwearSurface() throws Exception {
        for (String pants : List.of("denim_pants", "slacks")) for (String side : List.of("left_leg", "right_leg")) {
            var shell = DualEnvelopeDeformerTest.part(pants + "/" + side).elements().get(0);
            for (String family : List.of("shoes", "boots", "tall_boots", "low_shoes", "sandals")) {
                var footwear = DualEnvelopeDeformerTest.part(family + "/" + side);
                for (var element : footwear.elements()) {
                    float bottom = Math.max(shell.from().y(), element.from().y());
                    float top = Math.min(shell.to().y(), element.to().y());
                    if (bottom >= top) continue;
                    for (float y : new float[]{bottom, top}) {
                        float weight = (y - shell.from().y()) / (shell.to().y() - shell.from().y());
                        float left = lerp(shell, 0, weight, true), right = lerp(shell, 1, weight, true);
                        float front = lerp(shell, 0, weight, false), back = lerp(shell, 2, weight, false);
                        String label = pants + "/" + side + "/" + family + "/" + element.name() + "@" + y;
                        assertTrue(element.from().x() - left >= CLEARANCE - EPSILON, label + " left");
                        assertTrue(right - element.to().x() >= CLEARANCE - EPSILON, label + " right");
                        assertTrue(element.from().z() - front >= CLEARANCE - EPSILON, label + " front");
                        assertTrue(back - element.to().z() >= CLEARANCE - EPSILON, label + " back");
                    }
                }
                for (var element : footwear.elements()) if (element.name().contains("toe")) {
                    assertTrue(shell.from().y() - element.to().y() >= CLEARANCE - EPSILON,
                            pants + "/" + side + "/" + family + " toe step");
                }
            }
        }
    }

    @Test
    void sharedTrouserWaistsAndTextureCoordinatesStayAtTheirOriginalPositions() throws Exception {
        for (String pants : List.of("denim_pants", "slacks")) for (String side : List.of("left_leg", "right_leg")) {
            var part = DualEnvelopeDeformerTest.part(pants + "/" + side);
            assertEquals(1, part.elements().size());
            var shell = part.elements().get(0);
            float center = side.equals("left_leg") ? 2 : -2;
            assertEquals(center - 2.09375F, shell.corners().get(0).x(), EPSILON);
            assertEquals(center + 2.09375F, shell.corners().get(1).x(), EPSILON);
            assertEquals(center - 2.005F, shell.corners().get(4).x(), EPSILON);
            assertEquals(center + 2.005F, shell.corners().get(5).x(), EPSILON);
            for (int i = 4; i < 8; i++) assertEquals(12.005F, shell.corners().get(i).y());
            assertEquals(-2.25F, shell.from().z());
            assertEquals(2.25F, shell.to().z());
            assertFalse(shell.faces().containsKey(Face.BOTTOM));
            assertEquals(5, shell.faces().size());
            int u = 0;
            for (var face : List.of(Face.LEFT, Face.FRONT, Face.RIGHT, Face.BACK)) {
                var uv = shell.faces().get(face);
                assertEquals(u, uv.u0());
                assertEquals(u + 4, uv.u1());
                assertEquals(4, uv.v0());
                assertEquals(14, uv.v1());
                u += 4;
            }
        }
    }

    private static float lerp(Element shell, int corner, float weight, boolean xAxis) {
        var bottom = shell.corners().get(corner);
        var top = shell.corners().get(corner + 4);
        return xAxis ? bottom.x() + (top.x() - bottom.x()) * weight
                : bottom.z() + (top.z() - bottom.z()) * weight;
    }
}
