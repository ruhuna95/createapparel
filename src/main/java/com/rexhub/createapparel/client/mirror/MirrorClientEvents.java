package com.rexhub.createapparel.client.mirror;

import com.rexhub.createapparel.CreateApparel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class MirrorClientEvents {
    private MirrorClientEvents() { }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { MirrorRenderManager.INSTANCE.reset(); }
}
