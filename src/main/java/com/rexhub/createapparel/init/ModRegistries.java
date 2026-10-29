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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class ModRegistries {
    public static final DeferredRegister<net.minecraft.world.entity.ai.village.poi.PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, CreateApparel.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.entity.npc.VillagerProfession> VILLAGER_PROFESSIONS =
            DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, CreateApparel.MOD_ID);
    public static final DeferredRegister<net.minecraftforge.fluids.FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, CreateApparel.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, CreateApparel.MOD_ID);
    public static final DeferredRegister<com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType> ARM_INTERACTION_POINT_TYPES =
            DeferredRegister.create(com.simibubi.create.api.registry.CreateRegistries.ARM_INTERACTION_POINT_TYPE, CreateApparel.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CreateApparel.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CreateApparel.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CreateApparel.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CreateApparel.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, CreateApparel.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, CreateApparel.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CreateApparel.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateApparel.MOD_ID);
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, CreateApparel.MOD_ID);

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
