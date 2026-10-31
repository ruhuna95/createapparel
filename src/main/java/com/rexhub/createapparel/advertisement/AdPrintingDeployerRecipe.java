package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.photo.AdvertisingPhotoData;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class AdPrintingDeployerRecipe extends DeployerApplicationRecipe {
    public AdPrintingDeployerRecipe(com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams params) { super(params); }

    public static boolean unprinted(ItemStack media) {
        return (media.is(AdvertisementRegistries.POSTER_ITEM.get()) || media.is(AdvertisementRegistries.BILLBOARD_PANEL_ITEM.get()))
                && (!com.rexhub.createapparel.item.ItemData.hasData(media) || AdvertisementSnapshot.load(com.rexhub.createapparel.item.ItemData.readOrEmpty(media).getCompound("Advertisement")).photo() == null);
    }
    public static ItemStack print(ItemStack media, ItemStack photo) {
        var data = AdvertisingPhotoData.read(photo);
        if (!unprinted(media) || data == null) return ItemStack.EMPTY;
        var result = media.copyWithCount(1);
        com.rexhub.createapparel.item.ItemData.update(result, customData -> customData.put("Advertisement", AdvertisementSnapshot.photo(data).save()));
        return result;
    }

    @Override public boolean matches(RecipeWrapper inventory, Level level) {
        return super.matches(inventory, level) && unprinted(inventory.getItem(0))
                && AdvertisingPhotoData.read(inventory.getItem(1)) != null;
    }
    @Override public ItemStack assemble(RecipeWrapper inventory, net.minecraft.core.HolderLookup.Provider access) {
        return print(inventory.getItem(0), inventory.getItem(1));
    }
    @Override public RecipeSerializer<?> getSerializer() { return AdvertisementRegistries.AD_PRINTING.get(); }
    @Override public net.minecraft.world.item.crafting.RecipeType<?> getType() { return AdvertisementRegistries.AD_PRINTING_TYPE.get(); }

    public DeployerApplicationRecipe bind(RecipeWrapper inventory, Level level, net.minecraft.resources.ResourceLocation id) {
        if (!matches(inventory, level)) throw new IllegalArgumentException("Invalid advertisement printing inputs");
        return new com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe.Builder<>(DeployerApplicationRecipe::new, id)
                .require(inventory.getItem(0).getItem()).require(inventory.getItem(1).getItem())
                .toolNotConsumed().output(assemble(inventory, level.registryAccess())).build();
    }

    @SubscribeEvent public static void select(DeployerRecipeSearchEvent event) {
        var level = event.getBlockEntity().getLevel();
        if (level == null || !event.shouldAddRecipeWithPriority(60)) return;
        event.addRecipe(() -> level.getRecipeManager().getRecipeFor(AdvertisementRegistries.AD_PRINTING_TYPE.get(), event.getInventory(), level)
                .filter(com.simibubi.create.AllRecipeTypes.CAN_BE_AUTOMATED)
                .map(holder -> new net.minecraft.world.item.crafting.RecipeHolder<>(holder.id(), holder.value().bind(event.getInventory(), level, holder.id()))), 60);
    }
}
