package com.rexhub.createapparel.client.advertisement;

import com.rexhub.createapparel.advertisement.*;
import net.minecraft.client.Minecraft;
import java.util.*;

public final class ClientAdvertisementChannels {
    private static final Map<String, AdvertisementNetwork.Update> CHANNELS = new HashMap<>();
    private static final Map<String, Long> REQUESTS = new HashMap<>();
    private static final LinkedHashMap<String, net.minecraft.core.BlockPos> PENDING = new LinkedHashMap<>();
    private static Object levelIdentity;
    private static long nextRequest;
    private static Object connection;
    private ClientAdvertisementChannels() { }
    private static void checkConnection() {
        if (connection != Minecraft.getInstance().getConnection()) {
            connection = Minecraft.getInstance().getConnection(); CHANNELS.clear(); REQUESTS.clear(); PENDING.clear(); nextRequest = 0; PhotoRenderCache.clear();
        }
        if (levelIdentity != Minecraft.getInstance().level) {
            levelIdentity = Minecraft.getInstance().level; REQUESTS.clear(); PENDING.clear(); nextRequest = 0;
        }
    }
    public static void accept(AdvertisementNetwork.Update update) {
        checkConnection();
        if (CHANNELS.size() >= 128 && !CHANNELS.containsKey(update.channel())) CHANNELS.remove(CHANNELS.keySet().iterator().next());
        CHANNELS.put(update.channel(), update);
    }
    public static AdvertisementSnapshot snapshot(AdvertisementPanelBlockEntity panel) {
        var scenes = scenes(panel);
        var level = Minecraft.getInstance().level;
        return scenes.get(AdvertisementScenes.index(level == null ? 0 : level.getGameTime(), scenes.size()));
    }
    public static List<AdvertisementSnapshot> scenes(AdvertisementPanelBlockEntity panel) {
        checkConnection(); var level = Minecraft.getInstance().level;
        if (level == null || panel.channel().isEmpty()) return panel.withSceneLayouts(AdvertisementScenes.DEFAULTS);
        long now = level.getGameTime(); String channel = panel.channel();
        if (now >= REQUESTS.getOrDefault(channel, Long.MIN_VALUE)) {
            if (PENDING.size() >= 128 && !PENDING.containsKey(channel)) PENDING.remove(PENDING.keySet().iterator().next());
            PENDING.put(channel, panel.getBlockPos());
        }
        if (now >= nextRequest && !PENDING.isEmpty()) {
            var request = PENDING.entrySet().iterator().next(); String id = request.getKey(); var pos = request.getValue(); PENDING.remove(id);
            if (REQUESTS.size() >= 128 && !REQUESTS.containsKey(id)) REQUESTS.remove(REQUESTS.keySet().iterator().next());
            REQUESTS.put(id, now + 100); nextRequest = now + 3; var previous = CHANNELS.get(id);
            AdvertisementNetwork.request(pos, previous == null ? -1 : previous.revision());
        }
        var current = CHANNELS.get(channel); return panel.withSceneLayouts(current == null ? AdvertisementScenes.DEFAULTS : current.scenes());
    }
}
