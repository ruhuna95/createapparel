package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedSupportPart;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.world.entity.HumanoidArm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.rexhub.createapparel.client.garment.ResolvedApparelState.RENDER_ORDER;

public final class FirstPersonApparelState {
    private final Function<GarmentSlot, Optional<ResolvedGarment>> resolver;
    private final Supplier<Optional<List<ResolvedSupportPart>>> bodyResolver;
    private long generation = Long.MIN_VALUE;
    private ArmParts right = new ArmParts(List.of(), List.of());
    private ArmParts left = right;

    public FirstPersonApparelState(Function<GarmentSlot, Optional<ResolvedGarment>> resolver,
                                  Supplier<Optional<List<ResolvedSupportPart>>> bodyResolver) {
        this.resolver = resolver;
        this.bodyResolver = bodyResolver;
    }

    public ArmParts arm(long resourceGeneration, HumanoidArm arm) {
        if (generation != resourceGeneration) {
            List<ResolvedSupportPart> rightBody = new ArrayList<>();
            List<ResolvedSupportPart> leftBody = new ArrayList<>();
            for (var part : bodyResolver.get().orElse(List.of())) {
                if (isArm(part.semanticName()) && !part.model().elements().isEmpty()) {
                    (side(part.model()) == HumanoidArm.RIGHT ? rightBody : leftBody).add(part);
                }
            }
            List<ResolvedPart> rightGarments = new ArrayList<>();
            List<ResolvedPart> leftGarments = new ArrayList<>();
            for (var slot : RENDER_ORDER) {
                var garment = resolver.apply(slot);
                if (garment.isEmpty()) {
                    continue;
                }
                for (var part : garment.get().parts()) {
                    if (isGarmentArm(part.semanticName()) && !part.model().elements().isEmpty()) {
                        (side(part.model()) == HumanoidArm.RIGHT ? rightGarments : leftGarments).add(part);
                    }
                }
            }
            right = new ArmParts(List.copyOf(rightBody), List.copyOf(rightGarments));
            left = new ArmParts(List.copyOf(leftBody), List.copyOf(leftGarments));
            generation = resourceGeneration;
        }
        return arm == HumanoidArm.RIGHT ? right : left;
    }

    private static boolean isArm(String name) {
        return name.equals("left_arm") || name.equals("right_arm") || isSleeve(name);
    }

    private static boolean isSleeve(String name) {
        return name.startsWith("left_sleeve") || name.startsWith("right_sleeve");
    }

    public static boolean isGarmentArm(String name) {
        return isSleeve(name) || name.startsWith("left_hand_") || name.startsWith("right_hand_");
    }

    private static HumanoidArm side(GarmentPartModel model) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (var element : model.elements()) {
            min = Math.min(min, Math.min(element.from().x(), element.to().x()));
            max = Math.max(max, Math.max(element.from().x(), element.to().x()));
        }
        return min + max < 0 ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
    }

    public record ArmParts(List<ResolvedSupportPart> body, List<ResolvedPart> garments) {
        public boolean isEmpty() {
            return body.isEmpty() && garments.isEmpty();
        }
    }
}
