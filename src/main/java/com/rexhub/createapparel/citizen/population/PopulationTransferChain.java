package com.rexhub.createapparel.citizen.population;

import javax.annotation.Nullable;
import java.util.*;

public final class PopulationTransferChain {
    public enum State { WAITING, MOVING }
    private final UUID id = UUID.randomUUID();
    private final UUID requestId;
    private final List<UUID> path;
    private final long createdAt;
    private long lastProgressAt;
    private int hop;
    @Nullable private UUID citizen;
    private State state = State.WAITING;
    public PopulationTransferChain(UUID request, List<UUID> path, long time) {
        if (path.size() < 2 || new HashSet<>(path).size() != path.size()) throw new IllegalArgumentException("Invalid supply path");
        requestId = request; this.path = List.copyOf(path); createdAt = lastProgressAt = time;
    }
    public UUID id() { return id; }
    public UUID requestId() { return requestId; }
    public List<UUID> path() { return path; }
    public long createdAt() { return createdAt; }
    public long lastProgressAt() { return lastProgressAt; }
    public int hop() { return hop; }
    public boolean complete() { return hop >= path.size() - 1; }
    public UUID from() { return path.get(hop); }
    public UUID to() { return path.get(hop + 1); }
    @Nullable public UUID citizen() { return citizen; }
    public State state() { return state; }
    public void reserve(UUID citizen, long time) { this.citizen = citizen; lastProgressAt = time; }
    public void started(long time) { state = State.MOVING; lastProgressAt = time; }
    public void advance(long time) { hop++; citizen = null; state = State.WAITING; lastProgressAt = time; }
    public int incoming(UUID gauge) { return path.subList(hop + 1, path.size()).contains(gauge) ? 1 : 0; }
    public int outgoing(UUID gauge) { return path.subList(hop, path.size() - 1).contains(gauge) ? 1 : 0; }
}
