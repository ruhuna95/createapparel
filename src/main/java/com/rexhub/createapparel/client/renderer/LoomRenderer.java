package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rexhub.createapparel.block.entity.LoomBlockEntity;
import com.rexhub.createapparel.client.model.ModPartialModels;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public final class LoomRenderer extends SafeBlockEntityRenderer<LoomBlockEntity> {
    private static final float SLIDE_TRAVEL = 3.0F / 16.0F;
    private static final float SLIDE_AMPLITUDE = SLIDE_TRAVEL * 0.5F;

    public LoomRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(
            LoomBlockEntity blockEntity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        Direction.Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(blockEntity);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());

        KineticBlockEntityRenderer.renderRotatingKineticBlock(
                blockEntity,
                KineticBlockEntityRenderer.shaft(axis),
                poseStack,
                buffer,
                light
        );

        PartialModel slideModel = blockEntity.hasThread()
                ? ModPartialModels.LOOM_SLIDE_WITH_THREAD
                : ModPartialModels.LOOM_SLIDE;
        float angle = KineticBlockEntityRenderer.getAngleForBe(
                blockEntity,
                blockEntity.getBlockPos(),
                axis
        );
        float slideOffset = -SLIDE_AMPLITUDE + SLIDE_AMPLITUDE * Mth.cos(angle);
        SuperByteBuffer slide = CachedBuffers.partial(slideModel, state)
                .translate(0.0D, slideOffset, 0.0D)
                .light(light);
        float yRotation = facing.toYRot();
        if (yRotation != 0.0F) {
            slide.rotateCentered(AngleHelper.rad(-yRotation), Direction.UP);
        }
        slide.renderInto(poseStack, buffer);
    }

    @Override
    public boolean shouldRenderOffScreen(LoomBlockEntity blockEntity) {
        return true;
    }
}
