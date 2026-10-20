package com.rexhub.createapparel.citizen.population;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PopulationChainIndex {
    private record Entry(List<UUID> path, int hop) { }
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, Integer> incoming = new HashMap<>();
    private final Map<UUID, Integer> outgoing = new HashMap<>();
    private final Map<UUID, Integer> busy = new HashMap<>();

    public int incoming(UUID gauge) { return incoming.getOrDefault(gauge, 0); }
    public int outgoing(UUID gauge) { return outgoing.getOrDefault(gauge, 0); }
    public boolean busy(UUID gauge) { return busy.containsKey(gauge); }

    public void put(PopulationTransferChain chain) {
        remove(chain.id());
        if (chain.complete()) return;
        var entry = new Entry(chain.path(), chain.hop());
        entries.put(chain.id(), entry);
        apply(entry, 1);
    }

    public void remove(UUID chain) {
        var previous = entries.remove(chain);
        if (previous != null) apply(previous, -1);
    }

    private void apply(Entry entry, int delta) {
        for (int index = 0; index < entry.path.size(); index++) {
            var gauge = entry.path.get(index);
            adjust(busy, gauge, delta);
            if (index > entry.hop) adjust(incoming, gauge, delta);
            if (index >= entry.hop && index < entry.path.size() - 1) adjust(outgoing, gauge, delta);
        }
    }

    private static void adjust(Map<UUID, Integer> totals, UUID gauge, int delta) {
        int next = totals.getOrDefault(gauge, 0) + delta;
        if (next == 0) totals.remove(gauge); else totals.put(gauge, next);
    }
}
