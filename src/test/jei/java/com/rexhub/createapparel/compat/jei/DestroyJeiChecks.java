package com.rexhub.createapparel.compat.jei;

import petrolpark.mc.destroy.chemistry.legacy.LegacyElement;
import petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.stream.Collectors;

import static com.rexhub.createapparel.compat.jei.JeiClientTestLauncher.check;

final class DestroyJeiChecks {
    private DestroyJeiChecks() { }

    static void audit(IJeiRuntime runtime) {
        var mc = Minecraft.getInstance();
        boolean selected = mc.level.getRecipeManager().byKey(ResourceLocation.parse(
                "createapparel:mixing/mirror_coating_fluid_from_destroy")).isPresent();
        var type = mezz.jei.api.recipe.RecipeType.<ReactionRecipe>createRecipeHolderType(ResourceLocation.parse("destroy:reaction"));
        var reactions = runtime.getRecipeManager().createRecipeLookup(type).get()
                .map(recipe -> recipe.value().getReaction().getFullId()).filter(id -> id.startsWith("createapparel:"))
                .collect(Collectors.toSet());
        check(reactions.equals(selected ? Set.of("createapparel:silver_dissolution", "createapparel:silver_ammonia_complexation")
                : Set.of()), "Destroy chemical JEI recipes follow the synchronized final recipe: " + reactions);
        check(LegacyElement.fromSymbol("Ag").getPartial().get() != mc.getModelManager().getMissingModel(),
                "Silver atom's model is baked rather than missing");
    }
}
