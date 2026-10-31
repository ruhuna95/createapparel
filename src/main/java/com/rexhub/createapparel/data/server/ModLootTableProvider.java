package com.rexhub.createapparel.data.server;

import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.List;
import java.util.Set;

public final class ModLootTableProvider {
    private ModLootTableProvider() {
    }

    public static LootTableProvider create(PackOutput output, java.util.concurrent.CompletableFuture<net.minecraft.core.HolderLookup.Provider> registries) {
        return new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(
                        ModBlockLootSubProvider::new,
                        LootContextParamSets.BLOCK
                )),
                registries
        );
    }
}
