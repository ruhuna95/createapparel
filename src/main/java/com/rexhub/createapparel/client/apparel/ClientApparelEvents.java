package com.rexhub.createapparel.client.apparel;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class ClientApparelEvents {
    private ClientApparelEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void trimTextures(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        RuntimeGarmentTextureCache.endFrame();
        com.rexhub.createapparel.client.garment.CitizenTextureCache.endFrame();
    }

    @SubscribeEvent
    public static void clearOnLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientApparelInventories.clear();
        com.rexhub.createapparel.client.garment.CitizenTextureCache.clear();
    }

    @SubscribeEvent
    public static void removeUnloadedCitizen(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof com.rexhub.createapparel.entity.Citizen citizen
                && net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(citizen)
                instanceof com.rexhub.createapparel.client.renderer.CitizenRenderer renderer) {
            renderer.forget(citizen);
        }
    }

    @SubscribeEvent
    public static void removeUnloadedPlayer(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof Player player) {
            ClientApparelInventories.remove(player.getId());
        }
    }
}
