package com.rexhub.createapparel.client.garment;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class RenderResourceCache<K, V> {
    private final Map<K, Entry<V>> entries = new HashMap<>();
    private final int capacity;
    private final Consumer<V> release;
    enum Event { HIT, MISS, EVICTION, RELEASE }
    private final Consumer<Event> observer;
    private long clock;
    private long frame;

    RenderResourceCache(int capacity, Consumer<V> release) {
        this(capacity, release, null);
    }

    RenderResourceCache(int capacity, Consumer<V> release, Consumer<Event> observer) {
        if (capacity < 1) throw new IllegalArgumentException("Invalid cache capacity");
        this.capacity = capacity;
        this.release = release;
        this.observer = observer;
    }

    Reference reference(K key, Supplier<V> load) {
        return new Reference(key, load);
    }

    V get(K key, Supplier<V> load) {
        Entry<V> entry = find(key, load);
        entry.lastUse = ++clock;
        entry.lastFrame = frame;
        return entry.value;
    }

    private Entry<V> find(K key, Supplier<V> load) {
        Entry<V> entry = entries.get(key);
        if (observer != null) observer.accept(entry == null ? Event.MISS : Event.HIT);
        if (entry == null) { entry = new Entry<>(load.get()); entries.put(key, entry); }
        return entry;
    }

    void endFrame() {
        long completedFrame = frame++;
        int excess = entries.size() - capacity;
        if (excess <= 0) return;
        var oldest = entries.entrySet().stream()
                .filter(entry -> entry.getValue().lastFrame < completedFrame)
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastUse)).limit(excess).toList();
        for (var entry : oldest) {
            entries.remove(entry.getKey());
            entry.getValue().valid = false;
            release.accept(entry.getValue().value);
            if (observer != null) observer.accept(Event.EVICTION);
        }
    }

    void clear() {
        for (var entry : entries.values()) {
            entry.valid = false;
            release.accept(entry.value);
            if (observer != null) observer.accept(Event.RELEASE);
        }
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    final class Reference {
        private final K key;
        private final Supplier<V> load;
        private Entry<V> entry;

        private Reference(K key, Supplier<V> load) {
            this.key = key;
            this.load = load;
        }

        V get() {
            if (entry == null || !entry.valid) {
                entry = find(key, load);
            } else if (observer != null) observer.accept(Event.HIT);
            entry.lastUse = ++clock;
            entry.lastFrame = frame;
            return entry.value;
        }
    }

    private static final class Entry<V> {
        private final V value;
        private long lastUse;
        private long lastFrame;
        private boolean valid = true;

        private Entry(V value) {
            this.value = value;
        }
    }
}
