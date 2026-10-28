package com.rexhub.createapparel.client.advertisement;

import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.photo.PhotoLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhotoLayoutMathTest {
    @Test void newPhotosDefaultToWhiteWithoutOverwritingSavedBackgrounds() {
        assertEquals(FabricColor.WHITE, PhotoLayout.DEFAULT.background());
        var saved = layout(PhotoLayout.Aspect.FIT, PhotoLayout.Horizontal.CENTER, PhotoLayout.Vertical.CENTER);
        assertEquals(FabricColor.CREAM, PhotoLayout.load(saved.save()).background());
    }

    @Test void bodyRotationPreservesLegacyForwardFacingAndRejectsInvalidAngles() {
        assertEquals(0, PhotoLayout.DEFAULT.bodyYaw());
        assertEquals(0, layout(PhotoLayout.Aspect.FIT, PhotoLayout.Horizontal.CENTER, PhotoLayout.Vertical.CENTER).bodyYaw());
        for (int yaw : new int[]{0, 15, 90, 180, 345, 359}) {
            assertEquals(yaw, new PhotoLayout(FabricColor.CREAM, PhotoLayout.Framing.FULL_BODY, PhotoLayout.Aspect.FIT,
                    PhotoLayout.Horizontal.CENTER, PhotoLayout.Vertical.CENTER, false, yaw).bodyYaw());
        }
        for (int yaw : new int[]{-1, 360, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new PhotoLayout(FabricColor.CREAM, PhotoLayout.Framing.FULL_BODY,
                    PhotoLayout.Aspect.FIT, PhotoLayout.Horizontal.CENTER, PhotoLayout.Vertical.CENTER, false, yaw));
        }
    }
    @Test void fitPreservesAspectAndUsesRequestedAlignmentAcrossScreenRatios() {
        for (int[] screen : new int[][]{{200, 100}, {100, 300}, {400, 200}}) {
            var left = layout(PhotoLayout.Aspect.FIT, PhotoLayout.Horizontal.LEFT, PhotoLayout.Vertical.UP);
            var right = layout(PhotoLayout.Aspect.FIT, PhotoLayout.Horizontal.RIGHT, PhotoLayout.Vertical.DOWN);
            var a = PhotoLayoutMath.place(screen[0], screen[1], -0.5, -0.5, 0.5, 1.5, left);
            var b = PhotoLayoutMath.place(screen[0], screen[1], -0.5, -0.5, 0.5, 1.5, right);
            assertEquals(a.scale(), b.scale()); assertTrue(a.scale() <= screen[0]); assertTrue(a.scale() * 2 <= screen[1]);
            assertTrue(b.x() >= a.x() - 1e-9); assertTrue(b.y() >= a.y() - 1e-9);
        }
    }
    @Test void fillCropsWithOneUniformScaleInsteadOfStretching() {
        var placement = PhotoLayoutMath.place(200, 100, -0.5, -0.5, 0.5, 1.5,
                layout(PhotoLayout.Aspect.FILL, PhotoLayout.Horizontal.CENTER, PhotoLayout.Vertical.CENTER));
        assertEquals(200, placement.scale()); assertEquals(-50, placement.y());
        assertEquals(-150, placement.y() - placement.scale() * 0.5);
        assertEquals(250, placement.y() + placement.scale() * 1.5);
    }
    private static PhotoLayout layout(PhotoLayout.Aspect aspect, PhotoLayout.Horizontal horizontal, PhotoLayout.Vertical vertical) {
        return new PhotoLayout(FabricColor.CREAM, PhotoLayout.Framing.FULL_BODY, aspect, horizontal, vertical, false);
    }
}
