package com.rexhub.createapparel.block.entity;

import net.minecraft.util.Mth;

final class MachineProcessingSpeed {
    private static final float MINIMUM_SPEED = 1.0F;

    private MachineProcessingSpeed() {
    }

    static boolean isSufficient(float speed) {
        return Math.abs(speed) >= MINIMUM_SPEED;
    }

    static int progressPerTick(float speed) {
        return Mth.clamp((int) Math.abs(speed / 16.0F), 1, 512);
    }
}
