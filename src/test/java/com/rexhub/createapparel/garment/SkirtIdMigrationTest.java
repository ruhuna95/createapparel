package com.rexhub.createapparel.garment;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SkirtIdMigrationTest {
    @Test
    void onlyTheRenamedSkirtRecipeIsMigrated() {
        var legacy = ResourceLocation.parse("createapparel:a_line_skirt");
        var knee = ResourceLocation.parse("createapparel:a_line_skirt_knee");
        assertEquals(knee, SkirtIdMigration.recipeId(legacy));
        assertEquals(knee, new PatternData(legacy, "plain", Optional.empty(), Optional.empty()).recipe());
        for (String value : new String[]{"createapparel:a_line_skirt_mini", "other:a_line_skirt", "createapparel:shorts"}) {
            var id = ResourceLocation.parse(value);
            assertSame(id, SkirtIdMigration.recipeId(id));
        }
    }
}
