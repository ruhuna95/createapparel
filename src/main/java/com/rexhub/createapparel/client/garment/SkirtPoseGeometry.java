package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class SkirtPoseGeometry {
    private SkirtPoseGeometry() {
    }

    public static GarmentPartModel animate(GarmentPartModel part, PlayerModel<?> model) {
        if (part.skirtMotion().isEmpty()) {
            return part;
        }
        Element panel = part.elements().stream().filter(e -> e.name().equals("front_panel")).findFirst().orElseThrow();
        Matrix4f inverseBody = pose(model.body).invert();
        List<Vec3> legs = new ArrayList<>();
        sampleLeg(legs, inverseBody, model.leftLeg, panel.from().y(), panel.to().y());
        sampleLeg(legs, inverseBody, model.rightLeg, panel.from().y(), panel.to().y());
        return deform(part, legs);
    }

    private static Matrix4f pose(ModelPart part) {
        return new Matrix4f().translation(part.x, part.y, part.z)
                .rotateZYX(part.zRot, part.yRot, part.xRot);
    }

    private static void sampleLeg(List<Vec3> points, Matrix4f inverseBody, ModelPart leg, float bottom, float top) {
        Matrix4f transform = new Matrix4f(inverseBody).mul(pose(leg));
        List<Vec3> box = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            Vector3f point = transform.transformPosition(new Vector3f(
                    (index & 1) == 0 ? -2 : 2, (index & 2) == 0 ? 0 : 12, (index & 4) == 0 ? -2 : 2));
            box.add(new Vec3(point.x, 24 - point.y, point.z));
        }
        for (int index = 0; index < 8; index++) {
            Vec3 a = box.get(index);
            if (a.y() >= bottom && a.y() < top) {
                points.add(a);
            }
            for (int bit : new int[]{1, 2, 4}) {
                if ((index & bit) != 0) {
                    continue;
                }
                Vec3 b = box.get(index | bit);
                for (float plane : new float[]{bottom, top - 0.001F}) {
                    if ((a.y() < plane && b.y() > plane) || (b.y() < plane && a.y() > plane)) {
                        float t = (plane - a.y()) / (b.y() - a.y());
                        points.add(new Vec3(a.x() + t * (b.x() - a.x()), plane, a.z() + t * (b.z() - a.z())));
                    }
                }
            }
        }
    }

    static GarmentPartModel deform(GarmentPartModel part, List<Vec3> legs) {
        var motion = part.skirtMotion().orElseThrow();
        Element reference = part.elements().stream().filter(e -> e.name().equals("front_panel")).findFirst().orElseThrow();
        List<Vec3> corners = new ArrayList<>(reference.corners());
        float minX = corners.get(0).x();
        float maxX = corners.get(1).x();
        float minZ = corners.get(0).z();
        float maxZ = corners.get(2).z();
        float height = reference.to().y() - reference.from().y();
        Vec3 upperMin = corners.get(4);
        Vec3 upperMax = corners.get(6);
        for (Vec3 point : legs) {
            float weight = (reference.to().y() - point.y()) / height;
            if (weight <= 0 || weight > 1.00001F) {
                continue;
            }
            minX = Math.min(minX, upperMin.x() + (point.x() - motion.clearance() - upperMin.x()) / weight);
            maxX = Math.max(maxX, upperMax.x() + (point.x() + motion.clearance() - upperMax.x()) / weight);
            minZ = Math.min(minZ, upperMin.z() + (point.z() - motion.clearance() - upperMin.z()) / weight);
            maxZ = Math.max(maxZ, upperMax.z() + (point.z() + motion.clearance() - upperMax.z()) / weight);
        }
        float sideFlare = Math.max(upperMin.x() - minX, maxX - upperMax.x());
        minX = upperMin.x() - sideFlare;
        maxX = upperMax.x() + sideFlare;
        float y = reference.from().y();
        corners.set(0, new Vec3(minX, y, minZ));
        corners.set(1, new Vec3(maxX, y, minZ));
        corners.set(2, new Vec3(maxX, y, maxZ));
        corners.set(3, new Vec3(minX, y, maxZ));
        if (corners.equals(reference.corners())) {
            return part;
        }
        for (int index = 0; index < 4; index++) {
            Vec3 top = reference.corners().get(index + 4);
            Vec3 rest = reference.corners().get(index);
            Vec3 target = corners.get(index);
            if (target.equals(rest)) {
                continue;
            }
            double restLength = distance(top, rest);
            double factor = restLength / distance(top, target);
            corners.set(index, new Vec3(
                    top.x() + (float) ((target.x() - top.x()) * factor),
                    top.y() + (float) ((target.y() - top.y()) * factor),
                    top.z() + (float) ((target.z() - top.z()) * factor)));
        }
        List<Vec3> posedCorners = List.copyOf(corners);
        List<Element> elements = part.elements().stream().map(element -> switch (element.name()) {
            case "front_panel", "back_panel", "left_panel", "right_panel" -> new Element(
                    element.name(), element.from(), element.to(), element.rotation(), element.faces(), posedCorners);
            default -> element;
        }).toList();
        return new GarmentPartModel(part.id(), part.textureWidth(), part.textureHeight(), part.pivot(), elements,
                part.variants(), part.designs(), part.materialOverrides(), part.skirtMotion(), part.hemGeometry());
    }

    private static double distance(Vec3 a, Vec3 b) {
        double x = a.x() - b.x();
        double y = a.y() - b.y();
        double z = a.z() - b.z();
        return Math.sqrt(x * x + y * y + z * z);
    }
}
