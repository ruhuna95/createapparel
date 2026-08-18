package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rexhub.createapparel.block.entity.SpinningMachineBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;

public final class SpinningMachineRenderer extends SafeBlockEntityRenderer<SpinningMachineBlockEntity> {
    public SpinningMachineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(
            SpinningMachineBlockEntity blockEntity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());
        renderRotatingModel(
                ModPartialModels.SPINNING_MACHINE_ROTATING,
                blockEntity,
                poseStack,
                buffer,
                light
        );
        if (blockEntity.hasInput()) {
            renderRotatingModel(
                    ModPartialModels.SPINNING_MACHINE_BOBBIN_THREAD,
                    blockEntity,
                    poseStack,
                    buffer,
                    light
            );
        }
    }

    private static void renderRotatingModel(
            PartialModel model,
            SpinningMachineBlockEntity blockEntity,
            PoseStack poseStack,
            VertexConsumer buffer,
            int light
    ) {
        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        SuperByteBuffer modelBuffer = CachedBuffers.partial(model, state);
        KineticBlockEntityRenderer.standardKineticRotationTransform(modelBuffer, blockEntity, light);
        float yRotation = facing.toYRot();
        if (yRotation != 0.0F) {
            modelBuffer.rotateCentered(AngleHelper.rad(yRotation), Direction.UP);
        }
        modelBuffer.renderInto(poseStack, buffer);
    }
}
