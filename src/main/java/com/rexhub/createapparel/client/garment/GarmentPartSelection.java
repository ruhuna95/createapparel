package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GarmentPartSelection {
    private GarmentPartSelection() {
    }

    static List<String> select(
            Map<String, String> styles,
            ArmProfile armProfile,
            ChestProfile chestProfile,
            Set<String> availableParts
    ) {
        List<String> selected = new ArrayList<>(select(styles, armProfile, chestProfile, availableParts.contains("bust")));
        selected.removeIf(name -> (name.startsWith("left_sleeve") || name.startsWith("right_sleeve"))
                && !availableParts.contains(name.replace("_rolled", "")));
        for (String leg : List.of("left_leg", "right_leg")) {
            if (availableParts.contains(leg)) {
                selected.add(leg);
            }
        }
        return List.copyOf(selected);
    }

    static List<String> select(
            Map<String, String> styles,
            ArmProfile armProfile,
            ChestProfile chestProfile,
            boolean hasBust
    ) {
        String openSuffix = "opened".equals(styles.get("open")) ? "_opened" : "";
        String sleeveSuffix = "rolled".equals(styles.get("sleeves")) ? "_rolled" : "";
        String torso = "tucked".equals(styles.get("tuck")) ? "tucked_torso" : "torso";
        List<String> selected = new ArrayList<>();
        selected.add(torso + openSuffix);
        if (chestProfile == ChestProfile.BUST && hasBust) {
            selected.add("bust" + openSuffix);
        }
        selected.add("left_sleeve_" + armProfile.getSerializedName() + sleeveSuffix);
        selected.add("right_sleeve_" + armProfile.getSerializedName() + sleeveSuffix);
        if (styles.containsKey("hood")) {
            selected.add("hood_" + styles.get("hood"));
        }
        return List.copyOf(selected);
    }

    static Set<String> requiredStyleParts(Map<String, List<String>> styles, boolean hasBust) {
        Set<String> required = new LinkedHashSet<>();
        for (String hood : styles.getOrDefault("hood", List.of())) {
            required.add("hood_" + hood);
        }
        boolean tucked = styles.getOrDefault("tuck", List.of()).contains("tucked");
        if (tucked) {
            required.add("tucked_torso");
        }
        if (styles.getOrDefault("open", List.of()).contains("opened")) {
            required.add("torso_opened");
            if (tucked) {
                required.add("tucked_torso_opened");
            }
            if (hasBust) {
                required.add("bust_opened");
            }
        }
        if (styles.getOrDefault("sleeves", List.of()).contains("rolled")) {
            for (ArmProfile armProfile : ArmProfile.values()) {
                required.add("left_sleeve_" + armProfile.getSerializedName() + "_rolled");
                required.add("right_sleeve_" + armProfile.getSerializedName() + "_rolled");
            }
        }
        return Set.copyOf(required);
    }
}
