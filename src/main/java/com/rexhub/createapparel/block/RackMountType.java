package com.rexhub.createapparel.block;

import net.minecraft.util.StringRepresentable;

public enum RackMountType implements StringRepresentable {
    FLOOR, WALL;

    public boolean isSlotUsable(boolean leftConnected, boolean rightConnected, int slot) {
        return slot >= 0 && slot < 4 && (this == WALL || switch (slot) {
            case 0 -> leftConnected;
            case 3 -> rightConnected;
            default -> true;
        });
    }

    @Override
    public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
}
