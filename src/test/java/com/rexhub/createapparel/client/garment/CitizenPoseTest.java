package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.client.model.CitizenModel;
import com.rexhub.createapparel.client.model.MannequinModel;
import com.rexhub.createapparel.client.renderer.CommonGarmentRenderer;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.entity.MannequinForm;
import com.rexhub.createapparel.entity.PosePreset;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CitizenPoseTest {
    @Test
    void unadjustedTrouserAndShoeVerticesMatchPlayerForTheSameAdultPose() throws Exception {
        var method = CommonGarmentRenderer.class.getDeclaredMethod("humanoidGarmentPose", HumanoidModel.class,
                ArmProfile.class, ClientGarmentAssets.ResolvedPart.class);
        method.setAccessible(true);
        for (var profile : ArmProfile.values()) {
            var player = new net.minecraft.client.model.PlayerModel<>(net.minecraft.client.model.geom.builders.LayerDefinition.create(
                    net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE,
                            profile == ArmProfile.SLIM), 64, 64).bakeRoot(), profile == ArmProfile.SLIM);
            var citizen = new CitizenModel(CitizenModel.createBodyLayer().bakeRoot());
            for (var pose : MannequinForm.FULL.poses()) {
                citizen.applyPose(pose);
                player.leftLeg.setRotation(citizen.leftLeg.xRot, citizen.leftLeg.yRot, citizen.leftLeg.zRot);
                player.rightLeg.setRotation(citizen.rightLeg.xRot, citizen.rightLeg.yRot, citizen.rightLeg.zRot);
                for (String family : List.of("denim_pants", "slacks", "shoes", "boots", "tall_boots")) {
                    for (String side : List.of("left_leg", "right_leg")) {
                        var part = DualEnvelopeDeformerTest.part(family + "/" + side);
                        var resolved = new ClientGarmentAssets.ResolvedPart(side, part, null);
                        var playerPose = method.invoke(null, player, profile, resolved);
                        var citizenPose = method.invoke(null, citizen, profile, resolved);
                        var apply = playerPose.getClass().getDeclaredMethod("apply", PoseStack.class);
                        apply.setAccessible(true);
                        var expected = new PoseStack();
                        var actual = new PoseStack();
                        apply.invoke(playerPose, expected);
                        apply.invoke(citizenPose, actual);
                        for (var element : part.elements()) for (var corner : element.corners()) {
                            var point = new Vector3f(corner.x(), 24 - corner.y(), corner.z());
                            assertTrue(new Vector3f(point).mulPosition(expected.last().pose()).equals(
                                    new Vector3f(point).mulPosition(actual.last().pose()), 0.000001F),
                                    profile + "/" + pose + "/" + family + "/" + side);
                        }
                    }
                }
            }
        }
    }
    @Test
    void allEightPosesMatchMannequinPartsAndKeepChildHeadScale() {
        var mannequin = new MannequinModel(MannequinModel.createBodyLayer(false).bakeRoot());
        var citizen = new CitizenModel(CitizenModel.createBodyLayer().bakeRoot());
        for (var age : CitizenAge.values()) for (var pose : MannequinForm.FULL.poses()) {
            citizen.applyAge(age);
            citizen.applyPose(pose);
            mannequin.apply(MannequinForm.FULL, pose);
            var actual = parts(citizen);
            var expected = parts(mannequin);
            for (int index = 0; index < actual.size(); index++) {
                assertEquals(expected.get(index).xRot, actual.get(index).xRot);
                assertEquals(expected.get(index).yRot, actual.get(index).yRot);
                assertEquals(expected.get(index).zRot, actual.get(index).zRot);
            }
            assertEquals(citizen.head.yRot, citizen.hat.yRot);
            assertEquals(age.headScale() / age.scale(), citizen.head.xScale);
            assertEquals(citizen.head.xScale, citizen.hat.xScale);
        }
        citizen.applyPose(PosePreset.ARMS_CROSSED);
        assertEquals(Math.toRadians(-65), citizen.leftArm.xRot, 0.000001);
        assertEquals(Math.toRadians(35), citizen.leftArm.yRot, 0.000001);
        assertEquals(Math.toRadians(-30), citizen.leftArm.zRot, 0.000001);
        citizen.applyPose(PosePreset.ATTENTION);
        for (var part : parts(citizen)) {
            assertEquals(0, part.xRot);
            assertEquals(0, part.yRot);
            assertEquals(0, part.zRot);
        }
    }

    @Test
    void garmentTransformsUsePosedBodyHeadArmsAndLegsForBothAges() throws Exception {
        var model = new CitizenModel(CitizenModel.createBodyLayer().bakeRoot());
        var method = CommonGarmentRenderer.class.getDeclaredMethod("humanoidGarmentPose", HumanoidModel.class,
                ArmProfile.class, ClientGarmentAssets.ResolvedPart.class);
        method.setAccessible(true);
        var semantics = List.of("head", "torso", "left_sleeve_classic", "right_sleeve_classic", "left_leg", "right_leg");
        var files = List.of("t_shirt/torso", "t_shirt/torso", "t_shirt/left_short_sleeve_classic",
                "t_shirt/right_short_sleeve_classic", "denim_pants/left_leg", "denim_pants/right_leg");
        for (var age : CitizenAge.values()) for (var pose : MannequinForm.FULL.poses()) {
            model.applyAge(age);
            model.applyPose(pose);
            for (int index = 0; index < semantics.size(); index++) {
                String semantic = semantics.get(index);
                var source = ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:pose_test"),
                        JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/createapparel/garment_models/parts/"
                                + files.get(index) + ".json"))).getAsJsonObject());
                var mapped = method.invoke(null, model, ArmProfile.CLASSIC, new ClientGarmentAssets.ResolvedPart(semantic, source, null));
                var apply = mapped.getClass().getDeclaredMethod("apply", PoseStack.class);
                apply.setAccessible(true);
                var actual = new PoseStack();
                apply.invoke(mapped, actual);
                var part = parts(model).get(index);
                var pivot = new Vector3f(part.x, part.y, part.z);
                var transformedPivot = new Vector3f(pivot).mulPosition(actual.last().pose());
                assertTrue(pivot.equals(transformedPivot, 0.000001F), "Garment keeps the joint pivot: " + semantic);
                var expected = new Matrix4f().rotateZ(part.zRot).rotateY(part.yRot).rotateX(part.xRot)
                        .scale(part.xScale, part.yScale, part.zScale);
                for (var axis : List.of(new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1))) {
                    assertTrue(new Vector3f(axis).mulDirection(expected).equals(new Vector3f(axis).mulDirection(actual.last().pose()), 0.000001F),
                            age + "/" + pose + "/" + semantic);
                }
            }
        }
    }

    private static List<ModelPart> parts(HumanoidModel<?> model) {
        return List.of(model.head, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg);
    }
}
