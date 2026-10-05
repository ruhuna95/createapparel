package com.rexhub.createapparel.client.garment;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarmentTexturePixelOpsTest {
    private static final int PRIMARY = 0x000000;
    private static final int SECONDARY = 0xFFFFFF;
    private static final int ACCENT = 0xFF0000;

    @Test
    void designOverlayBlendsAfterBrightnessAndCommonDetailsAreLast() {
        int tint = GarmentTexturePixelOps.composeTint(0x102030, OptionalInt.of(0x304050), OptionalInt.empty(), 255, 0);
        int base = 0xFF000000 | tint;
        int brightened = GarmentTexturePixelOps.applyBrightness(base, 2, 100);
        assertEquals(0xFF6080A0, brightened);
        int print = GarmentTexturePixelOps.applyOverlays(brightened, OptionalInt.of(0x80E0C080), OptionalInt.empty());
        assertEquals(0xFFA0A090, print);
        assertEquals(0xFF132435, GarmentTexturePixelOps.applyOverlays(brightened,
                OptionalInt.of(0xFFE0C080), OptionalInt.of(0xFF132435)));
        for (int alpha = 0; alpha <= 255; alpha++) {
            assertEquals(255, GarmentTexturePixelOps.applyOverlays(brightened,
                    OptionalInt.of(alpha << 24 | 0x10E070), OptionalInt.empty()) >>> 24);
        }
    }

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
    void overlayAllowsEveryEightBitAlpha() {
        for (int alpha = 0; alpha <= 255; alpha++) {
            assertTrue(GarmentTexturePixelOps.isValidOverlayAlpha(alpha));
        }
        assertFalse(GarmentTexturePixelOps.isValidOverlayAlpha(-1));
        assertFalse(GarmentTexturePixelOps.isValidOverlayAlpha(256));
    }

    @Test
    void halfOverlayBlendsChannelsWithoutSwappingAbgr() {
        assertEquals(0xFF00807F, GarmentTexturePixelOps.applyOverlay(0xFF0000FF, OptionalInt.of(0x8000FF00)));
    }

    @Test
    void halfOverlayOverTransparentBaseRetainsStraightColorAndAlpha() {
        assertEquals(0x80332211, GarmentTexturePixelOps.applyOverlay(0x00FFFFFF, OptionalInt.of(0x80332211)));
    }

    @Test
    void twoHalfAlphaLayersUseSourceOver() {
        assertEquals(0xC000AA55, GarmentTexturePixelOps.applyOverlay(0x800000FF, OptionalInt.of(0x8000FF00)));
    }

    @Test
    void overlayUsesEachPixelsAlphaDirectly() {
        for (int alpha = 0; alpha <= 255; alpha++) {
            int overlay = alpha << 24 | 0xFFFFFF;
            int expected = 0xFF000000 | alpha << 16 | alpha << 8 | alpha;
            assertEquals(expected, GarmentTexturePixelOps.applyOverlay(0xFF000000, OptionalInt.of(overlay)));
        }
    }

    @Test
    void neutralBrightnessAndTransparentPixelsPreserveEveryBit() {
        assertEquals(0x80102030, GarmentTexturePixelOps.applyBrightness(
                0x80102030, 1.0F, GarmentTexturePixelOps.NEUTRAL_BRIGHTNESS_LUMINANCE));
        assertEquals(0x80102030, GarmentTexturePixelOps.applyBrightness(0x80102030, 2.0F, 50));
        assertEquals(0x00102030, GarmentTexturePixelOps.applyBrightness(0x00102030, 2.0F, 255));
    }

    @Test
    void brightnessScalesTintedChannelsAndPreservesAlpha() {
        assertEquals(0x803C78F0, GarmentTexturePixelOps.applyBrightness(0x802850A0, 1.5F, 100));
        assertEquals(0x80606060, GarmentTexturePixelOps.applyBrightness(0x80404040, 2.0F, 75));
        assertEquals(0x80202020, GarmentTexturePixelOps.applyBrightness(0x80404040, 0.5F, 100));
        assertEquals(0x80000000, GarmentTexturePixelOps.applyBrightness(0x80404040, 0.0F, 255));
        assertEquals(0x80000000, GarmentTexturePixelOps.applyBrightness(0x80404040, 1.0F, 0));
    }

    @Test
    void brightnessUsesEveryGrayscaleLuminance() {
        for (int luminance = 0; luminance <= 255; luminance++) {
            int channel = (64 * luminance + 50) / 100;
            int expected = 0xFF000000 | channel << 16 | channel << 8 | channel;
            assertEquals(expected, GarmentTexturePixelOps.applyBrightness(0xFF404040, 1.0F, luminance));
        }
    }

    @Test
    void brightnessMaskDirectlyEncodesPercent() {
        assertEquals(0xFF000000, GarmentTexturePixelOps.applyBrightness(0xFF646464, 1.0F, 0));
        assertEquals(0xFF323232, GarmentTexturePixelOps.applyBrightness(0xFF646464, 1.0F, 50));
        assertEquals(0xFF646464, GarmentTexturePixelOps.applyBrightness(0xFF646464, 1.0F, 100));
        assertEquals(0xFF828282, GarmentTexturePixelOps.applyBrightness(0xFF646464, 1.0F, 130));
        assertEquals(0xFFFFFFFF, GarmentTexturePixelOps.applyBrightness(0xFF646464, 1.0F, 255));
        assertEquals(0xFF666666, GarmentTexturePixelOps.applyBrightness(0xFF282828, 1.0F, 255));
    }

    @Test
    void brightnessClampsHighlightsWithoutWrapping() {
        assertEquals(0xFFFFFFFF, GarmentTexturePixelOps.applyBrightness(0xFFF0F0F0, 1.15F, 255));
    }

    @Test
    void overlayRunsAfterBrightnessWithoutBrighteningFixedColor() {
        int brightened = GarmentTexturePixelOps.applyBrightness(0xFF204060, 1.0F, 200);
        assertEquals(0xFF102030, GarmentTexturePixelOps.applyOverlay(brightened, OptionalInt.of(0xFF102030)));
        assertEquals(0xFFA0C0E0, GarmentTexturePixelOps.applyOverlay(brightened, OptionalInt.of(0x80FFFFFF)));
    }

    @Test
    void invalidSettingsAreRejected() {
        for (float value : new float[]{-0.1F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 4.01F}) {
            assertFalse(GarmentTexturePixelOps.isValidBrightness(value));
            assertThrows(IllegalArgumentException.class, () -> GarmentTexturePixelOps.applyBrightness(0, value, 255));
        }
        assertTrue(GarmentTexturePixelOps.isValidBrightness(0.0F));
        assertTrue(GarmentTexturePixelOps.isValidBrightness(4.0F));
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
