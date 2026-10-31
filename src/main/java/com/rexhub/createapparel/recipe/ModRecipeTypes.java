package com.rexhub.createapparel.recipe;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModRegistries;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe.Factory;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Optional;
import java.util.function.Supplier;

public enum ModRecipeTypes implements IRecipeTypeInfo {
    SILKWORM_REARING(() -> new SilkwormRearingRecipe.Serializer()),
    SPINNING(SpinningRecipe::new),
    WEAVING(WeavingRecipe::new),
    SEWING(() -> new SewingRecipe.Serializer());

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GaugeResetRecipe>> GAUGE_RESET = ModRegistries.RECIPE_SERIALIZERS.register(
            "citizen_gauge_reset", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(GaugeResetRecipe::new));

    private final ResourceLocation id;
    private final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> serializer;
    private final DeferredHolder<RecipeType<?>, RecipeType<?>> type;

    ModRecipeTypes(Factory<?> factory) {
        this(() -> new StandardProcessingRecipe.Serializer<>(factory));
    }

    ModRecipeTypes(Supplier<RecipeSerializer<?>> serializerFactory) {
        String name = name().toLowerCase(java.util.Locale.ROOT);
        id = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, name);
        serializer = ModRegistries.RECIPE_SERIALIZERS.register(
                name,
                serializerFactory
        );
        type = ModRegistries.RECIPE_TYPES.register(name, () -> RecipeType.simple(id));
    }

    public static void bootstrap() {
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends RecipeSerializer<?>> T getSerializer() {
        return (T) serializer.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
        return (RecipeType<R>) type.get();
    }

    public <C extends RecipeInput, T extends Recipe<C>> Optional<T> find(C inventory, Level level) {
        return level.getRecipeManager().getRecipeFor(this.<C, T>getType(), inventory, level)
                .map(net.minecraft.world.item.crafting.RecipeHolder::value);
    }
}
