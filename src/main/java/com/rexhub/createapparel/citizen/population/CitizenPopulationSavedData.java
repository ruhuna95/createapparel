package com.rexhub.createapparel.citizen.population;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.*;

public final class CitizenPopulationSavedData extends SavedData {
    public static final String NAME = "createapparel_citizen_population";
    private final Map<UUID, CitizenGauge> gauges = new LinkedHashMap<>();
    private record Address(BlockPos pos, int slot) { }
    private final Map<Address, UUID> positions = new HashMap<>();
    private final Map<UUID, UUID> districts = new HashMap<>();
    private final Map<Long, Set<UUID>> centers = new HashMap<>();
    private final Map<Long, UUID> centerReservations = new HashMap<>();
    private final Map<UUID, Long> reservedCenters = new HashMap<>();
    private final Map<UUID, Integer> reservedItems = new HashMap<>();
    private static final int UNKNOWN_RESERVED_ITEMS = -1;
    private final Map<UUID, Set<UUID>> links = new HashMap<>();
    private final Map<UUID, CitizenPopulationState> citizens = new LinkedHashMap<>();
    private final Map<UUID, UUID> transferReservations = new HashMap<>();
    private final Map<UUID, Set<UUID>> residents = new HashMap<>();
    private final Set<UUID> walkers = new HashSet<>();
    private final Map<UUID, Integer> walkingPopulation = new HashMap<>();
    private final DistrictTerritory territory = new DistrictTerritory(this::setDirty);
    private final Map<Long, Set<UUID>> gaugeChunks = new HashMap<>();
    private final Map<UUID, List<UUID>> linkedDistrictCache = new HashMap<>();
    private final Map<UUID, DistrictOverlap> overlapCache = new HashMap<>();
    public record DistrictOverlap(int count, boolean strong) { }
    private static final DistrictOverlap NO_OVERLAP = new DistrictOverlap(0, false);
    private long version;
    private final PopulationNetworkIndex networks = new PopulationNetworkIndex(this);
    public PopulationNetworkIndex networks() { return networks; }

