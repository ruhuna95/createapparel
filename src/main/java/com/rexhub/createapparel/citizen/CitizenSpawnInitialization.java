package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.entity.CitizenState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Zombie;

public final class CitizenSpawnInitialization {
    private CitizenSpawnInitialization() { }

    public static CitizenAge selectAge(RandomSource random) {
        return Zombie.getSpawnAsBabyOdds(random) ? CitizenAge.CHILD : CitizenAge.ADULT;
    }

    public static void initialize(Citizen citizen, CitizenAge age, RandomSource random) {
        var catalog = CitizenPresetManager.snapshot();
        var prepared = new CitizenState();
        prepared.setAge(age);
        var selected = CitizenPresetSelector.select(catalog, age, random);
        try {
            prepared.setAppearance(selected.appearance());
            prepared.loadout().deserializeNBT(catalog.createLoadout(selected.garment()).serializeNBT());
        } catch (RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger().warn("Citizen spawn preset failed; using built-in fallback: {}", exception.getMessage());
            var fallback = CitizenPresetSelector.fallback();
            prepared.setAppearance(fallback.appearance());
            CitizenOutfitPreset.DEFAULT.equip(prepared);
        }
        if (!CitizenCoverage.isCovered(prepared.loadout())) throw new IllegalStateException("Citizen spawn lacks upper or lower coverage");
        citizen.citizenState().deserializeNBT(prepared.serializeNBT());
        citizen.syncState();
    }
}
