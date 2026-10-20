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
    private final Map<Long, NavigableSet<UUID>> spatialIndex = new HashMap<>();
    private final NavigableSet<Long> markerChunks = new TreeSet<>();
    private long territoryRevision = -1;
    private final Map<UUID, Set<UUID>> adjacency = new HashMap<>();
    private final Map<UUID, Set<UUID>> districtGateways = new HashMap<>();
    private final Map<UUID, List<RouteMarker>> gatewayCache = new HashMap<>();
    private final Set<UUID> conditionalEdges = new LinkedHashSet<>();
    private final Map<BlockPos, Set<UUID>> controls = new HashMap<>();
    private long routeGraphVersion;
    private final Map<Long, Long> viewRevisions = new HashMap<>();
    private long viewEpoch;

    public long viewRevision(BlockPos center, int radius) {
        long revision = viewEpoch;
        for (int x = Math.floorDiv(center.getX() - radius, 16); x <= Math.floorDiv(center.getX() + radius, 16); x++)
            for (int z = Math.floorDiv(center.getZ() - radius, 16); z <= Math.floorDiv(center.getZ() + radius, 16); z++)
                revision = Math.max(revision, viewRevisions.getOrDefault(ChunkPos.asLong(x, z), 0L));
        return revision;
    }

    private void touch(BlockPos pos) {
        if (viewRevisions.size() >= 32768) { viewRevisions.clear(); viewEpoch = routeGraphVersion + 1; }
        viewRevisions.put(new ChunkPos(pos).toLong(), routeGraphVersion + 1);
    }

    public long version() { return routeGraphVersion; }
    public Collection<RouteMarker> markers() { return Collections.unmodifiableCollection(markers.values()); }
    public Collection<RouteEdge> edges() { return Collections.unmodifiableCollection(edges.values()); }
    public List<RouteEdge> conditionalEdges() { return conditionalEdges.stream().map(edges::get).toList(); }
    @Nullable public RouteMarker marker(UUID id) { return markers.get(id); }
    @Nullable public RouteMarker at(BlockPos pos) { var id = positions.get(pos); return id == null ? null : markers.get(id); }
    @Nullable public RouteEdge edge(UUID id) { return edges.get(id); }

    public List<RouteMarker> gateways(UUID district) {
        if (!districtGateways.containsKey(district)) return List.of();
        return gatewayCache.computeIfAbsent(district, ignored -> districtGateways.get(district).stream().map(markers::get).toList());
    }

    private void unindexGateway(@Nullable RouteMarker marker) {
        if (marker == null || marker.type() != RouteMarkerType.GATEWAY || marker.districtId() == null) return;
        gatewayCache.remove(marker.districtId());
        var entries = districtGateways.get(marker.districtId());
        if (entries != null) {
            entries.remove(marker.id());
            if (entries.isEmpty()) districtGateways.remove(marker.districtId());
        }
    }

    public List<RouteEdge> connected(UUID id) {
        return adjacency.getOrDefault(id, Set.of()).stream().map(edges::get).filter(Objects::nonNull).toList();
    }

    public Iterator<RouteEdge> connections(UUID id) {
        return adjacency.getOrDefault(id, Set.of()).stream().map(edges::get).filter(Objects::nonNull).iterator();
    }

    public List<RouteMarker> inChunk(long chunk) {
        var members = spatialIndex.get(chunk); return members == null ? List.of() : members.stream().map(markers::get).toList();
    }
    public long territoryRevision() { return territoryRevision; }
    public boolean hasMarkerChunk(long chunk) { return spatialIndex.containsKey(chunk); }
    public void territoryRevision(long revision) { if (territoryRevision != revision) { territoryRevision = revision; setDirty(); } }
    @Nullable public Long nextMarkerChunk(@Nullable Long after) { return markerChunks.isEmpty() ? null : after == null ? markerChunks.first() : markerChunks.higher(after); }
    public List<RouteMarker> markersInChunkAfter(long chunk, @Nullable UUID after, int limit) {
        var members = spatialIndex.get(chunk); if (members == null) return List.of();
        return (after == null ? members : members.tailSet(after, false)).stream().limit(limit).map(markers::get).toList();
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
        touch(marker.pos());
        unindexControl(existing);
        unindexGateway(existing);
        markers.put(marker.id(), marker);
        if (marker.type() == RouteMarkerType.GATEWAY && marker.districtId() != null) {
            districtGateways.computeIfAbsent(marker.districtId(), ignored -> new LinkedHashSet<>()).add(marker.id());
            gatewayCache.remove(marker.districtId());
        }
        if (marker.condition().configured()) controls.computeIfAbsent(marker.condition().pos(), ignored -> new HashSet<>()).add(marker.id());
        for (var edge : connected(marker.id())) {
            touch(Objects.requireNonNull(markers.get(edge.other(marker.id()))).pos());
            boolean conditional = crossingEndpoints(edge.a(), edge.b());
            if (edge.conditional() != conditional) {
                edges.put(edge.id(), new RouteEdge(edge.id(), edge.a(), edge.b(), conditional));
                if (conditional) conditionalEdges.add(edge.id()); else conditionalEdges.remove(edge.id());
            }
        }
        positions.put(marker.pos(), marker.id());
        long chunk = new ChunkPos(marker.pos()).toLong();
        spatialIndex.computeIfAbsent(chunk, ignored -> new TreeSet<>()).add(marker.id()); markerChunks.add(chunk);
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
        touch(first.pos()); touch(second.pos());
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
        touch(marker.pos());
        unindexControl(marker);
        unindexGateway(marker);
        var referencing = controls.remove(marker.pos());
        if (referencing != null) for (var reference : referencing) {
            var node = markers.get(reference);
            if (node != null) { touch(node.pos()); markers.put(reference, node.withCondition(node.condition().disconnected())); }
        }
        for (var edge : connected(id)) removeEdgeInternal(edge.id());
        adjacency.remove(id);
        positions.remove(marker.pos());
        var chunk = new ChunkPos(marker.pos()).toLong();
        var members = spatialIndex.get(chunk);
        if (members != null) { members.remove(id); if (members.isEmpty()) { spatialIndex.remove(chunk); markerChunks.remove(chunk); } }
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
        if (edge.conditional()) conditionalEdges.add(edge.id());
        adjacency.computeIfAbsent(edge.a(), ignored -> new LinkedHashSet<>()).add(edge.id());
        adjacency.computeIfAbsent(edge.b(), ignored -> new LinkedHashSet<>()).add(edge.id());
    }

    private boolean removeEdgeInternal(UUID id) {
        var edge = edges.remove(id);
        if (edge == null) return false;
        var a = markers.get(edge.a()); var b = markers.get(edge.b());
        if (a != null) touch(a.pos());
        if (b != null) touch(b.pos());
        conditionalEdges.remove(id);
        for (var endpoint : List.of(edge.a(), edge.b())) {
            var connected = adjacency.get(endpoint);
            if (connected != null) { connected.remove(id); if (connected.isEmpty()) adjacency.remove(endpoint); }
        }
        return true;
    }

    private void changed() { routeGraphVersion++; setDirty(); }

    private void unindexControl(@Nullable RouteMarker marker) {
        if (marker == null || !marker.condition().configured()) return;
        var indexed = controls.get(marker.condition().pos());
        if (indexed != null) { indexed.remove(marker.id()); if (indexed.isEmpty()) controls.remove(marker.condition().pos()); }
    }

    private boolean crossingEndpoints(UUID a, UUID b) {
        return markers.get(a).type() == RouteMarkerType.CROSSING && markers.get(b).type() == RouteMarkerType.CROSSING;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("TerritoryRevision", territoryRevision);
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
        result.viewRevisions.clear(); result.viewEpoch = result.routeGraphVersion;
        result.setDirty(repaired);
        result.territoryRevision = tag.contains("TerritoryRevision", Tag.TAG_LONG) ? tag.getLong("TerritoryRevision") : -1;
        return result;
    }
}
