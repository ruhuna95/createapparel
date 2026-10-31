package com.rexhub.createapparel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class TestRecipes {
    private TestRecipes() { }

    public static ResourceLocation id(Recipe<?> recipe) {
        var server = java.util.Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer());
        return server.getRecipeManager().getRecipes().stream().filter(holder -> holder.value() == recipe)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Test recipe is not registered")).id();
    }
}
