package com.rexhub.createapparel.data.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModFluids;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.recipe.OptionalRecipeCondition;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllFluids;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.concurrent.CompletableFuture;

public final class OptionalIntegrationRecipes implements DataProvider {
    private final PackOutput.PathProvider recipes;

    public OptionalIntegrationRecipes(PackOutput output) {
        recipes = output.createRegistryElementsPathProvider(Registries.RECIPE);
    }

    static void build(RecipeOutput output) {
        new StandardProcessingRecipe.Builder<>(MixingRecipe::new, id("synthetic_fiber"))
                .require(Items.SUGAR).require(Items.SLIME_BALL).require(Fluids.WATER, 250)
                .requiresHeat(HeatCondition.HEATED).output(ModItems.SYNTHETIC_FIBER.get())
                .withCondition(OptionalRecipeCondition.SYNTHETIC_FALLBACK).build(output);
        new StandardProcessingRecipe.Builder<>(MixingRecipe::new, id("mirror_coating_fluid"))
                .require(Fluids.WATER, 1000).require(AllItems.ZINC_NUGGET.get()).require(Items.GLOWSTONE_DUST)
                .requiresHeat(HeatCondition.HEATED).output(ModFluids.MIRROR_COATING_FLUID.get(), 1000)
                .withCondition(OptionalRecipeCondition.MIRROR_FALLBACK).build(output);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return CompletableFuture.allOf(
                DataProvider.saveStable(output, nylon(), recipes.json(id("synthetic_fiber_from_nylon"))),
                DataProvider.saveStable(output, mirror(), recipes.json(id("mixing/mirror_coating_fluid_from_destroy"))));
    }

    private static JsonObject nylon() {
        var recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");
        recipe.addProperty("category", "misc");
        var ingredient = new JsonObject();
        ingredient.addProperty("item", "destroy:nylon");
        var ingredients = new JsonArray();
        ingredients.add(ingredient);
        recipe.add("ingredients", ingredients);
        var result = new JsonObject();
        result.addProperty("id", ModItems.SYNTHETIC_FIBER.getId().toString());
        result.addProperty("count", 2);
        recipe.add("result", result);
        condition(recipe, OptionalRecipeCondition.SYNTHETIC_DESTROY);
        return recipe;
    }

    private static JsonObject mirror() {
        var recipe = new JsonObject();
        recipe.addProperty("type", "create:mixing");
        var mixture = new JsonObject();
        mixture.addProperty("type", "createapparel:mirror_mixture");
        mixture.addProperty("molecule", "createapparel:diamminesilver_i");
        mixture.addProperty("min_concentration", 0.9F);
        mixture.addProperty("max_concentration", 1.1F);
        mixture.addProperty("amount", 900);
        var ingredients = new JsonArray();
        ingredients.add(mixture);
        var honey = net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient.FLAT_CODEC.encodeStart(
                JsonOps.INSTANCE, SizedFluidIngredient.of((net.minecraft.world.level.material.Fluid) AllFluids.HONEY.getSource(), 100)).getOrThrow().getAsJsonObject();
        honey.addProperty("type", "neoforge:single");
        ingredients.add(honey);
        recipe.add("ingredients", ingredients);
        var results = new JsonArray();
        results.add(FluidStack.CODEC.encodeStart(JsonOps.INSTANCE, new FluidStack(ModFluids.MIRROR_COATING_FLUID.get(), 1000)).getOrThrow());
        recipe.add("results", results);
        condition(recipe, OptionalRecipeCondition.MIRROR_DESTROY);
        recipe.getAsJsonArray("neoforge:conditions").add(ICondition.CODEC.encodeStart(JsonOps.INSTANCE,
                new net.neoforged.neoforge.common.conditions.NotCondition(
                        new net.neoforged.neoforge.common.conditions.TagEmptyCondition("c:ingots/silver"))).getOrThrow());
        return recipe;
    }

    private static void condition(JsonObject recipe, ICondition condition) {
        var conditions = new JsonArray();
        conditions.add(ICondition.CODEC.encodeStart(JsonOps.INSTANCE, condition).getOrThrow());
        recipe.add("neoforge:conditions", conditions);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path);
    }

    @Override public String getName() { return "Create: Apparel optional integration recipes"; }
}
