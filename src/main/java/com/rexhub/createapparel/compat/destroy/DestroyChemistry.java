package com.rexhub.createapparel.compat.destroy;

import petrolpark.mc.destroy.chemistry.legacy.LegacyElement;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMolecularStructure;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.recipe.OptionalRecipeRoutes;
import com.rexhub.createapparel.recipe.RecipeRouteSelection;
import net.minecraft.tags.ItemTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class DestroyChemistry {
    public static final float SILVER_MOLES_PER_INGOT = 4.5f;
    public static LegacySpecies SILVER_ION;
    public static LegacySpecies DIAMMINESILVER_I;
    public static LegacyReaction SILVER_DISSOLUTION;
    public static LegacyReaction SILVER_AMMONIA_COMPLEXATION;

    private DestroyChemistry() { }

    public static void register() {
        if (SILVER_ION != null) return;
        SILVER_ION = new LegacySpecies.MoleculeBuilder(CreateApparel.MOD_ID)
            .id("silver_ion").structure(LegacyMolecularStructure.atom(LegacyElement.fromSymbol("Ag"), 1)).build();
        DIAMMINESILVER_I = new LegacySpecies.MoleculeBuilder(CreateApparel.MOD_ID)
            .id("diamminesilver_i").structure(LegacyMolecularStructure.atom(LegacyElement.fromSymbol("Ag"), 1)
                    .addGroup(ammonia()).addGroup(ammonia())).build();
        SILVER_DISSOLUTION = new LegacyReaction.ReactionBuilder(CreateApparel.MOD_ID)
            .id("silver_dissolution")
            .addSimpleItemTagReactant(ItemTags.create(ResourceLocation.parse("c:ingots/silver")), SILVER_MOLES_PER_INGOT)
            .addReactant(DestroyMolecules.PROTON, 2).addReactant(DestroyMolecules.NITRATE)
            .addProduct(SILVER_ION).addProduct(DestroyMolecules.NITROGEN_DIOXIDE).addProduct(DestroyMolecules.WATER)
            .includeInJeiIf(DestroyChemistry::isSelected).build();
        SILVER_AMMONIA_COMPLEXATION = new LegacyReaction.ReactionBuilder(CreateApparel.MOD_ID)
            .id("silver_ammonia_complexation").addReactant(SILVER_ION).addReactant(DestroyMolecules.AMMONIA, 2)
            .addProduct(DIAMMINESILVER_I).includeInJeiIf(DestroyChemistry::isSelected).build();
    }

    private static LegacyMolecularStructure ammonia() {
        return LegacyMolecularStructure.atom(LegacyElement.NITROGEN)
                .addAtom(LegacyElement.HYDROGEN).addAtom(LegacyElement.HYDROGEN).addAtom(LegacyElement.HYDROGEN);
    }

    private static boolean isSelected() {
        return OptionalRecipeRoutes.selection().useDestroy(RecipeRouteSelection.Product.MIRROR_COATING,
                BuiltInRegistries.ITEM.getTag(ItemTags.create(ResourceLocation.parse("c:ingots/silver"))).filter(tag -> tag.size() > 0).isPresent());
    }
}
