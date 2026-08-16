package com.rexhub.createapparel.init;

import com.mojang.serialization.Codec;
import com.rexhub.createapparel.loot.AddSeedsLootModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.RegistryObject;

public final class ModLootModifiers {
    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ADD_SEEDS =
            ModRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS.register(
                    "add_seeds",
                    () -> AddSeedsLootModifier.CODEC
            );

    private ModLootModifiers() {
    }

    public static void bootstrap() {
    }
}
