package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum MannequinForm {
    FULL(EnumSet.allOf(GarmentSlot.class), List.of(PosePreset.STANDING, PosePreset.ATTENTION,
            PosePreset.CASUAL, PosePreset.RUNWAY, PosePreset.HAND_ON_HIP, PosePreset.ONE_LEG_FORWARD,
            PosePreset.ARMS_CROSSED, PosePreset.WALKING)),
    UPPER(EnumSet.of(GarmentSlot.INNER_TOP, GarmentSlot.TOP, GarmentSlot.OUTER, GarmentSlot.COAT, GarmentSlot.HEAD,
            GarmentSlot.FACE, GarmentSlot.BACK, GarmentSlot.HANDS, GarmentSlot.NECK),
            List.of(PosePreset.STRAIGHT, PosePreset.CASUAL, PosePreset.ARMS_CROSSED, PosePreset.SLIGHT_TURN)),
    LOWER(EnumSet.of(GarmentSlot.INNER_BOTTOM, GarmentSlot.BOTTOM, GarmentSlot.LEGWEAR, GarmentSlot.FEET),
            List.of(PosePreset.STRAIGHT, PosePreset.STEP, PosePreset.ONE_KNEE_BENT, PosePreset.CROSS_LEG)),
    HEAD(EnumSet.of(GarmentSlot.HEAD, GarmentSlot.FACE), List.of(PosePreset.FRONT,
            PosePreset.LEFT_30, PosePreset.RIGHT_30, PosePreset.LEFT_60, PosePreset.RIGHT_60,
            PosePreset.TILT_LEFT, PosePreset.TILT_RIGHT));

    public static final float BODY_SCALE = 0.9375F;
    public static final float PEDESTAL_HEIGHT = 1.0F / 16;
    private final Set<GarmentSlot> slots;
    private final List<PosePreset> poses;

    MannequinForm(Set<GarmentSlot> slots, List<PosePreset> poses) {
        this.slots = Set.copyOf(slots);
        this.poses = List.copyOf(poses);
    }

    public Set<GarmentSlot> slots() { return slots; }
    public List<PosePreset> poses() { return poses; }
    public PosePreset defaultPose() { return poses.get(0); }
    public boolean hasHead() { return this != LOWER; }
    public boolean hasTorso() { return this == FULL || this == UPPER; }
    public boolean hasLegs() { return this == FULL || this == LOWER; }
    public int modelOriginPixels() { return this == HEAD ? 8 : 25; }

    public float bodyOriginHeight() {
        return this == HEAD ? 1 - 8 * BODY_SCALE / 16 : PEDESTAL_HEIGHT + 24 * BODY_SCALE / 16;
    }

    public float displayHeight() {
        return bodyOriginHeight() - (this == LOWER ? 7 : -8) * BODY_SCALE / 16;
    }

    public float supportHeightPixels() {
        return switch (this) {
            case UPPER -> (bodyOriginHeight() - PEDESTAL_HEIGHT) * 16 - 12 * BODY_SCALE;
            case HEAD -> (bodyOriginHeight() - PEDESTAL_HEIGHT) * 16 - 2 * BODY_SCALE;
            default -> 0;
        };
    }

    public Vec3 modelToEntity(Vec3 point, float rotation) {
        return rotate(new Vec3(point.x * BODY_SCALE / 16, bodyOriginHeight() - point.y * BODY_SCALE / 16,
                -point.z * BODY_SCALE / 16), -rotation);
    }

    public Vec3 entityToModel(Vec3 point, float rotation) {
        Vec3 horizontal = rotate(point, rotation);
        return new Vec3(horizontal.x * 16 / BODY_SCALE, (bodyOriginHeight() - point.y) * 16 / BODY_SCALE,
                -horizontal.z * 16 / BODY_SCALE);
    }

    static Vec3 rotate(Vec3 point, float rotation) {
        double radians = Math.toRadians(rotation);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec3(point.x * cosine + point.z * sine, point.y, point.z * cosine - point.x * sine);
    }
    public float height() {
        return switch (this) {
            case LOWER -> 18.0F / 16;
            case HEAD -> 1;
            default -> 2.1F;
        };
    }
}
