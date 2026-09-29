package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class GarmentItemRenderer extends BlockEntityWithoutLevelRenderer {
    public GarmentItemRenderer(
            BlockEntityRenderDispatcher blockEntityRenderDispatcher,
            EntityModelSet entityModelSet
    ) {
        super(blockEntityRenderDispatcher, entityModelSet);
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        ClientGarmentAssets.INSTANCE.resolveForItem(stack).ifPresent(garment ->
                CommonGarmentRenderer.renderItem(
                        garment,
                        displayContext,
                        poseStack,
                        buffer,
                        packedLight,
                        packedOverlay
                )
        );
    }
}
