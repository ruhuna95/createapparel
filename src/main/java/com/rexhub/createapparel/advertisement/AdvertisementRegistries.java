package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class AdvertisementRegistries {
    public static final DeferredHolder<Block, Block> POSTER = panel("poster", AdvertisementPanelBlock.Kind.POSTER);
    public static final DeferredHolder<Block, Block> BILLBOARD_PANEL = panel("billboard_panel", AdvertisementPanelBlock.Kind.BILLBOARD);
    public static final DeferredHolder<Block, Block> SIGNAGE_PANEL = panel("signage_panel", AdvertisementPanelBlock.Kind.SIGNAGE);
    public static final DeferredHolder<Block, Block> SIGNAGE_BROADCASTER = ModRegistries.BLOCKS.register("signage_broadcaster",
            () -> new SignageBroadcasterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2).noOcclusion()));
    public static final DeferredHolder<Item, Item> POSTER_ITEM = item("poster", POSTER);
    public static final DeferredHolder<Item, Item> BILLBOARD_PANEL_ITEM = item("billboard_panel", BILLBOARD_PANEL);
    public static final DeferredHolder<Item, Item> SIGNAGE_PANEL_ITEM = item("signage_panel", SIGNAGE_PANEL);
    public static final DeferredHolder<Item, Item> SIGNAGE_BROADCASTER_ITEM = item("signage_broadcaster", SIGNAGE_BROADCASTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvertisementPanelBlockEntity>> PANEL_ENTITY = ModRegistries.BLOCK_ENTITY_TYPES.register("advertisement_panel",
            () -> BlockEntityType.Builder.of(AdvertisementPanelBlockEntity::new, POSTER.get(), BILLBOARD_PANEL.get(), SIGNAGE_PANEL.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<AdvertisementControlMenu>> CONTROL = ModRegistries.MENU_TYPES.register("advertisement_control",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(AdvertisementControlMenu::new));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SignageBroadcasterBlockEntity>> BROADCASTER_ENTITY = ModRegistries.BLOCK_ENTITY_TYPES.register("signage_broadcaster",
            () -> BlockEntityType.Builder.of(SignageBroadcasterBlockEntity::new, SIGNAGE_BROADCASTER.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, net.minecraft.world.item.crafting.RecipeSerializer<com.rexhub.createapparel.photo.PhotoCopyRecipe>> PHOTO_COPY = ModRegistries.RECIPE_SERIALIZERS.register("photo_copy",
            () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(com.rexhub.createapparel.photo.PhotoCopyRecipe::new));
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, net.minecraft.world.item.crafting.RecipeSerializer<AdPrintingDeployerRecipe>> AD_PRINTING = ModRegistries.RECIPE_SERIALIZERS.register("ad_printing",
            () -> new com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe.Serializer<>(AdPrintingDeployerRecipe::new));
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeType<?>, net.minecraft.world.item.crafting.RecipeType<AdPrintingDeployerRecipe>> AD_PRINTING_TYPE = ModRegistries.RECIPE_TYPES.register("ad_printing",
            () -> net.minecraft.world.item.crafting.RecipeType.simple(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createapparel", "ad_printing")));
    private AdvertisementRegistries() { }
    private static DeferredHolder<Block, Block> panel(String id, AdvertisementPanelBlock.Kind kind) {
        return ModRegistries.BLOCKS.register(id, () -> new AdvertisementPanelBlock(kind == AdvertisementPanelBlock.Kind.POSTER
                ? BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).instabreak().noCollission().noOcclusion()
                        .sound(new net.neoforged.neoforge.common.util.DeferredSoundType(1, 1, () -> net.minecraft.sounds.SoundEvents.PAINTING_BREAK,
                                () -> net.minecraft.sounds.SoundEvents.WOOD_STEP, () -> net.minecraft.sounds.SoundEvents.PAINTING_PLACE,
                                () -> net.minecraft.sounds.SoundEvents.WOOD_HIT, () -> net.minecraft.sounds.SoundEvents.WOOD_FALL))
                : BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(1).noOcclusion().lightLevel(state -> kind == AdvertisementPanelBlock.Kind.SIGNAGE ? 15 : 0), kind));
    }
    private static DeferredHolder<Item, Item> item(String id, DeferredHolder<Block, Block> block) {
        return ModRegistries.ITEMS.register(id, () -> id.equals("poster") || id.equals("billboard_panel")
                ? new AdvertisementBlockItem(block.get(), new Item.Properties()) : new BlockItem(block.get(), new Item.Properties()));
    }
    public static void bootstrap() { }
}
