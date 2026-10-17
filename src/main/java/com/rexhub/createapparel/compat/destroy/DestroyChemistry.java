package com.rexhub.createapparel.compat.destroy;

import com.petrolpark.destroy.chemistry.legacy.LegacyElement;
import com.petrolpark.destroy.chemistry.legacy.LegacyMolecularStructure;
import com.petrolpark.destroy.chemistry.legacy.LegacyReaction;
import com.petrolpark.destroy.chemistry.legacy.LegacySpecies;
import com.petrolpark.destroy.chemistry.legacy.index.DestroyMolecules;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.recipe.OptionalRecipeRoutes;
import com.rexhub.createapparel.recipe.RecipeRouteSelection;
import net.minecraft.tags.ItemTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public final class DestroyChemistry {
    public static final float SILVER_MOLES_PER_INGOT = 4.5f;
    public static final LegacySpecies SILVER_ION = new LegacySpecies.MoleculeBuilder(CreateApparel.MOD_ID)
            .id("silver_ion").structure(LegacyMolecularStructure.atom(LegacyElement.fromSymbol("Ag"), 1)).build();
    public static final LegacySpecies DIAMMINESILVER_I = new LegacySpecies.MoleculeBuilder(CreateApparel.MOD_ID)
            .id("diamminesilver_i").structure(LegacyMolecularStructure.atom(LegacyElement.fromSymbol("Ag"), 1)
                    .addGroup(ammonia()).addGroup(ammonia())).build();
    public static final LegacyReaction SILVER_DISSOLUTION = new LegacyReaction.ReactionBuilder(CreateApparel.MOD_ID)
            .id("silver_dissolution")
            .addSimpleItemTagReactant(ItemTags.create(ResourceLocation.parse("forge:ingots/silver")), SILVER_MOLES_PER_INGOT)
            .addReactant(DestroyMolecules.PROTON, 2).addReactant(DestroyMolecules.NITRATE)
            .addProduct(SILVER_ION).addProduct(DestroyMolecules.NITROGEN_DIOXIDE).addProduct(DestroyMolecules.WATER)
            .includeInJeiIf(DestroyChemistry::isSelected).build();
    public static final LegacyReaction SILVER_AMMONIA_COMPLEXATION = new LegacyReaction.ReactionBuilder(CreateApparel.MOD_ID)
            .id("silver_ammonia_complexation").addReactant(SILVER_ION).addReactant(DestroyMolecules.AMMONIA, 2)
            .addProduct(DIAMMINESILVER_I).includeInJeiIf(DestroyChemistry::isSelected).build();

    private DestroyChemistry() { }

    public static void register() {
        com.petrolpark.destroy.core.recipe.ingredient.fluid.MixtureFluidIngredient.MIXTURE_FLUID_INGREDIENT_SUBTYPES
                .computeIfAbsent("createapparel:mirror_mixture", ignored -> MirrorMixtureIngredient.TYPE);
    }

    private static LegacyMolecularStructure ammonia() {
        return LegacyMolecularStructure.atom(LegacyElement.NITROGEN)
                .addAtom(LegacyElement.HYDROGEN).addAtom(LegacyElement.HYDROGEN).addAtom(LegacyElement.HYDROGEN);
    }

    private static boolean isSelected() {
        return OptionalRecipeRoutes.selection().useDestroy(RecipeRouteSelection.Product.MIRROR_COATING,
                !ForgeRegistries.ITEMS.tags().getTag(ItemTags.create(ResourceLocation.parse("forge:ingots/silver"))).isEmpty());
    }
}
