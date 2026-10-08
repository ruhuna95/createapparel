package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.client.model.ModPartialModels;
import com.rexhub.createapparel.init.ModBlocks;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class SilkwormRearingTrayItemRenderer extends BlockEntityWithoutLevelRenderer {
    public SilkwormRearingTrayItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        CachedBuffers.partial(ModPartialModels.REARING_ITEM, ModBlocks.SILKWORM_REARING_TRAY.get().defaultBlockState())
                .light(light).overlay(overlay).renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
        CompoundTag blockTag = stack.getTagElement("BlockEntityTag");
        if (blockTag != null && blockTag.getCompound("TrayData").getBoolean("HasSilkworm")) {
            SilkwormRearingTrayContentsRenderer.renderLarva(pose, buffers, light, overlay, false);
        }
    }
}
