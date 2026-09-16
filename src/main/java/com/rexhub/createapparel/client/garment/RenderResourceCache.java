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
    private long clock;
    private long frame;

    RenderResourceCache(int capacity, Consumer<V> release) {
        if (capacity < 1) throw new IllegalArgumentException("Invalid cache capacity");
        this.capacity = capacity;
        this.release = release;
    }

    Reference reference(K key, Supplier<V> load) {
        return new Reference(key, load);
    }

    V get(K key, Supplier<V> load) {
        Entry<V> entry = entries.computeIfAbsent(key, ignored -> new Entry<>(load.get()));
        entry.lastUse = ++clock;
        entry.lastFrame = frame;
        return entry.value;
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
        }
    }

    void clear() {
        for (var entry : entries.values()) {
            entry.valid = false;
            release.accept(entry.value);
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
                entry = entries.computeIfAbsent(key, ignored -> new Entry<>(load.get()));
            }
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
