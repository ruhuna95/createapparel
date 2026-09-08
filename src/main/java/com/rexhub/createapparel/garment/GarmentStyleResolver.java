package com.rexhub.createapparel.garment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GarmentStyleResolver {
    private GarmentStyleResolver() {
    }

    public static Map<String, List<String>> allowedStyles(GarmentDefinition definition, GarmentSlot slot, ApparelContext context) {
        Map<String, List<String>> allowed = new LinkedHashMap<>(definition.styles());
        List<String> sleeves = allowed.getOrDefault("sleeves", List.of());
        if (slot == GarmentSlot.TOP && context.has(GarmentSlot.OUTER)
                && sleeves.contains("normal") && sleeves.contains("rolled")) {
            allowed.put("sleeves", sleeves.stream().filter(value -> !value.equals("rolled")).toList());
        }
        return Collections.unmodifiableMap(allowed);
    }

    public static EquippedStyleState normalize(GarmentDefinition definition, GarmentSlot slot,
                                               EquippedStyleState current, ApparelContext context) {
        Map<String, String> styles = new LinkedHashMap<>();
        allowedStyles(definition, slot, context).forEach((category, values) -> {
            String value = current.styles().get(category);
            styles.put(category, value != null && values.contains(value) ? value : values.get(0));
        });
        return new EquippedStyleState(styles);
    }
}
