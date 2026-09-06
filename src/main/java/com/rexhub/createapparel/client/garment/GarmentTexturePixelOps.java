package com.rexhub.createapparel.client.garment;

import java.util.OptionalInt;

final class GarmentTexturePixelOps {
    static final int NEUTRAL_BRIGHTNESS_LUMINANCE = 100;
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
        return isChannel(alpha);
    }

    static boolean isValidBrightness(float brightness) {
        return Float.isFinite(brightness) && brightness >= 0.0F && brightness <= 4.0F;
    }

    static int applyBrightness(int pixel, float brightness, int maskLuminance) {
        if (!isValidBrightness(brightness)) {
            throw new IllegalArgumentException("Brightness must be finite and between 0 and 4");
        }
        double factor = brightness * clampChannel(maskLuminance) / (double) NEUTRAL_BRIGHTNESS_LUMINANCE;
        if (factor == 1.0D || pixel >>> 24 == 0) {
            return pixel;
        }
        int result = pixel & 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int channel = pixel >>> shift & 0xFF;
            result |= clampChannel((int) Math.round(channel * factor)) << shift;
        }
        return result;
    }

    static int applyOverlay(int composedPixel, OptionalInt overlayPixel) {
        if (overlayPixel.isEmpty()) {
            return composedPixel;
        }
        int pixel = overlayPixel.getAsInt();
        int sourceAlpha = pixel >>> 24;
        if (sourceAlpha == 0) {
            return composedPixel;
        }
        int destinationAlpha = composedPixel >>> 24;
        if (sourceAlpha == 255 || destinationAlpha == 0) {
            return pixel;
        }
        int destinationWeight = destinationAlpha * (255 - sourceAlpha);
        int alphaNumerator = sourceAlpha * 255 + destinationWeight;
        int result = (alphaNumerator + 127) / 255 << 24;
        for (int shift = 0; shift < 24; shift += 8) {
            int source = pixel >>> shift & 0xFF;
            int destination = composedPixel >>> shift & 0xFF;
            int channel = (source * sourceAlpha * 255 + destination * destinationWeight
                    + alphaNumerator / 2) / alphaNumerator;
            result |= channel << shift;
        }
        return result;
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
