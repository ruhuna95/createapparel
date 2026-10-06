package com.rexhub.createapparel.init;

import com.rexhub.createapparel.entity.Mannequin;
import com.rexhub.createapparel.entity.MannequinVariant;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.entity.Silkworm;
import net.minecraft.world.entity.EntityType;
import java.util.Map;
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

    public static final RegistryObject<EntityType<Mannequin>> FULL_MANNEQUIN_WHITE_CLASSIC = registerMannequin(MannequinVariant.FULL_WHITE_CLASSIC);
    public static final RegistryObject<EntityType<Mannequin>> FULL_MANNEQUIN_BLACK_CLASSIC = registerMannequin(MannequinVariant.FULL_BLACK_CLASSIC);
    public static final RegistryObject<EntityType<Mannequin>> FULL_MANNEQUIN_WHITE_SLIM = registerMannequin(MannequinVariant.FULL_WHITE_SLIM);
    public static final RegistryObject<EntityType<Mannequin>> FULL_MANNEQUIN_BLACK_SLIM = registerMannequin(MannequinVariant.FULL_BLACK_SLIM);
    public static final RegistryObject<EntityType<Mannequin>> UPPER_MANNEQUIN_WHITE_CLASSIC = registerMannequin(MannequinVariant.UPPER_WHITE_CLASSIC);
    public static final RegistryObject<EntityType<Mannequin>> UPPER_MANNEQUIN_BLACK_CLASSIC = registerMannequin(MannequinVariant.UPPER_BLACK_CLASSIC);
    public static final RegistryObject<EntityType<Mannequin>> UPPER_MANNEQUIN_WHITE_SLIM = registerMannequin(MannequinVariant.UPPER_WHITE_SLIM);
    public static final RegistryObject<EntityType<Mannequin>> UPPER_MANNEQUIN_BLACK_SLIM = registerMannequin(MannequinVariant.UPPER_BLACK_SLIM);
    public static final RegistryObject<EntityType<Mannequin>> LOWER_MANNEQUIN_WHITE = registerMannequin(MannequinVariant.LOWER_WHITE);
    public static final RegistryObject<EntityType<Mannequin>> LOWER_MANNEQUIN_BLACK = registerMannequin(MannequinVariant.LOWER_BLACK);
    public static final RegistryObject<EntityType<Mannequin>> HEAD_MANNEQUIN_WHITE = registerMannequin(MannequinVariant.HEAD_WHITE);
    public static final RegistryObject<EntityType<Mannequin>> HEAD_MANNEQUIN_BLACK = registerMannequin(MannequinVariant.HEAD_BLACK);
    private static final Map<MannequinVariant, RegistryObject<EntityType<Mannequin>>> MANNEQUINS = Map.ofEntries(
            Map.entry(MannequinVariant.FULL_WHITE_CLASSIC, FULL_MANNEQUIN_WHITE_CLASSIC),
            Map.entry(MannequinVariant.FULL_BLACK_CLASSIC, FULL_MANNEQUIN_BLACK_CLASSIC),
            Map.entry(MannequinVariant.FULL_WHITE_SLIM, FULL_MANNEQUIN_WHITE_SLIM),
            Map.entry(MannequinVariant.FULL_BLACK_SLIM, FULL_MANNEQUIN_BLACK_SLIM),
            Map.entry(MannequinVariant.UPPER_WHITE_CLASSIC, UPPER_MANNEQUIN_WHITE_CLASSIC),
            Map.entry(MannequinVariant.UPPER_BLACK_CLASSIC, UPPER_MANNEQUIN_BLACK_CLASSIC),
            Map.entry(MannequinVariant.UPPER_WHITE_SLIM, UPPER_MANNEQUIN_WHITE_SLIM),
            Map.entry(MannequinVariant.UPPER_BLACK_SLIM, UPPER_MANNEQUIN_BLACK_SLIM),
            Map.entry(MannequinVariant.LOWER_WHITE, LOWER_MANNEQUIN_WHITE),
            Map.entry(MannequinVariant.LOWER_BLACK, LOWER_MANNEQUIN_BLACK),
            Map.entry(MannequinVariant.HEAD_WHITE, HEAD_MANNEQUIN_WHITE),
            Map.entry(MannequinVariant.HEAD_BLACK, HEAD_MANNEQUIN_BLACK));

    public static Map<MannequinVariant, RegistryObject<EntityType<Mannequin>>> mannequins() {
        return MANNEQUINS;
    }

    public static MannequinVariant mannequinVariant(EntityType<?> type) {
        return MANNEQUINS.entrySet().stream().filter(entry -> entry.getValue().get() == type)
                .map(Map.Entry::getKey).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown mannequin entity type"));
    }

    private static RegistryObject<EntityType<Mannequin>> registerMannequin(
            MannequinVariant variant) {
        return ModRegistries.ENTITY_TYPES.register(variant.id(),
                () -> EntityType.Builder.of(Mannequin::new, MobCategory.MISC)
                        .sized(0.75F, variant.form().height())
                        .clientTrackingRange(10).updateInterval(3)
                        .build(CreateApparel.MOD_ID + ":" + variant.id()));
    }

    private ModEntities() {
    }

    public static void bootstrap() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SILKWORM.get(), Silkworm.createAttributes().build());
        MANNEQUINS.values().forEach(type -> event.put(type.get(), Mannequin.createAttributes().build()));
    }
}
