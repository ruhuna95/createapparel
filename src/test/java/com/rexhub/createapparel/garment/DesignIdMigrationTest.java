package com.rexhub.createapparel.garment;

import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesignIdMigrationTest {
    @Test
    void garmentAndPatternUseTheSameMigrationAndKeepColors() {
        for (String old : List.of("logo", "stripe_logo", "diagonal_stripe")) {
            String expected = old.equals("logo") ? "logo_creeper" : "stripe";
            var garment = new GarmentData(FabricMaterial.SILK, old, FabricColor.RED,
                    Optional.of(FabricColor.BLUE), Optional.of(FabricColor.YELLOW));
            var pattern = new PatternData(ResourceLocation.parse("createapparel:t_shirt"), old,
                    garment.secondaryColor(), garment.accentColor());
            assertEquals(expected, garment.design());
            assertEquals(expected, pattern.design());
            assertEquals(Optional.of(FabricColor.BLUE), pattern.secondaryColor());
            assertEquals(Optional.of(FabricColor.YELLOW), pattern.accentColor());
            assertEquals(FabricMaterial.SILK, garment.material());
            assertEquals(FabricColor.RED, garment.primaryColor());
        }
    }

    @Test
    void unimplementedOldLogosAndCurrentDesignsAreNotMigrated() {
        for (String design : List.of("logo_diamond", "logo_redstone", "logo_wordmark", "logo_emblem",
                "logo_creeper", "logo_word", "logo_round", "plain", "stripe", "two_tone_lr")) {
            assertEquals(design, DesignIdMigration.designId(design));
        }
    }
}
