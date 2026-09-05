package com.rexhub.createapparel.client.garment;

import java.util.OptionalInt;

final class GarmentTexturePixelOps {
    private static final int MAX_CHANNEL_VALUE = 255;

    private GarmentTexturePixelOps() {
    }

    static boolean isValidMaskPixel(int red, int green, int blue, int alpha) {
        return isChannel(red)
                && isChannel(green)
                && isChannel(blue)
                && alpha == MAX_CHANNEL_VALUE
                && red == green
                && green == blue;
    }

    static boolean isValidOverlayAlpha(int alpha) {
        return alpha == 0 || alpha == MAX_CHANNEL_VALUE;
    }

    static int applyOverlay(int composedPixel, OptionalInt overlayPixel) {
        if (overlayPixel.isEmpty()) {
            return composedPixel;
        }
        int pixel = overlayPixel.getAsInt();
        return pixel >>> 24 == 0 ? composedPixel : pixel;
    }

    static int composeTint(
            int primaryTint,
            OptionalInt secondaryTint,
            OptionalInt accentTint,
            int secondaryMaskRed,
            int accentMaskRed
    ) {
        int secondaryResult = lerpRgb(
                primaryTint,
                secondaryTint.orElse(primaryTint),
                secondaryMaskRed
        );
        return lerpRgb(secondaryResult, accentTint.orElse(primaryTint), accentMaskRed);
    }

    static int lerpRgb(int fromRgb, int toRgb, int maskRed) {
        double weight = maskWeight(maskRed);
        int red = lerpChannel(fromRgb >> 16 & 0xFF, toRgb >> 16 & 0xFF, weight);
        int green = lerpChannel(fromRgb >> 8 & 0xFF, toRgb >> 8 & 0xFF, weight);
        int blue = lerpChannel(fromRgb & 0xFF, toRgb & 0xFF, weight);
        return red << 16 | green << 8 | blue;
    }

    static double maskWeight(int maskRed) {
        return clampChannel(maskRed) / (double) MAX_CHANNEL_VALUE;
    }

    private static int lerpChannel(int from, int to, double weight) {
        return clampChannel((int) Math.round(from + (to - from) * weight));
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(MAX_CHANNEL_VALUE, value));
    }

    private static boolean isChannel(int value) {
        return value >= 0 && value <= MAX_CHANNEL_VALUE;
    }
}
