package com.rexhub.createapparel.client.screen;

import com.rexhub.createapparel.recipe.SewingRecipe;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;

final class SewingPatternOrder {
    private static final List<ResourceLocation> GARMENTS = List.of(
            "t_shirt", "t_shirt_u_neck", "t_shirt_v_neck",
            "t_shirt_longsleeves", "t_shirt_longsleeves_u_neck", "t_shirt_longsleeves_v_neck",
            "shirt_shortsleeves", "shirt_longsleeves",
            "blouse_round", "blouse_frill", "blouse_bow_tie",
            "blouse_round_three_quarter_sleeve", "blouse_frill_three_quarter_sleeve",
            "blouse_bow_tie_three_quarter_sleeve",
            "pullover_hoodie", "zipup_hoodie", "sweater", "v_neck_sweater"
    ).stream().map(path -> ResourceLocation.fromNamespaceAndPath("createapparel", path)).toList();

    static final Comparator<SewingRecipe> RECIPES = Comparator
            .comparingInt((SewingRecipe recipe) -> rank(recipe.getResultId()))
            .thenComparing(recipe -> recipe.getResultId().toString())
            .thenComparing(recipe -> recipe.getId().toString());

    private SewingPatternOrder() { }

    private static int rank(ResourceLocation garment) {
        int index = GARMENTS.indexOf(garment);
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
