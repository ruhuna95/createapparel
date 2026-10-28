package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.RegistryObject;

public final class AdvertisementRegistries {
    public static final RegistryObject<Block> POSTER = panel("poster", AdvertisementPanelBlock.Kind.POSTER);
    public static final RegistryObject<Block> BILLBOARD_PANEL = panel("billboard_panel", AdvertisementPanelBlock.Kind.BILLBOARD);
    public static final RegistryObject<Block> SIGNAGE_PANEL = panel("signage_panel", AdvertisementPanelBlock.Kind.SIGNAGE);
    public static final RegistryObject<Block> SIGNAGE_BROADCASTER = ModRegistries.BLOCKS.register("signage_broadcaster",
            () -> new SignageBroadcasterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(2).noOcclusion()));
    public static final RegistryObject<Item> POSTER_ITEM = item("poster", POSTER);
    public static final RegistryObject<Item> BILLBOARD_PANEL_ITEM = item("billboard_panel", BILLBOARD_PANEL);
    public static final RegistryObject<Item> SIGNAGE_PANEL_ITEM = item("signage_panel", SIGNAGE_PANEL);
    public static final RegistryObject<Item> SIGNAGE_BROADCASTER_ITEM = item("signage_broadcaster", SIGNAGE_BROADCASTER);
    public static final RegistryObject<BlockEntityType<AdvertisementPanelBlockEntity>> PANEL_ENTITY = ModRegistries.BLOCK_ENTITY_TYPES.register("advertisement_panel",
            () -> BlockEntityType.Builder.of(AdvertisementPanelBlockEntity::new, POSTER.get(), BILLBOARD_PANEL.get(), SIGNAGE_PANEL.get()).build(null));
    public static final RegistryObject<MenuType<AdvertisementControlMenu>> CONTROL = ModRegistries.MENU_TYPES.register("advertisement_control",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(AdvertisementControlMenu::new));
    public static final RegistryObject<BlockEntityType<SignageBroadcasterBlockEntity>> BROADCASTER_ENTITY = ModRegistries.BLOCK_ENTITY_TYPES.register("signage_broadcaster",
            () -> BlockEntityType.Builder.of(SignageBroadcasterBlockEntity::new, SIGNAGE_BROADCASTER.get()).build(null));
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<com.rexhub.createapparel.photo.PhotoCopyRecipe>> PHOTO_COPY = ModRegistries.RECIPE_SERIALIZERS.register("photo_copy",
            () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(com.rexhub.createapparel.photo.PhotoCopyRecipe::new));
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<AdPrintingDeployerRecipe>> AD_PRINTING = ModRegistries.RECIPE_SERIALIZERS.register("ad_printing",
            () -> new com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer<>(AdPrintingDeployerRecipe::new));
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeType<AdPrintingDeployerRecipe>> AD_PRINTING_TYPE = ModRegistries.RECIPE_TYPES.register("ad_printing",
            () -> net.minecraft.world.item.crafting.RecipeType.simple(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createapparel", "ad_printing")));
    private AdvertisementRegistries() { }
    private static RegistryObject<Block> panel(String id, AdvertisementPanelBlock.Kind kind) {
        return ModRegistries.BLOCKS.register(id, () -> new AdvertisementPanelBlock(kind == AdvertisementPanelBlock.Kind.POSTER
                ? BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).instabreak().noCollission().noOcclusion()
                        .sound(new net.minecraftforge.common.util.ForgeSoundType(1, 1, () -> net.minecraft.sounds.SoundEvents.PAINTING_BREAK,
                                () -> net.minecraft.sounds.SoundEvents.WOOD_STEP, () -> net.minecraft.sounds.SoundEvents.PAINTING_PLACE,
                                () -> net.minecraft.sounds.SoundEvents.WOOD_HIT, () -> net.minecraft.sounds.SoundEvents.WOOD_FALL))
                : BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(1).noOcclusion().lightLevel(state -> kind == AdvertisementPanelBlock.Kind.SIGNAGE ? 15 : 0), kind));
    }
    private static RegistryObject<Item> item(String id, RegistryObject<Block> block) {
        return ModRegistries.ITEMS.register(id, () -> id.equals("poster") || id.equals("billboard_panel")
                ? new AdvertisementBlockItem(block.get(), new Item.Properties()) : new BlockItem(block.get(), new Item.Properties()));
    }
    public static void bootstrap() { }
}
