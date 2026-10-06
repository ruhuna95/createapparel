package com.rexhub.createapparel.entity;

import java.util.Locale;

public enum PosePreset {
    STANDING, ATTENTION, CASUAL, RUNWAY, HAND_ON_HIP, ONE_LEG_FORWARD, ARMS_CROSSED, WALKING,
    STRAIGHT, SLIGHT_TURN, STEP, ONE_KNEE_BENT, CROSS_LEG,
    FRONT, LEFT_30, RIGHT_30, LEFT_60, RIGHT_60, TILT_LEFT, TILT_RIGHT;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static PosePreset read(String id, MannequinForm form) {
        return form.poses().stream().filter(pose -> pose.id().equals(id)).findFirst().orElse(form.defaultPose());
    }

    public PartRotations rotations() {
        Rotation zero = Rotation.ZERO;
        return switch (this) {
            case STANDING, STRAIGHT -> new PartRotations(zero, zero,
                    new Rotation(0, 0, -4), new Rotation(0, 0, 4), zero, zero);
            case ATTENTION, FRONT -> new PartRotations(zero, zero, zero, zero, zero, zero);
            case CASUAL -> new PartRotations(new Rotation(0, -12, -3), new Rotation(0, 6, 0),
                    new Rotation(-10, 0, -7), new Rotation(8, 0, 5),
                    new Rotation(-8, 0, -3), new Rotation(5, 0, 3));
            case RUNWAY -> new PartRotations(new Rotation(0, -15, 0), new Rotation(0, 10, -3),
                    new Rotation(12, 0, -8), new Rotation(-18, 0, 6),
                    new Rotation(-22, -8, -5), new Rotation(12, 5, 2));
            case HAND_ON_HIP -> new PartRotations(new Rotation(0, 10, 0), zero,
                    new Rotation(-12, 0, -55), new Rotation(0, 0, 4), zero, new Rotation(8, 0, 5));
            case ONE_LEG_FORWARD, STEP -> new PartRotations(zero, zero, zero, zero,
                    new Rotation(-25, 0, 0), new Rotation(8, 0, 0));
            case ARMS_CROSSED -> new PartRotations(zero, zero,
                    new Rotation(-65, 35, -30), new Rotation(-65, -35, 30), zero, zero);
            case WALKING -> new PartRotations(zero, zero,
                    new Rotation(25, 0, -4), new Rotation(-25, 0, 4),
                    new Rotation(-30, 0, 0), new Rotation(30, 0, 0));
            case SLIGHT_TURN -> new PartRotations(new Rotation(0, -15, 0), new Rotation(0, 20, 0),
                    zero, zero, zero, zero);
            case ONE_KNEE_BENT -> new PartRotations(zero, zero, zero, zero,
                    new Rotation(-35, 0, -5), zero);
            case CROSS_LEG -> new PartRotations(zero, zero, zero, zero,
                    new Rotation(-12, 0, 15), new Rotation(10, 0, -8));
            case LEFT_30 -> new PartRotations(new Rotation(0, 30, 0), zero, zero, zero, zero, zero);
            case RIGHT_30 -> new PartRotations(new Rotation(0, -30, 0), zero, zero, zero, zero, zero);
            case LEFT_60 -> new PartRotations(new Rotation(0, 60, 0), zero, zero, zero, zero, zero);
            case RIGHT_60 -> new PartRotations(new Rotation(0, -60, 0), zero, zero, zero, zero, zero);
            case TILT_LEFT -> new PartRotations(new Rotation(0, 0, -15), zero, zero, zero, zero, zero);
            case TILT_RIGHT -> new PartRotations(new Rotation(0, 0, 15), zero, zero, zero, zero, zero);
        };
    }

    public record Rotation(float x, float y, float z) {
        public static final Rotation ZERO = new Rotation(0, 0, 0);
    }

    public record PartRotations(Rotation head, Rotation body, Rotation leftArm, Rotation rightArm,
                                Rotation leftLeg, Rotation rightLeg) { }
}
