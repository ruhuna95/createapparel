package com.rexhub.createapparel.compat.jei;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.client.screen.CitizenGaugeScreen;
import com.rexhub.createapparel.client.screen.CitizenScreen;
import com.rexhub.createapparel.client.screen.WardrobeScreen;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.recipe.ModRecipeTypes;
import com.rexhub.createapparel.recipe.SewingRecipe;
import com.rexhub.createapparel.recipe.SilkwormRearingRecipe;
import com.rexhub.createapparel.recipe.SpinningRecipe;
import com.rexhub.createapparel.recipe.WeavingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@JeiPlugin
public final class ApparelJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(CitizenGaugeScreen.class, screen -> null);
        registration.addGuiScreenHandler(CitizenScreen.class, screen -> null);
        registration.addGuiScreenHandler(WardrobeScreen.class, screen -> null);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new ProcessingRecipeCategory(gui, ApparelJeiRecipeTypes.SPINNING,
                        new ItemStack(ModBlocks.SPINNING_MACHINE.get())),
                new ProcessingRecipeCategory(gui, ApparelJeiRecipeTypes.WEAVING,
                        new ItemStack(ModBlocks.LOOM.get())),
                new SewingRecipeCategory(gui, new ItemStack(ModBlocks.SEWING_MACHINE.get())),
                new SilkwormRearingCategory(gui, new ItemStack(ModBlocks.SILKWORM_REARING_TRAY.get())));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.SPINNING_MACHINE.get()), ApparelJeiRecipeTypes.SPINNING);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.LOOM.get()), ApparelJeiRecipeTypes.WEAVING);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.SEWING_MACHINE.get()), ApparelJeiRecipeTypes.SEWING);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.SILKWORM_REARING_TRAY.get()), ApparelJeiRecipeTypes.SILKWORM_REARING);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RecipeManager manager = level.getRecipeManager();
        var registries = level.registryAccess();
        registration.addRecipes(ApparelJeiRecipeTypes.SPINNING,
                displays(manager, ModRecipeTypes.SPINNING, SpinningRecipe.class,
                        recipe -> valid(ProcessingRecipeDisplay.spinning(recipe, registries))));
        registration.addRecipes(ApparelJeiRecipeTypes.WEAVING,
                displays(manager, ModRecipeTypes.WEAVING, WeavingRecipe.class,
                        recipe -> valid(ProcessingRecipeDisplay.weaving(recipe, registries))));
        var rearing = new ArrayList<>(
                displays(manager, ModRecipeTypes.SILKWORM_REARING, SilkwormRearingRecipe.class,
                        recipe -> Optional.of(SilkwormRearingDisplay.tray(recipe, registries)).filter(SilkwormRearingDisplay::isValid)));
        var silkworm = SilkwormRearingDisplay.silkworm();
        if (silkworm.isValid()) rearing.add(silkworm);
        registration.addRecipes(ApparelJeiRecipeTypes.SILKWORM_REARING, rearing);
        var sewing = new SewingRecipeDisplay.Factory(ClientGarmentAssets::getDefinition);
        registration.addRecipes(ApparelJeiRecipeTypes.SEWING,
                displays(manager, ModRecipeTypes.SEWING, SewingRecipe.class, sewing::create));
    }

    private static Optional<ProcessingRecipeDisplay> valid(ProcessingRecipeDisplay display) {
        return display.isValid() ? Optional.of(display) : Optional.empty();
    }

    private static <R extends Recipe<?>, D> List<D> displays(RecipeManager manager, ModRecipeTypes type,
                                                            Class<R> recipeClass,
                                                            Function<R, Optional<D>> factory) {
        LinkedHashMap<ResourceLocation, D> displays = new LinkedHashMap<>();
        manager.getRecipes().stream().filter(recipe -> recipe.getType() == type.getType())
                .filter(recipeClass::isInstance).map(recipeClass::cast)
                .sorted(Comparator.comparing(recipe -> recipe.getId().toString()))
                .forEach(recipe -> {
                    try {
                        Optional<D> display = factory.apply(recipe);
                        if (display.isPresent()) {
                            displays.putIfAbsent(recipe.getId(), display.get());
                        } else {
                            LOGGER.warn("Skipping JEI recipe with unresolved ingredients or result: {}", recipe.getId());
                        }
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Unable to display JEI recipe {}", recipe.getId(), exception);
                    }
                });
        return List.copyOf(displays.values());
    }
}
