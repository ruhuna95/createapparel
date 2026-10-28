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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.wrapper.RecipeWrapper;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class AdPrintingDeployerRecipe extends DeployerApplicationRecipe {
    public AdPrintingDeployerRecipe(ProcessingRecipeBuilder.ProcessingRecipeParams params) { super(params); }

    public static boolean unprinted(ItemStack media) {
        return (media.is(AdvertisementRegistries.POSTER_ITEM.get()) || media.is(AdvertisementRegistries.BILLBOARD_PANEL_ITEM.get()))
                && (!media.hasTag() || AdvertisementSnapshot.load(media.getOrCreateTag().getCompound("Advertisement")).photo() == null);
    }
    public static ItemStack print(ItemStack media, ItemStack photo) {
        var data = AdvertisingPhotoData.read(photo);
        if (!unprinted(media) || data == null) return ItemStack.EMPTY;
        var result = media.copyWithCount(1);
        result.getOrCreateTag().put("Advertisement", AdvertisementSnapshot.photo(data).save());
        return result;
    }

    @Override public boolean matches(RecipeWrapper inventory, Level level) {
        return super.matches(inventory, level) && unprinted(inventory.getItem(0))
                && AdvertisingPhotoData.read(inventory.getItem(1)) != null;
    }
    @Override public ItemStack assemble(RecipeWrapper inventory, RegistryAccess access) {
        return print(inventory.getItem(0), inventory.getItem(1));
    }
    @Override public RecipeSerializer<?> getSerializer() { return AdvertisementRegistries.AD_PRINTING.get(); }
    @Override public net.minecraft.world.item.crafting.RecipeType<?> getType() { return AdvertisementRegistries.AD_PRINTING_TYPE.get(); }

    public DeployerApplicationRecipe bind(RecipeWrapper inventory, Level level) {
        if (!matches(inventory, level)) throw new IllegalArgumentException("Invalid advertisement printing inputs");
        return new ProcessingRecipeBuilder<>(DeployerApplicationRecipe::new, getId())
                .require(inventory.getItem(0).getItem()).require(inventory.getItem(1).getItem())
                .toolNotConsumed().output(assemble(inventory, level.registryAccess())).build();
    }

    @SubscribeEvent public static void select(DeployerRecipeSearchEvent event) {
        var level = event.getBlockEntity().getLevel();
        if (level == null || !event.shouldAddRecipeWithPriority(60)) return;
        event.addRecipe(() -> level.getRecipeManager().getRecipeFor(AdvertisementRegistries.AD_PRINTING_TYPE.get(), event.getInventory(), level)
                .filter(com.simibubi.create.AllRecipeTypes.CAN_BE_AUTOMATED)
                .map(recipe -> recipe.bind(event.getInventory(), level)), 60);
    }
}
