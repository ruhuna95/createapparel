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
            "sailor_blouse_shortsleeves", "sailor_blouse_longsleeves",
            "pullover_hoodie", "zipup_hoodie", "sweater", "v_neck_sweater",
            "a_line_dress_knee",
            "a_line_dress_midi",
            "fit_and_flare_dress_knee",
            "fit_and_flare_dress_midi",
            "straight_dress_mini",
            "straight_dress_knee",
            "straight_dress_midi",
            "shirt_dress_knee",
            "shirt_dress_midi",
            "jumper_dress_mini_shortsleeves",
            "jumper_dress_mini_longsleeves",
            "jumper_dress_knee_shortsleeves",
            "jumper_dress_knee_longsleeves",
            "jumper_dress_midi_shortsleeves",
            "jumper_dress_midi_longsleeves",
            "camisole_dress_knee_shortsleeves",
            "camisole_dress_knee_longsleeves",
            "camisole_dress_midi_shortsleeves",
            "camisole_dress_midi_longsleeves",
            "camisole_dress_long_shortsleeves",
            "camisole_dress_long_longsleeves",
            "camisole_dress_maxi_shortsleeves",
            "camisole_dress_maxi_longsleeves",
            "bodycon_dress_mini",
            "bodycon_dress_knee",
            "bodycon_dress_midi",
            "denim_pants", "slacks", "shorts",
            "a_line_skirt_mini", "a_line_skirt_knee", "a_line_skirt_midi", "a_line_skirt_long", "a_line_skirt_maxi",
            "pleated_skirt_micro", "pleated_skirt_mini", "pleated_skirt_knee", "pleated_skirt_midi", "pleated_skirt_long",
            "flared_skirt_mini", "flared_skirt_knee", "flared_skirt_midi", "flared_skirt_long", "flared_skirt_maxi",
            "tight_skirt_micro", "tight_skirt_mini", "tight_skirt_knee", "tight_skirt_midi", "tight_skirt_long",
            "tights", "ankle_socks", "crew_socks", "knee_high_socks",
            "over_knee_socks", "thigh_high_socks", "suit_jacket", "blazer_jacket", "denim_jacket",
            "chesterfield_coat", "trench_coat", "duffle_coat", "pea_coat",
            "undershirt", "bra", "briefs", "boxer_briefs", "panties",
            "sneakers", "canvas_sneakers", "dress_shoes", "loafers",
            "ankle_boots", "work_boots", "knee_high_boots", "pumps", "sandals", "slippers",
            "cap", "beanie", "beret", "hat", "casquette", "sun_visor", "mask",
            "backpack", "daypack", "crossbody_bag", "necktie", "wristband", "gloves"
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
