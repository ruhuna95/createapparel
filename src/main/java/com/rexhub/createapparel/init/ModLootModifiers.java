package com.rexhub.createapparel.init;

import com.mojang.serialization.Codec;
import com.rexhub.createapparel.loot.AddSeedsLootModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModLootModifiers {
    public static final DeferredHolder<com.mojang.serialization.MapCodec<? extends IGlobalLootModifier>, com.mojang.serialization.MapCodec<? extends IGlobalLootModifier>> ADD_SEEDS =
            ModRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS.register(
                    "add_seeds",
                    () -> AddSeedsLootModifier.CODEC
            );

    private ModLootModifiers() {
    }

    public static void bootstrap() {
    }
}
