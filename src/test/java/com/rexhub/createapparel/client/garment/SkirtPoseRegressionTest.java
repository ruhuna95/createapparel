package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedTextures;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SkirtPoseRegressionTest {
    @Test
    void sittingSideSurfacesCoverThighsAboveTheActualHem() throws Exception {
        for (String name : models()) {
            var model = DualEnvelopeDeformerTest.part(name);
            var player = DualEnvelopeDeformerTest.player();
            player.leftLeg.xRot = player.rightLeg.xRot = -1.4137167F;
            player.leftLeg.yRot = -.31415927F;
            player.rightLeg.yRot = .31415927F;
            player.leftLeg.zRot = -.07853982F;
            player.rightLeg.zRot = .07853982F;
            var pose = DualEnvelopeDeformerTest.pose(model, player);
            var geometry = pose.geometry();
            int checked = 0;
            for (int side = 0; side < 2; side++) {
                var leg = side == 0 ? player.rightLeg : player.leftLeg;
                var matrix = new Matrix4f().translation(leg.x, leg.y, leg.z)
                        .rotateZYX(leg.zRot, leg.yRot, leg.xRot);
                float waistX = geometry.boundary(side, 0, true);
                for (float localZ : new float[]{-1.8F, 0, 1.8F}) for (float along = .5F; along <= 10; along += .25F) {
                    var point = matrix.transformPosition(new Vector3f(side == 0 ? -1.95F : 1.95F, along, localZ));
                    point.y = 24 - point.y;
                    float depth = geometry.top() - point.y;
                    float reach = Math.max(0, geometry.boundary(0, 0, false) - point.z);
                    float distance = (float) Math.sqrt(depth * depth + reach * reach
                            + (point.x - waistX) * (point.x - waistX));
                    if (distance > geometry.top() - geometry.bottom() - .5F || reach == 0) continue;
                    float outer = Float.NaN;
                    var a = new Vector3f(); var b = new Vector3f(); var c = new Vector3f();
                    for (var quad : geometry.quads()) {
                        if (!quad.panel().equals(side == 0 ? "right_side" : "left_side")) continue;
                        for (int[] indices : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                            pose.shift(quad.points().get(indices[0]), a);
                            pose.shift(quad.points().get(indices[1]), b);
                            pose.shift(quad.points().get(indices[2]), c);
                            float denominator = (b.z - c.z) * (a.y - c.y) + (c.y - b.y) * (a.z - c.z);
                            if (Math.abs(denominator) < 1e-8F) continue;
                            float u = ((b.z - c.z) * (point.y - c.y) + (c.y - b.y) * (point.z - c.z)) / denominator;
                            float v = ((c.z - a.z) * (point.y - c.y) + (a.y - c.y) * (point.z - c.z)) / denominator;
                            if (u < -.00001F || v < -.00001F || u + v > 1.00001F) continue;
                            float x = u * a.x + v * b.x + (1 - u - v) * c.x;
                            outer = Float.isFinite(outer) ? (side == 0 ? Math.min(outer, x) : Math.max(outer, x)) : x;
                        }
                    }
                    if (!Float.isFinite(outer)) continue;
                    assertTrue(side == 0 ? outer <= point.x : outer >= point.x,
                            name + " exposed thigh at " + point + " clothX=" + outer);
                    checked++;
                }
            }
            assertTrue(checked > 0, name);
        }
    }

    private static List<String> models() {
        return List.of("a_line_skirt/torso", "a_line_skirt_mini/torso", "a_line_skirt_midi/torso",
                "a_line_skirt_long/torso", "a_line_skirt_maxi/torso",
                "pleated_skirt_micro/torso", "pleated_skirt_mini/torso", "pleated_skirt_knee/torso",
                "pleated_skirt_midi/torso", "pleated_skirt_long/torso",
                "flared_skirt_mini/torso", "flared_skirt_knee/torso", "flared_skirt_midi/torso",
                "flared_skirt_long/torso", "flared_skirt_maxi/torso",
                "tight_skirt_micro/torso", "tight_skirt_mini/torso", "tight_skirt_knee/torso",
                "tight_skirt_midi/torso", "tight_skirt_long/torso");
    }

    private static Vec3 rearHem(GarmentPartModel part, int side) {
        var geometry = part.legEnvelope().orElseThrow().geometry();
        return new Vec3(geometry.boundary(side, 1, true), geometry.bottom(), geometry.boundary(2, 1, false));
    }

    @Test
    void sittingDoesNotMagnifyThighSpreadIntoTheRearHemWidth() throws Exception {
        for (String name : models()) {
            var model = DualEnvelopeDeformerTest.part(name);
            var player = DualEnvelopeDeformerTest.player();
            player.leftLeg.xRot = player.rightLeg.xRot = -1.4137167F;
            player.leftLeg.yRot = -.31415927F;
            player.rightLeg.yRot = .31415927F;
            player.leftLeg.zRot = -.07853982F;
            player.rightLeg.zRot = .07853982F;
            var legs = new LegEnvelopeGeometry(player);
            var pose = DualEnvelopeDeformerTest.pose(model, player);
            var bounds = new float[4];
            var result = new Vector3f();
            for (int side = 0; side < 2; side++) {
                assertTrue(legs.extents(side, -100, 100, bounds));
                pose.shift(rearHem(model, side), result);
                float legWidth = side == 0 ? -bounds[0] : bounds[1];
                float skirtWidth = Math.abs(rearHem(model, side).x());
                assertTrue(Math.abs(result.x) <= Math.max(legWidth, skirtWidth) + .1F,
                        name + " sitting x=" + result.x + " leg=" + legWidth + " rest=" + skirtWidth);
            }
            for (var quad : pose.geometry().quads()) for (Vec3 point : quad.points()) {
                if (point.y() >= pose.geometry().top()) continue;
                float ring = (pose.geometry().top() - point.y()) / (pose.geometry().top() - pose.geometry().bottom());
                int side = point.x() < 0 ? 0 : 1;
                if (Math.abs(point.x()) > Math.abs(pose.geometry().boundary(side, ring, true)) + .00001F) continue;
                assertTrue(legs.extents(side, -100, 100, bounds));
                float limit = Math.max(side == 0 ? -bounds[0] : bounds[1], Math.abs(rearHem(model, side).x()));
                pose.shift(point, result);
                assertTrue(Math.abs(result.x) <= limit + .1F, name + " width=" + result.x + " limit=" + limit);
            }
        }
    }

    @Test
    void crouchingRearPanelsTiltTowardTheLegsWithoutMovingTheWaist() throws Exception {
        for (String name : models()) {
            var model = DualEnvelopeDeformerTest.part(name);
            var player = DualEnvelopeDeformerTest.player();
            player.crouching = true;
            player.body.xRot = .5F;
            player.body.y = 3.2F;
            player.leftLeg.xRot = player.rightLeg.xRot = 0;
            player.leftLeg.y = player.rightLeg.y = 12.2F;
            player.leftLeg.z = player.rightLeg.z = 4;
            var motion = model.legEnvelope().orElseThrow();
            var pose = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(player), motion.clearance(), true);
            var result = new Vector3f();
            for (int side = 0; side < 2; side++) {
                Vec3 rest = rearHem(model, side);
                pose.shift(rest, result);
                float before = (rest.z() - 2.249F) / (14 - rest.y());
                float after = (result.z - 2.249F) / (12 - result.y);
                assertTrue(after < before - .05F, name + " rear slope=" + after + " rest=" + before);
                Vec3 waist = new Vec3(side == 0 ? -4.004F : 4.004F, 14, 2.249F);
                pose.shift(waist, result);
                assertEquals(new Vector3f(waist.x(), waist.y(), waist.z()), result);
            }
        }
    }

    @Test
    void crouchingRearPitchFollowsEachLegAndIncludesTheConnectionInTheLength() throws Exception {
        for (String name : models()) for (float rightPitch : new float[]{.5F, .7F}) {
            var model = DualEnvelopeDeformerTest.part(name);
            var player = DualEnvelopeDeformerTest.player();
            player.crouching = true;
            player.body.xRot = .5F;
            player.body.y = 3.2F;
            player.leftLeg.xRot = 0;
            player.rightLeg.xRot = rightPitch;
            player.leftLeg.y = player.rightLeg.y = 12.2F;
            player.leftLeg.z = player.rightLeg.z = 4;
            var motion = model.legEnvelope().orElseThrow();
            var pose = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(player), motion.clearance(), true);
            var result = new Vector3f();
            for (int side = 0; side < 2; side++) {
                Vec3 rest = rearHem(model, side);
                var waist = new Vector3f(side == 0 ? -4.004F : 4.004F, 14, 2.249F);
                var anchor = new Vector3f(waist.x, 12, waist.z);
                pose.shift(rest, result);
                float relativePitch = (side == 0 ? player.rightLeg.xRot : player.leftLeg.xRot) - player.body.xRot;
                float height = 14 - rest.y();
                double expected = Math.tan(relativePitch) + (rest.z() - anchor.z) / (height * Math.cos(relativePitch));
                assertEquals(expected, (result.z - anchor.z) / (anchor.y - result.y), .00001F, name);
                assertEquals(waist.distance(rest.x(), rest.y(), rest.z()), waist.distance(anchor) + anchor.distance(result), .00001F, name);
            }
        }
    }

    @Test
    void renderFrameSharesCrouchingRearMotionWithLayeredHems() throws Exception {
        for (String name : models()) {
            var model = DualEnvelopeDeformerTest.part(name);
            var definition = new GarmentDefinition(model.id(), GarmentSlot.BOTTOM, model.id(), "default", "default", Set.of("plain"), Map.of());
            var textures = new ResolvedTextures(model.id(), Optional.empty(), Optional.empty(), Optional.empty(), 1, Optional.empty());
            var garment = new ResolvedGarment(definition, null, List.of(new ResolvedPart("torso", model, textures)));
            var player = DualEnvelopeDeformerTest.player();
            player.crouching = true;
            player.body.xRot = .5F;
            player.body.y = 3.2F;
            player.leftLeg.y = player.rightLeg.y = 12.2F;
            player.leftLeg.z = player.rightLeg.z = 4;
            var frame = new ApparelRenderFrame(slot -> slot == GarmentSlot.BOTTOM ? Optional.of(garment) : Optional.empty(), player);
            var pose = frame.envelope(model).orElseThrow();
            var slope = frame.skirtSlope().orElseThrow();
            assertSame(pose, slope.envelope());
            var shifted = new Vector3f();
            slope.forSlot(GarmentSlot.TOP).shift(new Vec3(0, 11, 2.249F), 3, shifted);
            assertTrue(shifted.z < 2.249F, name + " layered rear follows legs");
            var shared = new Vector3f();
            var g = pose.geometry();
            pose.shift(0, 11, g.boundary(2, 3 / (g.top() - g.bottom()), false), shared);
            assertEquals(shared.y, shifted.y, .00001F, name + " layered rear shares body-edge motion");
            assertEquals(shared.z + .025F, shifted.z, .00001F, name + " layered rear keeps its spacing");
        }
    }
}
