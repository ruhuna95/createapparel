package com.rexhub.createapparel.recipe;

import org.junit.jupiter.api.Test;

import static com.rexhub.createapparel.recipe.RecipeRouteSelection.Product.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeRouteSelectionTest {
    @Test void everyRouteCombinationUsesPrerequisitesAndIndependentAdditiveFallbacks() {
        for (boolean destroy : new boolean[]{false, true}) {
            for (boolean silver : new boolean[]{false, true}) {
                for (boolean synthetic : new boolean[]{false, true}) {
                    for (boolean mirror : new boolean[]{false, true}) {
                        var selection = new RecipeRouteSelection(destroy, synthetic, mirror);
                        assertEquals(!destroy || synthetic, selection.useFallback(SYNTHETIC_FIBER, silver));
                        assertEquals(!destroy || !silver || mirror, selection.useFallback(MIRROR_COATING, silver));
                        assertEquals(destroy, selection.useDestroy(SYNTHETIC_FIBER, silver));
                        assertEquals(destroy && silver, selection.useDestroy(MIRROR_COATING, silver));
                    }
                }
            }
        }
    }

    @Test void bootstrapDoesNotRequireAWorldConfigOrDestroy() {
        for (boolean silver : new boolean[]{false, true}) {
            assertTrue(RecipeRouteSelection.BOOTSTRAP.useFallback(SYNTHETIC_FIBER, silver));
            assertTrue(RecipeRouteSelection.BOOTSTRAP.useFallback(MIRROR_COATING, silver));
            assertFalse(RecipeRouteSelection.BOOTSTRAP.useDestroy(SYNTHETIC_FIBER, silver));
            assertFalse(RecipeRouteSelection.BOOTSTRAP.useDestroy(MIRROR_COATING, silver));
        }
    }
}
