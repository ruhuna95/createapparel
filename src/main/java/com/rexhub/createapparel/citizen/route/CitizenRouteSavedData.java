package com.rexhub.createapparel.citizen.route;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import javax.annotation.Nullable;
import java.util.*;

public final class CitizenRouteSavedData extends SavedData {
    public static final String NAME = "createapparel_citizen_routes";
    private final Map<UUID, RouteMarker> markers = new LinkedHashMap<>();
    private final Map<UUID, RouteEdge> edges = new LinkedHashMap<>();
    private final Map<BlockPos, UUID> positions = new HashMap<>();
    private final Map<Long, Set<UUID>> spatialIndex = new HashMap<>();
    private final Map<UUID, Set<UUID>> adjacency = new HashMap<>();
    private long routeGraphVersion;

    public long version() { return routeGraphVersion; }
    public Collection<RouteMarker> markers() { return Collections.unmodifiableCollection(markers.values()); }
    public Collection<RouteEdge> edges() { return Collections.unmodifiableCollection(edges.values()); }
    @Nullable public RouteMarker marker(UUID id) { return markers.get(id); }
    @Nullable public RouteMarker at(BlockPos pos) { var id = positions.get(pos); return id == null ? null : markers.get(id); }
    @Nullable public RouteEdge edge(UUID id) { return edges.get(id); }

    public List<RouteEdge> connected(UUID id) {
        return adjacency.getOrDefault(id, Set.of()).stream().map(edges::get).filter(Objects::nonNull).toList();
    }

    public List<RouteMarker> inChunk(long chunk) {
        return spatialIndex.getOrDefault(chunk, Set.of()).stream().map(markers::get).filter(Objects::nonNull).toList();
    }

    public List<RouteMarker> nearby(BlockPos pos, int radius) {
        var result = new ArrayList<RouteMarker>();
        for (int x = Math.floorDiv(pos.getX() - radius, 16); x <= Math.floorDiv(pos.getX() + radius, 16); x++) {
            for (int z = Math.floorDiv(pos.getZ() - radius, 16); z <= Math.floorDiv(pos.getZ() + radius, 16); z++) {
                for (var marker : inChunk(ChunkPos.asLong(x, z))) {
                    if (marker.pos().distSqr(pos) <= (double) radius * radius) result.add(marker);
                }
            }
        }
        return result;
    }

    public boolean put(RouteMarker marker) {
        var existing = markers.get(marker.id());
        var occupied = at(marker.pos());
        if (occupied != null && !occupied.id().equals(marker.id())) return false;
        if (existing != null && !existing.pos().equals(marker.pos())) return false;
        if (marker.equals(existing)) return false;
        markers.put(marker.id(), marker);
        for (var edge : connected(marker.id())) {
            boolean conditional = crossingEndpoints(edge.a(), edge.b());
            if (edge.conditional() != conditional) edges.put(edge.id(), new RouteEdge(edge.id(), edge.a(), edge.b(), conditional));
        }
        positions.put(marker.pos(), marker.id());
        spatialIndex.computeIfAbsent(new ChunkPos(marker.pos()).toLong(), ignored -> new LinkedHashSet<>()).add(marker.id());
        changed();
        return true;
    }

    public boolean link(UUID a, UUID b, boolean conditional) {
        var first = marker(a);
        var second = marker(b);
        if (a.equals(b) || first == null || second == null) return false;
        if (conditional && !crossingEndpoints(a, b)) return false;
        if (connected(a).stream().anyMatch(edge -> edge.other(a).equals(b))) return false;
        var edge = new RouteEdge(UUID.randomUUID(), a, b, crossingEndpoints(a, b));
        addEdgeInternal(edge);
        changed();
        return true;
    }

    public boolean removeEdge(UUID id) {
        if (!removeEdgeInternal(id)) return false;
        changed();
        return true;
    }

    public boolean removePair(UUID marker) {
        if (!removePairInternal(marker)) return false;
        changed();
        return true;
    }

    public boolean remove(UUID id) {
        var marker = markers.remove(id);
        if (marker == null) return false;
        for (var edge : connected(id)) removeEdgeInternal(edge.id());
        adjacency.remove(id);
        positions.remove(marker.pos());
        var chunk = new ChunkPos(marker.pos()).toLong();
        var members = spatialIndex.get(chunk);
        if (members != null) { members.remove(id); if (members.isEmpty()) spatialIndex.remove(chunk); }
        changed();
        return true;
    }

    private boolean removePairInternal(UUID id) {
        boolean changed = false;
        for (var edge : connected(id)) if (edge.conditional()) changed |= removeEdgeInternal(edge.id());
        return changed;
    }

    private void addEdgeInternal(RouteEdge edge) {
        edges.put(edge.id(), edge);
        adjacency.computeIfAbsent(edge.a(), ignored -> new LinkedHashSet<>()).add(edge.id());
        adjacency.computeIfAbsent(edge.b(), ignored -> new LinkedHashSet<>()).add(edge.id());
    }

    private boolean removeEdgeInternal(UUID id) {
        var edge = edges.remove(id);
        if (edge == null) return false;
        for (var endpoint : List.of(edge.a(), edge.b())) {
            var connected = adjacency.get(endpoint);
            if (connected != null) { connected.remove(id); if (connected.isEmpty()) adjacency.remove(endpoint); }
        }
        return true;
    }

    private void changed() { routeGraphVersion++; setDirty(); }

    private boolean crossingEndpoints(UUID a, UUID b) {
        return markers.get(a).type() == RouteMarkerType.CROSSING && markers.get(b).type() == RouteMarkerType.CROSSING;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("SchemaVersion", 1);
        tag.putLong("RouteGraphVersion", routeGraphVersion);
        var nodes = new ListTag();
        markers.values().forEach(marker -> nodes.add(marker.save()));
        tag.put("Markers", nodes);
        var links = new ListTag();
        edges.values().forEach(edge -> links.add(edge.save()));
        tag.put("Edges", links);
        return tag;
    }

    public static CitizenRouteSavedData load(CompoundTag tag) {
        var result = new CitizenRouteSavedData();
        boolean repaired = false;
        for (var entry : tag.getList("Markers", Tag.TAG_COMPOUND)) {
            try {
                if (!result.put(RouteMarker.load((CompoundTag) entry))) repaired = true;
            } catch (RuntimeException exception) { repaired = true; LogUtils.getLogger().warn("Ignoring invalid route marker data", exception); }
        }
        for (var entry : tag.getList("Edges", Tag.TAG_COMPOUND)) {
            try {
                var edge = RouteEdge.load((CompoundTag) entry);
                var a = result.marker(edge.a());
                var b = result.marker(edge.b());
                if (a == null || b == null || result.edges.containsKey(edge.id())
                        || result.connected(a.id()).stream().anyMatch(e -> e.other(a.id()).equals(b.id()))) {
                    repaired = true;
                    continue;
                }
                boolean conditional = result.crossingEndpoints(edge.a(), edge.b());
                repaired |= conditional != edge.conditional();
                result.addEdgeInternal(new RouteEdge(edge.id(), edge.a(), edge.b(), conditional));
            } catch (RuntimeException exception) { repaired = true; LogUtils.getLogger().warn("Ignoring invalid route edge data", exception); }
        }
        result.routeGraphVersion = Math.max(0, tag.getLong("RouteGraphVersion")) + (repaired ? 1 : 0);
        result.setDirty(repaired);
        return result;
    }
}
