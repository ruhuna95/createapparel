package com.rexhub.createapparel.init;

import com.rexhub.createapparel.CreateApparel;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ForgeBiomeModifiers;
import net.minecraftforge.registries.ForgeRegistries;

public final class ModBiomeModifiers {
    public static final ResourceKey<BiomeModifier> ADD_SILKWORM_SPAWNS = ResourceKey.create(
            ForgeRegistries.Keys.BIOME_MODIFIERS,
            ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "add_silkworm_spawns")
    );

    private ModBiomeModifiers() {
    }

    public static void bootstrap(BootstapContext<BiomeModifier> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        context.register(
                ADD_SILKWORM_SPAWNS,
                ForgeBiomeModifiers.AddSpawnsBiomeModifier.singleSpawn(
                        biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                        new MobSpawnSettings.SpawnerData(ModEntities.SILKWORM.get(), 8, 1, 3)
                )
        );
    }
}
