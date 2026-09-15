package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkirtPoseGeometryTest {
    private static GarmentPartModel model() throws Exception {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:a_line_skirt/torso"),
                JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/assets/createapparel/garment_models/parts/a_line_skirt/torso.json"))).getAsJsonObject());
    }

    @Test
    void actualLegPoseDrivesMotionWithoutRidingFlag() throws Exception {
        var part = model();
        var player = new PlayerModel<LivingEntity>(
                PlayerModel.createMesh(CubeDeformation.NONE, false).getRoot().bake(64, 64), false);
        assertSame(part, SkirtPoseGeometry.animate(part, player));
        player.leftLeg.xRot = -(float) Math.PI / 2;
        var seated = SkirtPoseGeometry.animate(part, player);
        assertNotEquals(part, seated);
        assertTrue(seated.elements().get(1).corners().get(0).y() > part.elements().get(1).from().y());
        assertLengths(part, seated);
        player.leftLeg.xRot = 0;
        player.rightLeg.xRot = -(float) Math.PI / 2;
        assertEquals(seated, SkirtPoseGeometry.animate(part, player));
        player.rightLeg.xRot = 0;
        player.body.xRot = 0.5F;
        assertNotEquals(part, SkirtPoseGeometry.animate(part, player));
    }

    @Test
    void containedLegsKeepExactRestShape() throws Exception {
        var part = model();
        assertSame(part, SkirtPoseGeometry.deform(part, List.of(
                new Vec3(-3.9F, 12, -2), new Vec3(3.9F, 6, 2))));
    }

    @Test
    void strongestLegControlsWholeFrontAndSidesShareCorners() throws Exception {
        var part = model();
        var weaker = new Vec3(-2, 7, -4);
        var stronger = new Vec3(2, 9, -6);
        var both = SkirtPoseGeometry.deform(part, List.of(weaker, stronger));
        var one = SkirtPoseGeometry.deform(part, List.of(stronger));
        assertEquals(one, both);
        assertEquals(part.elements().get(0), both.elements().get(0));
        var corners = both.elements().get(1).corners();
        for (var panel : both.elements().subList(1, 5)) {
            assertEquals(corners, panel.corners());
            assertEquals(part.elements().get(1).corners().subList(4, 8), panel.corners().subList(4, 8));
        }
        assertTrue(corners.get(0).z() < part.elements().get(1).corners().get(0).z());
        assertEquals(part.elements().get(1).corners().get(2), corners.get(2));
    }

    @Test
    void containmentOverridesOldBackAngleLimit() throws Exception {
        var part = model();
        var points = List.of(new Vec3(2, 12, -8), new Vec3(-2, 12, 8));
        var result = SkirtPoseGeometry.deform(part, points);
        var corners = result.elements().get(1).corners();
        assertTrue(angle(corners.get(2), corners.get(6)) > 30);
        points.forEach(point -> assertContained(result, point));
    }

    @Test
    void walkingCrouchingAndRidingPreserveLengthAndCoverLegDirections() throws Exception {
        var part = model();
        var player = new PlayerModel<LivingEntity>(
                PlayerModel.createMesh(CubeDeformation.NONE, false).getRoot().bake(64, 64), false);
        for (float bodyAngle : new float[]{0, 0.5F}) {
            player.body.xRot = bodyAngle;
            for (float pitch : new float[]{-1.8F, -1.57F, -1, -0.5F, 0, 0.5F, 1}) {
                player.leftLeg.xRot = pitch;
                player.rightLeg.xRot = -pitch;
                for (float spread : new float[]{0, 0.25F}) {
                    player.leftLeg.zRot = spread;
                    player.rightLeg.zRot = -spread;
                    player.leftLeg.yRot = 0.3F;
                    player.rightLeg.yRot = -0.3F;
                    player.leftLeg.z = bodyAngle == 0 ? 0 : 4;
                    player.rightLeg.z = player.leftLeg.z;
                    var result = SkirtPoseGeometry.animate(part, player);
                    assertLengths(part, result);
                    assertEquals(part.elements().get(0), result.elements().get(0));
                    assertEquals(part.elements().get(1).corners().subList(4, 8),
                            result.elements().get(1).corners().subList(4, 8));
                    for (var leg : List.of(player.leftLeg, player.rightLeg)) {
                        var transform = new org.joml.Matrix4f().rotateX(-bodyAngle)
                                .translate(leg.x, leg.y, leg.z).rotateZYX(leg.zRot, leg.yRot, leg.xRot);
                        for (float y = 0; y <= 12; y += 0.25F) {
                            for (float x : new float[]{-2, 0, 2}) {
                                for (float z : new float[]{-2, 0, 2}) {
                                    var p = transform.transformPosition(new org.joml.Vector3f(x, y, z));
                                    var point = new Vec3(p.x, 24 - p.y, p.z);
                                    if (point.y() >= 5.996F && point.y() < 13.999F) {
                                        assertContained(result, point);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void assertContained(GarmentPartModel part, Vec3 point) {
        var c = part.elements().get(1).corners();
        float frontWeight = (c.get(4).y() - point.y()) / (c.get(4).y() - c.get(0).y());
        float backWeight = (c.get(6).y() - point.y()) / (c.get(6).y() - c.get(2).y());
        float minX = c.get(4).x() + frontWeight * (c.get(0).x() - c.get(4).x());
        float maxX = c.get(6).x() + backWeight * (c.get(2).x() - c.get(6).x());
        float minZ = c.get(4).z() + frontWeight * (c.get(0).z() - c.get(4).z());
        float maxZ = c.get(6).z() + backWeight * (c.get(2).z() - c.get(6).z());
        assertTrue(point.x() >= minX - 0.02F && point.x() <= maxX + 0.02F
                && point.z() >= minZ - 0.02F && point.z() <= maxZ + 0.02F, point.toString());
    }

    private static void assertLengths(GarmentPartModel rest, GarmentPartModel posed) {
        for (int panel = 1; panel < 5; panel++) {
            var original = rest.elements().get(panel).corners();
            var actual = posed.elements().get(panel).corners();
            for (int i = 0; i < 4; i++) {
                assertEquals(length(original.get(i), original.get(i + 4)),
                        length(actual.get(i), actual.get(i + 4)), 0.0001);
                assertEquals(original.get(i + 4), actual.get(i + 4));
            }
        }
    }

    private static double length(Vec3 a, Vec3 b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }

    @Test
    void textureAndOpenShellMatchAssetContract() throws Exception {
        var part = model();
        assertEquals(5, part.elements().size());
        assertEquals(14, part.elements().get(0).from().y());
        assertEquals(15.004F, part.elements().get(0).to().y());
        for (var panel : part.elements().subList(1, 5)) {
            assertEquals(14, panel.to().y());
            assertEquals(5.996F, panel.from().y());
            assertEquals(8, panel.faces().values().iterator().next().v1()
                    - panel.faces().values().iterator().next().v0());
        }
        var png = ImageIO.read(Path.of(
                "src/main/resources/assets/createapparel/textures/garment/a_line_skirt/torso/base/default.png").toFile());
        assertEquals(32, png.getWidth());
        assertEquals(32, png.getHeight());
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int pixel = png.getRGB(x, y);
                assertEquals(pixel >> 16 & 255, pixel >> 8 & 255);
                assertEquals(pixel >> 8 & 255, pixel & 255);
                assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
            }
        }
        for (var element : part.elements()) {
            assertFalse(element.faces().containsKey(ClientGarmentAssets.Face.TOP));
            assertFalse(element.faces().containsKey(ClientGarmentAssets.Face.BOTTOM));
            for (var uv : element.faces().values()) {
                assertEquals(255, png.getRGB((int) ((uv.u0() + uv.u1()) / 2),
                        (int) ((uv.v0() + uv.v1()) / 2)) >>> 24);
            }
        }
    }

    private static double angle(Vec3 bottom, Vec3 top) {
        return Math.toDegrees(Math.atan2(Math.abs(bottom.z() - top.z()), top.y() - bottom.y()));
    }
}
