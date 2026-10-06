package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.garment.GarmentSlot;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum MannequinForm {
    FULL(EnumSet.allOf(GarmentSlot.class), List.of(PosePreset.STANDING, PosePreset.ATTENTION,
            PosePreset.CASUAL, PosePreset.RUNWAY, PosePreset.HAND_ON_HIP, PosePreset.ONE_LEG_FORWARD,
            PosePreset.ARMS_CROSSED, PosePreset.WALKING)),
    UPPER(EnumSet.of(GarmentSlot.INNER_TOP, GarmentSlot.TOP, GarmentSlot.OUTER, GarmentSlot.HEAD,
            GarmentSlot.FACE, GarmentSlot.BACK, GarmentSlot.HANDS, GarmentSlot.NECK),
            List.of(PosePreset.STRAIGHT, PosePreset.CASUAL, PosePreset.ARMS_CROSSED, PosePreset.SLIGHT_TURN)),
    LOWER(EnumSet.of(GarmentSlot.INNER_BOTTOM, GarmentSlot.BOTTOM, GarmentSlot.LEGWEAR, GarmentSlot.FEET),
            List.of(PosePreset.STRAIGHT, PosePreset.STEP, PosePreset.ONE_KNEE_BENT, PosePreset.CROSS_LEG)),
    HEAD(EnumSet.of(GarmentSlot.HEAD, GarmentSlot.FACE), List.of(PosePreset.FRONT,
            PosePreset.LEFT_30, PosePreset.RIGHT_30, PosePreset.LEFT_60, PosePreset.RIGHT_60,
            PosePreset.TILT_LEFT, PosePreset.TILT_RIGHT));

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
    public float height() {
        return switch (this) {
            case LOWER -> 18.0F / 16;
            case HEAD -> 1;
            default -> 2.1F;
        };
    }
}
