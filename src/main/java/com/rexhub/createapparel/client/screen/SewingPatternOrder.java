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
            "pullover_hoodie", "zipup_hoodie", "sweater", "v_neck_sweater", "denim_pants", "slacks", "shorts",
            "a_line_skirt", "tights", "ankle_socks", "crew_socks", "knee_high_socks",
            "over_knee_socks", "thigh_high_socks", "suit_jacket", "blazer_jacket", "denim_jacket",
            "chesterfield_coat", "trench_coat", "duffle_coat", "pea_coat",
            "undershirt", "bra", "briefs", "boxer_briefs", "panties",
            "sneakers", "canvas_sneakers", "dress_shoes", "loafers",
            "ankle_boots", "work_boots", "knee_high_boots", "pumps", "sandals", "slippers",
            "cap", "beanie", "beret", "hat", "casquette", "sun_visor", "mask",
            "backpack", "daypack", "crossbody_bag", "necktie"
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
