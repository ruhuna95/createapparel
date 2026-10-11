package com.rexhub.createapparel.citizen;

public final class CitizenHeadPixels {
    public static final int SKIN_MASK = 0xFFFFE0C4;
    public static final int OUTLINE_MASK = 0xFF000000;
    public static final int WHITE_MASK = 0xFFFFFFFF;
    public static final int IRIS_MASK = 0xFFFF0000;
    public static final double OUTLINE_BRIGHTNESS = 0.6;
    private static final String[] FACE = {
            "SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "SSSSSSSS",
            "SOOSSOOS", "OWISSIWO", "OWISSIWO", "SSSSSSSS"
    };

    private CitizenHeadPixels() { }

    public static int maskPixel(int x, int y) {
        if (x < 0 || x >= 32 || y < 0 || y >= 16) return 0;
        if (y < 8) return x >= 8 && x < 24 ? SKIN_MASK : 0;
        if (x < 8 || x >= 16) return SKIN_MASK;
        return switch (FACE[y - 8].charAt(x - 8)) {
            case 'O' -> OUTLINE_MASK;
            case 'W' -> WHITE_MASK;
            case 'I' -> IRIS_MASK;
            default -> SKIN_MASK;
        };
    }

    public static int colorFace(int mask, int skin, int outline, int eye) {
        if (mask >>> 24 == 0) return 0;
        return switch (mask) {
            case SKIN_MASK -> 0xFF000000 | skin;
            case OUTLINE_MASK -> 0xFF000000 | outline;
            case WHITE_MASK -> WHITE_MASK;
            case IRIS_MASK -> 0xFF000000 | eye;
            default -> throw new IllegalArgumentException("Unknown Citizen face mask pixel");
        };
    }

    public static int tintHair(int pixel, int rgb) {
        if (pixel >>> 24 == 0) return 0;
        int luminance = (int) Math.round((pixel >> 16 & 255) * 0.2126
                + (pixel >> 8 & 255) * 0.7152 + (pixel & 255) * 0.0722);
        int result = pixel & 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            result |= (rgb >> shift & 255) * luminance / 255 << shift;
        }
        return result;
    }

    public static int tintFace(int pixel, int x, int y, int skin, int hair, int eye) {
        if (pixel >>> 24 == 0) return 0;
        return switch (maskPixel(x, y)) {
            case OUTLINE_MASK -> pixel & 0xFF000000 | outlineColor(hair);
            case WHITE_MASK -> tintHair(pixel, 0xFFFFFF);
            case IRIS_MASK -> tintHair(pixel, eye);
            default -> tintHair(pixel, skin);
        };
    }

    public static int fallbackFaceTint(int x, int y) {
        return maskPixel(x, y) == 0 ? 0 : maskPixel(x, y) == OUTLINE_MASK ? 0xFF0C0C0C : 0xFFFFFFFF;
    }

    public static int outlineColor(int hair) {
        int result = 0;
        for (int shift = 0; shift < 24; shift += 8) {
            result |= (int) Math.round((hair >> shift & 255) * OUTLINE_BRIGHTNESS) << shift;
        }
        return result;
    }
}
