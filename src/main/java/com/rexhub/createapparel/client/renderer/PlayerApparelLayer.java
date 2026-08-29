package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.apparel.ClientApparelInventories;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class PlayerApparelLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final List<GarmentSlot> RENDER_ORDER = List.of(
            GarmentSlot.INNER_TOP,
            GarmentSlot.INNER_BOTTOM,
            GarmentSlot.LEGWEAR,
            GarmentSlot.TOP,
            GarmentSlot.BOTTOM,
            GarmentSlot.HANDS,
            GarmentSlot.FEET,
            GarmentSlot.NECK,
            GarmentSlot.OUTER,
            GarmentSlot.BACK,
            GarmentSlot.FACE,
            GarmentSlot.HEAD
    );

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

        ArmProfile armProfile = ClientApparelInventories.armProfile(player);
        ChestProfile chestProfile = ClientApparelInventories.chestProfile(player);
        int packedOverlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        if (ClientApparelInventories.isBodyModeActive(player)) {
            ClientGarmentAssets.INSTANCE.resolveBody(armProfile, chestProfile).ifPresent(parts ->
                    CommonGarmentRenderer.renderHumanoidSupport(
                            parts,
                            getParentModel(),
                            armProfile,
                            ClientApparelInventories.skinTone(player).tintRgb(),
                            poseStack,
                            buffer,
                            packedLight,
                            packedOverlay
                    )
            );
            ClientGarmentAssets.INSTANCE.resolveDefaultUnderwear(chestProfile).ifPresent(parts ->
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

        for (GarmentSlot slot : RENDER_ORDER) {
            ItemStack stack = ClientApparelInventories.getStack(player, slot);
            if (stack.isEmpty()) {
                continue;
            }
            ClientGarmentAssets.INSTANCE.resolve(stack, armProfile, chestProfile).ifPresent(garment ->
                    CommonGarmentRenderer.renderHumanoidGarment(
                            garment,
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
