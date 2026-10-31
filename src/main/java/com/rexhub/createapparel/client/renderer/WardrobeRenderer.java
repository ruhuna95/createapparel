package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rexhub.createapparel.block.WardrobeBlock;
import com.rexhub.createapparel.block.entity.WardrobeBlockEntity;
import com.rexhub.createapparel.client.model.ModPartialModels;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class WardrobeRenderer extends SafeBlockEntityRenderer<WardrobeBlockEntity> {
    private static final float OPEN_ANGLE = 110.0F;
    private static final float LEFT_PIVOT_X = 15.0F / 16.0F;
    private static final float RIGHT_PIVOT_X = 1.0F / 16.0F;

    public WardrobeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(
            WardrobeBlockEntity blockEntity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(WardrobeBlock.FACING);
        float closeCorrection = OPEN_ANGLE * (1.0F - blockEntity.getOpenness(partialTicks));
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());

        renderDoor(
                ModPartialModels.WARDROBE_LOWER_LEFT_DOOR,
                state,
                facing,
                LEFT_PIVOT_X,
                closeCorrection,
                0.0F,
                poseStack,
                buffer,
                light
        );
        renderDoor(
                ModPartialModels.WARDROBE_LOWER_RIGHT_DOOR,
                state,
                facing,
                RIGHT_PIVOT_X,
                -closeCorrection,
                0.0F,
                poseStack,
                buffer,
                light
        );
        renderDoor(
                ModPartialModels.WARDROBE_UPPER_LEFT_DOOR,
                state,
                facing,
                LEFT_PIVOT_X,
                closeCorrection,
                1.0F,
                poseStack,
                buffer,
                light
        );
        renderDoor(
                ModPartialModels.WARDROBE_UPPER_RIGHT_DOOR,
                state,
                facing,
                RIGHT_PIVOT_X,
                -closeCorrection,
                1.0F,
                poseStack,
                buffer,
                light
        );
    }

    private static void renderDoor(
            PartialModel model,
            BlockState state,
            Direction facing,
            float pivotX,
            float closeCorrection,
            float yOffset,
            PoseStack poseStack,
            VertexConsumer buffer,
            int light
    ) {
        poseStack.pushPose();
        float facingRotation = 180.0F - facing.toYRot();
        if (facingRotation != 0.0F) {
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(facingRotation));
            poseStack.translate(-0.5F, 0.0F, -0.5F);
        }

        poseStack.translate(pivotX, yOffset, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(closeCorrection));
        poseStack.translate(-pivotX, 0.0F, 0.0F);

        SuperByteBuffer door = CachedBuffers.partial(model, state).light(light);
        door.renderInto(poseStack, buffer);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(WardrobeBlockEntity blockEntity) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(WardrobeBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }
}
