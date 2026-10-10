package com.rexhub.createapparel.client.garment;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

final class GarmentSourceImageCache<K, V> {
    private final Map<K, V> images = new HashMap<>();
    private final Loader<K, V> load;
    private final Consumer<V> release;

    GarmentSourceImageCache(Loader<K, V> load, Consumer<V> release) {
        this.load = load;
        this.release = release;
    }

    V get(K key) throws IOException {
        V image = images.get(key);
        if (image == null) {
            image = load.load(key);
            images.put(key, image);
        }
        return image;
    }

    void clear() {
        images.values().forEach(release);
        images.clear();
    }

    @FunctionalInterface
    interface Loader<K, V> {
        V load(K key) throws IOException;
    }
}
