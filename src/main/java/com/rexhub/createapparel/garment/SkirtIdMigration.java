package com.rexhub.createapparel.garment;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class SkirtIdMigration {
    private static final ResourceLocation OLD_ID = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "a_line_skirt");
    private static final ResourceLocation KNEE_ID = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "a_line_skirt_knee");

    private SkirtIdMigration() {
    }

    @SubscribeEvent
    public static void remapItem(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, CreateApparel.MOD_ID)) {
            if (mapping.getKey().equals(OLD_ID)) {
                mapping.remap(ModItems.A_LINE_SKIRT_KNEE.get());
            }
        }
    }

    static ResourceLocation recipeId(ResourceLocation id) {
        return OLD_ID.equals(id) ? KNEE_ID : id;
    }
}
