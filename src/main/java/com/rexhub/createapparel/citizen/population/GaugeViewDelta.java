package com.rexhub.createapparel.citizen.population;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import javax.annotation.Nullable;
import java.util.*;

public final class GaugeViewDelta {
    private static final List<String> COLLECTIONS = List.of("Gauges", "TerritoryGauges");
    private GaugeViewDelta() { }
    public static CompoundTag fieldsBetween(@Nullable CompoundTag previous, CompoundTag current, long baseRevision, long revision) {
        var packet = new CompoundTag();
        for (var key : current.getAllKeys()) if (!key.equals("Now") && (previous == null || !Objects.equals(previous.get(key), current.get(key)))) packet.put(key, current.get(key).copy());
        var removed = new ListTag();
        if (previous != null) for (var key : previous.getAllKeys()) if (!current.contains(key)) removed.add(net.minecraft.nbt.StringTag.valueOf(key));
        if (!removed.isEmpty()) packet.put("RemovedFields", removed);
        if (packet.isEmpty()) return packet;
        packet.putLong("Now", current.getLong("Now")); packet.putBoolean("MenuDelta", previous != null);
        packet.putLong("BaseMenuRevision", baseRevision); packet.putLong("MenuRevision", revision); return packet;
    }
    @Nullable public static CompoundTag applyFields(CompoundTag previous, long revision, CompoundTag packet) {
        if (!packet.getBoolean("MenuDelta")) return packet.copy();
        if (packet.getLong("BaseMenuRevision") != revision) return null;
        var result = previous.copy(); result.merge(packet);
        for (var key : packet.getList("RemovedFields", Tag.TAG_STRING)) result.remove(key.getAsString());
        return result;
    }
    public static CompoundTag between(@Nullable CompoundTag previous, CompoundTag current, long baseRevision, long revision) {
        var packet = current.copy(); packet.putBoolean("Snapshot", previous == null);
        packet.putLong("BaseRevision", baseRevision); packet.putLong("Revision", revision);
        if (previous == null) return packet;
        for (var key : COLLECTIONS) {
            var old = entries(previous, key); var next = entries(current, key);
            var changed = new ListTag(); var removed = new ListTag();
            next.forEach((id, tag) -> { if (!tag.equals(old.get(id))) changed.add(tag.copy()); });
            old.keySet().forEach(id -> { if (!next.containsKey(id)) { var tag = new CompoundTag(); tag.putUUID("Id", id); removed.add(tag); } });
            packet.put(key, changed); packet.put("Removed" + key, removed);
        }
        return packet;
    }
    @Nullable public static CompoundTag apply(@Nullable CompoundTag previous, long revision, CompoundTag packet) {
        if (packet.getBoolean("Snapshot")) return packet.copy();
        if (previous == null || packet.getLong("BaseRevision") != revision
                || !previous.getString("Dimension").equals(packet.getString("Dimension"))) return null;
        var result = packet.copy();
        for (var key : COLLECTIONS) {
            var entries = entries(previous, key);
            for (var raw : packet.getList("Removed" + key, Tag.TAG_COMPOUND)) {
                var tag = (CompoundTag) raw; if (tag.hasUUID("Id")) entries.remove(tag.getUUID("Id"));
            }
            entries.putAll(entries(packet, key)); var merged = new ListTag(); entries.values().forEach(tag -> merged.add(tag.copy())); result.put(key, merged);
        }
        return result;
    }
    private static Map<UUID, CompoundTag> entries(CompoundTag snapshot, String key) {
        var entries = new LinkedHashMap<UUID, CompoundTag>();
        for (var raw : snapshot.getList(key, Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) raw; if (tag.hasUUID("Id")) entries.put(tag.getUUID("Id"), tag);
        }
        return entries;
    }
}
