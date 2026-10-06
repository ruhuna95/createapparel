package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;
import java.util.Optional;

public final class MannequinInteraction {
    public enum BodyPart { HEAD, LEFT_ARM, RIGHT_ARM, BODY, LEFT_LEG, RIGHT_LEG }

    private MannequinInteraction() { }

    public static Optional<GarmentSlot> clickedSlot(MannequinVariant variant, PosePreset pose, Vec3 hit,
                                                  float yaw, float bodyRotation, ApparelLoadout loadout) {
        Vec3 horizontal = hit.yRot((float) Math.toRadians(yaw + bodyRotation));
        Vector3f point = new Vector3f((float) horizontal.x * 16,
                variant.form().modelOriginPixels() - (float) hit.y * 16, (float) -horizontal.z * 16);
        BodyPart nearest = null;
        Vector3f nearestLocal = new Vector3f();
        float distance = Float.POSITIVE_INFINITY;
        for (BodyPart part : BodyPart.values()) {
            if (!exists(part, variant.form())) continue;
            Vector3f local = partTransform(variant, pose, part).invert().transformPosition(new Vector3f(point));
            float candidateDistance = bounds(part, variant).distanceSquared(local);
            if (candidateDistance < distance) {
                nearest = part;
                nearestLocal.set(local);
                distance = candidateDistance;
            }
        }
        if (nearest == null) return Optional.empty();
        return candidates(nearest, nearestLocal, variant.form()).stream().filter(variant.form().slots()::contains)
                .filter(slot -> !loadout.getStack(slot).isEmpty()).findFirst();
    }

    public static Matrix4f partTransform(MannequinVariant variant, PosePreset preset, BodyPart part) {
        var pose = preset.rotations();
        float armY = variant.armProfile() == ArmProfile.SLIM ? 2.5F : 2;
        Vec3 pivot = switch (part) {
            case HEAD, BODY -> Vec3.ZERO;
            case LEFT_ARM -> new Vec3(5, armY, 0);
            case RIGHT_ARM -> new Vec3(-5, armY, 0);
            case LEFT_LEG -> new Vec3(1.9, 12, 0);
            case RIGHT_LEG -> new Vec3(-1.9, 12, 0);
        };
        var rotation = switch (part) {
            case HEAD -> pose.head();
            case BODY -> variant.form().hasTorso() ? pose.body() : PosePreset.Rotation.ZERO;
            case LEFT_ARM -> pose.leftArm();
            case RIGHT_ARM -> pose.rightArm();
            case LEFT_LEG -> pose.leftLeg();
            case RIGHT_LEG -> pose.rightLeg();
        };
        float radians = (float) Math.PI / 180;
        return new Matrix4f().translation((float) pivot.x, (float) pivot.y, (float) pivot.z)
                .rotateZYX(rotation.z() * radians, rotation.y() * radians, rotation.x() * radians);
    }

    private static boolean exists(BodyPart part, MannequinForm form) {
        return switch (part) {
            case HEAD -> form.hasHead();
            case BODY -> form.hasTorso() || form == MannequinForm.LOWER;
            case LEFT_ARM, RIGHT_ARM -> form.hasTorso();
            case LEFT_LEG, RIGHT_LEG -> form.hasLegs();
        };
    }

    private static Bounds bounds(BodyPart part, MannequinVariant variant) {
        boolean slim = variant.armProfile() == ArmProfile.SLIM;
        return switch (part) {
            case HEAD -> new Bounds(-4, -8, -4, 4, 0, 4);
            case BODY -> new Bounds(-4, variant.form() == MannequinForm.LOWER ? 7 : 0, -2, 4, 12, 2);
            case LEFT_ARM -> new Bounds(-1, -2, -2, slim ? 2 : 3, 10, 2);
            case RIGHT_ARM -> new Bounds(slim ? -2 : -3, -2, -2, 1, 10, 2);
            case LEFT_LEG -> new Bounds(-1.9F, 0, -2, 2.1F, 12, 2);
            case RIGHT_LEG -> new Bounds(-2.1F, 0, -2, 1.9F, 12, 2);
        };
    }

    private static List<GarmentSlot> candidates(BodyPart part, Vector3f local, MannequinForm form) {
        return switch (part) {
            case HEAD -> local.z < -0.8F ? List.of(GarmentSlot.FACE, GarmentSlot.HEAD)
                    : List.of(GarmentSlot.HEAD, GarmentSlot.FACE);
            case LEFT_ARM, RIGHT_ARM -> local.y >= 6
                    ? List.of(GarmentSlot.HANDS, GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP)
                    : List.of(GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP);
            case LEFT_LEG, RIGHT_LEG -> local.y > 8
                    ? List.of(GarmentSlot.FEET, GarmentSlot.LEGWEAR, GarmentSlot.BOTTOM, GarmentSlot.INNER_BOTTOM)
                    : List.of(GarmentSlot.BOTTOM, GarmentSlot.LEGWEAR, GarmentSlot.INNER_BOTTOM, GarmentSlot.TOP);
            case BODY -> form == MannequinForm.LOWER
                    ? List.of(GarmentSlot.BOTTOM, GarmentSlot.LEGWEAR, GarmentSlot.INNER_BOTTOM)
                    : local.y < 3 ? List.of(GarmentSlot.NECK, GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP)
                    : local.z > 1.6F ? List.of(GarmentSlot.BACK, GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP)
                    : List.of(GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP, GarmentSlot.BACK);
        };
    }

    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float distanceSquared(Vector3f point) {
            float x = Math.max(minX - point.x, Math.max(0, point.x - maxX));
            float y = Math.max(minY - point.y, Math.max(0, point.y - maxY));
            float z = Math.max(minZ - point.z, Math.max(0, point.z - maxZ));
            return x * x + y * y + z * z;
        }
    }
}
