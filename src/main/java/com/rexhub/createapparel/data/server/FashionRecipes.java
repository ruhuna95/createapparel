package com.rexhub.createapparel.data.server;

import com.rexhub.createapparel.advertisement.AdvertisementRegistries;
import com.rexhub.createapparel.fashion.FashionRegistries;
import com.rexhub.createapparel.photo.PhotoRegistries;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllBlocks;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import java.util.function.Consumer;

public final class FashionRecipes {
    private FashionRecipes() { }
    public static void build(RecipeOutput consumer) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, FashionRegistries.CASH_REGISTER_ITEM.get()).pattern(" GT").pattern("IRI").pattern("III")
                .define('G', Items.GLASS_PANE).define('T', AllItems.ELECTRON_TUBE.get()).define('I', AllItems.IRON_SHEET.get()).define('R', Items.REDSTONE)
                .unlockedBy("has_iron", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT)).save(consumer);
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, PhotoRegistries.CAMERA.get()).pattern(" I ").pattern("IGI").pattern(" R ")
                .define('I', Items.IRON_INGOT).define('G', Items.GLASS).define('R', Items.REDSTONE)
                .unlockedBy("has_iron", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT)).save(consumer);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, AdvertisementRegistries.POSTER_ITEM.get()).pattern("PP").pattern("PP")
                .define('P', Items.PAPER).unlockedBy("has_paper", InventoryChangeTrigger.TriggerInstance.hasItems(Items.PAPER)).save(consumer);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, AdvertisementRegistries.BILLBOARD_PANEL_ITEM.get(), 2)
                .requires(AdvertisementRegistries.POSTER_ITEM.get()).requires(Items.GLASS_PANE)
                .unlockedBy("has_poster", InventoryChangeTrigger.TriggerInstance.hasItems(AdvertisementRegistries.POSTER_ITEM.get())).save(consumer);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, AdvertisementRegistries.SIGNAGE_PANEL_ITEM.get(), 2).pattern("GG").pattern("BT")
                .define('G', Items.GLASS).define('B', AllItems.BRASS_SHEET.get()).define('T', AllItems.ELECTRON_TUBE.get())
                .unlockedBy("has_brass", InventoryChangeTrigger.TriggerInstance.hasItems(AllItems.BRASS_SHEET.get())).save(consumer);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, AdvertisementRegistries.SIGNAGE_BROADCASTER_ITEM.get()).pattern(" T ").pattern("RBR")
                .define('T', AllItems.ELECTRON_TUBE.get()).define('R', AllBlocks.REDSTONE_LINK.get()).define('B', AllBlocks.BRASS_CASING.get())
                .unlockedBy("has_brass", InventoryChangeTrigger.TriggerInstance.hasItems(AllItems.BRASS_SHEET.get())).save(consumer);
        SpecialRecipeBuilder.special(com.rexhub.createapparel.photo.PhotoCopyRecipe::new).save(consumer, "createapparel:photo_copy_manual_only");
        for (var media : java.util.List.of(AdvertisementRegistries.POSTER_ITEM, AdvertisementRegistries.BILLBOARD_PANEL_ITEM)) {
            new com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe.Builder<>(
                    com.rexhub.createapparel.advertisement.AdPrintingDeployerRecipe::new,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createapparel", media.getId().getPath() + "_printing"))
                    .require(media.get()).require(PhotoRegistries.ADVERTISING_PHOTO.get()).toolNotConsumed().output(media.get()).build(consumer);
        }
    }
}
