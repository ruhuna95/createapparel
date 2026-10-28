package com.rexhub.createapparel.compat.jei;

import com.rexhub.createapparel.client.advertisement.PhotographyScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class FashionJeiChecks implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("createapparel", "fashion_client_test");
    }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime = value; }
    @Override public void onRuntimeUnavailable() { runtime = null; }

    public static void checkCamera(PhotographyScreen screen) {
        checkHidden(screen);
        checkPrinting();
    }
    public static void checkHidden(net.minecraft.client.gui.screens.Screen screen) {
        if (runtime == null) throw new AssertionError("JEI runtime is unavailable");
        if (runtime.getScreenHelper().getGuiProperties(screen).isPresent() || runtime.getIngredientListOverlay().isListDisplayed())
            throw new AssertionError("JEI must be disabled on the camera screen");
    }

    private static void checkPrinting() {
        var manager = runtime.getRecipeManager();
        var factory = runtime.getJeiHelpers().getFocusFactory();
        var type = new mezz.jei.api.recipe.RecipeType<>(ResourceLocation.parse("create:deploying"),
                com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe.class);
        for (var media : java.util.List.of(com.rexhub.createapparel.advertisement.AdvertisementRegistries.POSTER_ITEM,
                com.rexhub.createapparel.advertisement.AdvertisementRegistries.BILLBOARD_PANEL_ITEM)) {
            var id = ResourceLocation.parse("createapparel:deploying/" + media.getId().getPath() + "_printing");
            var recipe = manager.createRecipeLookup(type).get().filter(value -> value.getId().equals(id)).findFirst().orElseThrow();
            if (!recipe.shouldKeepHeldItem()) throw new AssertionError("JEI printing must show a reusable photo");
            var category = manager.getRecipeCategory(type);
            var layout = manager.createRecipeLayoutDrawable(category, recipe, factory.getEmptyFocusGroup()).orElseThrow();
            var inputs = layout.getRecipeSlotsView().getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT).stream().flatMap(slot -> slot.getItemStacks()).toList();
            var outputs = layout.getRecipeSlotsView().getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT).stream().flatMap(slot -> slot.getItemStacks()).toList();
            if (outputs.size() != 1 || !outputs.get(0).is(media.get()) || !outputs.get(0).hasFoil()
                    || com.rexhub.createapparel.advertisement.AdvertisementSnapshot.load(outputs.get(0).getOrCreateTag().getCompound("Advertisement")).photo() == null)
                throw new AssertionError("JEI deployer output must show printed media with glint");
            var level = net.minecraft.client.Minecraft.getInstance().level;
            var source = level.getRecipeManager().byKey(id).orElseThrow();
            if (source.getResultItem(level.registryAccess()).hasFoil()) throw new AssertionError("JEI must not alter the server recipe output");
            if (inputs.stream().noneMatch(stack -> stack.is(media.get()))
                    || inputs.stream().noneMatch(stack -> stack.is(com.rexhub.createapparel.photo.PhotoRegistries.ADVERTISING_PHOTO.get())))
                throw new AssertionError("JEI deployer printing inputs are missing");
            for (var role : java.util.List.of(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT)) {
                var focus = factory.createFocus(role, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new net.minecraft.world.item.ItemStack(media.get()));
                if (manager.createRecipeLookup(type).limitFocus(java.util.List.of(focus)).get().noneMatch(value -> value.getId().equals(id)))
                    throw new AssertionError("JEI deployer printing R/U lookup failed: " + id);
                if (manager.createRecipeLookup(mezz.jei.api.constants.RecipeTypes.INFORMATION).limitFocus(java.util.List.of(focus)).get().findAny().isPresent())
                    throw new AssertionError("Advertisement media must not have JEI information pages");
            }
        }
        var photo = new net.minecraft.world.item.ItemStack(com.rexhub.createapparel.photo.PhotoRegistries.ADVERTISING_PHOTO.get());
        new com.rexhub.createapparel.photo.AdvertisingPhotoData(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), null,
                com.rexhub.createapparel.entity.CitizenAppearance.DEFAULT, com.rexhub.createapparel.entity.CitizenAge.ADULT, java.util.List.of(),
                com.rexhub.createapparel.entity.PosePreset.STANDING, com.rexhub.createapparel.photo.PhotoLayout.DEFAULT).write(photo);
        var focus = factory.createFocus(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, photo);
        if (manager.createRecipeLookup(type).limitFocus(java.util.List.of(focus)).get()
                .filter(value -> value.getId().getNamespace().equals("createapparel") && value.getId().getPath().endsWith("_printing")).count() != 2)
            throw new AssertionError("JEI photo usage must find both deployer prints");
        if (manager.createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).get()
                .anyMatch(value -> value.getId().equals(ResourceLocation.parse("createapparel:ad_print_manual_only"))))
            throw new AssertionError("JEI must not list removed manual printing recipes");
    }
}
