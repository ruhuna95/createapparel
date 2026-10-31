package com.rexhub.createapparel.init;

import com.mojang.serialization.Codec;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.recipe.ModRecipeTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModRegistries {
    public static final DeferredRegister<net.minecraft.world.entity.ai.village.poi.PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, CreateApparel.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.entity.npc.VillagerProfession> VILLAGER_PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, CreateApparel.MOD_ID);
    public static final DeferredRegister<net.neoforged.neoforge.fluids.FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreateApparel.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, CreateApparel.MOD_ID);
    public static final DeferredRegister<com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType> ARM_INTERACTION_POINT_TYPES =
            DeferredRegister.create(com.simibubi.create.api.registry.CreateRegistries.ARM_INTERACTION_POINT_TYPE, CreateApparel.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, CreateApparel.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, CreateApparel.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CreateApparel.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, CreateApparel.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, CreateApparel.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateApparel.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateApparel.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateApparel.MOD_ID);
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, CreateApparel.MOD_ID);

    private ModRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        ModBlocks.bootstrap();
        com.rexhub.createapparel.fashion.FashionRegistries.bootstrap();
        com.rexhub.createapparel.photo.PhotoRegistries.bootstrap();
        com.rexhub.createapparel.advertisement.AdvertisementRegistries.bootstrap();
        ModMirrors.bootstrap();
        ModFluids.bootstrap();
        ModArmInteractionPoints.bootstrap();
        ModBlockEntities.bootstrap();
        ModMenuTypes.bootstrap();
        ModRecipeTypes.bootstrap();
        ModEntities.bootstrap();
        ModLootModifiers.bootstrap();
        ModItems.bootstrap();
        com.rexhub.createapparel.garment.SkirtIdMigration.registerAliases();
        com.rexhub.createapparel.villager.ModVillagers.bootstrap();
        ModCreativeModeTabs.bootstrap();

        BLOCKS.register(modEventBus);
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        ARM_INTERACTION_POINT_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        POI_TYPES.register(modEventBus);
        VILLAGER_PROFESSIONS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        GLOBAL_LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}
