package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.citizen.route.CitizenRouteSavedData;
import com.rexhub.createapparel.citizen.route.RouteEdge;
import com.rexhub.createapparel.citizen.route.RouteMarker;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

public final class DistrictRouteCapacity {
    public static final double METERS_PER_WALKER = 3;
    public static final int EDGE_BUDGET = 64;
    private final CitizenRouteSavedData routes;
    private final DistrictTerritory territory;
    private final Predicate<RouteMarker> loaded;
    private final LongSupplier currentLoadRevision;
    private final Map<UUID, Double> lengths = new HashMap<>();
    private final Set<UUID> incompleteDistricts = new HashSet<>();
    private Iterator<RouteEdge> remaining = Collections.emptyIterator();
    private long graphRevision = -1, territoryRevision = -1, loadRevision = -1;
    private boolean ready;

    public DistrictRouteCapacity(CitizenRouteSavedData routes, DistrictTerritory territory, Predicate<RouteMarker> loaded, LongSupplier currentLoadRevision) {
        this.routes = routes; this.territory = territory; this.loaded = loaded; this.currentLoadRevision = currentLoadRevision;
    }
    public boolean ready() {
        return ready && graphRevision == routes.version() && territoryRevision == territory.revision()
                && loadRevision == currentLoadRevision.getAsLong();
    }
    public double length(UUID district) { return ready() ? lengths.getOrDefault(district, 0D) : 0; }
    public int limit(UUID district) { return (int) Math.ceil(Math.max(0, length(district) - 1.0E-7) / METERS_PER_WALKER); }
    public boolean hasSpace(UUID district, int walkers) { return ready() && walkers < limit(district); }
    public boolean overCapacity(UUID district, int walkers) { return ready() && !incompleteDistricts.contains(district) && walkers > limit(district); }

    public int tick() {
        long loadedRevision = currentLoadRevision.getAsLong();
        if (graphRevision != routes.version() || territoryRevision != territory.revision() || loadRevision != loadedRevision) {
            graphRevision = routes.version(); territoryRevision = territory.revision(); loadRevision = loadedRevision;
            remaining = routes.edges().iterator(); lengths.clear(); incompleteDistricts.clear(); ready = false;
        }
        if (ready) return 0;
        int processed = 0;
        while (remaining.hasNext() && processed < EDGE_BUDGET) {
            var edge = remaining.next(); processed++;
            var a = routes.marker(edge.a()); var b = routes.marker(edge.b());
            if (a == null || b == null) continue;
            boolean endpointsLoaded = loaded.test(a) && loaded.test(b);
            var district = territory.districtAt(a.pos());
            if (district == null || !district.equals(territory.districtAt(b.pos()))) continue;
            if (!endpointsLoaded) { incompleteDistricts.add(district); continue; }
            lengths.merge(district, a.center().distanceTo(b.center()), Double::sum);
        }
        ready = !remaining.hasNext();
        return processed;
    }
}
