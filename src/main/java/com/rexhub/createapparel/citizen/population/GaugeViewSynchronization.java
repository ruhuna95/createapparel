package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.network.CitizenGaugeInfoPacket;
import com.rexhub.createapparel.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import javax.annotation.Nullable;
import java.util.*;

public final class GaugeViewSynchronization {
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_RESYNC = new HashMap<>();
    private static final int SHARED_LIMIT = 32;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new LinkedHashMap<>(32, .75f, true);
    private static final Map<DeltaKey, CompoundTag> DELTAS = new LinkedHashMap<>(32, .75f, true);
    private record SnapshotKey(ServerLevel level, long geometry, long presentation, long chunk) { }
    private static final class Snapshot {
        private final CompoundTag tag;
        private Snapshot(CompoundTag tag) { this.tag = tag; }
    }
    private record DeltaKey(@Nullable Snapshot previous, Snapshot current, long baseRevision, long revision) { }
    private record View(String dimension, long geometry, long presentation, long chunk, Snapshot snapshot, long revision) { }
    private GaugeViewSynchronization() { }
    public static void clear(UUID player) { VIEWS.remove(player); NEXT_RESYNC.remove(player); }
    public static void stopObserving(UUID player) { VIEWS.remove(player); }
    public static void clear() { VIEWS.clear(); NEXT_RESYNC.clear(); SNAPSHOTS.clear(); DELTAS.clear(); }
    public static void clear(ServerLevel level) {
        VIEWS.values().removeIf(view -> view.dimension.equals(level.dimension().location().toString()));
        SNAPSHOTS.keySet().removeIf(key -> key.level == level); DELTAS.clear();
    }
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
        var key = new SnapshotKey(player.serverLevel(), data.districtGeometryRevision(), data.presentationRevision(), chunk);
        var snapshot = SNAPSHOTS.get(key);
        if (snapshot == null) {
            snapshot = new Snapshot(snapshot(player, data, dimension)); SNAPSHOTS.put(key, snapshot);
            if (SNAPSHOTS.size() > SHARED_LIMIT) SNAPSHOTS.remove(SNAPSHOTS.keySet().iterator().next());
        }
        boolean sameDimension = previous != null && previous.dimension.equals(dimension);
        long revision = sameDimension ? previous.revision + 1 : 1;
        if (!sameDimension || !previous.snapshot.tag.equals(snapshot.tag)) {
            var deltaKey = new DeltaKey(sameDimension ? previous.snapshot : null, snapshot, revision - 1, revision);
            var delta = DELTAS.get(deltaKey);
            if (delta == null) {
                delta = GaugeViewDelta.between(sameDimension ? previous.snapshot.tag : null, snapshot.tag, revision - 1, revision);
                DELTAS.put(deltaKey, delta); if (DELTAS.size() > SHARED_LIMIT) DELTAS.remove(DELTAS.keySet().iterator().next());
            }
            ModNetwork.sendGaugeInfo(player, new CitizenGaugeInfoPacket(-1, new UUID(0, 0), delta));
        } else revision = previous.revision;
        VIEWS.put(player.getUUID(), new View(dimension, data.districtGeometryRevision(), data.presentationRevision(), chunk, snapshot, revision));
    }
    private static CompoundTag snapshot(ServerPlayer player, CitizenPopulationSavedData data, String dimension) {
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
        return snapshot;
    }
}
