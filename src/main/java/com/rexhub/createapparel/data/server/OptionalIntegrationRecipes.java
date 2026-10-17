package com.rexhub.createapparel.data.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModFluids;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.recipe.OptionalRecipeCondition;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.CraftingHelper;

import javax.annotation.Nullable;
import java.util.function.Consumer;

final class OptionalIntegrationRecipes {
    private OptionalIntegrationRecipes() { }

    static void build(Consumer<FinishedRecipe> consumer) {
        new ProcessingRecipeBuilder<>(MixingRecipe::new, id("synthetic_fiber"))
                .require(Items.SUGAR).require(Items.SLIME_BALL).require(Fluids.WATER, 250)
                .requiresHeat(HeatCondition.HEATED).output(ModItems.SYNTHETIC_FIBER.get())
                .withCondition(OptionalRecipeCondition.SYNTHETIC_FALLBACK).build(consumer);
        consumer.accept(new NylonRecipe());
        new ProcessingRecipeBuilder<>(MixingRecipe::new, id("mirror_coating_fluid"))
                .require(Fluids.WATER, 1000).require(AllItems.ZINC_NUGGET.get()).require(Items.GLOWSTONE_DUST)
                .requiresHeat(HeatCondition.HEATED).output(ModFluids.MIRROR_COATING_FLUID.get(), 1000)
                .withCondition(OptionalRecipeCondition.MIRROR_FALLBACK).build(consumer);
        consumer.accept(new MirrorDestroyRecipe());
    }

    private record MirrorDestroyRecipe() implements FinishedRecipe {
        @Override public void serializeRecipeData(JsonObject json) {
            var conditions = new JsonArray();
            conditions.add(CraftingHelper.serialize(OptionalRecipeCondition.MIRROR_DESTROY));
            var nonEmptySilver = new JsonObject();
            nonEmptySilver.addProperty("type", "forge:not");
            var emptySilver = new JsonObject();
            emptySilver.addProperty("type", "forge:tag_empty");
            emptySilver.addProperty("tag", "forge:ingots/silver");
            nonEmptySilver.add("value", emptySilver);
            conditions.add(nonEmptySilver);
            json.add("conditions", conditions);
            var mixture = new JsonObject();
            mixture.addProperty("createapparel:mirror_mixture", "destroy:mixture");
            mixture.addProperty("molecule", "createapparel:diamminesilver_i");
            mixture.addProperty("concentration", 1.0f);
            mixture.addProperty("amount", 900);
            var honey = new JsonObject();
            honey.addProperty("fluid", "create:honey");
            honey.addProperty("amount", 100);
            var ingredients = new JsonArray();
            ingredients.add(mixture);
            ingredients.add(honey);
            json.add("ingredients", ingredients);
            var result = new JsonObject();
            result.addProperty("fluid", ModFluids.MIRROR_COATING_FLUID.getId().toString());
            result.addProperty("amount", 1000);
            var results = new JsonArray();
            results.add(result);
            json.add("results", results);
        }
        @Override public ResourceLocation getId() { return id("mixing/mirror_coating_fluid_from_destroy"); }
        @Override public RecipeSerializer<?> getType() { return AllRecipeTypes.MIXING.getSerializer(); }
        @Override @Nullable public JsonObject serializeAdvancement() { return null; }
        @Override @Nullable public ResourceLocation getAdvancementId() { return null; }
    }

    private record NylonRecipe() implements FinishedRecipe {
        @Override public void serializeRecipeData(JsonObject json) {
            var conditions = new JsonArray();
            conditions.add(CraftingHelper.serialize(OptionalRecipeCondition.SYNTHETIC_DESTROY));
            json.add("conditions", conditions);
            json.addProperty("category", "misc");
            var ingredient = new JsonObject();
            ingredient.addProperty("item", "destroy:nylon");
            var ingredients = new JsonArray();
            ingredients.add(ingredient);
            json.add("ingredients", ingredients);
            var result = new JsonObject();
            result.addProperty("item", ModItems.SYNTHETIC_FIBER.getId().toString());
            result.addProperty("count", 2);
            json.add("result", result);
        }

        @Override public ResourceLocation getId() { return id("synthetic_fiber_from_nylon"); }
        @Override public RecipeSerializer<?> getType() { return RecipeSerializer.SHAPELESS_RECIPE; }
        @Override @Nullable public JsonObject serializeAdvancement() { return null; }
        @Override @Nullable public ResourceLocation getAdvancementId() { return null; }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path);
    }
}
