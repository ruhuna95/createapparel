package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.apparel.ClientApparelInventories;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.client.garment.ApparelRenderFrame;
import com.rexhub.createapparel.client.garment.SkirtPoseGeometry;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import static com.rexhub.createapparel.client.garment.ResolvedApparelState.RENDER_ORDER;

final class PlayerApparelLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    PlayerApparelLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
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
        PlayerSkinRenderEvents.restore(player);
        if (player.isInvisible()) {
            return;
        }

        var snapshot = ClientApparelInventories.snapshot(player);
        ArmProfile armProfile = snapshot.armProfile();
        ChestProfile chestProfile = snapshot.chestProfile();
        int packedOverlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        if (snapshot.bodyMode()) {
            ClientGarmentAssets.INSTANCE.resolveBody(armProfile, chestProfile).ifPresent(parts ->
                    CommonGarmentRenderer.renderHumanoidSupport(
                            parts,
                            getParentModel(),
                            armProfile,
                            snapshot.skinTone().tintRgb(),
                            poseStack,
                            buffer,
                            packedLight,
                            packedOverlay
                    )
            );
            boolean includeDefaultTop = snapshot.isEmpty(GarmentSlot.INNER_TOP) && snapshot.isEmpty(GarmentSlot.TOP);
            boolean includeDefaultBottom = snapshot.isEmpty(GarmentSlot.INNER_BOTTOM);
            ClientGarmentAssets.INSTANCE.resolveDefaultUnderwear(
                    chestProfile,
                    includeDefaultTop,
                    includeDefaultBottom
            ).ifPresent(parts ->
                    CommonGarmentRenderer.renderHumanoidSupport(
                            parts,
                            getParentModel(),
                            armProfile,
                            0xFFFFFF,
                            poseStack,
                            buffer,
                            packedLight,
                            packedOverlay
                    )
            );
        }

        var frame = new ApparelRenderFrame(snapshot::garment, part -> SkirtPoseGeometry.animate(part, getParentModel()));
        for (GarmentSlot slot : RENDER_ORDER) {
            frame.garment(slot).ifPresent(garment ->
                    CommonGarmentRenderer.renderHumanoidGarment(
                            garment,
                            slot,
                            frame,
                            snapshot,
                            getParentModel(),
                            armProfile,
                            poseStack,
                            buffer,
                            packedLight,
                            packedOverlay
                    )
            );
        }
    }
}
