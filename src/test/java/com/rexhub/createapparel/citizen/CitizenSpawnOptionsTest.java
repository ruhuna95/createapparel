package com.rexhub.createapparel.citizen;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.entity.CitizenAge;
import net.minecraft.world.item.DyeColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CitizenSpawnOptionsTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapVanillaColors() throws ReflectiveOperationException {
        CitizenColorTestBootstrap.initializeTags();
    }

    private static CitizenSpawnOptions parse(String input) {
        return CitizenSpawnOptions.parse(input, RandomSource.create(17));
    }

    @Test
    void fullySpecifiedValuesRemainIndependent() {
        var options = parse("slim flat child dark twintails #123ABC blue default");
        var appearance = options.appearance();
        assertEquals(ArmProfile.SLIM, appearance.armProfile());
        assertEquals(ChestProfile.FLAT, appearance.chestProfile());
        assertEquals(CitizenAge.CHILD, options.age());
        assertEquals(SkinTone.DARK, appearance.skinTone());
        assertEquals(ResourceLocation.parse("createapparel:twintails"), appearance.hairStyleId());
        assertEquals(0x123ABC, appearance.hairColorRgb());
        assertEquals(CitizenDyeColors.rgb(DyeColor.BLUE), appearance.eyeColorRgb());
        assertEquals(CitizenOutfitPreset.DEFAULT, options.outfit());
        assertEquals(ChestProfile.BUST, parse("classic bust").appearance().chestProfile());
    }

    @Test
    void everyOmittedOrWildcardBodyFieldMaintainsPairing() {
        for (String input : new String[]{"", "*", "* *", "classic", "slim", "classic *", "slim *", "* flat", "* bust"}) {
            for (int seed = 0; seed < 100; seed++) {
                var appearance = CitizenSpawnOptions.parse(input, RandomSource.create(seed)).appearance();
                assertEquals(appearance.armProfile() == ArmProfile.CLASSIC ? ChestProfile.FLAT : ChestProfile.BUST,
                        appearance.chestProfile(), input);
            }
        }
    }

    @Test
    void everyTrailingOmissionMatchesExplicitWildcardsWithSameSeed() {
        String[] values = {"slim", "flat", "adult", "fair", "bob", "cyan", "purple", "default"};
        for (int length = 0; length <= values.length; length++) {
            String prefix = String.join(" ", java.util.Arrays.copyOf(values, length));
            String expanded = prefix + " *".repeat(values.length - length);
            assertEquals(CitizenSpawnOptions.parse(prefix, RandomSource.create(91)),
                    CitizenSpawnOptions.parse(expanded, RandomSource.create(91)));
        }
    }

    @Test
    void randomSelectionsReachAllBuiltInValues() {
        var random = RandomSource.create(17);
        Set<ArmProfile> arms = new HashSet<>();
        Set<CitizenAge> ages = new HashSet<>();
        Set<SkinTone> skins = new HashSet<>();
        Set<String> hair = new HashSet<>();
        Set<Integer> hairColors = new HashSet<>();
        Set<Integer> eyes = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            var options = CitizenSpawnOptions.parse("* * * * * * * *", random);
            arms.add(options.appearance().armProfile());
            ages.add(options.age());
            skins.add(options.appearance().skinTone());
            hair.add(options.appearance().hairStyleId().getPath());
            hairColors.add(options.appearance().hairColorRgb());
            eyes.add(options.appearance().eyeColorRgb());
        }
        assertEquals(Set.of(ArmProfile.values()), arms);
        assertEquals(Set.of(CitizenAge.values()), ages);
        assertEquals(Set.of(SkinTone.values()), skins);
        assertEquals(Set.copyOf(CitizenSpawnOptions.HAIR_STYLES), hair);
        Set<Integer> palette = new HashSet<>();
        for (DyeColor color : DyeColor.values()) palette.add(CitizenDyeColors.rgb(color));
        assertEquals(palette, hairColors);
        assertEquals(palette, eyes);
    }

    @Test
    void allDyeNamesAndResourcePackHairIdsAreSupported() {
        for (DyeColor color : DyeColor.values()) {
            var options = parse("classic flat adult natural example:custom_hair " + color.getSerializedName() + " "
                    + color.getSerializedName() + " createapparel:default");
            assertEquals(CitizenDyeColors.rgb(color), options.appearance().hairColorRgb());
            assertEquals(CitizenDyeColors.rgb(color), options.appearance().eyeColorRgb());
            assertEquals(ResourceLocation.parse("example:custom_hair"), options.appearance().hairStyleId());
        }
        assertEquals(0xFFFFFF, parse("* * * * * 0xFFFFFF").appearance().hairColorRgb());
    }

    @Test
    void invalidExplicitValuesAndExcessArgumentsFail() {
        for (String input : new String[]{"wrong", "* wrong", "* * wrong", "* * * wrong", "* * * * INVALID",
                "* * * * * mint", "* * * * * * plum",
                "* * * * * #123", "* * * * * #FFFFFFF", "* * * * * * #FFFFFF", "* * * * * * missing",
                "* * * * * * * missing", "* * * * * * * * extra"}) {
            assertThrows(IllegalArgumentException.class, () -> parse(input), input);
        }
    }

    @Test
    void completionReplacesOnlyCurrentPositionalToken() {
        String input = "createapparel citizen slim * child natural ";
        int start = "createapparel citizen ".length();
        var suggestions = CitizenCommand.suggest(new SuggestionsBuilder(input, start)).join();
        assertEquals(input.length(), suggestions.getRange().getStart());
        assertTrue(suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("twintails")));
        assertTrue(suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("bald")));
        var colors = CitizenCommand.suggest(new SuggestionsBuilder(input + "bob cyan blu", start)).join();
        assertEquals(input.length() + "bob cyan ".length(), colors.getRange().getStart());
        assertEquals("blue", colors.getList().get(0).getText());
        assertTrue(CitizenCommand.suggest(new SuggestionsBuilder(input + "bob cyan purple default ", start)).join().isEmpty());
    }

    @Test
    void allNewHairNamesAndVanillaTextureColorsAreSelectable() {
        assertEquals(16, CitizenDyeColors.PALETTE.size());
        assertEquals(0xF9FFFE, CitizenDyeColors.rgb(DyeColor.WHITE));
        assertEquals(0x835432, CitizenDyeColors.rgb(DyeColor.BROWN));
        assertEquals(0x1D1D21, CitizenDyeColors.rgb(DyeColor.BLACK));
        for (String style : CitizenHairStyles.BUILT_IN) {
            assertEquals(style, parse("classic flat adult natural " + style + " brown blue default")
                    .appearance().hairStyleId().getPath());
        }
    }

    @Test
    void ageParsingAndFallbackUseVanillaBabyScale() {
        assertEquals(CitizenAge.CHILD, CitizenAge.fromSerializedName("child"));
        assertEquals(CitizenAge.ADULT, CitizenAge.fromSerializedName("adult"));
        assertEquals(CitizenAge.CHILD, CitizenAge.read("child"));
        assertEquals(CitizenAge.ADULT, CitizenAge.read("invalid"));
        assertEquals(CitizenAge.ADULT, CitizenAge.read(""));
        assertThrows(IllegalArgumentException.class, () -> CitizenAge.fromSerializedName("invalid"));
        assertEquals(0.5F, CitizenAge.CHILD.scale());
        assertEquals(1, CitizenAge.ADULT.scale());
        assertEquals(0.75F, CitizenAge.CHILD.headScale());
        assertEquals(1, CitizenAge.ADULT.headScale());
    }
}
