package com.rexhub.createapparel.recipe;

import org.junit.jupiter.api.Test;

import static com.rexhub.createapparel.recipe.RecipeRouteSelection.Product.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeRouteSelectionTest {
    @Test void everyRouteCombinationIsExclusiveAndIndependent() {
        for (boolean destroy : new boolean[]{false, true}) {
            for (boolean synthetic : new boolean[]{false, true}) {
                for (boolean mirror : new boolean[]{false, true}) {
                    var selection = new RecipeRouteSelection(destroy, synthetic, mirror);
                    assertEquals(!destroy || synthetic, selection.useFallback(SYNTHETIC_FIBER));
                    assertEquals(!destroy || mirror, selection.useFallback(MIRROR_COATING));
                    assertEquals(destroy && !synthetic, !selection.useFallback(SYNTHETIC_FIBER));
                    assertEquals(destroy && !mirror, !selection.useFallback(MIRROR_COATING));
                }
            }
        }
    }

    @Test void bootstrapDoesNotRequireAWorldConfigOrDestroy() {
        assertTrue(RecipeRouteSelection.BOOTSTRAP.useFallback(SYNTHETIC_FIBER));
        assertTrue(RecipeRouteSelection.BOOTSTRAP.useFallback(MIRROR_COATING));
    }
}
