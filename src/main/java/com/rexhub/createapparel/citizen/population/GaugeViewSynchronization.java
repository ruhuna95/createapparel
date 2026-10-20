package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.network.CitizenGaugeInfoPacket;
import com.rexhub.createapparel.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class GaugeViewSynchronization {
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_RESYNC = new HashMap<>();
    private record View(String dimension, long geometry, long presentation, long chunk, CompoundTag snapshot, long revision) { }
    private GaugeViewSynchronization() { }
    public static void clear(UUID player) { VIEWS.remove(player); NEXT_RESYNC.remove(player); }
    public static void stopObserving(UUID player) { VIEWS.remove(player); }
    public static void clear() { VIEWS.clear(); NEXT_RESYNC.clear(); }
    public static void resync(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        if (!GaugeInteractions.observingDistricts(player) || now < NEXT_RESYNC.getOrDefault(player.getUUID(), Long.MIN_VALUE)) return;
        NEXT_RESYNC.put(player.getUUID(), now + 20); VIEWS.remove(player.getUUID()); send(player);
    }
    public static void send(ServerPlayer player) {
        var data = CitizenPopulationManager.get(player.serverLevel()).data();
        var previous = VIEWS.get(player.getUUID()); String dimension = player.level().dimension().location().toString();
        long chunk = player.chunkPosition().toLong();
        if (previous != null && previous.dimension.equals(dimension) && previous.chunk == chunk
                && previous.geometry == data.districtGeometryRevision() && previous.presentation == data.presentationRevision()) return;
        var snapshot = new CompoundTag(); snapshot.putString("Dimension", dimension);
        int x = player.chunkPosition().getMinBlockX(), z = player.chunkPosition().getMinBlockZ();
        var seeds = data.territory().candidates(new DistrictTerritory.Bounds(x - 152, z - 152, x + 167, z + 167));
        var entries = new ListTag();
        seeds.stream().sorted(Comparator.comparing(DistrictTerritory.Seed::districtId)).limit(1024).forEach(seed -> {
            var gauge = data.district(seed.districtId());
            if (gauge != null) { var tag = gauge.save(); tag.remove("CooldownUntil"); tag.remove("Resolving"); entries.add(tag); }
        });
        snapshot.put("TerritoryGauges", entries); snapshot.putBoolean("TerritoryComplete", seeds.size() <= 1024);
        var previews = new ListTag(); for (int i = 0; i < Math.min(256, entries.size()); i++) previews.add(entries.getCompound(i).copy());
        snapshot.put("Gauges", previews);
        boolean sameDimension = previous != null && previous.dimension.equals(dimension);
        long revision = sameDimension ? previous.revision + 1 : 1;
        if (!sameDimension || !previous.snapshot.equals(snapshot)) ModNetwork.sendGaugeInfo(player,
                new CitizenGaugeInfoPacket(-1, new UUID(0, 0), GaugeViewDelta.between(sameDimension ? previous.snapshot : null, snapshot, revision - 1, revision)));
        else revision = previous.revision;
        VIEWS.put(player.getUUID(), new View(dimension, data.districtGeometryRevision(), data.presentationRevision(), chunk, snapshot, revision));
    }
}
