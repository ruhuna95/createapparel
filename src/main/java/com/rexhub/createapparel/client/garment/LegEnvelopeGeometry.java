package com.rexhub.createapparel.client.garment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class LegEnvelopeGeometry {
    private final float[][] legs = new float[2][24];
    private final float[] crouchingRearSlopes = {Float.NaN, Float.NaN};
    private final Vector3f[] axes = new Vector3f[2];
    private final Vector3f[] frontNormals = new Vector3f[2];
    private float torsoBottom = Float.NaN;
    private float torsoFront;

    public LegEnvelopeGeometry(PlayerModel<?> player) {
        Matrix4f inverse = pose(player.body).invert();
        Matrix4f right = new Matrix4f(inverse).mul(pose(player.rightLeg));
        Matrix4f left = new Matrix4f(inverse).mul(pose(player.leftLeg));
        sample(legs[0], right);
        sample(legs[1], left);
        axes[0] = direction(right, 0, 1, 0);
        axes[1] = direction(left, 0, 1, 0);
        frontNormals[0] = direction(right, 0, 0, -1);
        frontNormals[1] = direction(left, 0, 0, -1);
        if (player.crouching && !player.riding) {
            crouchingRearSlopes[0] = rearSlope(right);
            crouchingRearSlopes[1] = rearSlope(left);
            player.body.visit(new PoseStack(), (pose, path, index, cube) -> {
                float bottom = 24 - cube.maxY;
                if (!Float.isFinite(torsoBottom) || bottom < torsoBottom) {
                    torsoBottom = bottom;
                    torsoFront = cube.minZ;
                }
            });
        }
    }

    private static Vector3f direction(Matrix4f matrix, float x, float y, float z) {
        Vector3f result = matrix.transformDirection(new Vector3f(x, y, z)).normalize();
        result.y = -result.y;
        return result;
    }

    boolean hasBodyEdges() { return Float.isFinite(torsoBottom); }
    float torsoBottom() { return torsoBottom; }
    float torsoFront() { return torsoFront; }

    void axis(int side, Vector3f result) { result.set(axes[side]); }

    void frontEdge(int side, float across, float clearance, Vector3f result) {
        float[] box = legs[side];
        result.set(box[0] + (box[3] - box[0]) * across,
                box[1] + (box[4] - box[1]) * across,
                box[2] + (box[5] - box[2]) * across).fma(clearance, frontNormals[side]);
    }

    float crouchingRearSlope(int side) {
        return crouchingRearSlopes[side];
    }

    private static float rearSlope(Matrix4f pose) {
        Vector3f direction = pose.transformDirection(new Vector3f(0, 1, 0)).normalize();
        return direction.y > 0.000001F ? direction.z / direction.y : Float.NaN;
    }

    public static float layerClearance(GarmentSlot slot) {
        return slot == GarmentSlot.OUTER ? 0.05F : 0.025F;
    }

    private static Matrix4f pose(ModelPart part) {
        return new Matrix4f().translation(part.x, part.y, part.z).rotateZYX(part.zRot, part.yRot, part.xRot)
                .scale(part.xScale, part.yScale, part.zScale);
    }

    private static void sample(float[] target, Matrix4f matrix) {
        Vector3f point = new Vector3f();
        for (int i = 0; i < 8; i++) {
            matrix.transformPosition(point.set((i & 1) == 0 ? -2 : 2,
                    (i & 2) == 0 ? 0 : 12, (i & 4) == 0 ? -2 : 2));
            target[i * 3] = point.x;
            target[i * 3 + 1] = 24 - point.y;
            target[i * 3 + 2] = point.z;
        }
    }

    public boolean extents(int side, float bottom, float top, float[] result) {
        result[0] = result[2] = Float.POSITIVE_INFINITY;
        result[1] = result[3] = Float.NEGATIVE_INFINITY;
        float[] box = legs[side];
        for (int i = 0; i < 8; i++) {
            int a = i * 3;
            if (box[a + 1] >= bottom && box[a + 1] <= top) include(result, box[a], box[a + 2]);
            for (int bit = 1; bit <= 4; bit *= 2) {
                if ((i & bit) != 0) continue;
                int b = (i | bit) * 3;
                intersect(box, a, b, bottom, result);
                intersect(box, a, b, top, result);
            }
        }
        return Float.isFinite(result[0]);
    }

    float lateralSlope(int side, float waistX, float waistY, float front, float back, float clearance) {
        float slope = 0;
        float[] box = legs[side];
        for (int i = 0; i < 8; i++) {
            int index = i * 3;
            float depth = waistY - box[index + 1];
            if (depth <= 0) continue;
            float reach = Math.max(0, Math.max(front - box[index + 2], box[index + 2] - back));
            float protrusion = side == 0 ? waistX - box[index] + clearance : box[index] + clearance - waistX;
            slope = Math.max(slope, protrusion / (float) Math.hypot(depth, reach));
        }
        return slope;
    }

    private static void intersect(float[] box, int a, int b, float y, float[] result) {
        float dy = box[b + 1] - box[a + 1];
        if (Math.abs(dy) < 0.000001F) return;
        float t = (y - box[a + 1]) / dy;
        if (t >= 0 && t <= 1) include(result,
                box[a] + t * (box[b] - box[a]), box[a + 2] + t * (box[b + 2] - box[a + 2]));
    }

    private static void include(float[] result, float x, float z) {
        result[0] = Math.min(result[0], x);
        result[1] = Math.max(result[1], x);
        result[2] = Math.min(result[2], z);
        result[3] = Math.max(result[3], z);
    }
}
