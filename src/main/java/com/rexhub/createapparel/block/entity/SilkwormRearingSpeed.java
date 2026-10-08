package com.rexhub.createapparel.block.entity;

public final class SilkwormRearingSpeed {
    private SilkwormRearingSpeed() { }

    public static float progressPerTick(float speed) {
        float rpm = Math.abs(speed);
        if (!Float.isFinite(rpm) || rpm < 1) return 0;
        if (rpm < 16) return rpm / 32;
        if (rpm < 32) return interpolate(rpm, 16, 32, 0.5F, 2.0F / 3);
        if (rpm < 64) return interpolate(rpm, 32, 64, 2.0F / 3, 1);
        if (rpm < 128) return interpolate(rpm, 64, 128, 1, 4.0F / 3);
        return interpolate(Math.min(rpm, 256), 128, 256, 4.0F / 3, 1.5F);
    }

    private static float interpolate(float rpm, float low, float high, float from, float to) {
        return from + (to - from) * (rpm - low) / (high - low);
    }
}
