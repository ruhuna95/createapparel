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
        List<CitizenBodyPreset> bodies = snapshot.bodies().getOrDefault(age, Map.of()).entrySet().stream()
                .filter(entry -> !entry.getKey().equals(FALLBACK_PRESET_ID))
                .map(Map.Entry::getValue)
                .filter(body -> body.garmentPresets().stream().anyMatch(id -> isSelectableGarment(snapshot, id)))
                .toList();
        if (bodies.isEmpty()) return fallback();
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
