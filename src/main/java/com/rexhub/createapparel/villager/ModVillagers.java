package com.rexhub.createapparel.villager;

import com.google.common.collect.ImmutableSet;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModVillagers {
    public static final DeferredHolder<PoiType, PoiType> TAILOR_POI = ModRegistries.POI_TYPES.register("tailor_poi",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.SEWING_MACHINE.get().getStateDefinition().getPossibleStates()), 1, 1));
    public static final DeferredHolder<VillagerProfession, VillagerProfession> TAILOR = ModRegistries.VILLAGER_PROFESSIONS.register("tailor",
            () -> new VillagerProfession("createapparel:tailor",
                    poi -> poi.is(TAILOR_POI.getKey()), poi -> poi.is(TAILOR_POI.getKey()),
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_SHEPHERD));

    private ModVillagers() { }

    public static void bootstrap() { }
}
