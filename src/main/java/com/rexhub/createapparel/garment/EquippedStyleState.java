package com.rexhub.createapparel.garment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record EquippedStyleState(Map<String, String> styles) {
    public static final EquippedStyleState EMPTY = new EquippedStyleState(Map.of());

    public EquippedStyleState {
        styles = Collections.unmodifiableMap(new LinkedHashMap<>(styles));
    }

    public static EquippedStyleState defaultsFor(GarmentDefinition definition) {
        return EMPTY.normalize(definition);
    }

    public EquippedStyleState normalize(GarmentDefinition definition) {
        return normalize(definition, definition.defaultSlot());
    }

    public EquippedStyleState normalize(GarmentDefinition definition, GarmentSlot slot) {
        Map<String, String> normalized = new LinkedHashMap<>();
        definition.stylesFor(slot).forEach((category, allowed) -> {
            String value = styles.get(category);
            normalized.put(category, value != null && allowed.contains(value) ? value : allowed.get(0));
        });
        return new EquippedStyleState(normalized);
    }

    public EquippedStyleState with(String category, String value) {
        Map<String, String> updated = new LinkedHashMap<>(styles);
        updated.put(category, value);
        return new EquippedStyleState(updated);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        styles.forEach(tag::putString);
        return tag;
    }

    public static EquippedStyleState fromNBT(CompoundTag tag) {
        Map<String, String> styles = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            if (tag.contains(key, CompoundTag.TAG_STRING)) {
                styles.put(key, tag.getString(key));
            }
        }
        return new EquippedStyleState(styles);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(styles.size());
        styles.forEach((category, value) -> {
            buffer.writeUtf(category);
            buffer.writeUtf(value);
        });
    }

    public static EquippedStyleState read(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) {
            throw new IllegalArgumentException("Invalid equipped style count: " + size);
        }
        Map<String, String> styles = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            styles.put(buffer.readUtf(), buffer.readUtf());
        }
        return new EquippedStyleState(styles);
    }
}
