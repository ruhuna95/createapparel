package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GarmentPartSelectionTest {
    @ParameterizedTest
    @CsvSource({
            "untucked, closed, normal, torso, bust, ''",
            "untucked, closed, rolled, torso, bust, _rolled",
            "untucked, opened, normal, torso_opened, bust_opened, ''",
            "untucked, opened, rolled, torso_opened, bust_opened, _rolled",
            "tucked, closed, normal, tucked_torso, bust, ''",
            "tucked, closed, rolled, tucked_torso, bust, _rolled",
            "tucked, opened, normal, tucked_torso_opened, bust_opened, ''",
            "tucked, opened, rolled, tucked_torso_opened, bust_opened, _rolled"
    })
    void shirtStylesCombineIndependentlyForEveryBodyProfile(
            String tuck, String open, String sleeves, String torso, String bust, String sleeveSuffix
    ) {
        Map<String, String> styles = Map.of("tuck", tuck, "open", open, "sleeves", sleeves);
        for (ArmProfile arms : ArmProfile.values()) {
            for (ChestProfile chest : ChestProfile.values()) {
                List<String> expected = new ArrayList<>();
                expected.add(torso);
                if (chest == ChestProfile.BUST) {
                    expected.add(bust);
                }
                expected.add("left_sleeve_" + arms.getSerializedName() + sleeveSuffix);
                expected.add("right_sleeve_" + arms.getSerializedName() + sleeveSuffix);
                assertEquals(expected, GarmentPartSelection.select(styles, arms, chest, true));
            }
        }
    }

    @Test
    void shortSleeveShirtSupportsOpenAndTuckWithoutRolledSleeves() {
        assertEquals(
                List.of("tucked_torso_opened", "bust_opened", "left_sleeve_slim", "right_sleeve_slim"),
                GarmentPartSelection.select(
                        Map.of("tuck", "tucked", "open", "opened"), ArmProfile.SLIM, ChestProfile.BUST, true
                )
        );
        assertEquals(
                Set.of("tucked_torso", "torso_opened", "tucked_torso_opened", "bust_opened"),
                GarmentPartSelection.requiredStyleParts(Map.of(
                        "tuck", List.of("untucked", "tucked"), "open", List.of("closed", "opened")
                ), true)
        );
    }

    @Test
    void existingTShirtKeepsItsNormalAndTuckedParts() {
        assertEquals(
                List.of("torso", "left_sleeve_classic", "right_sleeve_classic"),
                GarmentPartSelection.select(Map.of(), ArmProfile.CLASSIC, ChestProfile.FLAT, true)
        );
        assertEquals(
                List.of("tucked_torso", "bust", "left_sleeve_slim", "right_sleeve_slim"),
                GarmentPartSelection.select(Map.of("tuck", "tucked"), ArmProfile.SLIM, ChestProfile.BUST, true)
        );
    }

    @Test
    void garmentWithoutBustDoesNotSelectOrRequireBustParts() {
        assertEquals(
                List.of("torso_opened", "left_sleeve_classic", "right_sleeve_classic"),
                GarmentPartSelection.select(Map.of("open", "opened"), ArmProfile.CLASSIC, ChestProfile.BUST, false)
        );
        assertEquals(
                Set.of("torso_opened"),
                GarmentPartSelection.requiredStyleParts(Map.of("open", List.of("closed", "opened")), false)
        );
    }

    @Test
    void validationRequiresCombinedOpenTuckedAndRolledProfileParts() {
        assertEquals(Set.of(
                "tucked_torso", "torso_opened", "tucked_torso_opened", "bust_opened",
                "left_sleeve_classic_rolled", "right_sleeve_classic_rolled",
                "left_sleeve_slim_rolled", "right_sleeve_slim_rolled"
        ), GarmentPartSelection.requiredStyleParts(Map.of(
                "tuck", List.of("untucked", "tucked"),
                "open", List.of("opened", "closed"),
                "sleeves", List.of("normal", "rolled")
        ), true));
    }

    @Test
    void existingTShirtValidationOnlyRequiresTuckedTorso() {
        assertEquals(Set.of("tucked_torso"), GarmentPartSelection.requiredStyleParts(
                Map.of("tuck", List.of("untucked", "tucked")), true
        ));
    }
}
