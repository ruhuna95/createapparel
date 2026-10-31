package com.rexhub.createapparel.client.mirror;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.block.entity.MirrorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public final class MirrorBlockEntityRenderer implements BlockEntityRenderer<MirrorBlockEntity> {
    public MirrorBlockEntityRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(MirrorBlockEntity mirror, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int light, int overlay) {
        MirrorRenderManager.INSTANCE.collect(mirror, poseStack.last().pose());
    }

    @Override public int getViewDistance() { return 64; }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(MirrorBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }
}
