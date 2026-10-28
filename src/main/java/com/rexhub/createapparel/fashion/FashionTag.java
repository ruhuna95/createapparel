package com.rexhub.createapparel.fashion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum FashionTag {
    COOL(Category.STYLE), CUTE(Category.STYLE), SIMPLE(Category.STYLE), FLASHY(Category.STYLE),
    CASUAL(Category.STYLE), FORMAL(Category.STYLE), FEMININE(Category.TARGET), MASCULINE(Category.TARGET),
    KIDS(Category.TARGET), THIN(Category.CLIMATE), THICK(Category.CLIMATE);

    public enum Category { STYLE, TARGET, CLIMATE }
    private final Category category;
    FashionTag(Category category) { this.category = category; }
    public Category category() { return category; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Set<FashionTag> parse(JsonArray array) {
        Set<FashionTag> result = EnumSet.noneOf(FashionTag.class);
        for (JsonElement value : array) {
            String id = value.getAsString();
            FashionTag tag = java.util.Arrays.stream(values()).filter(candidate -> candidate.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown fashion tag: " + id));
            if (!result.add(tag)) throw new IllegalArgumentException("Duplicate fashion tag: " + id);
        }
        return Set.copyOf(result);
    }
}
