package com.rexhub.createapparel.recipe;

import com.google.gson.JsonObject;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

import java.util.List;

public final class OptionalRecipeCondition implements ICondition {
    public static final OptionalRecipeCondition SYNTHETIC_FALLBACK = new OptionalRecipeCondition(
            "synthetic_fiber_fallback", RecipeRouteSelection.Product.SYNTHETIC_FIBER, true);
    public static final OptionalRecipeCondition SYNTHETIC_DESTROY = new OptionalRecipeCondition(
            "synthetic_fiber_destroy", RecipeRouteSelection.Product.SYNTHETIC_FIBER, false);
    public static final OptionalRecipeCondition MIRROR_FALLBACK = new OptionalRecipeCondition(
            "mirror_coating_fallback", RecipeRouteSelection.Product.MIRROR_COATING, true);
    public static final OptionalRecipeCondition MIRROR_DESTROY = new OptionalRecipeCondition(
            "mirror_coating_destroy", RecipeRouteSelection.Product.MIRROR_COATING, false);

    private final ResourceLocation id;
    private final RecipeRouteSelection.Product product;
    private final boolean fallback;

    private OptionalRecipeCondition(String path, RecipeRouteSelection.Product product, boolean fallback) {
        id = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path);
        this.product = product;
        this.fallback = fallback;
    }

    public static void register() {
        for (var condition : List.of(SYNTHETIC_FALLBACK, SYNTHETIC_DESTROY, MIRROR_FALLBACK, MIRROR_DESTROY)) {
            CraftingHelper.register(new IConditionSerializer<OptionalRecipeCondition>() {
                @Override public void write(JsonObject json, OptionalRecipeCondition value) { }
                @Override public OptionalRecipeCondition read(JsonObject json) { return condition; }
                @Override public ResourceLocation getID() { return condition.id; }
            });
        }
    }

    @Override public ResourceLocation getID() { return id; }

    @Override public boolean test(IContext context) {
        return OptionalRecipeRoutes.selection().useFallback(product) == fallback;
    }
}
