package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rexhub.createapparel.block.entity.SewingMachineBlockEntity;
import com.rexhub.createapparel.client.model.ModPartialModels;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class SewingMachineRenderer extends SmartBlockEntityRenderer<SewingMachineBlockEntity> {
    private static final float MODEL_UNIT = 1.0F / 16.0F;
    private static final float PISTON_AMPLITUDE = 1.0F / 16.0F;

    public SewingMachineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(
            SewingMachineBlockEntity blockEntity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        super.renderSafe(blockEntity, partialTicks, poseStack, bufferSource, light, overlay);

        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        Direction.Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(blockEntity);
        float angle = KineticBlockEntityRenderer.getAngleForBe(blockEntity, blockEntity.getBlockPos(), axis);
        float modelAngle = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? angle : -angle;
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());

        SuperByteBuffer rotating = CachedBuffers.partial(ModPartialModels.SEWING_MACHINE_ROTATING, state)
                .rotateCentered(modelAngle, Direction.SOUTH)
                .light(light);
        orientToFacing(rotating, facing);
        rotating.renderInto(poseStack, buffer);

        double pistonOffset = -PISTON_AMPLITUDE + PISTON_AMPLITUDE * Mth.cos(
                angle * SewingMachineBlockEntity.PISTON_ANGLE_MULTIPLIER
        );
        SuperByteBuffer piston = CachedBuffers.partial(ModPartialModels.SEWING_MACHINE_PISTON, state)
                .translate(0.0D, pistonOffset, 0.0D)
                .light(light);
        orientToFacing(piston, facing);
        piston.renderInto(poseStack, buffer);

        if (blockEntity.hasThread()) {
            SuperByteBuffer thread = CachedBuffers.partial(ModPartialModels.SEWING_MACHINE_THREAD, state)
                    .light(light);
            orientToFacing(thread, facing);
            thread.renderInto(poseStack, buffer);
        }

        ItemStack fabric = blockEntity.getFabricForRendering();
        if (!fabric.isEmpty()) {
            renderFabric(blockEntity, fabric, facing, poseStack, bufferSource, light, overlay);
        }
    }

    private static void renderFabric(
            SewingMachineBlockEntity blockEntity,
            ItemStack fabric,
            Direction facing,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        poseStack.translate(8.0F * MODEL_UNIT, 1.26F * MODEL_UNIT, 11.5F * MODEL_UNIT);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        Minecraft.getInstance().getItemRenderer().renderStatic(
                fabric,
                ItemDisplayContext.FIXED,
                light,
                overlay,
                poseStack,
                bufferSource,
                blockEntity.getLevel(),
                0
        );
        poseStack.popPose();
    }

    private static void orientToFacing(SuperByteBuffer buffer, Direction facing) {
        float yRotation = facing.toYRot();
        if (yRotation != 0.0F) {
            buffer.rotateCentered(AngleHelper.rad(-yRotation), Direction.UP);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(SewingMachineBlockEntity blockEntity) {
        return true;
    }
}
