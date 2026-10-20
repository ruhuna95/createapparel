package com.rexhub.createapparel.citizen.population;

import javax.annotation.Nullable;
import java.util.*;

public final class PopulationNetworkIndex {
    public static final class Network {
        private Set<UUID> members = new HashSet<>();
        private Set<UUID> districts = new HashSet<>();
        private LinkedHashSet<UUID> shortages = new LinkedHashSet<>();
        private LinkedHashSet<UUID> surpluses = new LinkedHashSet<>();
        private boolean complete, outflow, invalid, unresolved;
        public boolean saturated() { return complete && !invalid && !unresolved && !districts.isEmpty() && shortages.isEmpty(); }
        public boolean hasOutflow() { return !invalid && outflow; }
        public boolean hasSurplus() { return !invalid && !surpluses.isEmpty(); }
        public boolean contains(UUID gauge) { return members.contains(gauge); }
        public int surplusCount() { return surpluses.size(); }
        public int shortageCount() { return shortages.size(); }
        @Nullable public UUID nextSurplus() { return rotate(surpluses); }
        @Nullable public UUID nextShortage() { return rotate(shortages); }
        @Nullable private static UUID rotate(LinkedHashSet<UUID> entries) {
            if (entries.isEmpty()) return null;
            var iterator = entries.iterator(); var id = iterator.next(); iterator.remove(); entries.add(id); return id;
        }
    }
    private final CitizenPopulationSavedData data;
    private final Map<UUID, Network> networks = new HashMap<>();
    private final LinkedHashSet<UUID> roots = new LinkedHashSet<>();
    private final ArrayDeque<UUID> frontier = new ArrayDeque<>();
    private final Set<Network> components = Collections.newSetFromMap(new IdentityHashMap<>());
    private int lastVisited;
    @Nullable private Network building;

    public PopulationNetworkIndex(CitizenPopulationSavedData data) { this.data = data; }
    public void invalidate(UUID gauge) {
        var network = networks.get(gauge);
        if (network != null && !network.invalid) {
            network.invalid = true; components.remove(network);
            network.members = Set.of(); network.districts = Set.of();
            network.shortages = new LinkedHashSet<>(); network.surpluses = new LinkedHashSet<>();
        }
        if (building != null && building.invalid) { frontier.clear(); building = null; }
    }
    public void removed(UUID gauge) { invalidate(gauge); networks.remove(gauge); }
    public int cacheCount() { return components.size(); }
    public int lastVisited() { return lastVisited; }
    @Nullable public Network cached(UUID gauge) {
        var network = networks.get(gauge);
        return network != null && network.complete && !network.invalid ? network : null;
    }
    @Nullable public Network request(UUID gauge) {
        var network = networks.get(gauge);
        if ((network == null || network.invalid) && data.gauge(gauge) != null) roots.add(gauge);
        return cached(gauge);
    }
    public void tick(int budget) {
        try (var diagnosticScope = CitizenSystemMetrics.measure("networkRebuild")) {
            lastVisited = 0;
            while (budget > 0) {
                if (building == null) {
                    if (roots.isEmpty()) return;
                    var iterator = roots.iterator(); var root = iterator.next(); iterator.remove();
                    if (data.gauge(root) == null || networks.containsKey(root) && !networks.get(root).invalid) { budget--; continue; }
                    building = new Network(); components.add(building); building.members.add(root); networks.put(root, building); frontier.add(root);
                }
                var id = frontier.removeFirst(); budget--; lastVisited++;
                var gauge = data.gauge(id);
                if (gauge != null) {
                    if (gauge.outflow()) building.outflow = true;
                    if (!gauge.source()) { building.districts.add(id); refresh(building, gauge); }
                    for (var linked : data.linked(id)) if (building.members.add(linked)) {
                        roots.remove(linked);
                        networks.put(linked, building); frontier.addLast(linked);
                    }
                } else building.unresolved = true;
                if (frontier.isEmpty()) { building.complete = true; building = null; }
            }
        }
    }
    public void populationChanged(@Nullable UUID district) {
        var gauge = data.district(district);
        if (gauge == null) return;
        var network = networks.get(gauge.id());
        if (network != null && !network.invalid && network.districts.contains(gauge.id())) refresh(network, gauge);
    }
    private void refresh(Network network, CitizenGauge gauge) {
        int population = data.population(gauge.districtId());
        if (population < gauge.target()) network.shortages.add(gauge.id()); else network.shortages.remove(gauge.id());
        if (population > gauge.target()) network.surpluses.add(gauge.id()); else network.surpluses.remove(gauge.id());
    }
}
