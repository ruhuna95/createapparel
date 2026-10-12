package com.rexhub.createapparel.citizen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Function;
import net.minecraft.util.RandomSource;

public final class CitizenPresetJson {
    private CitizenPresetJson() { }

    public static JsonElement read(Reader source) throws IOException {
        JsonReader reader = new JsonReader(source);
        reader.setLenient(false);
        JsonElement result = readValue(reader, 0);
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON content");
        return result;
    }

    private static JsonElement readValue(JsonReader reader, int depth) throws IOException {
        if (depth > 32) throw new IllegalArgumentException("Preset JSON nesting exceeds 32");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) throw new IllegalArgumentException("Duplicate JSON key: " + key);
                    object.add(key, readValue(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readValue(reader, depth + 1));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Unexpected JSON token: " + reader.peek());
        };
    }

    static JsonObject object(JsonElement value, String field) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object");
        return value.getAsJsonObject();
    }

    static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) throw new IllegalArgumentException(field + " must be a nonempty string");
        return value.getAsString();
    }

    static <T> List<T> options(JsonObject object, String field, Function<String, T> parser) {
        JsonElement value = object.get(field);
        List<T> values = new ArrayList<>();
        if (value != null && value.isJsonArray()) {
            if (value.getAsJsonArray().size() > 256) throw new IllegalArgumentException(field + " has too many options");
            for (JsonElement entry : value.getAsJsonArray()) values.add(parser.apply(optionString(entry, field)));
        } else {
            values.add(parser.apply(optionString(value, field)));
        }
        return immutableOptions(values, field);
    }

    static boolean optionalBoolean(JsonObject object, String field) {
        if (!object.has(field)) return false;
        JsonElement value = object.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static String optionString(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a string or a nonempty array of strings");
        }
        return value.getAsString();
    }

    static <T> List<T> immutableOptions(List<T> values, String field) {
        if (values.isEmpty() || values.size() > 256 || new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(field + " options must be nonempty, unique and at most 256");
        }
        return List.copyOf(values);
    }

    static <T> T choose(List<T> values, RandomSource random) {
        return values.get(values.size() == 1 ? 0 : random.nextInt(values.size()));
    }

    static void keys(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown field: " + key);
        }
    }

    static ResourceLocation id(String value) {
        ResourceLocation id = value.isBlank() || !value.contains(":") ? null : ResourceLocation.tryParse(value);
        if (id == null || id.getPath().isBlank()) throw new IllegalArgumentException("Invalid namespaced ID: " + value);
        return id;
    }
}
