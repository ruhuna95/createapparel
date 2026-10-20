package com.rexhub.createapparel.citizen.population;

import javax.annotation.Nullable;
import java.util.*;

public final class PopulationRequestQueue {
    private record Entry(PopulationRequest request, long retryAt, long attemptedAt, double ratio, long generation) { }
    private static final Comparator<Entry> READY = Comparator.comparingLong(Entry::attemptedAt)
            .thenComparingLong(entry -> entry.request.createdAt()).thenComparingDouble(Entry::ratio).thenComparingLong(Entry::generation);
    private static final Comparator<Entry> DELAYED = Comparator.comparingLong(Entry::retryAt).thenComparing(READY);
    private final Map<UUID, Entry> current = new HashMap<>();
    private final LinkedHashSet<UUID> dirty = new LinkedHashSet<>();
    private PriorityQueue<Entry> ready = new PriorityQueue<>(READY), delayed = new PriorityQueue<>(DELAYED);
    private PriorityQueue<Entry> oldReady = new PriorityQueue<>(READY), oldDelayed = new PriorityQueue<>(DELAYED);
    private long generation;
    private long now = Long.MIN_VALUE;
    private int insertions;

    public void put(PopulationRequest request, double ratio) {
        var entry = new Entry(request, request.retryAt(), request.lastAttemptAt(), ratio, generation++);
        current.put(request.requester(), entry); dirty.add(request.requester());
        if (insertions > 0) enqueue(entry);
    }
    private void enqueue(Entry entry) {
        dirty.remove(entry.request.requester()); insertions--;
        (entry.retryAt <= now ? ready : delayed).add(entry);
    }
    public void remove(UUID requester) { current.remove(requester); dirty.remove(requester); }
    public int size() { return current.size(); }
    public int entryCount() { return ready.size() + delayed.size() + oldReady.size() + oldDelayed.size(); }
    private boolean valid(Entry entry) { return current.get(entry.request.requester()) == entry; }

    public void tick(long now) {
        this.now = now;
        insertions = 64;
        while (insertions > 0 && !dirty.isEmpty()) {
            var id = dirty.iterator().next(); var entry = current.get(id);
            if (entry == null) dirty.remove(id); else enqueue(entry);
        }
        if (oldReady.isEmpty() && oldDelayed.isEmpty() && entryCount() > (current.isEmpty() ? 0 : current.size() * 3 + 64)) {
            oldReady = ready; oldDelayed = delayed;
            ready = new PriorityQueue<>(READY); delayed = new PriorityQueue<>(DELAYED);
        }
        for (int i = 0; i < 128 && (!oldReady.isEmpty() || !oldDelayed.isEmpty()); i++) {
            boolean wasReady = !oldReady.isEmpty();
            var entry = (wasReady ? oldReady : oldDelayed).remove();
            if (valid(entry)) (wasReady ? ready : delayed).add(entry);
        }
        for (int i = 0; i < 64; i++) {
            var queue = earlier(delayed, oldDelayed, DELAYED);
            var entry = queue.peek();
            if (entry == null || entry.retryAt > now) break;
            queue.remove(); if (valid(entry)) ready.add(entry);
        }
    }
    @Nullable public PopulationRequest poll() {
        for (int i = 0; i < 64; i++) {
            var queue = earlier(ready, oldReady, READY);
            var entry = queue.poll(); if (entry == null) return null;
            if (valid(entry)) { remove(entry.request.requester()); return entry.request; }
        }
        return null;
    }
    private static PriorityQueue<Entry> earlier(PriorityQueue<Entry> a, PriorityQueue<Entry> b, Comparator<Entry> order) {
        return a.isEmpty() || !b.isEmpty() && order.compare(b.peek(), a.peek()) < 0 ? b : a;
    }
}
