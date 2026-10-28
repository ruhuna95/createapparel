package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.config.ModConfigs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import javax.annotation.Nullable;
import java.util.*;

public final class AdvertisementNetworkSavedData extends SavedData {
    public record Channel(List<AdvertisementSnapshot> scenes, long revision, @Nullable UUID owner) {
        public Channel { scenes = AdvertisementScenes.validate(scenes); }
        public AdvertisementSnapshot snapshot() { return scenes.get(0); }
    }
    private static final Channel UNCONFIGURED = new Channel(AdvertisementScenes.DEFAULTS, 0, null);
    private final Map<String, Channel> channels = new LinkedHashMap<>();
    private final Map<String, CompoundTag> recoveries = new HashMap<>();
    public static AdvertisementNetworkSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(AdvertisementNetworkSavedData::load,
                AdvertisementNetworkSavedData::new, "createapparel_advertisements");
    }
    public static boolean validChannel(String id) { return id.matches("[a-z0-9_]{1,48}"); }
    public Channel channel(String id) { return channels.getOrDefault(id, UNCONFIGURED); }
    public boolean publish(ServerPlayer player, String id, AdvertisementSnapshot snapshot) {
        return publish(player, id, List.of(snapshot));
    }
    public boolean publish(ServerPlayer player, String id, List<AdvertisementSnapshot> scenes) {
        if (!validChannel(id) || !player.getAbilities().mayBuild) return false;
        scenes = AdvertisementScenes.validate(scenes);
        var previous = channel(id);
        if (previous.owner() != null && !previous.owner().equals(player.getUUID()) && !player.hasPermissions(2) && !player.isCreative()) return false;
        if (!channels.containsKey(id) && channels.size() >= ModConfigs.COMMON.fashion.maxChannels.get()) return false;
        if (channels.containsKey(id) && previous.scenes().equals(scenes)) return true;
        var next = new Channel(scenes, previous.revision() + 1, previous.owner() == null ? player.getUUID() : previous.owner());
        channels.put(id, next); setDirty();
        com.rexhub.createapparel.fashion.RetailInspection.channelChanged(id);
        AdvertisementNetwork.publish(player.getServer(), id, next); return true;
    }
    public void recovery(String key, CompoundTag state) { recoveries.put(key, state.copy()); setDirty(); }
    @Nullable public CompoundTag recovery(String key) { return recoveries.get(key); }
    public void clearRecovery(String key) { recoveries.remove(key); setDirty(); }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", 2); ListTag list = new ListTag();
        channels.forEach((id, value) -> {
            CompoundTag entry = new CompoundTag(); entry.putString("Id", id); entry.putLong("Revision", value.revision());
            entry.put("Scenes", AdvertisementScenes.save(value.scenes())); if (value.owner() != null) entry.putUUID("Owner", value.owner()); list.add(entry);
        }); tag.put("Channels", list);
        CompoundTag recovery = new CompoundTag(); recoveries.forEach(recovery::put); tag.put("Recoveries", recovery); return tag;
    }
    private static AdvertisementNetworkSavedData load(CompoundTag tag) {
        var result = new AdvertisementNetworkSavedData(); var list = tag.getList("Channels", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(list.size(), 1024); index++) {
            var entry = list.getCompound(index); String id = entry.getString("Id");
            if (validChannel(id)) {
                try {
                    var scenes = entry.contains("Scenes", CompoundTag.TAG_LIST) ? AdvertisementScenes.load(entry.getList("Scenes", CompoundTag.TAG_COMPOUND))
                            : List.of(AdvertisementSnapshot.load(entry.getCompound("Snapshot")));
                    result.channels.put(id, new Channel(scenes, Math.max(0, entry.getLong("Revision")), entry.hasUUID("Owner") ? entry.getUUID("Owner") : null));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        var recoveries = tag.getCompound("Recoveries");
        for (var key : recoveries.getAllKeys()) if (key.length() <= 128 && recoveries.getCompound(key).sizeInBytes() <= 65536)
            result.recoveries.put(key, recoveries.getCompound(key));
        return result;
    }
}
