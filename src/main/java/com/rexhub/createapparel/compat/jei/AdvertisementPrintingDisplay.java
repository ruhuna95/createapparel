package com.rexhub.createapparel.compat.jei;

import com.rexhub.createapparel.advertisement.AdPrintingDeployerRecipe;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.entity.CitizenAppearance;
import com.rexhub.createapparel.entity.PosePreset;
import com.rexhub.createapparel.photo.AdvertisingPhotoData;
import com.rexhub.createapparel.photo.PhotoLayout;
import com.rexhub.createapparel.photo.PhotoRegistries;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class AdvertisementPrintingDisplay {
    static final RecipeType<net.minecraft.world.item.crafting.RecipeHolder<DeployerApplicationRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.parse("create:deploying"));

    private AdvertisementPrintingDisplay() { }

    static Optional<net.minecraft.world.item.crafting.RecipeHolder<DeployerApplicationRecipe>> create(net.minecraft.world.item.crafting.RecipeHolder<AdPrintingDeployerRecipe> holder) {
        var recipe = holder.value();
        var photo = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get());
        new AdvertisingPhotoData(new UUID(0, 1), new UUID(0, 2), null, CitizenAppearance.DEFAULT, CitizenAge.ADULT,
                List.of(), PosePreset.STANDING, PhotoLayout.DEFAULT).write(photo);
        if (!recipe.getRequiredHeldItem().test(photo)) return Optional.empty();
        return java.util.Arrays.stream(recipe.getProcessedItem().getItems()).filter(AdPrintingDeployerRecipe::unprinted).findFirst()
                .map(media -> new com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe.Builder<>(DeployerApplicationRecipe::new, holder.id())
                        .require(recipe.getProcessedItem()).require(recipe.getRequiredHeldItem()).toolNotConsumed()
                        .output(AdPrintingDeployerRecipe.print(media, photo)).build())
                .map(display -> new net.minecraft.world.item.crafting.RecipeHolder<>(holder.id(), display));
    }
}
