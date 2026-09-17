package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedSupportPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedTextures;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FirstPersonApparelStateTest {
    private static GarmentPartModel model(String name) throws Exception {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:" + name),
                JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/assets/createapparel/" + name + ".json"))).getAsJsonObject());
    }

    private static ResolvedPart sleeve(String side, String profile) throws Exception {
        var part = model("garment_models/parts/t_shirt/" + side + "_short_sleeve_" + profile);
        return new ResolvedPart(side + "_sleeve", part, new ResolvedTextures(part.id(), Optional.empty(),
                Optional.empty(), Optional.empty(), 1, Optional.empty()));
    }

    private static ResolvedSupportPart body(String side, String profile) throws Exception {
        var part = model("body_models/parts/" + side + "_arm_" + profile);
        return new ResolvedSupportPart(side + "_arm", part, part.id());
    }

    @Test
    void sharesBothArmsAcrossFramesAndRebuildsOnlyOnReload() throws Exception {
        var right = sleeve("right", "classic");
        var left = sleeve("left", "classic");
        var bodyRight = body("right", "classic");
        var bodyLeft = body("left", "classic");
        Map<GarmentSlot, Optional<ResolvedGarment>> equipment = new EnumMap<>(GarmentSlot.class);
        equipment.put(GarmentSlot.TOP, Optional.of(new ResolvedGarment(null, null, List.of(right, left))));
        AtomicInteger resolves = new AtomicInteger();
        AtomicInteger bodies = new AtomicInteger();
        var state = new FirstPersonApparelState(slot -> {
            resolves.incrementAndGet();
            return equipment.getOrDefault(slot, Optional.empty());
        }, () -> {
            bodies.incrementAndGet();
            return Optional.of(List.of(bodyRight, bodyLeft));
        });
        var cached = state.arm(1, HumanoidArm.RIGHT);
        for (int i = 0; i < 100; i++) {
            assertSame(cached, state.arm(1, HumanoidArm.RIGHT));
            assertSame(right, cached.garments().get(0));
            assertSame(bodyRight, cached.body().get(0));
            assertSame(left, state.arm(1, HumanoidArm.LEFT).garments().get(0));
            assertSame(bodyLeft, state.arm(1, HumanoidArm.LEFT).body().get(0));
        }
        assertEquals(GarmentSlot.values().length, resolves.get());
        assertEquals(1, bodies.get());
        var replacement = sleeve("right", "slim");
        equipment.put(GarmentSlot.TOP, Optional.of(new ResolvedGarment(null, null, List.of(replacement))));
        var reloaded = state.arm(2, HumanoidArm.RIGHT);
        assertNotSame(cached, reloaded);
        assertSame(replacement, reloaded.garments().get(0));
        assertTrue(state.arm(2, HumanoidArm.LEFT).garments().isEmpty());
        assertEquals(2 * GarmentSlot.values().length, resolves.get());
        assertEquals(2, bodies.get());
    }

    @Test
    void keepsLayerOrderAndExcludesTorsoHoodLegsAndSkirt() throws Exception {
        for (String profile : List.of("classic", "slim")) {
            var inner = sleeve("right", profile);
            var top = sleeve("right", profile);
            var outer = sleeve("right", profile);
            var left = sleeve("left", profile);
            var torso = new ResolvedPart("torso", model("garment_models/parts/a_line_skirt/torso"), top.textures());
            var hood = new ResolvedPart("hood_up", torso.model(), top.textures());
            var leg = new ResolvedPart("right_leg", torso.model(), top.textures());
            var state = new FirstPersonApparelState(slot -> Optional.of(new ResolvedGarment(null, null,
                    switch (slot) {
                        case INNER_TOP -> List.of(inner);
                        case TOP -> List.of(top, left, torso);
                        case OUTER -> List.of(outer, hood);
                        case BOTTOM -> List.of(torso, leg);
                        default -> List.of();
                    })), Optional::empty);
            assertEquals(List.of(inner, top, outer), state.arm(0, HumanoidArm.RIGHT).garments());
            assertEquals(List.of(left), state.arm(0, HumanoidArm.LEFT).garments());
            assertTrue(state.arm(0, HumanoidArm.RIGHT).body().isEmpty());
            assertSame(top.model(), state.arm(0, HumanoidArm.RIGHT).garments().get(1).model());
        }
    }

    @Test
    void cachesMissingAssetsAndSeparatesSnapshotLifetimes() throws Exception {
        AtomicInteger resolves = new AtomicInteger();
        var missing = new FirstPersonApparelState(slot -> {
            resolves.incrementAndGet();
            return Optional.empty();
        }, Optional::empty);
        assertTrue(missing.arm(1, HumanoidArm.RIGHT).isEmpty());
        assertTrue(missing.arm(1, HumanoidArm.LEFT).isEmpty());
        assertEquals(GarmentSlot.values().length, resolves.get());
        var arm = body("left", "slim");
        var replacementSnapshot = new FirstPersonApparelState(slot -> Optional.empty(),
                () -> Optional.of(List.of(arm)));
        assertEquals(List.of(arm), replacementSnapshot.arm(1, HumanoidArm.LEFT).body());
        assertTrue(replacementSnapshot.arm(1, HumanoidArm.RIGHT).isEmpty());
        assertTrue(missing.arm(1, HumanoidArm.LEFT).isEmpty());
    }

    @Test
    void assignsSidesByGeometryLikeThirdPersonEvenWhenSemanticNamesAreReversed() throws Exception {
        var source = sleeve("right", "classic");
        var reversedName = new ResolvedPart("left_sleeve_rolled", source.model(), source.textures());
        var state = new FirstPersonApparelState(slot -> slot == GarmentSlot.TOP
                ? Optional.of(new ResolvedGarment(null, null, List.of(reversedName))) : Optional.empty(), Optional::empty);
        assertEquals(List.of(reversedName), state.arm(0, HumanoidArm.RIGHT).garments());
        assertTrue(state.arm(0, HumanoidArm.LEFT).isEmpty());
    }
}
