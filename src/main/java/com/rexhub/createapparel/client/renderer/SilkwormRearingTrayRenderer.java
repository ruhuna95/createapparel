package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rexhub.createapparel.block.SilkwormRearingTrayBlock;
import com.rexhub.createapparel.block.entity.SilkwormRearingTrayBlockEntity;
import com.rexhub.createapparel.client.model.ModPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public final class SilkwormRearingTrayRenderer extends SafeBlockEntityRenderer<SilkwormRearingTrayBlockEntity> {
    public SilkwormRearingTrayRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    protected void renderSafe(SilkwormRearingTrayBlockEntity tray, float partialTicks, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay) {
        var buffer = buffers.getBuffer(RenderType.cutoutMipped());
        var state = tray.getBlockState();
        KineticBlockEntityRenderer.renderRotatingKineticBlock(tray,
                KineticBlockEntityRenderer.shaft(KineticBlockEntityRenderer.getRotationAxisOf(tray)),
                pose, buffer, light);
        boolean feeding = tray.hasSilkworm() && tray.processingState() == SilkwormRearingTrayBlockEntity.State.PROCESSING;
        if (tray.hasSilkworm()) {
            if (feeding) CachedBuffers.partial(ModPartialModels.REARING_FEEDING, state).light(light)
                    .rotateCentered(AngleHelper.rad(-state.getValue(SilkwormRearingTrayBlock.HORIZONTAL_FACING).toYRot()), Direction.UP)
                    .renderInto(pose, buffer);
            pose.pushPose();
            orient(pose, state.getValue(SilkwormRearingTrayBlock.HORIZONTAL_FACING));
            SilkwormRearingTrayContentsRenderer.renderLarva(pose, buffers, light, overlay, feeding);
            pose.popPose();
        }
        pose.pushPose();
        orient(pose, state.getValue(SilkwormRearingTrayBlock.HORIZONTAL_FACING));
        SilkwormRearingTrayContentsRenderer.renderOutput(tray.hasOutput() ? tray.output() : tray.completedResult(),
                pose, buffers, light, overlay, feeding);
        pose.popPose();
    }

    private static void orient(PoseStack pose, Direction facing) {
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
    }
}
