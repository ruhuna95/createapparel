package com.rexhub.createapparel.client.screen;

import com.rexhub.createapparel.recipe.SewingRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SewingPatternOrderTest {
    @Test
    void existingGarmentsFollowCreationOrderRegardlessOfRecipeIdsAndInputOrder() {
        var expected = List.of(
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
                "ankle_boots", "work_boots", "knee_high_boots", "pumps", "sandals", "slippers"
        );
        var recipes = new ArrayList<SewingRecipe>();
        for (int index = 0; index < expected.size(); index++) {
            recipes.add(recipe("addon:recipe_" + (expected.size() - index), "createapparel:" + expected.get(index)));
        }
        Collections.reverse(recipes);
        recipes.sort(SewingPatternOrder.RECIPES);
        assertEquals(expected, recipes.stream().map(recipe -> recipe.getResultId().getPath()).toList());
    }

    @Test
    void unknownGarmentsSortAfterKnownOnesByNamespacedId() {
        var recipes = List.of(recipe("addon:a", "other:t_shirt"),
                recipe("addon:b", "createapparel:future_garment"),
                recipe("addon:c", "createapparel:sweater"));
        assertEquals(List.of("createapparel:sweater", "createapparel:future_garment", "other:t_shirt"),
                recipes.stream().sorted(SewingPatternOrder.RECIPES)
                        .map(recipe -> recipe.getResultId().toString()).toList());
    }

    @Test
    void recipesForTheSameGarmentHaveStableIdOrder() {
        var recipes = List.of(recipe("addon:z", "createapparel:t_shirt"),
                recipe("addon:a", "createapparel:t_shirt"));
        assertEquals(List.of("addon:a", "addon:z"), recipes.stream().sorted(SewingPatternOrder.RECIPES)
                .map(recipe -> recipe.getId().toString()).toList());
    }

    private static SewingRecipe recipe(String id, String garment) {
        return new SewingRecipe(ResourceLocation.parse(id), null, null,
                ResourceLocation.parse("createapparel:sewing_pattern"), ResourceLocation.parse(garment));
    }
}
