package com.rexhub.createapparel.recipe;

import com.mojang.serialization.MapCodec;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.common.conditions.ICondition;

import java.util.List;

public final class OptionalRecipeCondition implements ICondition {
    private static final TagKey<Item> SILVER_INGOTS = ItemTags.create(ResourceLocation.parse("c:ingots/silver"));
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

    private final MapCodec<OptionalRecipeCondition> codec = MapCodec.unit(this);

    public static void register(IEventBus bus) {
        DeferredRegister<MapCodec<? extends ICondition>> serializers =
                DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, CreateApparel.MOD_ID);
        for (var condition : List.of(SYNTHETIC_FALLBACK, SYNTHETIC_DESTROY, MIRROR_FALLBACK, MIRROR_DESTROY)) {
            serializers.register(condition.id.getPath(), () -> condition.codec);
        }
        serializers.register(bus);
    }

    public ResourceLocation getID() { return id; }

    @Override public MapCodec<? extends ICondition> codec() { return codec; }

    @Override public boolean test(IContext context) {
        boolean silverAvailable = product != RecipeRouteSelection.Product.MIRROR_COATING || !context.getTag(SILVER_INGOTS).isEmpty();
        var selection = OptionalRecipeRoutes.selection();
        return fallback ? selection.useFallback(product, silverAvailable) : selection.useDestroy(product, silverAvailable);
    }
}
