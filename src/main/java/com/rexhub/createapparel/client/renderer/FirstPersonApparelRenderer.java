package com.rexhub.createapparel.client.renderer;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.client.apparel.ClientApparelInventories;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class FirstPersonApparelRenderer {
    private static PlayerModel<AbstractClientPlayer> classic;
    private static PlayerModel<AbstractClientPlayer> slim;

    private FirstPersonApparelRenderer() {
    }

    public static void reloadModels(EntityModelSet models) {
        classic = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        if (player.isInvisible() || player.isSpectator()) {
            return;
        }
        var snapshot = ClientApparelInventories.snapshot(player);
        var parts = snapshot.firstPersonArm(event.getArm());
        var model = snapshot.armProfile() == ArmProfile.SLIM ? slim : classic;
        if (parts.isEmpty() || model == null) {
            return;
        }
        preparePose(model, player);
        var arm = event.getArm() == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        arm.xRot = 0;
        CommonGarmentRenderer.renderFirstPersonArm(parts, snapshot, event.getArm(), arm,
                event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        if (!parts.body().isEmpty()) {
            event.setCanceled(true);
        }
    }

    private static void preparePose(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player) {
        ArmPose main = armPose(player, InteractionHand.MAIN_HAND);
        ArmPose off = armPose(player, InteractionHand.OFF_HAND);
        if (main.isTwoHanded()) {
            off = player.getOffhandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
        }
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        model.rightArmPose = rightHanded ? main : off;
        model.leftArmPose = rightHanded ? off : main;
        model.attackTime = 0;
        model.crouching = false;
        model.swimAmount = 0;
        model.riding = false;
        model.young = false;
        model.setupAnim(player, 0, 0, 0, 0, 0);
    }

    private static ArmPose armPose(AbstractClientPlayer player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return ArmPose.EMPTY;
        }
        if (player.getUsedItemHand() == hand && player.getUseItemRemainingTicks() > 0) {
            ArmPose using = switch (stack.getUseAnimation()) {
                case BLOCK -> ArmPose.BLOCK;
                case BOW -> ArmPose.BOW_AND_ARROW;
                case SPEAR -> ArmPose.THROW_SPEAR;
                case CROSSBOW -> ArmPose.CROSSBOW_CHARGE;
                case SPYGLASS -> ArmPose.SPYGLASS;
                case TOOT_HORN -> ArmPose.TOOT_HORN;
                case BRUSH -> ArmPose.BRUSH;
                default -> null;
            };
            if (using != null) {
                return using;
            }
        } else if (!player.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return ArmPose.CROSSBOW_HOLD;
        }
        ArmPose custom = IClientItemExtensions.of(stack).getArmPose(player, hand, stack);
        return custom != null ? custom : ArmPose.ITEM;
    }
}
