package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rexhub.createapparel.block.ClothingRackBlock;
import com.rexhub.createapparel.block.HangerGeometry;
import com.rexhub.createapparel.block.RackMountType;
import com.rexhub.createapparel.block.entity.ClothingRackBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public final class ClothingRackRenderer implements BlockEntityRenderer<ClothingRackBlockEntity> {
    public ClothingRackRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(ClothingRackBlockEntity rack, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        var level = rack.getLevel();
        if (level == null) return;
        var upperPos = rack.getBlockPos().above();
        var upperState = level.getBlockState(upperPos);
        var facing = rack.getBlockState().getValue(ClothingRackBlock.FACING);
        var mount = rack.getBlockState().getValue(ClothingRackBlock.MOUNT);
        int displayLight = LevelRenderer.getLightColor(level, upperPos);
        poseStack.pushPose();
        poseStack.translate(0, 1, 0);
        for (int slot = 0; slot < 4; slot++) {
            if (rack.isSlotUsable(slot))
                HangerDisplay.renderHanger(level, upperPos, upperState, mount, slot, facing, poseStack, buffer, overlay);
        }
        poseStack.popPose();
        poseStack.pushPose();
        HangerDisplay.orient(poseStack, facing);
        for (int slot = 0; slot < 4; slot++) {
            if (!rack.isSlotUsable(slot)) continue;
            poseStack.pushPose();
            var anchor = HangerGeometry.rackAnchor(mount, slot);
            poseStack.translate(anchor.x, anchor.y + 1, anchor.z);
            if (mount == RackMountType.FLOOR) poseStack.mulPose(Axis.YP.rotationDegrees(90));
            HangerDisplay.renderGarment(rack.garment(slot), poseStack, buffer, displayLight, overlay);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
