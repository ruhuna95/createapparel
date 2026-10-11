package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.client.citizen.CitizenHairGravity;
import com.rexhub.createapparel.client.citizen.CitizenHairLayers;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.model.CitizenModel;
import com.rexhub.createapparel.client.renderer.CommonGarmentRenderer;
import com.rexhub.createapparel.entity.CitizenAge;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CitizenAgeScaleTest {
    private static CitizenModel model(CitizenAge age) {
        var model = new CitizenModel(CitizenModel.createBodyLayer().bakeRoot());
        model.applyAge(age);
        return model;
    }

    private static PoseStack bodyStack(CitizenAge age) {
        var stack = new PoseStack();
        float scale = CitizenModel.BODY_SCALE * age.scale();
        stack.scale(-scale, -scale, scale);
        stack.translate(0, -1.5, 0);
        return stack;
    }

    private static Matrix4f garmentPose(HumanoidModel<?> model, String name, ArmProfile arms) throws Exception {
        var source = ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:age_test"),
                JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/assets/createapparel/garment_models/parts/t_shirt/torso.json"))).getAsJsonObject());
        var factory = CommonGarmentRenderer.class.getDeclaredMethod("humanoidGarmentPose",
                HumanoidModel.class, ArmProfile.class, ResolvedPart.class);
        factory.setAccessible(true);
        var pose = factory.invoke(null, model, arms, new ResolvedPart(name, source, null));
        var apply = pose.getClass().getDeclaredMethod("apply", PoseStack.class);
        apply.setAccessible(true);
        var stack = new PoseStack();
        apply.invoke(pose, stack);
        return new Matrix4f(stack.last().pose());
    }

    @Test
    void childHeadAndHatAreSeventyFivePercentWhileBodyIsFiftyPercent() {
        var adult = model(CitizenAge.ADULT);
        var child = model(CitizenAge.CHILD);
        for (ModelPart part : List.of(child.head, child.hat)) {
            assertEquals(1.5F, part.xScale);
            assertEquals(1.5F, part.yScale);
            assertEquals(1.5F, part.zScale);
            var stack = bodyStack(CitizenAge.CHILD);
            part.translateAndRotate(stack);
            float width = new Vector3f(8 / 16F, 0, 0).mulDirection(stack.last().pose()).length();
            assertEquals(CitizenModel.BODY_SCALE * 0.75F * 8 / 16F, width, 0.000001);
        }
        assertEquals(1, adult.head.xScale);
        for (ModelPart part : List.of(child.body, child.leftArm, child.rightArm, child.leftLeg, child.rightLeg)) {
            assertEquals(1, part.xScale);
            var stack = bodyStack(CitizenAge.CHILD);
            part.translateAndRotate(stack);
            assertEquals(CitizenModel.BODY_SCALE * 0.5F,
                    new Vector3f(1, 0, 0).mulDirection(stack.last().pose()).length(), 0.000001);
        }
        assertEquals(CitizenModel.BODY_SCALE * 0.5F * 1.5F,
                new Vector3f().mulPosition(bodyStack(CitizenAge.CHILD).last().pose()).y, 0.000001);
        child.applyAge(CitizenAge.ADULT);
        assertEquals(1, child.head.xScale);
        assertEquals(1, child.hat.xScale);
    }

    @Test
    void onlyHeadAttachedGarmentPartsInheritHeadScaleRegardlessOfSlot() throws Exception {
        for (ArmProfile arms : ArmProfile.values()) {
            var child = model(CitizenAge.CHILD);
            child.head.setRotation(0.08F, -0.21F, 0.03F);
            for (String name : List.of("head", "head_back", "head_up", "hood_up")) {
                var vanilla = new PoseStack();
                child.head.translateAndRotate(vanilla);
                var mapped = garmentPose(child, name, arms);
                assertTrue(mapped.equals(vanilla.last().pose(), 0.000001F), name);
                assertEquals(1.5F, new Vector3f(1, 0, 0).mulDirection(mapped).length(), 0.000001);
            }
            for (String name : List.of("torso", "bust", "hood_down", "torso_front", "torso_back", "neck")) {
                assertEquals(1, new Vector3f(1, 0, 0).mulDirection(garmentPose(child, name, arms)).length(), 0.000001, name);
            }
        }
    }

    @Test
    void adultAndUnscaledHumanoidGarmentPosesRemainIdentical() throws Exception {
        var adult = model(CitizenAge.ADULT);
        var unscaled = new HumanoidModel<>(CitizenModel.createBodyLayer().bakeRoot());
        for (var model : List.of(adult, unscaled)) {
            model.head.setPos(0.1F, -0.2F, 0.3F);
            model.head.setRotation(0.08F, -0.21F, 0.03F);
            for (String name : List.of("head", "head_back", "head_up", "hood_up")) {
                var legacy = new Matrix4f().translation(model.head.x, model.head.y, model.head.z)
                        .rotateZ(model.head.zRot).rotateY(model.head.yRot).rotateX(model.head.xRot);
                assertTrue(legacy.equals(garmentPose(model, name, ArmProfile.CLASSIC), 0.000001F), name);
            }
        }
    }

    @Test
    void childHairExtensionVerticesScaleWithHeadAndRemainOnItsRotatedEdges() throws Exception {
        var layers = CitizenHairLayers.read(JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/assets/createapparel/citizen/hair/extensions.json"))).getAsJsonObject())
                .withOpacity((u, v) -> (u == 32 || u == 48) && (v == 0 || v == 16));
        var rotation = new Quaternionf().rotationZYX(0.03F, -0.21F, 0.08F);
        var positions = new ArrayList<List<Vector3f>>();
        for (CitizenAge age : CitizenAge.values()) {
            var model = model(age);
            var stack = bodyStack(age);
            stack.scale(model.head.xScale, model.head.yScale, model.head.zScale);
            var points = new ArrayList<Vector3f>();
            var vertices = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{VertexConsumer.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("vertex") && arguments.length == 4) {
                            points.add(new Vector3f((float) arguments[1], (float) arguments[2], (float) arguments[3])
                                    .mulPosition((Matrix4f) arguments[0]));
                        }
                        return method.getReturnType() == void.class ? null : proxy;
                    });
            layers.render(stack, vertices, 0, 0, rotation, new Vector3f(), CitizenHairGravity.Torso.NONE, true);
            assertEquals(16, points.size());
            positions.add(points);
        }
        var adultNeck = new Vector3f().mulPosition(bodyStack(CitizenAge.ADULT).last().pose());
        var childNeck = new Vector3f().mulPosition(bodyStack(CitizenAge.CHILD).last().pose());
        for (int i = 0; i < positions.get(0).size(); i++) {
            var adult = new Vector3f(positions.get(0).get(i)).sub(adultNeck).mul(0.75F).add(childNeck);
            assertTrue(adult.equals(positions.get(1).get(i), 0.000001F));
        }
    }

    @Test
    void childFrontAndBackHairUseTorsoBoundsInHeadSpace() throws Exception {
        var torso = new CitizenHairGravity.Torso(-5, -0.01F, -7, 5, 12.2F, 7);
        var origin = new Vector3f(0.1F, -0.2F, 0.3F);
        var relative = torso.relativeTo(origin, 1.5F);
        assertEquals((torso.minZ() - origin.z) / 1.5F, relative.minZ());
        assertSame(CitizenHairGravity.Torso.NONE, CitizenHairGravity.Torso.NONE.relativeTo(origin, 1.5F));
        var layers = CitizenHairLayers.read(JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/assets/createapparel/citizen/hair/extensions.json"))).getAsJsonObject());
        for (var layer : layers.layers()) {
            if (layer.x0() == layer.x1()) continue;
            for (float yaw : new float[]{-0.21F, 0, 0.21F}) for (int across = 0; across <= 16; across++) {
                var rotation = new Quaternionf().rotationZYX(0, yaw, 0.08F);
                for (int drop = 0; drop <= 16; drop++) {
                    var point = CitizenHairGravity.point(layer, across / 16F, drop, rotation, new Vector3f(), relative)
                            .mul(1.5F).add(origin);
                    assertFalse(point.x > torso.minX() && point.x < torso.maxX()
                            && point.y > torso.minY() && point.y < torso.maxY()
                            && point.z > torso.minZ() && point.z < torso.maxZ());
                }
            }
        }
    }
}
