package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.block.WallHangerBlock;
import com.rexhub.createapparel.block.HangerGeometry;
import com.rexhub.createapparel.block.entity.WallHangerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public final class WallHangerRenderer implements BlockEntityRenderer<WallHangerBlockEntity> {
    public WallHangerRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(WallHangerBlockEntity hanger, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        poseStack.pushPose();
        HangerDisplay.orient(poseStack, hanger.getBlockState().getValue(WallHangerBlock.FACING));
        var anchor = HangerGeometry.WALL_ANCHOR;
        poseStack.translate(anchor.x, anchor.y, anchor.z);
        HangerDisplay.renderGarment(hanger.garment(), poseStack, buffer, light, overlay);
        poseStack.popPose();
    }
}
