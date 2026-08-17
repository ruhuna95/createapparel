package com.rexhub.createapparel.init;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.entity.Silkworm;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    public static final RegistryObject<EntityType<Silkworm>> SILKWORM = ModRegistries.ENTITY_TYPES.register(
            "silkworm",
            () -> EntityType.Builder.of(Silkworm::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.3F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build(CreateApparel.MOD_ID + ":silkworm")
    );

    private ModEntities() {
    }

    public static void bootstrap() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SILKWORM.get(), Silkworm.createAttributes().build());
    }
}
