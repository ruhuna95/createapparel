package com.rexhub.createapparel.garment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record GarmentDefinition(
        ResourceLocation id,
        GarmentSlot defaultSlot,
        Set<GarmentSlot> allowedSlots,
        ResourceLocation modelTemplate,
        String modelVariant,
        Map<GarmentSlot, String> slotModelVariants,
        String textureVariant,
        Set<String> designs,
        Map<String, List<String>> styles,
        Map<GarmentSlot, Map<String, List<String>>> slotStyles
) {
    private static final int MAX_COLLECTION_SIZE = 256;

    public GarmentDefinition(ResourceLocation id, GarmentSlot slot, ResourceLocation modelTemplate,
                             String modelVariant, String textureVariant, Set<String> designs,
                             Map<String, List<String>> styles) {
        this(id, slot, Set.of(slot), modelTemplate, modelVariant, Map.of(), textureVariant, designs, styles, Map.of());
    }

    public String modelVariantFor(GarmentSlot slot) {
        return slotModelVariants.getOrDefault(slot, modelVariant);
    }

    public Map<String, List<String>> stylesFor(GarmentSlot slot) {
        return slotStyles.getOrDefault(slot, styles);
    }

    public GarmentDefinition {
        allowedSlots = Set.copyOf(allowedSlots);
        slotModelVariants = Map.copyOf(slotModelVariants);
        if (!allowedSlots.contains(defaultSlot) || !allowedSlots.containsAll(slotModelVariants.keySet())
                || slotModelVariants.values().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Invalid garment slots or slot variants: " + id);
        }
        designs = Collections.unmodifiableSet(new LinkedHashSet<>(designs));
        LinkedHashMap<String, List<String>> immutableStyles = new LinkedHashMap<>();
        styles.forEach((key, values) -> immutableStyles.put(key, List.copyOf(values)));
        styles = Collections.unmodifiableMap(immutableStyles);
        if (modelVariant.isBlank() || textureVariant.isBlank()) {
            throw new IllegalArgumentException("Garment model and texture variants must not be blank: " + id);
        }
        if (designs.isEmpty()) {
            throw new IllegalArgumentException("Garment designs must not be empty: " + id);
        }
        styles.forEach((key, values) -> {
            if (key.isBlank() || values.isEmpty() || values.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("Garment style options must not be empty: " + id + " / " + key);
            }
        });
        Map<GarmentSlot, Map<String, List<String>>> immutableSlotStyles = new LinkedHashMap<>();
        Set<GarmentSlot> supportedSlots = allowedSlots;
        slotStyles.forEach((slot, categories) -> {
            if (!supportedSlots.contains(slot)) {
                throw new IllegalArgumentException("Style override for unsupported slot: " + id + " / " + slot);
            }
            Map<String, List<String>> immutableCategories = new LinkedHashMap<>();
            categories.forEach((key, values) -> {
                if (key.isBlank() || values.isEmpty() || values.stream().anyMatch(String::isBlank)
                        || new LinkedHashSet<>(values).size() != values.size()) {
                    throw new IllegalArgumentException("Invalid slot styles: " + id + " / " + slot + " / " + key);
                }
                immutableCategories.put(key, List.copyOf(values));
            });
            immutableSlotStyles.put(slot, Collections.unmodifiableMap(immutableCategories));
        });
        slotStyles = Collections.unmodifiableMap(immutableSlotStyles);
    }

    public static GarmentDefinition fromJson(ResourceLocation id, JsonObject json) {
        boolean legacy = json.has("slot");
        if (legacy && (json.has("default_slot") || json.has("allowed_slots"))) {
            throw new IllegalArgumentException("Cannot mix slot with default_slot/allowed_slots: " + id);
        }
        GarmentSlot slot = GarmentSlot.fromSerializedName(GsonHelper.getAsString(json, legacy ? "slot" : "default_slot"));
        Set<GarmentSlot> allowedSlots = new LinkedHashSet<>();
        if (legacy) {
            allowedSlots.add(slot);
        } else {
            for (JsonElement value : GsonHelper.getAsJsonArray(json, "allowed_slots")) {
                if (!allowedSlots.add(GarmentSlot.fromSerializedName(GsonHelper.convertToString(value, "allowed_slots")))) {
                    throw new IllegalArgumentException("Duplicate allowed slot: " + id);
                }
            }
        }
        Map<GarmentSlot, String> slotModelVariants = new LinkedHashMap<>();
        GsonHelper.getAsJsonObject(json, "slot_model_variants", new JsonObject()).entrySet().forEach(entry ->
                slotModelVariants.put(GarmentSlot.fromSerializedName(entry.getKey()),
                        GsonHelper.convertToString(entry.getValue(), entry.getKey())));
        ResourceLocation modelTemplate = parseResourceLocation(
                GsonHelper.getAsString(json, "model_template"),
                id + " model_template"
        );
        String modelVariant = GsonHelper.getAsString(json, "model_variant");
        String textureVariant = GsonHelper.getAsString(json, "texture_variant");

        LinkedHashSet<String> designs = new LinkedHashSet<>();
        JsonArray designArray = GsonHelper.getAsJsonArray(json, "designs");
        for (JsonElement element : designArray) {
            String design = GsonHelper.convertToString(element, "design");
            if (design.isBlank() || !designs.add(design)) {
                throw new IllegalArgumentException("Invalid or duplicate garment design: " + id + " / " + design);
            }
        }

        LinkedHashMap<String, List<String>> styles = new LinkedHashMap<>();
        JsonObject styleObject = GsonHelper.getAsJsonObject(json, "styles", new JsonObject());
        for (Map.Entry<String, JsonElement> entry : styleObject.entrySet()) {
            JsonArray valuesJson = GsonHelper.convertToJsonArray(entry.getValue(), entry.getKey());
            List<String> values = new ArrayList<>();
            for (JsonElement value : valuesJson) {
                String style = GsonHelper.convertToString(value, entry.getKey());
                if (style.isBlank() || values.contains(style)) {
                    throw new IllegalArgumentException(
                            "Invalid or duplicate garment style: " + id + " / " + entry.getKey() + "=" + style
                    );
                }
                values.add(style);
            }
            styles.put(entry.getKey(), values);
        }
        Map<GarmentSlot, Map<String, List<String>>> slotStyles = new LinkedHashMap<>();
        GsonHelper.getAsJsonObject(json, "slot_styles", new JsonObject()).entrySet().forEach(entry -> {
            Map<String, List<String>> categories = new LinkedHashMap<>();
            GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey()).entrySet().forEach(category -> {
                List<String> values = new ArrayList<>();
                for (JsonElement value : GsonHelper.convertToJsonArray(category.getValue(), category.getKey())) {
                    values.add(GsonHelper.convertToString(value, category.getKey()));
                }
                categories.put(category.getKey(), values);
            });
            slotStyles.put(GarmentSlot.fromSerializedName(entry.getKey()), categories);
        });
        return new GarmentDefinition(id, slot, allowedSlots, modelTemplate, modelVariant, slotModelVariants, textureVariant, designs, styles, slotStyles);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(id);
        buffer.writeEnum(defaultSlot);
        buffer.writeCollection(allowedSlots, FriendlyByteBuf::writeEnum);
        buffer.writeVarInt(slotModelVariants.size());
        slotModelVariants.forEach((slot, variant) -> {
            buffer.writeEnum(slot);
            buffer.writeUtf(variant);
        });
        buffer.writeResourceLocation(modelTemplate);
        buffer.writeUtf(modelVariant);
        buffer.writeUtf(textureVariant);
        buffer.writeCollection(designs, FriendlyByteBuf::writeUtf);
        buffer.writeVarInt(styles.size());
        styles.forEach((key, values) -> {
            buffer.writeUtf(key);
            buffer.writeCollection(values, FriendlyByteBuf::writeUtf);
        });
        buffer.writeVarInt(slotStyles.size());
        slotStyles.forEach((slot, categories) -> {
            buffer.writeEnum(slot);
            buffer.writeVarInt(categories.size());
            categories.forEach((key, values) -> {
                buffer.writeUtf(key);
                buffer.writeCollection(values, FriendlyByteBuf::writeUtf);
            });
        });
    }

    public static GarmentDefinition read(FriendlyByteBuf buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        GarmentSlot slot = buffer.readEnum(GarmentSlot.class);
        int allowedCount = buffer.readVarInt();
        if (allowedCount < 1 || allowedCount > GarmentSlot.values().length) {
            throw new IllegalArgumentException("Invalid allowed slot count: " + allowedCount);
        }
        Set<GarmentSlot> allowedSlots = new LinkedHashSet<>();
        for (int i = 0; i < allowedCount; i++) {
            if (!allowedSlots.add(buffer.readEnum(GarmentSlot.class))) {
                throw new IllegalArgumentException("Duplicate allowed slot");
            }
        }
        int variantCount = buffer.readVarInt();
        if (variantCount < 0 || variantCount > GarmentSlot.values().length) {
            throw new IllegalArgumentException("Invalid slot variant count: " + variantCount);
        }
        Map<GarmentSlot, String> slotModelVariants = new LinkedHashMap<>();
        for (int i = 0; i < variantCount; i++) {
            if (slotModelVariants.put(buffer.readEnum(GarmentSlot.class), buffer.readUtf()) != null) {
                throw new IllegalArgumentException("Duplicate slot model variant");
            }
        }
        ResourceLocation modelTemplate = buffer.readResourceLocation();
        String modelVariant = buffer.readUtf();
        String textureVariant = buffer.readUtf();
        Set<String> designs = new LinkedHashSet<>(buffer.readList(FriendlyByteBuf::readUtf));
        int styleCount = buffer.readVarInt();
        if (styleCount < 0 || styleCount > MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException("Invalid garment style category count: " + styleCount);
        }
        Map<String, List<String>> styles = new LinkedHashMap<>();
        for (int index = 0; index < styleCount; index++) {
            styles.put(buffer.readUtf(), buffer.readList(FriendlyByteBuf::readUtf));
        }
        int slotStyleCount = buffer.readVarInt();
        if (slotStyleCount < 0 || slotStyleCount > GarmentSlot.values().length) {
            throw new IllegalArgumentException("Invalid slot style count: " + slotStyleCount);
        }
        Map<GarmentSlot, Map<String, List<String>>> slotStyles = new LinkedHashMap<>();
        for (int index = 0; index < slotStyleCount; index++) {
            GarmentSlot styleSlot = buffer.readEnum(GarmentSlot.class);
            int categoryCount = buffer.readVarInt();
            if (categoryCount < 0 || categoryCount > MAX_COLLECTION_SIZE) {
                throw new IllegalArgumentException("Invalid slot style category count: " + categoryCount);
            }
            Map<String, List<String>> categories = new LinkedHashMap<>();
            for (int category = 0; category < categoryCount; category++) {
                if (categories.put(buffer.readUtf(), buffer.readList(FriendlyByteBuf::readUtf)) != null) {
                    throw new IllegalArgumentException("Duplicate slot style category");
                }
            }
            if (slotStyles.put(styleSlot, categories) != null) {
                throw new IllegalArgumentException("Duplicate slot style override");
            }
        }
        return new GarmentDefinition(id, slot, allowedSlots, modelTemplate, modelVariant, slotModelVariants, textureVariant, designs, styles, slotStyles);
    }

    private static ResourceLocation parseResourceLocation(String value, String field) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid resource location for " + field + ": " + value);
        }
        return id;
    }
}
