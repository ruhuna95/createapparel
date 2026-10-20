package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.citizen.route.CitizenRouteSavedData;
import com.rexhub.createapparel.citizen.route.RouteMarker;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

public final class MarkerDistrictSynchronizer {
    public static final int MARKER_BUDGET = 64;
    public static final int CHUNK_BUDGET = 16;
    private final DistrictTerritory territory;
    private final CitizenRouteSavedData routes;
    private final LinkedHashMap<Long, UUID> pending = new LinkedHashMap<>();
    private boolean fullScan;
    @Nullable private Long fullCursor;
    private long observedRevision;

    public MarkerDistrictSynchronizer(DistrictTerritory territory, CitizenRouteSavedData routes) {
        this.territory = territory; this.routes = routes; observedRevision = territory.revision();
        fullScan = routes.territoryRevision() != observedRevision;
    }
    public boolean pending() { return fullScan || !pending.isEmpty() || observedRevision != territory.revision(); }
    public void queue(long chunk) { if (routes.hasMarkerChunk(chunk) && !pending.containsKey(chunk)) pending.put(chunk, null); }
    public RouteMarker refresh(RouteMarker marker) {
        var district = territory.districtAt(marker.pos());
        if (Objects.equals(marker.districtId(), district)) return marker;
        var updated = marker.withDistrict(district); routes.put(updated); return updated;
    }
    public int tick() {
        var changes = territory.takeChanges();
        if (observedRevision != territory.revision() && changes.isEmpty()) { fullScan = true; fullCursor = null; }
        for (var bounds : changes)
            for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
                for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                    long chunk = ChunkPos.asLong(x, z);
                    if (routes.hasMarkerChunk(chunk)) pending.put(chunk, null);
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
        if (!fullScan && pending.isEmpty()) routes.territoryRevision(observedRevision);
        return processed;
    }
}
