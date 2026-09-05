package com.rexhub.createapparel.client.garment;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarmentTexturePixelOpsTest {
    private static final int PRIMARY = 0x000000;
    private static final int SECONDARY = 0xFFFFFF;
    private static final int ACCENT = 0xFF0000;

    @Test
    void zeroMasksUsePrimary() {
        assertEquals(PRIMARY, compose(0, 0));
    }

    @Test
    void fullSecondaryMaskUsesSecondary() {
        assertEquals(SECONDARY, compose(255, 0));
    }

    @Test
    void halfSecondaryMaskBlendsPrimaryAndSecondary() {
        assertEquals(0x808080, compose(128, 0));
    }

    @Test
    void fullAccentMaskUsesAccent() {
        assertEquals(ACCENT, compose(0, 255));
    }

    @Test
    void halfAccentMaskBlendsLowerTintAndAccent() {
        assertEquals(0x800000, compose(0, 128));
    }

    @Test
    void halfAccentMaskBlendsOverFullSecondary() {
        assertEquals(0xFF7F7F, compose(255, 128));
    }

    @Test
    void overlappingHalfMasksComposeSecondaryBeforeAccent() {
        assertEquals(0xC04040, compose(128, 128));
    }

    @Test
    void missingSecondaryColorFallsBackToPrimary() {
        int primary = 0x204060;
        assertEquals(primary, GarmentTexturePixelOps.composeTint(
                primary,
                OptionalInt.empty(),
                OptionalInt.of(ACCENT),
                255,
                0
        ));
    }

    @Test
    void missingAccentColorFallsBackToPrimary() {
        int primary = 0x204060;
        assertEquals(primary, GarmentTexturePixelOps.composeTint(
                primary,
                OptionalInt.of(SECONDARY),
                OptionalInt.empty(),
                255,
                255
        ));
    }

    @Test
    void binaryMaskEndpointsRemainExact() {
        assertEquals(PRIMARY, GarmentTexturePixelOps.lerpRgb(PRIMARY, SECONDARY, 0));
        assertEquals(SECONDARY, GarmentTexturePixelOps.lerpRgb(PRIMARY, SECONDARY, 255));
    }

    @Test
    void maskWeightUsesFullEightBitRange() {
        assertEquals(0.0D, GarmentTexturePixelOps.maskWeight(0));
        assertEquals(128.0D / 255.0D, GarmentTexturePixelOps.maskWeight(128));
        assertEquals(1.0D, GarmentTexturePixelOps.maskWeight(255));
    }

    @Test
    void maskPixelMustBeGrayscale() {
        assertFalse(GarmentTexturePixelOps.isValidMaskPixel(128, 127, 128, 255));
    }

    @Test
    void maskPixelMustBeOpaque() {
        assertFalse(GarmentTexturePixelOps.isValidMaskPixel(128, 128, 128, 254));
    }

    @Test
    void opaqueGrayscaleMaskAllowsEveryEightBitValue() {
        for (int value = 0; value <= 255; value++) {
            assertTrue(GarmentTexturePixelOps.isValidMaskPixel(value, value, value, 255));
        }
    }

    @Test
    void missingOverlayKeepsComposedPixel() {
        assertEquals(0xFF102030, GarmentTexturePixelOps.applyOverlay(
                0xFF102030,
                OptionalInt.empty()
        ));
    }

    @Test
    void transparentOverlayKeepsComposedPixel() {
        assertEquals(0xFF102030, GarmentTexturePixelOps.applyOverlay(
                0xFF102030,
                OptionalInt.of(0x00332211)
        ));
    }

    @Test
    void opaqueOverlayFullyReplacesComposedPixel() {
        assertEquals(0xFF332211, GarmentTexturePixelOps.applyOverlay(
                0xFF102030,
                OptionalInt.of(0xFF332211)
        ));
    }

    @Test
    void opaqueOverlayCanReplaceTransparentBase() {
        assertEquals(0xFF332211, GarmentTexturePixelOps.applyOverlay(
                0,
                OptionalInt.of(0xFF332211)
        ));
    }

    @Test
    void overlayAlphaMustBeBinary() {
        assertTrue(GarmentTexturePixelOps.isValidOverlayAlpha(0));
        assertTrue(GarmentTexturePixelOps.isValidOverlayAlpha(255));
        assertFalse(GarmentTexturePixelOps.isValidOverlayAlpha(128));
    }

    private static int compose(int secondaryMaskRed, int accentMaskRed) {
        return GarmentTexturePixelOps.composeTint(
                PRIMARY,
                OptionalInt.of(SECONDARY),
                OptionalInt.of(ACCENT),
                secondaryMaskRed,
                accentMaskRed
        );
    }
}
