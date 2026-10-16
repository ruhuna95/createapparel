package com.rexhub.createapparel.compat.jei;

import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.init.GarmentCreativePresets;
import com.rexhub.createapparel.recipe.SewingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public record SewingRecipeDisplay(SewingRecipe recipe, List<ItemStack> fabrics, List<ItemStack> threads,
                                  ItemStack pattern, ItemStack result) {
    public SewingRecipeDisplay {
        fabrics = List.copyOf(fabrics);
        threads = List.copyOf(threads);
    }

    public static final class Factory {
        private final List<ItemStack> items;
        private final Function<ResourceLocation, Optional<GarmentDefinition>> definitions;
        private final Map<List<TagKey<Item>>, List<ItemStack>> fabrics = new HashMap<>();
        private final Map<List<TagKey<Item>>, List<ItemStack>> threads = new HashMap<>();

        public Factory(Function<ResourceLocation, Optional<GarmentDefinition>> definitions) {
            this.definitions = definitions;
            items = ForgeRegistries.ITEMS.getValues().stream().map(ItemStack::new)
                    .filter(stack -> !stack.isEmpty()).toList();
        }

        public Optional<SewingRecipeDisplay> create(SewingRecipe recipe) {
            GarmentDefinition definition = definitions.apply(recipe.getResultId()).orElse(null);
            Item resultItem = ForgeRegistries.ITEMS.getValue(recipe.getResultId());
            if (definition == null || !(resultItem instanceof GarmentItem)) {
                return Optional.empty();
            }
            List<ItemStack> fabricCandidates = counted(fabrics.computeIfAbsent(recipe.fabricTags(),
                    tags -> items.stream().filter(recipe::matchesFabric).toList()), recipe.getFabricCount());
            List<ItemStack> threadCandidates = counted(threads.computeIfAbsent(recipe.threadTags(),
                    tags -> items.stream().filter(recipe::matchesThread).toList()), recipe.getThreadCount());
            ItemStack pattern = items.stream().filter(recipe::matchesPatternItem).findFirst()
                    .map(ItemStack::copy).orElse(ItemStack.EMPTY);
            if (fabricCandidates.isEmpty() || threadCandidates.isEmpty() || pattern.isEmpty()) {
                return Optional.empty();
            }
            String design = GarmentData.defaultFor(definition).design();
            new PatternData(recipe.getId(), design, Optional.empty(), Optional.empty()).write(pattern);
            ItemStack result = resultItem.getDefaultInstance();
            GarmentCreativePresets.appearance(recipe.getResultId())
                    .filter(appearance -> definition.designs().contains(appearance.design()))
                    .orElseGet(() -> GarmentData.defaultFor(definition)).write(result);
            return Optional.of(new SewingRecipeDisplay(recipe, fabricCandidates, threadCandidates, pattern, result));
        }

        private static List<ItemStack> counted(List<ItemStack> candidates, int count) {
            return candidates.stream().map(stack -> stack.copyWithCount(count)).toList();
        }
    }
}
