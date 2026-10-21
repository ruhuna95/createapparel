package com.rexhub.createapparel.client.apparel;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class ClientApparelEvents {
    private ClientApparelEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void trimTextures(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            RuntimeGarmentTextureCache.endFrame();
            com.rexhub.createapparel.client.garment.CitizenTextureCache.endFrame();
        }
    }

    @SubscribeEvent
    public static void clearOnLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        com.rexhub.createapparel.client.garment.GarmentDiagnostics.cancelComparison();
        com.rexhub.createapparel.client.garment.GarmentDiagnostics.stop();
        ClientApparelInventories.clear();
        com.rexhub.createapparel.client.garment.CitizenTextureCache.clear();
    }

    @SubscribeEvent
    public static void removeUnloadedPlayer(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof Player player) {
            ClientApparelInventories.remove(player.getId());
        }
    }
}
