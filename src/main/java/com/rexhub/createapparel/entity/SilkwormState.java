package com.rexhub.createapparel.entity;

public enum SilkwormState {
    LARVA(0),
    COCOONING(1),
    COCOONED(2);

    private final int id;

    SilkwormState(int id) {
        this.id = id;
    }

    public byte id() {
        return (byte) id;
    }

    public static SilkwormState byId(int id) {
        return switch (id) {
            case 1 -> COCOONING;
            case 2 -> COCOONED;
            default -> LARVA;
        };
    }
}
