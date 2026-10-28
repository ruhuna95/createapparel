package com.rexhub.createapparel.client.advertisement;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "createapparel", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class AdvertisementClientEvents {
    private AdvertisementClientEvents() { }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        PhotoRenderCache.clear();
    }
}
