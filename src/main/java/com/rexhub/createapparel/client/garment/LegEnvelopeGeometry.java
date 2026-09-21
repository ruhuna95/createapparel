package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class LegEnvelopeGeometry {
    private final float[][] legs = new float[2][24];

    public LegEnvelopeGeometry(PlayerModel<?> player) {
        Matrix4f inverse = pose(player.body).invert();
        sample(legs[0], new Matrix4f(inverse).mul(pose(player.rightLeg)));
        sample(legs[1], new Matrix4f(inverse).mul(pose(player.leftLeg)));
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
