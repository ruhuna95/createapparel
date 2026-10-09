package com.rexhub.createapparel.client.mirror;

import com.rexhub.createapparel.mirror.MirrorFurnitureGeometry;
import com.rexhub.createapparel.mirror.MirrorGeometry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MirrorFurnitureGeometryTest {
    @Test void fullMirrorIsEightyDegreesAndKeepsTwelveByTwentyEightPixels() {
        dimensionsAndTilt(MirrorFurnitureGeometry.fullSurface(), 12, 28, 80);
    }
    @Test void tableMirrorIsSeventyFiveDegreesAndKeepsEightByEightPixels() {
        dimensionsAndTilt(MirrorFurnitureGeometry.tableSurface(), 8, 8, 75);
    }
    @Test void reverseFaceHasOppositeWindingAndNormal() {
        var front = MirrorGeometry.north(0, 0, 1, 2, 1);
        var back = front.reverse();
        assertEquals(-1, front.normal().dot(back.normal()), 1e-8);
        assertEquals(front.vertices().get(0), back.vertices().get(3));
    }
    @Test void supportsAttachToTheFrameBackAtTheMirrorHeightCenter() {
        assertEquals(8, MirrorFurnitureGeometry.supportAttachment(true).x, 1e-6);
        assertEquals(8, MirrorFurnitureGeometry.supportAttachment(false).x, 1e-6);
        assertEquals(15 * Math.cos(Math.toRadians(10)), MirrorFurnitureGeometry.supportAttachment(true).y, 1e-5);
        assertEquals(5 * Math.cos(Math.toRadians(15)), MirrorFurnitureGeometry.supportAttachment(false).y, 1e-5);
    }
    private static void dimensionsAndTilt(MirrorGeometry geometry, double width, double height, double angle) {
        var vertices = geometry.vertices();
        assertEquals(width, vertices.get(0).distanceTo(vertices.get(1)) * 16, 1e-4);
        Vec3 vertical = vertices.get(2).subtract(vertices.get(1));
        assertEquals(height, vertical.length() * 16, 1e-4);
        assertEquals(angle, Math.toDegrees(Math.asin(vertical.y / vertical.length())), 1e-4);
        assertEquals(0, vertical.dot(geometry.normal()), 1e-6);
        assertTrue(geometry.normal().y > 0);
    }
}
