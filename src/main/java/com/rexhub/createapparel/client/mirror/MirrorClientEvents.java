package com.rexhub.createapparel.client.mirror;

import com.rexhub.createapparel.CreateApparel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class MirrorClientEvents {
    private MirrorClientEvents() { }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { MirrorRenderManager.INSTANCE.reset(); }
}
