package com.rexhub.createapparel.citizen;

import com.google.gson.JsonElement;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModTags;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.recipe.SewingRecipe;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class CitizenPresetManager extends SimplePreparableReloadListener<Map<String, Map<ResourceLocation, JsonElement>>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> ROOTS = List.of("citizen/body_presets/adult", "citizen/body_presets/child", "citizen/garment_presets");
    private static volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of());
    private final RecipeManager recipes;
    private final ICondition.IContext tags;

    public CitizenPresetManager(RecipeManager recipes, ICondition.IContext tags) {
        this.recipes = recipes;
        this.tags = tags;
    }

    public record Snapshot(Map<CitizenAge, Map<ResourceLocation, CitizenBodyPreset>> bodies,
                           Map<ResourceLocation, CitizenGarmentPreset> garments,
                           Map<ResourceLocation, GarmentDefinition> definitions,
                           Map<ResourceLocation, Set<FabricMaterial>> materials,
                           Set<ResourceLocation> lowerCoveringTops) {
        public Snapshot(Map<CitizenAge, Map<ResourceLocation, CitizenBodyPreset>> bodies,
                        Map<ResourceLocation, CitizenGarmentPreset> garments,
                        Map<ResourceLocation, GarmentDefinition> definitions,
                        Map<ResourceLocation, Set<FabricMaterial>> materials) {
            this(bodies, garments, definitions, materials, Set.of());
        }
        public Snapshot {
            Map<CitizenAge, Map<ResourceLocation, CitizenBodyPreset>> immutableBodies = new EnumMap<>(CitizenAge.class);
            bodies.forEach((age, values) -> immutableBodies.put(age, Collections.unmodifiableMap(new LinkedHashMap<>(values))));
            bodies = Map.copyOf(immutableBodies);
            garments = Collections.unmodifiableMap(new LinkedHashMap<>(garments));
            definitions = Map.copyOf(definitions);
            Map<ResourceLocation, Set<FabricMaterial>> immutableMaterials = new HashMap<>();
            materials.forEach((id, values) -> immutableMaterials.put(id, Set.copyOf(values)));
            materials = Map.copyOf(immutableMaterials);
            lowerCoveringTops = Set.copyOf(lowerCoveringTops);
        }

        public ApparelLoadout createLoadout(CitizenGarmentPreset preset) {
            return preset.createLoadout(id -> Optional.ofNullable(definitions.get(id)), ForgeRegistries.ITEMS::getValue,
                    (id, material) -> materials.getOrDefault(id, Set.of()).contains(material),
                    stack -> lowerCoveringTops.contains(ForgeRegistries.ITEMS.getKey(stack.getItem())));
        }
    }

    public static Snapshot snapshot() { return snapshot; }

    @Override
    protected Map<String, Map<ResourceLocation, JsonElement>> prepare(ResourceManager resources, ProfilerFiller profiler) {
        Map<String, Map<ResourceLocation, JsonElement>> result = new LinkedHashMap<>();
        for (String root : ROOTS) {
            Map<ResourceLocation, JsonElement> values = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
            resources.listResources(root, id -> id.getNamespace().equals(CreateApparel.MOD_ID) && id.getPath().endsWith(".json"))
                    .forEach((file, resource) -> {
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
                                file.getPath().substring(root.length() + 1, file.getPath().length() - 5));
                        try (var reader = resource.openAsReader()) {
                            values.put(id, CitizenPresetJson.read(reader));
                        } catch (Exception exception) {
                            LOGGER.warn("Invalid Citizen preset {} ({}): {}", id, root, exception.getMessage());
                        }
                    });
            result.put(root, values);
        }
        return result;
    }

    @Override
    protected void apply(Map<String, Map<ResourceLocation, JsonElement>> objects, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, GarmentDefinition> definitions = new HashMap<>();
        GarmentDefinitionManager.all().forEach(definition -> definitions.put(definition.id(), definition));
        Set<ResourceLocation> lowerCoveringTops = new HashSet<>();
        tags.getTag(ModTags.Items.CITIZEN_LOWER_COVERING_TOPS).forEach(holder ->
                lowerCoveringTops.add(Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(holder.value()))));
        snapshot = validate(objects, definitions, materialSupport(), lowerCoveringTops);
    }

    private Map<ResourceLocation, Set<FabricMaterial>> materialSupport() {
        Map<ResourceLocation, Set<FabricMaterial>> result = new HashMap<>();
        for (SewingRecipe recipe : recipes.getAllRecipesFor(SewingRecipe.recipeType())) {
            ModItems.fabricItems().forEach((item, attribute) -> {
                if (attribute.isDyed() && recipe.fabricTags().stream()
                        .anyMatch(tag -> tags.getTag(tag).stream().anyMatch(holder -> holder.value() == item.get()))) {
                    result.computeIfAbsent(recipe.getResultId(), id -> EnumSet.noneOf(FabricMaterial.class)).add(attribute.material());
                }
            });
        }
        return result;
    }

    public static Snapshot validate(Map<String, Map<ResourceLocation, JsonElement>> objects,
                                    Map<ResourceLocation, GarmentDefinition> definitions,
                                    Map<ResourceLocation, Set<FabricMaterial>> materials) {
        return validate(objects, definitions, materials, Set.of());
    }

    public static Snapshot validate(Map<String, Map<ResourceLocation, JsonElement>> objects,
                                    Map<ResourceLocation, GarmentDefinition> definitions,
                                    Map<ResourceLocation, Set<FabricMaterial>> materials,
                                    Set<ResourceLocation> lowerCoveringTops) {
        Snapshot validator = new Snapshot(Map.of(), Map.of(), definitions, materials, lowerCoveringTops);
        Map<ResourceLocation, CitizenGarmentPreset> garments = new LinkedHashMap<>();
        objects.getOrDefault(ROOTS.get(2), Map.of()).forEach((id, json) -> {
            try {
                CitizenGarmentPreset preset = CitizenGarmentPreset.fromJson(json);
                validator.createLoadout(preset);
                garments.put(id, preset);
            } catch (RuntimeException exception) {
                LOGGER.warn("Invalid Citizen garment preset {}: {}", id, exception.getMessage());
            }
        });
        Map<CitizenAge, Map<ResourceLocation, CitizenBodyPreset>> bodies = new EnumMap<>(CitizenAge.class);
        for (CitizenAge age : CitizenAge.values()) {
            Map<ResourceLocation, CitizenBodyPreset> loaded = new LinkedHashMap<>();
            String root = "citizen/body_presets/" + age.getSerializedName();
            objects.getOrDefault(root, Map.of()).forEach((id, json) -> {
                try {
                    CitizenBodyPreset body = CitizenBodyPreset.fromJson(json);
                    for (ResourceLocation reference : body.garmentPresets()) {
                        if (!garments.containsKey(reference)) LOGGER.warn("Citizen body preset {} ({}) has unresolved garment preset {}", id, age, reference);
                    }
                    loaded.put(id, body);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Invalid Citizen body preset {} ({}): {}", id, age, exception.getMessage());
                }
            });
            bodies.put(age, loaded);
        }
        try {
            validator.createLoadout(CitizenGarmentPreset.builtIn());
        } catch (RuntimeException exception) {
            LOGGER.warn("Citizen built-in fallback unavailable; conversions requiring it will abort: {}", exception.getMessage());
        }
        return new Snapshot(bodies, garments, definitions, materials, lowerCoveringTops);
    }
}