    public Collection<CitizenGauge> gauges() { return Collections.unmodifiableCollection(gauges.values()); }
    @Nullable public CitizenGauge gauge(UUID id) { return gauges.get(id); }
    @Nullable public CitizenGauge at(BlockPos pos) { return at(pos, 0); }
    @Nullable public CitizenGauge at(BlockPos pos, int slot) { return gauges.get(positions.get(new Address(pos, slot))); }
    public List<CitizenGauge> atAll(BlockPos pos) {
        var result = new ArrayList<CitizenGauge>();
        for (int slot = 0; slot < 4; slot++) { var gauge = at(pos, slot); if (gauge != null) result.add(gauge); }
        return List.copyOf(result);
    }
    @Nullable public CitizenGauge district(@Nullable UUID id) { return gauges.get(districts.get(id)); }
    public boolean centerUsed(BlockPos center) { return centers.containsKey(center.asLong()); }
    public boolean centerAvailable(BlockPos center, @Nullable UUID reservation) {
        var owner = centerReservations.get(center.asLong());
        return !centerUsed(center) && (owner == null || owner.equals(reservation));
    }
    @Nullable public UUID reserveCenter(BlockPos center, @Nullable UUID reservation) {
        return reserveCenter(center, reservation, 1);
    }
    @Nullable public UUID reserveCenter(BlockPos center, @Nullable UUID reservation, int items) {
        if (items <= 0) throw new IllegalArgumentException("Reservation requires an item");
        if (!centerAvailable(center, reservation)) return null;
        var existing = centerReservations.get(center.asLong());
        if (existing != null && reservedItems.getOrDefault(existing, UNKNOWN_RESERVED_ITEMS) != UNKNOWN_RESERVED_ITEMS) return existing;
        if (existing != null) { reservedCenters.remove(existing); reservedItems.remove(existing); }
        var id = UUID.randomUUID();
        centerReservations.put(center.asLong(), id); reservedCenters.put(id, center.asLong());
        reservedItems.put(id, items);
        setDirty(); return id;
    }
    public void releaseCenter(@Nullable UUID reservation, int items) {
        if (items <= 0) return;
        if (reservedItems.getOrDefault(reservation, 0) == UNKNOWN_RESERVED_ITEMS) return;
        int remaining = reservedItems.getOrDefault(reservation, 0) - items;
        if (remaining <= 0) { releaseCenter(reservation); return; }
        reservedItems.put(reservation, remaining); setDirty();
    }
    public void releaseCenterForRerecord(@Nullable UUID reservation, int items) {
        if (reservedItems.getOrDefault(reservation, 0) == UNKNOWN_RESERVED_ITEMS) releaseCenter(reservation);
        else releaseCenter(reservation, items);
    }
    public void releaseCenter(@Nullable UUID reservation) {
        var center = reservedCenters.remove(reservation);
        if (center == null) return;
        centerReservations.remove(center); reservedItems.remove(reservation); setDirty();
    }
    public List<UUID> linkedDistricts(UUID districtId) {
        var district = district(districtId);
        if (district == null) return List.of();
        return linkedDistrictCache.computeIfAbsent(districtId, ignored -> linked(district.id()).stream().map(gauges::get)
                .filter(Objects::nonNull).filter(gauge -> !gauge.source()).map(CitizenGauge::districtId).toList());
    }
    public List<CitizenGauge> inChunk(long chunk) {
        return gaugeChunks.getOrDefault(chunk, Set.of()).stream().map(gauges::get).toList();
    }
    public DistrictTerritory territory() { return territory; }
    @Nullable public CitizenGauge physicalDistrict(Vec3 point) {
        return district(territory.districtAt(point));
    }
    public boolean owns(CitizenGauge gauge, Vec3 point) {
        return !gauge.source() && gauge.districtId().equals(territory.districtAt(point));
    }
    public boolean containsOtherDistrict(UUID gaugeId, Vec3 point) {
        var owner = physicalDistrict(point); return owner != null && !owner.id().equals(gaugeId);
    }
    public DistrictOverlap overlap(UUID gaugeId) {
        var gauge = gauges.get(gaugeId);
        if (gauge == null || gauge.source()) return NO_OVERLAP;
        return overlapCache.computeIfAbsent(gaugeId, ignored -> {
            var bounds = new DistrictTerritory.Bounds(gauge.center().getX() - gauge.range(), gauge.center().getZ() - gauge.range(),
                    gauge.center().getX() + gauge.range(), gauge.center().getZ() + gauge.range());
            int count = (int) territory.candidates(bounds).stream().filter(seed -> !seed.districtId().equals(gauge.districtId())).count();
            return new DistrictOverlap(count, false);
        });
    }
    private static void removeIndex(Map<Long, Set<UUID>> index, long cell, UUID id) {
        var entries = index.get(cell);
        if (entries == null) return;
        entries.remove(id);
        if (entries.isEmpty()) index.remove(cell);
    }
    @Nullable public CitizenPopulationState citizen(UUID id) { return citizens.get(id); }
    @Nullable public UUID reservation(UUID citizen) { return transferReservations.get(citizen); }
    public void reserve(UUID citizen, UUID chain) {
        if (!citizens.containsKey(citizen) || transferReservations.containsKey(citizen)) throw new IllegalArgumentException("Citizen is not available for reservation");
        transferReservations.put(citizen, chain); setDirty();
    }
    public void release(UUID citizen) { if (transferReservations.remove(citizen) != null) setDirty(); }
    public Set<UUID> residents(UUID district) { return Collections.unmodifiableSet(residents.getOrDefault(district, Set.of())); }
    public int population(UUID district) { return residents.getOrDefault(district, Set.of()).size(); }
    public int walkingPopulation(UUID district) { return walkingPopulation.getOrDefault(district, 0); }
    private void countWalker(@Nullable UUID district, int change) {
        if (district != null) walkingPopulation.compute(district, (key, count) -> {
            int next = (count == null ? 0 : count) + change; return next <= 0 ? null : next;
        });
    }
    public void walking(UUID id, boolean walking) {
        var state = citizens.get(id);
        if (state == null || walking == walkers.contains(id)) return;
        if (walking) walkers.add(id); else walkers.remove(id);
        countWalker(state.districtId(), walking ? 1 : -1); setDirty();
    }
    public void register(UUID id, CitizenPopulationState state, boolean walking) {
        register(id, state); walking(id, walking);
    }
    public Set<UUID> linked(UUID id) { return Collections.unmodifiableSet(links.getOrDefault(id, Set.of())); }
    public long version() { return version; }
    public void put(CitizenGauge gauge) {
        var previous = gauges.get(gauge.id());
        if (gauge.equals(previous)) return;
        var address = new Address(gauge.pos(), gauge.slot());
        if (positions.containsKey(address) && !gauge.id().equals(positions.get(address))) throw new IllegalArgumentException("Duplicate Gauge panel");
        if (gauge.districtId() != null && districts.containsKey(gauge.districtId()) && !gauge.id().equals(districts.get(gauge.districtId())))
            throw new IllegalArgumentException("Duplicate District");
        boolean geometryChanged = previous == null ? !gauge.source() : !Objects.equals(previous.center(), gauge.center())
                || !Objects.equals(previous.districtId(), gauge.districtId()) || !gauge.source() && previous.range() != gauge.range();
        boolean addressChanged = previous == null || !previous.pos().equals(gauge.pos());
        if (geometryChanged) {
            if (previous != null && previous.districtId() != null && !Objects.equals(previous.districtId(), gauge.districtId())) territory.remove(previous.districtId());
            if (!gauge.source()) territory.put(new DistrictTerritory.Seed(gauge.districtId(), gauge.center().getX(), gauge.center().getZ(), gauge.range()));
            overlapCache.clear();
        }
        if (addressChanged) {
            if (previous != null) removeIndex(gaugeChunks, new ChunkPos(previous.pos()).toLong(), previous.id());
            gaugeChunks.computeIfAbsent(new ChunkPos(gauge.pos()).toLong(), ignored -> new LinkedHashSet<>()).add(gauge.id());
        }
        if (previous == null || !Objects.equals(previous.center(), gauge.center())) {
            if (previous != null && previous.center() != null) removeIndex(centers, previous.center().asLong(), previous.id());
            if (gauge.center() != null) centers.computeIfAbsent(gauge.center().asLong(), ignored -> new LinkedHashSet<>()).add(gauge.id());
        }
        if (gauge.center() != null) releaseCenter(centerReservations.get(gauge.center().asLong()));
        if (previous == null || !Objects.equals(previous.districtId(), gauge.districtId())) linkedDistrictCache.clear();
        if (previous != null) { positions.remove(new Address(previous.pos(), previous.slot())); if (previous.districtId() != null) districts.remove(previous.districtId()); }
        gauges.put(gauge.id(), gauge); positions.put(address, gauge.id());
        if (gauge.districtId() != null) districts.put(gauge.districtId(), gauge.id());
        if (previous == null || previous.slot() != gauge.slot() || !previous.pos().equals(gauge.pos()) || !Objects.equals(previous.districtId(), gauge.districtId())
                || !Objects.equals(previous.center(), gauge.center()) || previous.range() != gauge.range() || previous.target() != gauge.target()
                || !previous.name().equals(gauge.name()) || previous.flow() != gauge.flow()) version++;
        setDirty();
    }
    public boolean link(UUID a, UUID b) {
        if (a.equals(b) || !gauges.containsKey(a) || !gauges.containsKey(b) || linked(a).contains(b)) return false;
        links.computeIfAbsent(a, key -> new LinkedHashSet<>()).add(b);
        links.computeIfAbsent(b, key -> new LinkedHashSet<>()).add(a);
        linkedDistrictCache.clear();
        version++; setDirty(); return true;
    }
    public boolean unlink(UUID a, UUID b) {
        if (!linked(a).contains(b)) return false;
        links.get(a).remove(b); links.get(b).remove(a); linkedDistrictCache.clear(); version++; setDirty(); return true;
    }
    @Nullable public CitizenGauge remove(UUID id) {
        var gauge = gauges.get(id);
        if (gauge == null) return null;
        for (var other : Set.copyOf(linked(id))) unlink(id, other);
        links.remove(id); gauges.remove(id); positions.remove(new Address(gauge.pos(), gauge.slot()));
        if (gauge.center() != null) removeIndex(centers, gauge.center().asLong(), id);
        if (gauge.districtId() != null) territory.remove(gauge.districtId());
        removeIndex(gaugeChunks, new ChunkPos(gauge.pos()).toLong(), id);
        overlapCache.clear(); linkedDistrictCache.clear();
        if (gauge.districtId() != null) {
            districts.remove(gauge.districtId());
            for (var citizen : Set.copyOf(residents(gauge.districtId()))) {
                var state = citizens.get(citizen); register(citizen, state.district(null, state.transferCooldownUntil()));
            }
        }
        version++; setDirty(); return gauge;
    }
    public void register(UUID id, CitizenPopulationState state) {
        var previous = citizens.get(id);
        if (state.equals(previous)) return;
        if (walkers.contains(id)) {
            if (previous != null) countWalker(previous.districtId(), -1);
            countWalker(state.districtId(), 1);
        }
        if (previous != null && previous.districtId() != null) residents.get(previous.districtId()).remove(id);
        citizens.put(id, state);
        if (state.districtId() != null) residents.computeIfAbsent(state.districtId(), key -> new LinkedHashSet<>()).add(id);
        if (previous == null || !Objects.equals(previous.districtId(), state.districtId())) {
            if (previous != null) networks.populationChanged(previous.districtId());
            networks.populationChanged(state.districtId());
        }
        setDirty();
    }
    public void removeCitizen(UUID id) {
        release(id);
        var previous = citizens.remove(id);
        if (previous == null) return;
        if (walkers.remove(id)) countWalker(previous.districtId(), -1);
        if (previous.districtId() != null) residents.get(previous.districtId()).remove(id);
        networks.populationChanged(previous.districtId());
        setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("SchemaVersion", 5);
        tag.put("Territory", territory.save());
        var pendingCenters = new ListTag();
        centerReservations.forEach((center, id) -> {
            var entry = new CompoundTag(); entry.putLong("Center", center); entry.putUUID("Id", id);
            entry.putInt("Items", reservedItems.getOrDefault(id, UNKNOWN_RESERVED_ITEMS)); pendingCenters.add(entry);
        });
        tag.put("CenterReservations", pendingCenters);
        var nodes = new ListTag(); gauges.values().forEach(gauge -> nodes.add(gauge.save())); tag.put("Gauges", nodes);
        var edges = new ListTag();
        for (var a : gauges.keySet()) for (var b : linked(a)) if (a.compareTo(b) < 0) {
            var edge = new CompoundTag(); edge.putUUID("A", a); edge.putUUID("B", b); edges.add(edge);
        }
        tag.put("Links", edges);
        var entries = new ListTag(); citizens.forEach((id, state) -> {
            var entry = state.save(); entry.putUUID("Id", id);
            if (walkers.contains(id)) entry.putBoolean("Walking", true);
            if (transferReservations.containsKey(id)) entry.putUUID("TransferReservation", transferReservations.get(id));
            entries.add(entry);
        });
        tag.put("Citizens", entries); return tag;
    }
    public static CitizenPopulationSavedData load(CompoundTag tag) {
        var data = new CitizenPopulationSavedData();
        for (var raw : tag.getList("Gauges", Tag.TAG_COMPOUND)) try { data.put(CitizenGauge.load((CompoundTag) raw)); }
        catch (IllegalArgumentException exception) { com.mojang.logging.LogUtils.getLogger().warn("Ignoring invalid Citizen Gauge: {}", exception.getMessage()); }
        for (var raw : tag.getList("CenterReservations", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.contains("Center", Tag.TAG_LONG) || !entry.hasUUID("Id")) continue;
            long center = entry.getLong("Center"); var id = entry.getUUID("Id");
            if (data.centers.containsKey(center) || data.centerReservations.containsKey(center) || data.reservedCenters.containsKey(id)) continue;
            data.centerReservations.put(center, id); data.reservedCenters.put(id, center);
            data.reservedItems.put(id, entry.getInt("Items") > 0 ? entry.getInt("Items") : UNKNOWN_RESERVED_ITEMS);
        }
        for (var raw : tag.getList("Links", Tag.TAG_COMPOUND)) {
            var edge = (CompoundTag) raw; if (edge.hasUUID("A") && edge.hasUUID("B")) data.link(edge.getUUID("A"), edge.getUUID("B"));
        }
        for (var raw : tag.getList("Citizens", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Id")) continue;
            var state = CitizenPopulationState.load(entry);
            if (data.district(state.districtId()) == null) state = state.district(null, state.transferCooldownUntil());
            data.register(entry.getUUID("Id"), state, entry.getBoolean("Walking"));
        }
        data.territory.restore(tag.getCompound("Territory"));
        return data;
    }
}
