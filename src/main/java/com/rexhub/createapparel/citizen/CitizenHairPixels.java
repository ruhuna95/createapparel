package com.rexhub.createapparel.citizen;

public final class CitizenHairPixels {
    private static final int[] SHADES = {106, 128, 151, 176, 201, 222, 241};
    private static final String[] FRONT = {
            "34555443", "34566543", "23455432", "23344322",
            "12333211", "12232111", "11222101", "01111010"
    };
    private static final String[] SIDE = {
            "34455443", "34566543", "23455432", "23344322",
            "12344321", "12333211", "01232110", "01121010"
    };
    private static final String[] TOP = {
            "45555443", "45566543", "34555432", "23444322",
            "23344332", "34455432", "34556543", "23444332"
    };
    private static final String[] TOP_TUFTS = {
            ".44..4..", "45...55.", "..5..6..", "...44...",
            "..3..43.", ".44...5.", "..55.6..", "...4...."
    };

    private CitizenHairPixels() { }

    public static int pixel(String style, int x, int y) {
        if (x < 0 || x >= 32 || y < 0 || y >= 32) return 0;
        boolean outer = y >= 16;
        int row = y % 16;
        if (row < 8) {
            if (x < 8 || x >= 16) return 0;
            return shade((outer ? TOP_TUFTS : TOP)[row].charAt(x - 8));
        }
        row -= 8;
        int column = x % 8;
        if (x >= 8 && x < 16) {
            int[] fringe = fringe(style, outer);
            if (row >= fringe[column] || row >= 4 && CitizenHeadPixels.maskPixel(x, row + 8) != CitizenHeadPixels.SKIN_MASK) return 0;
            if (outer && row == 0 && (column == 0 || column == 7 || column == part(style))) return 0;
            return shade(FRONT[row].charAt(column));
        }
        if (x < 24) {
            int strand = x < 8 ? 7 - column : column;
            int[] tips = style.equals("short") ? (outer ? new int[]{4, 5, 7, 8, 7, 6, 5, 4} : new int[]{3, 4, 5, 6, 7, 7, 6, 6})
                    : new int[]{5, 6, 7, 8, 8, 8, 8, 8};
            if (row >= tips[strand] || strand < 2 && row >= 4 && row <= 5) return 0;
            if (outer && (row == 0 && strand % 3 == 0 || row == 2 && strand == 5)) return 0;
            return shade(SIDE[row].charAt(strand));
        }
        int[] tips = style.equals("short") ? (outer ? new int[]{7, 7, 8, 7, 7, 8, 7, 6} : new int[]{6, 7, 6, 7, 7, 6, 7, 6})
                : new int[]{8, 8, 8, 8, 8, 8, 8, 8};
        if (row >= tips[column] || outer && row < 3 && (column + row) % 4 == 0) return 0;
        return shade(SIDE[row].charAt(column));
    }

    private static int part(String style) {
        return switch (style) {
            case "long_straight" -> 4;
            case "ponytail" -> 5;
            default -> 3;
        };
    }

    private static int[] fringe(String style, boolean outer) {
        return switch (style) {
            case "bob" -> outer ? new int[]{5, 3, 4, 4, 5, 4, 3, 5} : new int[]{3, 2, 3, 3, 2, 2, 3, 3};
            case "long_straight" -> outer ? new int[]{5, 4, 3, 2, 2, 3, 4, 5} : new int[]{2, 3, 2, 1, 1, 2, 3, 2};
            case "ponytail" -> outer ? new int[]{3, 4, 4, 5, 2, 3, 4, 5} : new int[]{2, 2, 3, 2, 1, 2, 3, 2};
            case "twintails" -> outer ? new int[]{5, 3, 4, 5, 2, 4, 3, 5} : new int[]{2, 3, 2, 2, 1, 3, 2, 2};
            default -> outer ? new int[]{3, 4, 3, 2, 3, 4, 3, 2} : new int[]{2, 3, 2, 1, 2, 3, 2, 2};
        };
    }

    public static int strandPixel(int column, int row, int width, int height, boolean tail) {
        int tip = height - 1 - (column * 2 + 1) % 3;
        if (tail) {
            if (row == 0 && column == width - 1 || row >= height / 2 && column == 0
                    || row >= height - 2 && column == width - 1) return 0;
            tip = height - 1 - (column % 2);
        }
        if (row >= tip) return 0;
        int strand = Math.min(7, column * 8 / width);
        int depth = Math.min(7, row * 8 / height);
        return shade(SIDE[depth].charAt(strand));
    }

    private static int shade(char value) {
        if (value == '.') return 0;
        return 0xFF000000 | SHADES[value - '0'] * 0x010101;
    }
}
