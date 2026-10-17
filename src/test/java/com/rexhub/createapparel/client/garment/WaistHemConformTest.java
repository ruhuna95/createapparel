package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.client.model.CitizenModel;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WaistHemConformTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");

    private static ResolvedGarment pants(String family) throws Exception {
        var parts = new ArrayList<ResolvedPart>();
        for (String name : List.of("torso", "left_leg", "right_leg")) {
            parts.add(new ResolvedPart(name, DualEnvelopeDeformerTest.part(family + "/" + name), null));
        }
        return new ResolvedGarment(null, null, List.copyOf(parts));
    }

    private static ApparelRenderFrame frame(ResolvedGarment bottom, HumanoidModel<?> model) {
        return new ApparelRenderFrame(slot -> slot == GarmentSlot.BOTTOM ? Optional.of(bottom) : Optional.empty(), model);
    }

    @Test
    void everyOrdinaryTopHasCommonWaistSeparationFromDenimAndSlacks() throws Exception {
        var tops = new LinkedHashMap<ResourceLocation, GarmentPartModel>();
        try (var files = Files.list(Path.of("src/main/resources/data/createapparel/garments"))) {
            for (var path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var definition = GarmentDefinition.fromJson(ResourceLocation.parse("createapparel:waist_test"),
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject());
                if (!definition.allowedSlots().contains(GarmentSlot.TOP)) continue;
                var template = ClientGarmentAssetLoader.parseTemplate(definition.modelTemplate(),
                        JsonParser.parseString(Files.readString(ASSETS.resolve("garment_models/templates/"
                                + definition.modelTemplate().getPath() + ".json"))).getAsJsonObject());
                for (var variant : template.variants().values()) for (var entry : variant.entrySet()) {
                    if (!entry.getKey().equals("torso") && !entry.getKey().equals("torso_opened")) continue;
                    if (!tops.containsKey(entry.getValue())) tops.put(entry.getValue(),
                            DualEnvelopeDeformerTest.part(entry.getValue().getPath()));
                }
            }
        }
        assertTrue(tops.size() >= 13);
        for (String family : List.of("denim_pants", "slacks")) {
            var bottom = pants(family);
            var slope = frame(bottom, DualEnvelopeDeformerTest.player()).skirtSlope().orElseThrow();
            for (var upper : tops.values()) {
                if (upper.legEnvelope().isPresent()) continue;
                assertTrue(upper.hemGeometry().applies("torso", GarmentSlot.TOP, slope), upper.id().toString());
                var shell = upper.elements().get(0);
                float y = Math.max(shell.from().y(), 12F);
                var moved = new Vector3f();
                float depth = Math.max(upper.hemGeometry().hem() + 2, slope.min().y()) - y;
                var surface = slope.forSlot(GarmentSlot.TOP);
                float gap = .025F * Math.min(1, depth / 2);
                surface.shift(new Vec3(shell.to().x(), y, 0), depth, moved);
                assertTrue(moved.x >= 4.005F + gap - .00001F, upper.id() + " side: " + moved);
                surface.shift(new Vec3(0, y, shell.from().z()), depth, moved);
                assertTrue(moved.z <= -2.249F - gap + .00001F, upper.id() + " front: " + moved);
                surface.shift(new Vec3(0, y, shell.to().z()), depth, moved);
                assertTrue(moved.z >= 2.249F + gap - .00001F, upper.id() + " rear: " + moved);
            }
        }
    }

    @Test
    void matchingPlayerAndCitizenPosesShareExactlyTheSameWaistGeometry() throws Exception {
        for (var profile : ArmProfile.values()) for (String family : List.of("denim_pants", "slacks")) {
            var bottom = pants(family);
            boolean slim = profile == ArmProfile.SLIM;
            var player = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64)
                    .bakeRoot(), slim);
            var citizen = new CitizenModel(CitizenModel.createBodyLayer().bakeRoot());
            for (int pose = 0; pose < 4; pose++) {
                player.crouching = pose == 2;
                player.riding = pose == 3;
                player.body.xRot = pose == 2 ? .5F : 0;
                player.body.y = pose == 2 ? 3.2F : 0;
                player.rightLeg.xRot = pose == 0 ? .005F : pose == 1 ? .6F : -1.4F;
                player.leftLeg.xRot = pose < 2 ? -player.rightLeg.xRot : -.8F;
                player.rightLeg.yRot = player.rightLeg.zRot = .005F;
                player.leftLeg.yRot = player.leftLeg.zRot = -.005F;
                player.leftLeg.y = player.rightLeg.y = pose == 2 ? 12.2F : 12;
                player.leftLeg.z = player.rightLeg.z = pose == 2 ? 4 : 0;
                citizen.body.copyFrom(player.body);
                citizen.leftLeg.copyFrom(player.leftLeg);
                citizen.rightLeg.copyFrom(player.rightLeg);
                citizen.crouching = player.crouching;
                citizen.riding = player.riding;
                var expected = frame(bottom, player).skirtSlope().orElseThrow().forSlot(GarmentSlot.TOP);
                var actual = frame(bottom, citizen).skirtSlope().orElseThrow().forSlot(GarmentSlot.TOP);
                for (var point : List.of(new Vec3(-4.005F, 12, -2.25F), new Vec3(4.005F, 12, 2.25F),
                        new Vec3(0, 12, -2.25F), new Vec3(0, 14.004F, 2.25F))) {
                    var a = new Vector3f(); var b = new Vector3f();
                    float depth = Math.max(0, 14.004F - point.y());
                    expected.shift(point, depth, a);
                    actual.shift(point, depth, b);
                    assertTrue(Float.isFinite(a.x) && Float.isFinite(a.y) && Float.isFinite(a.z));
                    assertEquals(a, b, profile + "/" + family + "/" + pose);
                }
            }
        }
    }

    @Test
    void waistConformLeavesTheOriginalAssetsAndUpperLayerOrderingIntact() throws Exception {
        var upper = DualEnvelopeDeformerTest.part("t_shirt/torso");
        var shell = upper.elements().get(0);
        var pants = pants("denim_pants");
        var top = new ResolvedGarment(null, null, List.of(new ResolvedPart("torso", upper, null)));
        var frame = new ApparelRenderFrame(slot -> Optional.ofNullable(slot == GarmentSlot.TOP ? top
                : slot == GarmentSlot.BOTTOM ? pants : null), DualEnvelopeDeformerTest.player());
        assertSame(top, frame.garment(GarmentSlot.TOP).orElseThrow());
        assertSame(upper, top.parts().get(0).model());
        assertEquals(4.005F, shell.to().x());
        assertEquals(2.25F, shell.to().z());
        var slope = frame.skirtSlope().orElseThrow();
        var topPoint = new Vector3f(); var outerPoint = new Vector3f();
        slope.forSlot(GarmentSlot.TOP).shift(new Vec3(4.005F, 12, 2.25F), 2.004F, topPoint);
        slope.forSlot(GarmentSlot.OUTER).shift(new Vec3(4.01F, 12, 2.5F), 2.004F, outerPoint);
        assertTrue(outerPoint.x > topPoint.x + .024F);
        assertTrue(outerPoint.z > topPoint.z + .024F);
        var anchor = new Vec3(4.005F, 14.004F, 2.25F);
        slope.forSlot(GarmentSlot.TOP).shift(anchor, 0, topPoint);
        assertEquals(new Vector3f(anchor.x(), anchor.y(), anchor.z()), topPoint);
    }

    @Test
    void tuckedPartsUnrelatedSlotsAndAbsentBottomsDoNotConform() throws Exception {
        var player = DualEnvelopeDeformerTest.player();
        assertTrue(new ApparelRenderFrame(slot -> Optional.empty(), player).skirtSlope().isEmpty());
        var torsoOnly = new ResolvedGarment(null, null, List.of(new ResolvedPart("torso",
                DualEnvelopeDeformerTest.part("denim_pants/torso"), null)));
        assertTrue(GarmentHemGeometry.bottomGeometry(torsoOnly).isEmpty());
        var upper = DualEnvelopeDeformerTest.part("t_shirt/torso");
        var slope = frame(pants("denim_pants"), player).skirtSlope().orElseThrow();
        for (var semantic : List.of("tucked_torso", "tucked_torso_opened", "bust", "left_sleeve")) {
            assertFalse(upper.hemGeometry().applies(semantic, GarmentSlot.TOP, slope));
        }
        for (var slot : List.of(GarmentSlot.INNER_TOP, GarmentSlot.BOTTOM, GarmentSlot.LEGWEAR)) {
            assertFalse(upper.hemGeometry().applies("torso", slot, slope));
        }
        assertTrue(GarmentHemGeometry.bottomGeometry(new ResolvedGarment(null, null, List.of())).isEmpty());
    }
}
