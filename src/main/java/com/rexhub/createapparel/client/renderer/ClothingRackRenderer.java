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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class ClothingRackRenderer implements BlockEntityRenderer<ClothingRackBlockEntity> {
    public ClothingRackRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(ClothingRackBlockEntity rack, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        var level = rack.getLevel();
        if (level == null) return;
        var state = rack.getBlockState();
        var upperPos = rack.getBlockPos().above();
        var upperState = state.setValue(ClothingRackBlock.HALF, DoubleBlockHalf.UPPER);
        var facing = state.getValue(ClothingRackBlock.FACING);
        var mount = state.getValue(ClothingRackBlock.MOUNT);
        var hangerType = ((ClothingRackBlock) state.getBlock()).hangerType();
        boolean leftConnected = state.getValue(ClothingRackBlock.LEFT_CONNECTED);
        boolean rightConnected = state.getValue(ClothingRackBlock.RIGHT_CONNECTED);
        boolean[] usable = new boolean[4];
        for (int slot = 0; slot < 4; slot++) usable[slot] = mount.isSlotUsable(leftConnected, rightConnected, slot);
        int displayLight = LevelRenderer.getLightColor(level, upperPos);
        poseStack.pushPose();
        poseStack.translate(0, 1, 0);
        for (int slot = 0; slot < 4; slot++) {
            if (usable[slot])
                HangerDisplay.renderHanger(level, upperPos, upperState, hangerType, mount, slot, facing, poseStack, buffer, overlay);
        }
        poseStack.popPose();
        poseStack.pushPose();
        HangerDisplay.orient(poseStack, facing);
        for (int slot = 0; slot < 4; slot++) {
            if (!usable[slot]) continue;
            poseStack.pushPose();
            var anchor = HangerGeometry.rackAnchor(mount, slot);
            poseStack.translate(anchor.x, anchor.y + 1, anchor.z);
            if (mount == RackMountType.FLOOR) poseStack.mulPose(Axis.YP.rotationDegrees(90));
            HangerDisplay.renderGarment(rack.garment(slot), poseStack, buffer, displayLight, overlay);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ClothingRackBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }
}
