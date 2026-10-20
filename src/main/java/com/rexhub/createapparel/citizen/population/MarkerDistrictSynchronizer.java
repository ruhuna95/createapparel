package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.citizen.route.CitizenRouteSavedData;
import com.rexhub.createapparel.citizen.route.RouteMarker;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.LinkedHashSet;

public final class MarkerDistrictSynchronizer {
    public static final int MARKER_BUDGET = 64;
    public static final int CHUNK_BUDGET = 16;
    public static final int GEOMETRY_CHUNK_BUDGET = 256;
    private final DistrictTerritory territory;
    private final CitizenRouteSavedData routes;
    private final LinkedHashMap<Long, UUID> pending = new LinkedHashMap<>();
    private final LinkedHashSet<DistrictTerritory.Bounds> pendingBounds = new LinkedHashSet<>();
    @Nullable private DistrictTerritory.Bounds scanningBounds;
    private int nextX, nextZ;
    private int lastGeometryChunks;
    public int lastGeometryChunks() { return lastGeometryChunks; }
    private boolean fullScan;
    @Nullable private Long fullCursor;
    private long observedRevision;

    public MarkerDistrictSynchronizer(DistrictTerritory territory, CitizenRouteSavedData routes) {
        this.territory = territory; this.routes = routes; observedRevision = territory.revision();
        fullScan = !routes.markers().isEmpty() && routes.territoryRevision() != observedRevision;
    }
    public boolean pending() { return fullScan || !pending.isEmpty() || scanningBounds != null || !pendingBounds.isEmpty() || observedRevision != territory.revision(); }
    public void queue(long chunk) { if (routes.hasMarkerChunk(chunk) && !pending.containsKey(chunk)) pending.put(chunk, null); }
    public RouteMarker refresh(RouteMarker marker) {
        var district = territory.districtAt(marker.pos());
        if (Objects.equals(marker.districtId(), district)) return marker;
        var updated = marker.withDistrict(district); routes.put(updated); return updated;
    }
    public int tick() {
        try (var diagnosticScope = CitizenSystemMetrics.measure("markerTerritorySync")) {
            lastGeometryChunks = 0;
            var changes = territory.takeChanges();
            if (observedRevision != territory.revision() && changes.isEmpty()) { fullScan = true; fullCursor = null; }
            pendingBounds.addAll(changes);
            for (int visited = 0; visited < GEOMETRY_CHUNK_BUDGET; visited++) {
                if (scanningBounds == null) {
                    if (pendingBounds.isEmpty()) break;
                    var iterator = pendingBounds.iterator(); scanningBounds = iterator.next(); iterator.remove();
                    nextX = scanningBounds.minX() >> 4; nextZ = scanningBounds.minZ() >> 4;
                }
                long chunk = ChunkPos.asLong(nextX, nextZ);
                lastGeometryChunks++;
                if (routes.hasMarkerChunk(chunk)) pending.put(chunk, null);
                if (++nextZ > scanningBounds.maxZ() >> 4) { nextZ = scanningBounds.minZ() >> 4; nextX++; }
                if (nextX > scanningBounds.maxX() >> 4) scanningBounds = null;
            }
            observedRevision = territory.revision();
            int processed = 0;
            for (int chunks = 0; chunks < CHUNK_BUDGET && processed < MARKER_BUDGET; chunks++) {
                if (pending.isEmpty() && fullScan) {
                    var next = routes.nextMarkerChunk(fullCursor);
                    if (next == null) { fullScan = false; break; }
                    fullCursor = next; queue(next);
                }
                if (pending.isEmpty()) break;
                long chunk = pending.keySet().iterator().next();
                int remaining = MARKER_BUDGET - processed;
                var markers = routes.markersInChunkAfter(chunk, pending.get(chunk), remaining);
                for (var marker : markers) { refresh(marker); processed++; }
                if (markers.size() < remaining) pending.remove(chunk);
                else pending.put(chunk, markers.get(markers.size() - 1).id());
            }
            if (!fullScan && pending.isEmpty() && scanningBounds == null && pendingBounds.isEmpty()) routes.territoryRevision(observedRevision);
            return processed;
        }
    }
}
