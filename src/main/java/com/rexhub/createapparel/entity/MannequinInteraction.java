package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;
import java.util.Optional;

public final class MannequinInteraction {
    public enum BodyPart { HEAD, LEFT_ARM, RIGHT_ARM, BODY, LEFT_LEG, RIGHT_LEG }
    public record HitPart(Optional<BodyPart> bodyPart, Vec3 localPoint, Vec3 entityPoint) { }

    private MannequinInteraction() { }

    public static Optional<GarmentSlot> clickedSlot(MannequinVariant variant, PosePreset pose, Vec3 rayStart, Vec3 rayEnd,
                                                  float yaw, float bodyRotation, ApparelLoadout loadout) {
        return hitPart(variant, pose, rayStart, rayEnd, yaw, bodyRotation)
                .flatMap(hit -> clickedSlot(variant, hit, loadout));
    }

    public static Optional<GarmentSlot> clickedSlot(MannequinVariant variant, HitPart hit, ApparelLoadout loadout) {
        return hit.bodyPart().flatMap(part -> candidates(part,
                new Vector3f((float) hit.localPoint().x, (float) hit.localPoint().y, (float) hit.localPoint().z),
                variant.form()).stream().filter(variant.form().slots()::contains)
                .filter(slot -> !loadout.getStack(slot).isEmpty()).findFirst());
    }

