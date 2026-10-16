package com.rexhub.createapparel.compat.jei;

import com.rexhub.createapparel.recipe.ModRecipeTypes;
import mezz.jei.api.recipe.RecipeType;

public final class ApparelJeiRecipeTypes {
    public static final RecipeType<ProcessingRecipeDisplay> SPINNING =
            new RecipeType<>(ModRecipeTypes.SPINNING.getId(), ProcessingRecipeDisplay.class);
    public static final RecipeType<ProcessingRecipeDisplay> WEAVING =
            new RecipeType<>(ModRecipeTypes.WEAVING.getId(), ProcessingRecipeDisplay.class);
    public static final RecipeType<SewingRecipeDisplay> SEWING =
            new RecipeType<>(ModRecipeTypes.SEWING.getId(), SewingRecipeDisplay.class);
    public static final RecipeType<SilkwormRearingDisplay> SILKWORM_REARING =
            new RecipeType<>(ModRecipeTypes.SILKWORM_REARING.getId(), SilkwormRearingDisplay.class);

    private ApparelJeiRecipeTypes() { }
}
