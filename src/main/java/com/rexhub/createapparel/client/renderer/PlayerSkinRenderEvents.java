package com.rexhub.createapparel.client.renderer;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.apparel.ClientApparelInventories;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class PlayerSkinRenderEvents {
    private static final Map<AbstractClientPlayer, VisibilityState> HIDDEN_PLAYERS = new IdentityHashMap<>();

    private PlayerSkinRenderEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hideVanillaBody(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)
                || player.isSpectator()
                || !ClientApparelInventories.isBodyModeActive(player)) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        HIDDEN_PLAYERS.put(player, VisibilityState.capture(model));
        model.body.visible = false;
        model.rightArm.visible = false;
        model.leftArm.visible = false;
        model.rightLeg.visible = false;
        model.leftLeg.visible = false;
        model.jacket.visible = false;
        model.rightSleeve.visible = false;
        model.leftSleeve.visible = false;
        model.rightPants.visible = false;
        model.leftPants.visible = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void restoreAfterRender(RenderPlayerEvent.Post event) {
        if (event.getEntity() instanceof AbstractClientPlayer player) {
            restore(player);
        }
    }

    static void restore(AbstractClientPlayer player) {
        VisibilityState state = HIDDEN_PLAYERS.remove(player);
        if (state != null) {
            state.restore();
        }
    }

    private record VisibilityState(
            PlayerModel<AbstractClientPlayer> model,
            boolean body,
            boolean rightArm,
            boolean leftArm,
            boolean rightLeg,
            boolean leftLeg,
            boolean jacket,
            boolean rightSleeve,
            boolean leftSleeve,
            boolean rightPants,
            boolean leftPants
    ) {
        private static VisibilityState capture(PlayerModel<AbstractClientPlayer> model) {
            return new VisibilityState(
                    model,
                    model.body.visible,
                    model.rightArm.visible,
                    model.leftArm.visible,
                    model.rightLeg.visible,
                    model.leftLeg.visible,
                    model.jacket.visible,
                    model.rightSleeve.visible,
                    model.leftSleeve.visible,
                    model.rightPants.visible,
                    model.leftPants.visible
            );
        }

        private void restore() {
            model.body.visible = body;
            model.rightArm.visible = rightArm;
            model.leftArm.visible = leftArm;
            model.rightLeg.visible = rightLeg;
            model.leftLeg.visible = leftLeg;
            model.jacket.visible = jacket;
            model.rightSleeve.visible = rightSleeve;
            model.leftSleeve.visible = leftSleeve;
            model.rightPants.visible = rightPants;
            model.leftPants.visible = leftPants;
        }
    }
}
