package com.rexhub.createapparel.garment;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;

public final class SkirtIdMigration {
    private static final ResourceLocation OLD_ID = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "a_line_skirt");
    private static final ResourceLocation KNEE_ID = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "a_line_skirt_knee");

    private SkirtIdMigration() {
    }

    public static void registerAliases() {
        com.rexhub.createapparel.init.ModRegistries.ITEMS.addAlias(OLD_ID, KNEE_ID);
    }

    static ResourceLocation recipeId(ResourceLocation id) {
        return OLD_ID.equals(id) ? KNEE_ID : id;
    }
}
