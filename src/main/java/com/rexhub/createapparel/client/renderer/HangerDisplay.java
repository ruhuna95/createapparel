package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

public final class HangerDisplay {
    public static final float GARMENT_SCALE = 0.85F;

    private HangerDisplay() { }

    public static void orient(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        poseStack.translate(-0.5, 0, -0.5);
    }

    public static void renderGarment(ItemStack garment, PoseStack poseStack, MultiBufferSource buffer,
                                     int light, int overlay) {
        poseStack.pushPose();
        poseStack.scale(GARMENT_SCALE, GARMENT_SCALE, GARMENT_SCALE);
        CommonGarmentRenderer.renderHanger(garment, poseStack, buffer, light, overlay);
        poseStack.popPose();
    }
}
