package com.rexhub.createapparel.client.advertisement;

@net.neoforged.fml.common.EventBusSubscriber(modid = "createapparel", value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class AdvertisementClientEvents {
    private AdvertisementClientEvents() { }
    @net.neoforged.bus.api.SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        PhotoRenderCache.clear();
    }
}
