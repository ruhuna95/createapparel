package com.rexhub.createapparel.recipe;

import com.rexhub.createapparel.material.FabricAttributeMap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class SewingRecipeInputIndex {
    private static final Map<RecipeManager, SewingRecipeInputIndex> INDICES = new WeakHashMap<>();
    private static final SewingRecipeInputIndex EMPTY = new SewingRecipeInputIndex(List.of());
    private final Set<TagKey<Item>> fabricTags;
    private final Set<TagKey<Item>> threadTags;

    public SewingRecipeInputIndex(List<SewingRecipe> recipes) {
        Set<TagKey<Item>> fabrics = new HashSet<>();
        Set<TagKey<Item>> threads = new HashSet<>();
        for (SewingRecipe recipe : recipes) {
            fabrics.addAll(recipe.fabricTags());
            threads.addAll(recipe.threadTags());
        }
        fabricTags = Set.copyOf(fabrics);
        threadTags = Set.copyOf(threads);
    }

    public static synchronized SewingRecipeInputIndex get(RecipeManager manager) {
        return INDICES.getOrDefault(manager, EMPTY);
    }

    public static synchronized void rebuild(RecipeManager manager) {
        var recipes = manager.getAllRecipesFor(SewingRecipe.recipeType()).stream().map(holder -> {
            holder.value().bindId(holder.id());
            return holder.value();
        }).toList();
        INDICES.put(manager, new SewingRecipeInputIndex(recipes));
    }

    public boolean acceptsFabric(ItemStack stack) {
        return FabricAttributeMap.isDyed(stack) && fabricTags.stream().anyMatch(stack::is);
    }

    public boolean acceptsThread(ItemStack stack) {
        return !stack.isEmpty() && threadTags.stream().anyMatch(stack::is);
    }
}
