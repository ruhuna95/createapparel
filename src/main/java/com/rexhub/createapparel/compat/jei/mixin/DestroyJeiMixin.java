package com.rexhub.createapparel.compat.jei.mixin;

import petrolpark.mc.destroy.compat.jei.DestroyJEI;
import petrolpark.mc.destroy.compat.jei.category.ReactionCategory;
import petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe;
import com.rexhub.createapparel.compat.destroy.DestroyChemistry;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DestroyJEI.class, remap = false)
abstract class DestroyJeiMixin {
    @Inject(method = "registerRecipes", at = @At("HEAD"))
    private void createapparel$refreshSilverRecipes(IRecipeRegistration registration, CallbackInfo callback) {
        var level = Minecraft.getInstance().level;
        boolean selected = level != null && level.getRecipeManager().byKey(ResourceLocation.parse(
                "createapparel:mixing/mirror_coating_fluid_from_destroy")).isPresent();
        for (var reaction : java.util.List.of(DestroyChemistry.SILVER_DISSOLUTION, DestroyChemistry.SILVER_AMMONIA_COMPLEXATION)) {
            if (selected) ReactionCategory.RECIPES.put(reaction, ReactionRecipe.create(reaction));
            else ReactionCategory.RECIPES.remove(reaction);
        }
    }
}
