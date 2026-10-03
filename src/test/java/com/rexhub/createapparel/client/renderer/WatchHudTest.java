package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WatchHudTest {
    @Test
    void showsTheOuterHandFaceWithSomeSideDepthAndAHorizontallyTiltedArm() {
        for (var side : HumanoidArm.values()) {
            var rotation = WatchHud.armRotation(side);
            var outside = new Vector3f(side == HumanoidArm.LEFT ? 1 : -1, 0, 0).rotate(rotation);
            var front = new Vector3f(0, 0, -1).rotate(rotation);
            assertTrue(outside.z < -0.9F);
            assertTrue(front.z < -0.3F && front.z > -0.4F);
            var arm = new Vector3f(0, 1, 0).rotate(rotation);
            assertEquals(Math.tan(Math.toRadians(20)), Math.abs(arm.y / arm.x), 0.00001);
            assertTrue(arm.y < 0);
            assertEquals(-Math.sin(Math.toRadians(12)), arm.z, 0.00001);
            assertEquals(1, arm.length(), 0.00001);
        }
    }

    @Test
    void watchFaceIsAsBrightAsTheUnlitDialAndBrighterThanEveryOtherArmFace() {
        for (var side : HumanoidArm.values()) {
            for (float scale : new float[]{320, 336, 416}) {
                var pose = new PoseStack();
                WatchHud.scaleArm(pose, scale);
                pose.mulPose(WatchHud.armRotation(side));
                var normalMatrix = pose.last().normal();
                var lights = WatchHud.armLights(side, normalMatrix);
                assertEquals(1, lights.first().length(), 0.00001);
                assertEquals(1, lights.second().length(), 0.00001);
                var outside = new Vector3f(side == HumanoidArm.LEFT ? 1 : -1, 0, 0).mul(normalMatrix);
                assertEquals(1, brightness(outside, lights), 0.00001);
                for (var normal : new Vector3f[]{new Vector3f(0, 0, -1), new Vector3f(0, 0, 1),
                        new Vector3f(0, 1, 0), new Vector3f(0, -1, 0),
                        new Vector3f(side == HumanoidArm.LEFT ? -1 : 1, 0, 0)}) {
                    assertTrue(brightness(normal.mul(normalMatrix), lights) < 0.9F);
                }
            }
        }
    }

    @Test
    void hudReflectionKeepsUnitNormalsAndTheExpectedScaledCoordinates() {
        var pose = new PoseStack();
        WatchHud.scaleArm(pose, 416);
        var point = new Vector3f(1, 2, 3).mulPosition(pose.last().pose());
        assertEquals(new Vector3f(416, 832, -1248), point);
        var normal = new Vector3f(0, 0, -1).mul(pose.last().normal());
        assertEquals(new Vector3f(0, 0, 1), normal);
    }

    private static float brightness(Vector3f normal, WatchHud.HudLights lights) {
        float first = Math.max(0, new Vector3f(lights.first()).normalize().dot(normal));
        float second = Math.max(0, new Vector3f(lights.second()).normalize().dot(normal));
        return Math.min(1, (first + second) * 0.6F + 0.4F);
    }

    @Test
    void twelveIsAboveTheDialAndTextRunsLeftToRightForBothHands() {
        for (var side : HumanoidArm.values()) {
            var twelve = new Vector3f(0, -1, 0).rotate(WatchHud.dialRotation(side));
            assertEquals(0, twelve.x, 0.00001);
            assertEquals(0, twelve.y, 0.00001);
            assertEquals(1, twelve.z, 0.00001);
            twelve.rotate(WatchHud.armRotation(side));
            assertTrue(twelve.y < -0.8F);
            var right = new Vector3f(1, 0, 0).rotate(WatchHud.dialRotation(side))
                    .rotate(WatchHud.armRotation(side));
            assertTrue(right.x > 0.9F);
        }
    }

    @Test
    void dialStaysOnScreenAboveTheHotbarAcrossGuiSizes() {
        for (int width : new int[]{320, 427, 640, 854, 1280}) {
            for (int height : new int[]{240, 360, 480, 720}) {
                for (var side : HumanoidArm.values()) {
                    var layout = WatchHud.layout(side, width, height);
                    for (int x : new int[]{-1, 1}) {
                        for (int y : new int[]{-1, 1}) {
                            var corner = new Vector3f(x / 32F, y / 32F, 0)
                                    .rotate(WatchHud.dialRotation(side)).rotate(WatchHud.armRotation(side));
                            float guiX = layout.x() + corner.x * layout.scale();
                            float guiY = layout.y() + corner.y * layout.scale();
                            assertTrue(guiX >= 0 && guiX <= width);
                            assertTrue(guiY >= 0 && guiY < height - 22 - 4);
                        }
                    }
                }
            }
        }
    }
}
