package com.rexhub.createapparel.compat.destroy;

import com.petrolpark.destroy.chemistry.legacy.LegacyMixture;
import com.petrolpark.destroy.core.recipe.ingredient.fluid.IonFluidIngredient;
import com.petrolpark.destroy.core.recipe.ingredient.fluid.MixtureFluidIngredientSubType;
import com.petrolpark.destroy.core.recipe.ingredient.fluid.MoleculeFluidIngredient;

public final class MirrorMixtureIngredient extends IonFluidIngredient {
    public static final MixtureFluidIngredientSubType<MoleculeFluidIngredient> TYPE = new Type();

    @Override public MixtureFluidIngredientSubType<MoleculeFluidIngredient> getType() { return TYPE; }

    @Override protected boolean testMixture(LegacyMixture mixture) {
        float concentration = mixture.getConcentrationOf(molecule);
        return concentration >= minConcentration && concentration <= maxConcentration && super.testMixture(mixture);
    }

    private static final class Type extends IonFluidIngredient.Type {
        @Override public MirrorMixtureIngredient getNew() { return new MirrorMixtureIngredient(); }
        @Override public String getMixtureFluidIngredientSubtype() { return "createapparel:mirror_mixture"; }
    }
}
