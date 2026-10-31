package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.item.SewingPatternItem;
import com.rexhub.createapparel.recipe.SewingRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public final class SewingMachineGoggleTooltip {
    private SewingMachineGoggleTooltip() {
    }

    public static boolean addContents(List<Component> tooltip, ItemStack fabric, ItemStack thread,
                                      ItemStack pattern, @Nullable Level level) {
        Optional<SewingRecipe> configuredRecipe = level == null ? Optional.empty() : PatternData.fromStack(pattern)
                .flatMap(data -> level.getRecipeManager().byKey(data.recipe()))
                .map(net.minecraft.world.item.crafting.RecipeHolder::value)
                .filter(SewingRecipe.class::isInstance)
                .map(SewingRecipe.class::cast)
                .filter(recipe -> recipe.matchesPatternItem(pattern));
        boolean added = MachineGoggleTooltip.addStack(tooltip, "tooltip.createapparel.machine.fabric",
                fabric, configuredRecipe.map(SewingRecipe::getFabricCount).orElse(0));
        added |= MachineGoggleTooltip.addStack(tooltip, "tooltip.createapparel.machine.thread",
                thread, configuredRecipe.map(SewingRecipe::getThreadCount).orElse(0));
        if (!pattern.isEmpty()) {
            added |= MachineGoggleTooltip.addValue(tooltip, "tooltip.createapparel.machine.pattern",
                    SewingPatternItem.getConfiguredGarmentName(pattern, level));
        }
        return added;
    }
}
