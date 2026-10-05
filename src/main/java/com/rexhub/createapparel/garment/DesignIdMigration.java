package com.rexhub.createapparel.garment;

final class DesignIdMigration {
    private DesignIdMigration() {
    }

    static String designId(String design) {
        return switch (design) {
            case "stripe_logo" -> "stripe";
            case "diagonal_stripe" -> "stripe";
            case "logo" -> "logo_creeper";
            default -> design;
        };
    }
}
