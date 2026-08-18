package com.rexhub.createapparel.recipe;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModRegistries;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeFactory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

public enum ModRecipeTypes implements IRecipeTypeInfo {
    SPINNING(SpinningRecipe::new),
    WEAVING(WeavingRecipe::new);

    private final ResourceLocation id;
    private final RegistryObject<RecipeSerializer<?>> serializer;
    private final RegistryObject<RecipeType<?>> type;

    ModRecipeTypes(ProcessingRecipeFactory<?> factory) {
        String name = name().toLowerCase(java.util.Locale.ROOT);
        id = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, name);
        serializer = ModRegistries.RECIPE_SERIALIZERS.register(
                name,
                () -> new ProcessingRecipeSerializer<>(factory)
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
    public <T extends RecipeType<?>> T getType() {
        return (T) type.get();
    }

    public <C extends Container, T extends Recipe<C>> Optional<T> find(C inventory, Level level) {
        return level.getRecipeManager().getRecipeFor(getType(), inventory, level);
    }
}
