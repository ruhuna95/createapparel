package com.rexhub.createapparel.citizen.population;

import javax.annotation.Nullable;
import java.util.*;

public final class PopulationNetworkIndex {
    public static final class Network {
        private final Set<UUID> members = new HashSet<>();
        private final Set<UUID> districts = new HashSet<>();
        private final LinkedHashSet<UUID> shortages = new LinkedHashSet<>();
        private final LinkedHashSet<UUID> surpluses = new LinkedHashSet<>();
        private boolean complete, outflow;
        public boolean saturated() { return complete && !districts.isEmpty() && shortages.isEmpty(); }
        public boolean hasOutflow() { return outflow; }
        public boolean hasSurplus() { return !surpluses.isEmpty(); }
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
    private final ArrayDeque<UUID> roots = new ArrayDeque<>(), frontier = new ArrayDeque<>();
    private final Set<UUID> requested = new HashSet<>();
    private long version = -1;
    @Nullable private Network building;

    public PopulationNetworkIndex(CitizenPopulationSavedData data) { this.data = data; }
    private void validate() {
        if (version == data.version()) return;
        version = data.version(); networks.clear(); roots.clear(); requested.clear(); frontier.clear(); building = null;
    }
    @Nullable public Network cached(UUID gauge) {
        validate(); var network = networks.get(gauge);
        return network != null && network.complete ? network : null;
    }
    @Nullable public Network request(UUID gauge) {
        validate();
        if (!networks.containsKey(gauge) && data.gauge(gauge) != null && requested.add(gauge)) roots.addLast(gauge);
        return cached(gauge);
    }
    public void tick(int budget) {
        validate();
        while (budget > 0) {
            if (building == null) {
                if (roots.isEmpty()) return;
                var root = roots.removeFirst(); requested.remove(root);
                if (networks.containsKey(root)) continue;
                building = new Network(); building.members.add(root); networks.put(root, building); frontier.add(root);
            }
            var id = frontier.removeFirst(); budget--;
            var gauge = data.gauge(id);
            if (gauge != null) {
                if (gauge.outflow()) building.outflow = true;
                if (!gauge.source()) { building.districts.add(id); refresh(building, gauge); }
                for (var linked : data.linked(id)) if (building.members.add(linked)) {
                    networks.put(linked, building); frontier.addLast(linked);
                }
            }
            if (frontier.isEmpty()) { building.complete = true; building = null; }
        }
    }
    public void populationChanged(@Nullable UUID district) {
        validate(); var gauge = data.district(district);
        if (gauge == null) return;
        var network = networks.get(gauge.id());
        if (network != null && network.districts.contains(gauge.id())) refresh(network, gauge);
    }
    private void refresh(Network network, CitizenGauge gauge) {
        int population = data.population(gauge.districtId());
        if (population < gauge.target()) network.shortages.add(gauge.id()); else network.shortages.remove(gauge.id());
        if (population > gauge.target()) network.surpluses.add(gauge.id()); else network.surpluses.remove(gauge.id());
    }
}
