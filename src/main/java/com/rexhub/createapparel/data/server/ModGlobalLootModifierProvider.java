package com.rexhub.createapparel.data.server;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.loot.AddSeedsLootModifier;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;

public final class ModGlobalLootModifierProvider extends GlobalLootModifierProvider {
    private static final float SEED_DROP_CHANCE = 0.08F;

    public ModGlobalLootModifierProvider(PackOutput output, java.util.concurrent.CompletableFuture<net.minecraft.core.HolderLookup.Provider> registries) {
        super(output, registries, CreateApparel.MOD_ID);
    }

    @Override
    protected void start() {
        add("grass_seeds", new AddSeedsLootModifier(new LootItemCondition[0], SEED_DROP_CHANCE));
    }
}