    public static Optional<HitPart> hitPart(MannequinVariant variant, PosePreset pose, Vec3 rayStart, Vec3 rayEnd,
                                           float yaw, float bodyRotation) {
        if (rayStart.distanceToSqr(rayEnd) < 1.0E-12) return Optional.empty();
        Vector3f start = modelPoint(variant, rayStart, yaw + bodyRotation);
        Vector3f end = modelPoint(variant, rayEnd, yaw + bodyRotation);
        HitPart nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (BodyPart part : BodyPart.values()) {
            if (!exists(part, variant.form())) continue;
            Matrix4f transform = partTransform(variant, pose, part);
            Matrix4f inverse = new Matrix4f(transform).invert();
            Vector3f localStart = inverse.transformPosition(new Vector3f(start));
            Vector3f localEnd = inverse.transformPosition(new Vector3f(end));
            Vec3 from = new Vec3(localStart.x, localStart.y, localStart.z);
            Vec3 to = new Vec3(localEnd.x, localEnd.y, localEnd.z);
            var intersection = intersect(bounds(part, variant), from, to);
            if (part == BodyPart.BODY && variant.form().hasTorso() && variant.chestProfile() == ChestProfile.BUST) {
                var bustHit = bustIntersection(from, to);
                if (bustHit.isPresent() && (intersection.isEmpty()
                        || from.distanceToSqr(bustHit.get()) < from.distanceToSqr(intersection.get()))) {
                    intersection = bustHit;
                }
            }
            if (intersection.isEmpty()) continue;
            Vec3 local = intersection.get();
            Vector3f modelHit = transform.transformPosition(new Vector3f((float) local.x, (float) local.y, (float) local.z));
            Vec3 entityHit = variant.form().modelToEntity(new Vec3(modelHit.x, modelHit.y, modelHit.z), yaw + bodyRotation);
            double candidateDistance = rayStart.distanceToSqr(entityHit);
            if (candidateDistance < distance) {
                nearest = new HitPart(Optional.of(part), local, entityHit);
                distance = candidateDistance;
            }
        }
        if (variant.form() == MannequinForm.HEAD) {
            var headBody = intersect(new AABB(-4, 0, -2, 4, 2, 2),
                    new Vec3(start.x, start.y, start.z), new Vec3(end.x, end.y, end.z));
            if (headBody.isPresent()) {
                Vec3 hit = variant.form().modelToEntity(headBody.get(), yaw + bodyRotation);
                if (rayStart.distanceToSqr(hit) < distance) {
                    nearest = new HitPart(Optional.empty(), headBody.get(), hit);
                    distance = rayStart.distanceToSqr(hit);
                }
            }
        }
        Vec3 supportStart = MannequinForm.rotate(rayStart, yaw);
        Vec3 supportEnd = MannequinForm.rotate(rayEnd, yaw);
        var pedestal = intersect(new AABB(-6.0 / 16, 0, -6.0 / 16,
                6.0 / 16, MannequinForm.PEDESTAL_HEIGHT, 6.0 / 16), supportStart, supportEnd);
        var support = intersect(new AABB(-0.75 / 16, MannequinForm.PEDESTAL_HEIGHT, -0.75 / 16,
                0.75 / 16, MannequinForm.PEDESTAL_HEIGHT + variant.form().supportHeightPixels() / 16, 0.75 / 16),
                supportStart, supportEnd).filter(hit -> variant.form() == MannequinForm.UPPER || variant.form() == MannequinForm.HEAD);
        for (var fixedHit : List.of(pedestal, support)) {
            if (fixedHit.isEmpty()) continue;
            Vec3 hit = MannequinForm.rotate(fixedHit.get(), -yaw);
            if (rayStart.distanceToSqr(hit) < distance) {
                nearest = new HitPart(Optional.empty(), fixedHit.get(), hit);
                distance = rayStart.distanceToSqr(hit);
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static Vector3f modelPoint(MannequinVariant variant, Vec3 point, float rotation) {
        Vec3 local = variant.form().entityToModel(point, rotation);
        return new Vector3f((float) local.x, (float) local.y, (float) local.z);
    }

    private static Optional<Vec3> intersect(AABB bounds, Vec3 from, Vec3 to) {
        AABB preciseBounds = bounds.inflate(1.0E-5);
        return preciseBounds.contains(from) ? Optional.of(from) : preciseBounds.clip(from, to);
    }

    private static Optional<Vec3> bustIntersection(Vec3 from, Vec3 to) {
        Matrix4f transform = new Matrix4f().translation(0, -1, -0.9F).rotateX(-(float) Math.PI / 6);
        Matrix4f inverse = new Matrix4f(transform).invert();
        Vector3f start = inverse.transformPosition(new Vector3f((float) from.x, (float) from.y, (float) from.z));
        Vector3f end = inverse.transformPosition(new Vector3f((float) to.x, (float) to.y, (float) to.z));
        return intersect(new AABB(-3.5, 1.48, -0.49, 3.5, 5.48, 2.51),
                new Vec3(start.x, start.y, start.z), new Vec3(end.x, end.y, end.z)).map(hit -> {
                    Vector3f point = transform.transformPosition(new Vector3f((float) hit.x, (float) hit.y, (float) hit.z));
                    return new Vec3(point.x, point.y, point.z);
                });
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

    private static AABB bounds(BodyPart part, MannequinVariant variant) {
        boolean slim = variant.armProfile() == ArmProfile.SLIM;
        return switch (part) {
            case HEAD -> new AABB(-4, -8, -4, 4, 0, 4);
            case BODY -> new AABB(-4, variant.form() == MannequinForm.LOWER ? 7 : 0, -2, 4, 12, 2);
            case LEFT_ARM -> new AABB(-1, -2, -2, slim ? 2 : 3, 10, 2);
            case RIGHT_ARM -> new AABB(slim ? -2 : -3, -2, -2, 1, 10, 2);
            case LEFT_LEG -> new AABB(-1.9F, 0, -2, 2.1F, 12, 2);
            case RIGHT_LEG -> new AABB(-2.1F, 0, -2, 1.9F, 12, 2);
        };
    }

    private static List<GarmentSlot> candidates(BodyPart part, Vector3f local, MannequinForm form) {
        return switch (part) {
            case HEAD -> local.z < -0.8F ? List.of(GarmentSlot.FACE, GarmentSlot.HEAD)
                    : List.of(GarmentSlot.HEAD, GarmentSlot.FACE);
            case LEFT_ARM, RIGHT_ARM -> local.y >= 6
                    ? List.of(GarmentSlot.HANDS, GarmentSlot.COAT, GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP)
                    : List.of(GarmentSlot.COAT, GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP);
            case LEFT_LEG, RIGHT_LEG -> local.y > 8
                    ? List.of(GarmentSlot.FEET, GarmentSlot.LEGWEAR, GarmentSlot.BOTTOM, GarmentSlot.INNER_BOTTOM)
                    : List.of(GarmentSlot.BOTTOM, GarmentSlot.LEGWEAR, GarmentSlot.INNER_BOTTOM, GarmentSlot.TOP);
            case BODY -> form == MannequinForm.LOWER
                    ? List.of(GarmentSlot.BOTTOM, GarmentSlot.LEGWEAR, GarmentSlot.INNER_BOTTOM)
                    : local.y < 3 ? List.of(GarmentSlot.NECK, GarmentSlot.COAT, GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP)
                    : local.z > 1.6F ? List.of(GarmentSlot.BACK, GarmentSlot.COAT, GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP)
                    : List.of(GarmentSlot.COAT, GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP, GarmentSlot.BACK);
        };
    }
}
