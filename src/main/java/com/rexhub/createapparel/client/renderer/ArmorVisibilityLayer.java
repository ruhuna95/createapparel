package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.client.apparel.ClientApparelInventories;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

final class ArmorVisibilityLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> delegate;

    ArmorVisibilityLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer,
            RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> delegate
    ) {
        super(renderer);
        this.delegate = delegate;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!ClientApparelInventories.isArmorVisibilityActive(player)) {
            delegate.render(
                    poseStack,
                    buffer,
                    packedLight,
                    player,
                    limbSwing,
                    limbSwingAmount,
                    partialTick,
                    ageInTicks,
                    netHeadYaw,
                    headPitch
            );
        }
    }
}
