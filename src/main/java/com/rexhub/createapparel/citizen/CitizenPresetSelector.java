package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.entity.CitizenAppearance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Map;

public final class CitizenPresetSelector {
    private static final ResourceLocation FALLBACK_PRESET_ID = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "default");
    public record Selection(CitizenAppearance appearance, CitizenGarmentPreset garment) { }

    private CitizenPresetSelector() { }

    public static Selection select(CitizenPresetManager.Snapshot snapshot, CitizenAge age, RandomSource random) {
        var eligible = snapshot.bodies().getOrDefault(age, Map.of()).entrySet().stream()
                .filter(entry -> !entry.getKey().equals(FALLBACK_PRESET_ID))
                .filter(entry -> entry.getValue().garmentPresets().stream().anyMatch(id -> isSelectableGarment(snapshot, id)))
                .toList();
        if (eligible.isEmpty()) return fallback();
        List<CitizenBodyPreset> males = eligible.stream().filter(entry -> entry.getKey().getPath().startsWith("male_"))
                .map(Map.Entry::getValue).toList();
        List<CitizenBodyPreset> females = eligible.stream().filter(entry -> entry.getKey().getPath().startsWith("female_"))
                .map(Map.Entry::getValue).toList();
        List<CitizenBodyPreset> bodies;
        if (!males.isEmpty() && !females.isEmpty()) {
            bodies = random.nextBoolean() ? males : females;
        } else if (!males.isEmpty()) {
            bodies = males;
        } else if (!females.isEmpty()) {
            bodies = females;
        } else {
            bodies = eligible.stream().map(Map.Entry::getValue).toList();
        }
        CitizenBodyPreset body = bodies.get(random.nextInt(bodies.size()));
        List<ResourceLocation> garments = body.garmentPresets().stream().filter(id -> isSelectableGarment(snapshot, id)).toList();
        CitizenGarmentPreset garment = snapshot.garments().get(CitizenPresetJson.choose(garments, random));
        return new Selection(body.selectAppearance(random), garment.resolve(random));
    }

    public static Selection fallback() {
        return new Selection(CitizenAppearance.DEFAULT, CitizenGarmentPreset.builtIn());
    }

    private static boolean isSelectableGarment(CitizenPresetManager.Snapshot snapshot, ResourceLocation id) {
        return !id.equals(FALLBACK_PRESET_ID) && snapshot.garments().containsKey(id);
    }
}
