package com.rexhub.createapparel.entity;

import net.minecraft.util.StringRepresentable;

public enum CitizenAge implements StringRepresentable {
    ADULT("adult", 1, 1),
    CHILD("child", 0.5F, 0.75F);

    private final String id;
    private final float scale;
    private final float headScale;

    CitizenAge(String id, float scale, float headScale) {
        this.id = id;
        this.scale = scale;
        this.headScale = headScale;
    }

    @Override
    public String getSerializedName() { return id; }
    public float scale() { return scale; }
    public float headScale() { return headScale; }

    public static CitizenAge fromSerializedName(String id) {
        for (CitizenAge age : values()) if (age.id.equals(id)) return age;
        throw new IllegalArgumentException("Unknown citizen age: " + id);
    }

    public static CitizenAge read(String id) {
        return CHILD.id.equals(id) ? CHILD : ADULT;
    }
}
