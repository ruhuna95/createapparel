package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.entity.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MannequinPickingTest {
    @Test
    void allBodyPartsRemainPickableAcrossEveryPresetBodyStepAndEntityYaw() {
        for (var variant : MannequinVariant.values()) {
            var form = variant.form();
            for (var pose : form.poses()) for (int step = 0; step < 16; step++) {
                for (float yaw : new float[]{0, 45, 135, -170}) for (var part : MannequinInteraction.BodyPart.values()) {
                    boolean exists = switch (part) {
                        case HEAD -> form.hasHead();
                        case BODY -> form.hasTorso() || form == MannequinForm.LOWER;
                        case LEFT_ARM, RIGHT_ARM -> form.hasTorso();
                        case LEFT_LEG, RIGHT_LEG -> form.hasLegs();
                    };
                    if (!exists) continue;
                    float y = part == MannequinInteraction.BodyPart.HEAD ? -4
                            : part == MannequinInteraction.BodyPart.BODY && form == MannequinForm.LOWER ? 9 : 5;
                    var transform = MannequinInteraction.partTransform(variant, pose, part);
                    var from = transform.transformPosition(new Vector3f(0, y, -2.2F));
                    var to = transform.transformPosition(new Vector3f(0, y, 0));
                    float rotation = yaw + step * 22.5F;
                    var start = form.modelToEntity(vec(from), rotation);
                    var end = form.modelToEntity(vec(to), rotation);
                    assertTrue(MannequinInteraction.hitPart(variant, pose, start, end, yaw, step * 22.5F).isPresent(),
                            variant + "/" + pose + "/" + step + "/" + yaw + "/" + part);
                }
            }
        }
    }

    @Test
    void pedestalSupportsAndPartialBodiesUseTheirRenderedFrames() {
        for (var variant : MannequinVariant.values()) for (var pose : variant.form().poses()) {
            for (int step = 0; step < 16; step++) for (float yaw : new float[]{0, 45, 135, -170}) {
                var form = variant.form();
                Vec3 start = rotate(new Vec3(0.3, MannequinForm.PEDESTAL_HEIGHT / 2, -1), -yaw);
                Vec3 end = rotate(new Vec3(0.3, MannequinForm.PEDESTAL_HEIGHT / 2, 0), -yaw);
                var pedestal = MannequinInteraction.hitPart(variant, pose, start, end, yaw, step * 22.5F).orElseThrow();
                assertTrue(pedestal.bodyPart().isEmpty());
                assertEquals(1 - 6.0 / 16, start.distanceTo(pedestal.entityPoint()), 0.00002);
                if (form == MannequinForm.UPPER || form == MannequinForm.HEAD) {
                    float height = MannequinForm.PEDESTAL_HEIGHT + form.supportHeightPixels() / 32;
                    var support = MannequinInteraction.hitPart(variant, pose,
                            rotate(new Vec3(0, height, -1), -yaw), rotate(new Vec3(0, height, 0), -yaw),
                            yaw, step * 22.5F).orElseThrow();
                    assertTrue(support.bodyPart().isEmpty(), "Supports do not select apparel");
                    assertEquals(height, support.entityPoint().y, 0.00001);
                }
                if (form == MannequinForm.LOWER || form == MannequinForm.HEAD) {
                    float bodyY = form == MannequinForm.HEAD ? 1 : 9;
                    var body = MannequinInteraction.hitPart(variant, pose,
                            form.modelToEntity(new Vec3(3.5, bodyY, -3), yaw + step * 22.5F),
                            form.modelToEntity(new Vec3(3.5, bodyY, 0), yaw + step * 22.5F),
                            yaw, step * 22.5F).orElseThrow();
                    assertEquals(form == MannequinForm.HEAD, body.bodyPart().isEmpty());
                }
            }
        }
    }

    @Test
    void emptyBroadPhaseSpaceNeverBecomesAHit() {
        for (var variant : MannequinVariant.values()) for (int step = 0; step < 16; step++) {
            var form = variant.form();
            for (float yaw : new float[]{0, 45, 135, -170}) {
                for (Vec3 modelPoint : new Vec3[]{new Vec3(4.2, 8, 0), new Vec3(8.8, 8, 0), new Vec3(4.5, -4, 0)}) {
                    if (modelPoint.y > 0 && !form.hasTorso()) continue;
                    float rotation = yaw + step * 22.5F;
                    assertTrue(MannequinInteraction.hitPart(variant, form.defaultPose(),
                            form.modelToEntity(modelPoint.add(0, 0, -32), rotation),
                            form.modelToEntity(modelPoint.add(0, 0, 32), rotation), yaw, step * 22.5F).isEmpty(),
                            variant + "/" + step + "/" + yaw + "/" + modelPoint);
                }
            }
        }
        assertTrue(MannequinInteraction.hitPart(MannequinVariant.FULL_WHITE_CLASSIC, PosePreset.STANDING,
                Vec3.ZERO, Vec3.ZERO, 0, 0).isEmpty());
    }

    @Test
    void clientMixinIsRequiredClientOnlyAndUsesOfficialInjectionTargets() throws Exception {
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/createapparel.mixins.json"))).getAsJsonObject();
        assertTrue(config.get("required").getAsBoolean());
        assertTrue(config.getAsJsonArray("client").asList().stream()
                .anyMatch(value -> value.getAsString().equals("GameRendererMixin")));
        assertFalse(config.has("mixins"));
        assertFalse(config.has("server"));
        assertFalse(config.has("refmap"));
        assertEquals("JAVA_21", config.get("compatibilityLevel").getAsString());
        assertNotNull(net.minecraft.client.renderer.GameRenderer.class.getDeclaredMethod("pick",
                net.minecraft.world.entity.Entity.class, double.class, double.class, float.class));
        assertNotNull(net.minecraft.world.entity.projectile.ProjectileUtil.class.getDeclaredMethod("getEntityHitResult",
                net.minecraft.world.entity.Entity.class, Vec3.class, Vec3.class, net.minecraft.world.phys.AABB.class,
                java.util.function.Predicate.class, double.class));
    }

    private static Vec3 vec(Vector3f point) { return new Vec3(point.x, point.y, point.z); }

    private static Vec3 rotate(Vec3 point, float degrees) {
        double angle = Math.toRadians(degrees);
        return new Vec3(point.x * Math.cos(angle) + point.z * Math.sin(angle), point.y,
                point.z * Math.cos(angle) - point.x * Math.sin(angle));
    }
}
