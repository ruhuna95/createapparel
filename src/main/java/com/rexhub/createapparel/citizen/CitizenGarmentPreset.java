package com.rexhub.createapparel.citizen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.GarmentAppearance;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.MetalAccessoryData;
import com.rexhub.createapparel.garment.MetalAccessoryItem;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public record CitizenGarmentPreset(Map<GarmentSlot, Entry> garments) {
    public record FabricOptions(FabricMaterial material, List<String> designs, List<FabricColor> primaryColors,
                                Optional<List<FabricColor>> secondaryColors, Optional<List<FabricColor>> accentColors) {
        public FabricOptions {
            primaryColors = CitizenPresetJson.immutableOptions(primaryColors, "primary_color");
            FabricColor firstColor = primaryColors.get(0);
            FabricMaterial fabricMaterial = material;
            designs = CitizenPresetJson.immutableOptions(designs.stream().map(design ->
                    new GarmentData(fabricMaterial, design, firstColor, Optional.empty(), Optional.empty()).design()).toList(), "design");
            secondaryColors = secondaryColors.map(values -> CitizenPresetJson.immutableOptions(values, "secondary_color"));
            accentColors = accentColors.map(values -> CitizenPresetJson.immutableOptions(values, "accent_color"));
        }

        static FabricOptions of(GarmentData data) {
            return new FabricOptions(data.material(), List.of(data.design()), List.of(data.primaryColor()),
                    data.secondaryColor().map(List::of), data.accentColor().map(List::of));
        }

        GarmentData first() {
            return new GarmentData(material, designs.get(0), primaryColors.get(0),
                    secondaryColors.map(values -> values.get(0)), accentColors.map(values -> values.get(0)));
        }

        GarmentData select(RandomSource random) {
            return new GarmentData(material, CitizenPresetJson.choose(designs, random), CitizenPresetJson.choose(primaryColors, random),
                    secondaryColors.map(values -> CitizenPresetJson.choose(values, random)),
                    accentColors.map(values -> CitizenPresetJson.choose(values, random)));
        }

        boolean isFixed() {
            return designs.size() == 1 && primaryColors.size() == 1
                    && secondaryColors.map(values -> values.size() == 1).orElse(true)
                    && accentColors.map(values -> values.size() == 1).orElse(true);
        }
    }

    public record Entry(ResourceLocation item, Optional<FabricOptions> fabric, Optional<MetalAccessoryData> metal,
                        Map<String, List<String>> styleOptions, boolean optional) {
        public Entry {
            if (fabric.isPresent() == metal.isPresent()) throw new IllegalArgumentException("Exactly one garment appearance type is required");
            Map<String, List<String>> immutableStyles = new TreeMap<>();
            styleOptions.forEach((key, values) -> immutableStyles.put(key, CitizenPresetJson.immutableOptions(values, "styles." + key)));
            styleOptions = Collections.unmodifiableMap(immutableStyles);
        }

        public Entry(ResourceLocation item, Optional<FabricOptions> fabric, Optional<MetalAccessoryData> metal,
                     Map<String, String> styles) {
            this(item, fabric, metal, singletonStyles(styles), false);
        }

        public Entry(ResourceLocation item, GarmentAppearance appearance, Map<String, String> styles) {
            this(item, appearance instanceof GarmentData data ? Optional.of(FabricOptions.of(data)) : Optional.empty(),
                    appearance instanceof MetalAccessoryData data ? Optional.of(data) : Optional.empty(), singletonStyles(styles), false);
        }

        private static Map<String, List<String>> singletonStyles(Map<String, String> styles) {
            Map<String, List<String>> choices = new TreeMap<>();
            styles.forEach((key, value) -> choices.put(key, List.of(value)));
            return choices;
        }

        public Map<String, String> styles() {
            Map<String, String> first = new LinkedHashMap<>();
            styleOptions.forEach((key, values) -> first.put(key, values.get(0)));
            return Collections.unmodifiableMap(first);
        }

        public GarmentAppearance appearance() {
            return fabric.isPresent() ? fabric.get().first() : metal.orElseThrow();
        }

        Entry resolve(RandomSource random) {
            if (!optional && fabric.map(FabricOptions::isFixed).orElse(true)
                    && styleOptions.values().stream().allMatch(values -> values.size() == 1)) return this;
            Map<String, String> selectedStyles = new LinkedHashMap<>();
            styleOptions.forEach((key, values) -> selectedStyles.put(key, CitizenPresetJson.choose(values, random)));
            GarmentAppearance selected = fabric.isPresent() ? fabric.get().select(random) : metal.orElseThrow();
            return new Entry(item, selected, selectedStyles);
        }
    }

    public CitizenGarmentPreset { garments = Map.copyOf(garments); }

    public static CitizenGarmentPreset fromJson(JsonElement value) {
        JsonObject json = CitizenPresetJson.object(value, "garment preset");
        CitizenPresetJson.keys(json, Set.of("garments"));
        JsonObject entries = CitizenPresetJson.object(json.get("garments"), "garments");
        Map<GarmentSlot, Entry> garments = new EnumMap<>(GarmentSlot.class);
        entries.entrySet().forEach(pair -> {
            GarmentSlot slot = GarmentSlot.fromSerializedName(pair.getKey());
            JsonObject garment = CitizenPresetJson.object(pair.getValue(), pair.getKey());
            ResourceLocation item = CitizenPresetJson.id(CitizenPresetJson.string(garment, "item"));
            boolean metal = garment.has("metal_color") || garment.has("gem");
            CitizenPresetJson.keys(garment, metal ? Set.of("item", "metal_color", "gem", "styles", "optional")
                    : Set.of("item", "material", "design", "primary_color", "secondary_color", "accent_color", "styles", "optional"));
            Entry entry;
            if (metal) {
                CompoundTag tag = new CompoundTag();
                tag.putString("metal_color", CitizenPresetJson.string(garment, "metal_color"));
                tag.putString("gem", CitizenPresetJson.string(garment, "gem"));
                entry = new Entry(item, MetalAccessoryData.fromNBT(tag), Map.of());
            } else {
                String material = CitizenPresetJson.string(garment, "material");
                FabricOptions options = new FabricOptions(FabricMaterial.fromSerializedName(material)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown material: " + material)),
                        garment.has("design") ? CitizenPresetJson.options(garment, "design", valueId -> valueId) : List.of("plain"),
                        CitizenBodyPreset.colors(garment, "primary_color"),
                        garment.has("secondary_color") ? Optional.of(CitizenBodyPreset.colors(garment, "secondary_color")) : Optional.empty(),
                        garment.has("accent_color") ? Optional.of(CitizenBodyPreset.colors(garment, "accent_color")) : Optional.empty());
                entry = new Entry(item, Optional.of(options), Optional.empty(), Map.of());
            }
            Map<String, List<String>> styles = new LinkedHashMap<>();
            if (garment.has("styles")) {
                JsonObject styleJson = CitizenPresetJson.object(garment.get("styles"), "styles");
                styleJson.keySet().forEach(key -> styles.put(key, CitizenPresetJson.options(styleJson, key, style -> style)));
            }
            garments.put(slot, new Entry(item, entry.fabric(), entry.metal(), styles, CitizenPresetJson.optionalBoolean(garment, "optional")));
        });
        return new CitizenGarmentPreset(garments);
    }

    public CitizenGarmentPreset resolve(RandomSource random) {
        Map<GarmentSlot, Entry> resolved = new EnumMap<>(GarmentSlot.class);
        boolean changed = false;
        for (GarmentSlot slot : GarmentSlot.values()) {
            Entry entry = garments.get(slot);
            if (entry == null) continue;
            if (entry.optional() && !random.nextBoolean()) {
                changed = true;
                continue;
            }
            Entry selected = entry.resolve(random);
            resolved.put(slot, selected);
            changed |= selected != entry;
        }
        return changed ? new CitizenGarmentPreset(resolved) : this;
    }

    public ApparelLoadout createLoadout(Function<ResourceLocation, Optional<GarmentDefinition>> definitions,
                                       Function<ResourceLocation, Item> items,
                                       BiPredicate<ResourceLocation, FabricMaterial> materials,
                                       Predicate<ItemStack> lowerCoveringTop) {
        ApparelLoadout loadout = new ApparelLoadout();
        loadout.setDefinitionLookup(definitions);
        for (var pair : garments.entrySet()) {
            GarmentSlot slot = pair.getKey();
            Entry entry = pair.getValue();
            Item item = items.apply(entry.item());
            GarmentDefinition definition = definitions.apply(entry.item())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown garment: " + entry.item()));
            if (!(item instanceof GarmentItem) || !definition.allowedSlots().contains(slot)) {
                throw new IllegalArgumentException("Garment cannot equip " + slot + ": " + entry.item());
            }
            if ((item instanceof MetalAccessoryItem) != entry.metal().isPresent()) {
                throw new IllegalArgumentException("Wrong appearance type: " + entry.item());
            }
            if (entry.fabric().isPresent()) {
                FabricOptions fabric = entry.fabric().get();
                if (!materials.test(entry.item(), fabric.material())) {
                    throw new IllegalArgumentException("Unsupported material: " + entry.item() + " / " + fabric.material());
                }
                for (String design : fabric.designs()) {
                    if (!definition.designs().contains(design)) throw new IllegalArgumentException("Unsupported design: " + entry.item() + " / " + design);
                }
            } else if (!definition.designs().contains(entry.appearance().design())) {
                throw new IllegalArgumentException("Unsupported design: " + entry.item() + " / " + entry.appearance().design());
            }
            entry.styleOptions().forEach((category, choices) -> {
                for (String style : choices) {
                    if (!definition.stylesFor(slot).getOrDefault(category, List.of()).contains(style)) {
                        throw new IllegalArgumentException("Unsupported style: " + entry.item() + " / " + category + "=" + style);
                    }
                }
            });
            ItemStack stack = new ItemStack(item);
            entry.appearance().write(stack);
            if (entry.appearance() instanceof GarmentData) GarmentData.fromStackStrict(stack, definition);
            loadout.setStackInSlot(slot.ordinal(), stack);
        }
        for (var pair : garments.entrySet()) {
            pair.getValue().styleOptions().forEach((category, choices) -> {
                for (String choice : choices) {
                    if (!loadout.allowedStyles(pair.getKey()).getOrDefault(category, List.of()).contains(choice)) {
                        throw new IllegalArgumentException("Style is incompatible with outfit: " + pair.getKey() + " / " + category + "=" + choice);
                    }
                }
            });
            for (var style : pair.getValue().styles().entrySet()) {
                if (!loadout.setStyle(pair.getKey(), style.getKey(), style.getValue())) {
                    throw new IllegalArgumentException("Style is incompatible with outfit: " + pair.getKey() + " / " + style);
                }
            }
        }
        for (var pair : garments.entrySet()) {
            pair.getValue().styles().forEach((category, style) -> {
                if (!style.equals(loadout.styleState(pair.getKey()).styles().get(category))) {
                    throw new IllegalArgumentException("Outfit changes requested style: " + pair.getKey() + " / " + category);
                }
            });
        }
        if (!CitizenCoverage.isCovered(loadout, lowerCoveringTop)) throw new IllegalArgumentException("Outfit requires upper and lower coverage");
        boolean requiredTop = required(GarmentSlot.TOP);
        boolean upper = required(GarmentSlot.INNER_TOP) || requiredTop;
        boolean lower = required(GarmentSlot.INNER_BOTTOM) || required(GarmentSlot.BOTTOM)
                || requiredTop && lowerCoveringTop.test(loadout.getStack(GarmentSlot.TOP));
        if (!upper || !lower) throw new IllegalArgumentException("Optional garments cannot be the only upper or lower coverage");
        return loadout;
    }

    private boolean required(GarmentSlot slot) {
        Entry entry = garments.get(slot);
        return entry != null && !entry.optional();
    }

    public static CitizenGarmentPreset builtIn() {
        return new CitizenGarmentPreset(Map.of(
                GarmentSlot.TOP, fabric("t_shirt", FabricMaterial.COTTON, FabricColor.WHITE),
                GarmentSlot.BOTTOM, fabric("denim_pants", FabricMaterial.COTTON, FabricColor.BLUE),
                GarmentSlot.FEET, fabric("sneakers", FabricMaterial.SYNTHETIC, FabricColor.WHITE)));
    }

    private static Entry fabric(String item, FabricMaterial material, FabricColor color) {
        return new Entry(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, item),
                new GarmentData(material, "plain", color, Optional.empty(), Optional.empty()), Map.of());
    }
}
