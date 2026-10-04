package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkirtBodyEdgeGeometryTest {
    private static final List<String> MODELS = List.of("a_line_skirt/torso", "a_line_skirt_mini/torso",
            "a_line_skirt_midi/torso", "a_line_skirt_long/torso", "a_line_skirt_maxi/torso",
            "pleated_skirt_micro/torso", "pleated_skirt_mini/torso", "pleated_skirt_knee/torso",
            "pleated_skirt_midi/torso", "pleated_skirt_long/torso",
            "flared_skirt_mini/torso", "flared_skirt_knee/torso", "flared_skirt_midi/torso",
            "flared_skirt_long/torso", "flared_skirt_maxi/torso",
            "tight_skirt_micro/torso", "tight_skirt_mini/torso", "tight_skirt_knee/torso",
            "tight_skirt_midi/torso", "tight_skirt_long/torso");

    private static net.minecraft.client.model.PlayerModel<?> crouching() {
        var player = DualEnvelopeDeformerTest.player();
        player.crouching = true;
        player.body.xRot = .5F;
        player.body.y = 3.2F;
        player.leftLeg.y = player.rightLeg.y = 12.2F;
        player.leftLeg.z = player.rightLeg.z = 4;
        return player;
    }

    @Test
    void rearStartsAtTheTorsoEdgeAndFrontStartsAtEachPosedLegEdge() throws Exception {
        for (String name : MODELS) {
            var player = crouching();
            player.rightLeg.xRot = .6F;
            player.leftLeg.xRot = -.3F;
            var motion = DualEnvelopeDeformerTest.part(name).legEnvelope().orElseThrow();
            var legs = new LegEnvelopeGeometry(player);
            var pose = new DualEnvelopeDeformer.Pose(motion.geometry(), legs, motion.clearance(), true);
            assertTrue(pose.usesBodyEdges(), name);
            var g = pose.geometry();
            float ring = (g.top() - 12) / (g.top() - g.bottom());
            var actual = new Vector3f();
            var expected = new Vector3f();
            for (int side = 0; side < 2; side++) {
                pose.shift(g.boundary(side, ring, true), 12, g.boundary(2, ring, false), actual);
                assertEquals(new Vector3f(g.boundary(side, 0, true), 12, g.boundary(2, 0, false)), actual, name);
                legs.frontEdge(side, side, legs.torsoFront() - g.boundary(0, 0, false), expected);
                expected.x += (side == 0 ? -1 : 1) * motion.clearance();
                expected.x = side == 0 ? Math.min(expected.x, g.boundary(side, 0, true))
                        : Math.max(expected.x, g.boundary(side, 0, true));
                pose.shift(g.boundary(side, ring, true), 12, g.boundary(0, ring, false), actual);
                assertEquals(expected.x, actual.x, .00001F, name);
                assertEquals(expected.y, actual.y, .00001F, name);
                assertEquals(expected.z, actual.z, .00001F, name);
            }
        }
    }

    @Test
    void waistbandRemainsFixedAndConnectionsAreSplitAndCachedWithInterpolatedUv() throws Exception {
        for (String name : MODELS) {
            var motion = DualEnvelopeDeformerTest.part(name).legEnvelope().orElseThrow();
            var g = motion.geometry();
            var pose = new DualEnvelopeDeformer.Pose(g, new LegEnvelopeGeometry(crouching()), motion.clearance(), true);
            var again = new DualEnvelopeDeformer.Pose(g, new LegEnvelopeGeometry(crouching()), motion.clearance(), true);
            assertSame(pose.quads(), again.quads(), name);
            assertTrue(pose.quads().size() >= g.quads().size(), name);
            var moved = new Vector3f();
            for (var quad : pose.quads()) for (Vec3 p : quad.points()) {
                pose.shift(p, moved);
                assertTrue(moved.isFinite(), name);
                if (p.y() >= g.top()) assertEquals(new Vector3f(p.x(), p.y(), p.z()), moved, name);
                for (var uv : quad.uv()) assertTrue(Float.isFinite(uv.u()) && Float.isFinite(uv.v()), name);
            }
            assertTrue(pose.quads().stream().flatMap(q -> q.points().stream()).anyMatch(p -> p.y() == 12), name);
            assertFalse(pose.quads().stream().anyMatch(q -> q.points().stream().anyMatch(p -> p.y() < 12)
                    && q.points().stream().anyMatch(p -> p.y() > 12)), name + " unsplit body-edge crease");
        }
    }

    @Test
    void everyHemMeridianKeepsItsTotalLengthIncludingTheConnection() throws Exception {
        for (String name : MODELS) for (float pitch : new float[]{-.7F, 0, .7F}) for (float yaw : new float[]{0, -.25F, .25F}) {
            var player = crouching();
            player.rightLeg.xRot = pitch;
            player.leftLeg.xRot = -pitch;
            player.body.yRot = yaw;
            var motion = DualEnvelopeDeformerTest.part(name).legEnvelope().orElseThrow();
            var g = motion.geometry();
            var pose = new DualEnvelopeDeformer.Pose(g, new LegEnvelopeGeometry(player), motion.clearance(), true);
            for (float x : new float[]{0, .375F, .5F, .625F, 1}) for (float z : new float[]{0, .25F, .5F, .75F, 1}) {
                float cut = (g.top() - 12) / (g.top() - g.bottom());
                var waist = new Vector3f(mix(g.boundary(0, 0, true), g.boundary(1, 0, true), x), g.top(),
                        mix(g.boundary(0, 0, false), g.boundary(2, 0, false), z));
                var root = new Vector3f();
                var hem = new Vector3f();
                float hemX = mix(g.boundary(0, 1, true), g.boundary(1, 1, true), x);
                float hemZ = mix(g.boundary(0, 1, false), g.boundary(2, 1, false), z);
                pose.shift(mix(g.boundary(0, cut, true), g.boundary(1, cut, true), x), 12,
                        mix(g.boundary(0, cut, false), g.boundary(2, cut, false), z), root);
                pose.shift(hemX, g.bottom(), hemZ, hem);
                assertEquals(waist.distance(hemX, g.bottom(), hemZ), waist.distance(root) + root.distance(hem), .00002F, name);
                assertTrue(root.distance(hem) > 0, name + " collapsed main panel");
                float remaining = waist.distance(hemX, g.bottom(), hemZ) - waist.distance(root);
                var point = new Vector3f();
                for (float progress : new float[]{.0001F, .125F, .5F}) {
                    float ring = mix(cut, 1, progress);
                    pose.shift(mix(g.boundary(0, ring, true), g.boundary(1, ring, true), x),
                            mix(g.top(), g.bottom(), ring),
                            mix(g.boundary(0, ring, false), g.boundary(2, ring, false), z), point);
                    assertEquals(remaining * progress, root.distance(point), .00004F,
                            name + " main length at " + progress + " yaw=" + yaw);
                }
            }
        }
    }

    @Test
    void crouchingSideTrianglesStayOutsideTheLowerTorso() throws Exception {
        for (String name : MODELS) for (float pitch : new float[]{0, -.5F, .5F}) {
            for (float yaw : new float[]{0, -.25F, .25F}) {
                var player = crouching();
                player.rightLeg.xRot = pitch;
                player.leftLeg.xRot = -pitch;
                player.body.yRot = yaw;
                var motion = DualEnvelopeDeformerTest.part(name).legEnvelope().orElseThrow();
                var pose = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(player), motion.clearance(), true);
                for (int side = 0; side < 2; side++) {
                    for (float y : new float[]{12.05F, 12.5F, 13, 13.5F, 13.95F}) {
                        for (float z : new float[]{-1.9F, -1, 0, 1, 1.9F}) {
                            var point = new Vector3f(side == 0 ? -4 : 4, y, z);
                            float outer = sideOuter(pose, side, point);
                            assertTrue(Float.isFinite(outer), name + " missing torso side at " + point
                                    + " pitch=" + pitch + " yaw=" + yaw);
                            assertTrue(side == 0 ? outer < point.x : outer > point.x,
                                    name + " exposed torso at " + point + " clothX=" + outer
                                            + " pitch=" + pitch + " yaw=" + yaw);
                        }
                    }
                }
            }
        }
    }

    @Test
    void crouchingSideTrianglesCoverTheUpperLegsWhileWalking() throws Exception {
        for (String name : MODELS) for (float pitch : new float[]{-.5F, 0, .5F}) {
            var player = crouching();
            player.rightLeg.xRot = pitch;
            player.leftLeg.xRot = -pitch;
            var motion = DualEnvelopeDeformerTest.part(name).legEnvelope().orElseThrow();
            var pose = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(player), motion.clearance(), true);
            var inverse = new Matrix4f().translation(player.body.x, player.body.y, player.body.z)
                    .rotateZYX(player.body.zRot, player.body.yRot, player.body.xRot).invert();
            for (int side = 0; side < 2; side++) {
                var leg = side == 0 ? player.rightLeg : player.leftLeg;
                var matrix = new Matrix4f(inverse).mul(new Matrix4f().translation(leg.x, leg.y, leg.z)
                        .rotateZYX(leg.zRot, leg.yRot, leg.xRot));
                for (float z : new float[]{-1.8F, 0, 1.8F}) for (float along : new float[]{.25F, .5F, 1}) {
                    var p = matrix.transformPosition(new Vector3f(side == 0 ? -1.95F : 1.95F, along, z));
                    p.y = 24 - p.y;
                    float outer = sideOuter(pose, side, p);
                    assertTrue(Float.isFinite(outer), name + " missing side at " + p + " pitch=" + pitch);
                    assertTrue(side == 0 ? outer <= p.x : outer >= p.x, name + " exposed side at " + p);
                }
            }
        }
    }

    static float sideOuter(DualEnvelopeDeformer.Pose pose, int side, Vector3f p) {
        float outer = Float.NaN;
        var a = new Vector3f(); var b = new Vector3f(); var c = new Vector3f();
        for (var quad : pose.quads()) {
            if (!quad.panel().equals(side == 0 ? "right_side" : "left_side")) continue;
            for (int[] indices : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                pose.shift(quad.points().get(indices[0]), a);
                pose.shift(quad.points().get(indices[1]), b);
                pose.shift(quad.points().get(indices[2]), c);
                float denominator = (b.z - c.z) * (a.y - c.y) + (c.y - b.y) * (a.z - c.z);
                if (Math.abs(denominator) < 1e-8F) continue;
                float u = ((b.z - c.z) * (p.y - c.y) + (c.y - b.y) * (p.z - c.z)) / denominator;
                float v = ((c.z - a.z) * (p.y - c.y) + (a.y - c.y) * (p.z - c.z)) / denominator;
                if (u < -.00001F || v < -.00001F || u + v > 1.00001F) continue;
                float x = u * a.x + v * b.x + (1 - u - v) * c.x;
                outer = Float.isFinite(outer) ? (side == 0 ? Math.min(outer, x) : Math.max(outer, x)) : x;
            }
        }
        return outer;
    }

    @Test
    void upperHemCachesSeparateTheFixedWaistAndBodyEdgeCrease() throws Exception {
        var motion = DualEnvelopeDeformerTest.part("a_line_skirt/torso").legEnvelope().orElseThrow();
        var standing = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(DualEnvelopeDeformerTest.player()),
                motion.clearance(), true);
        var crouching = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(crouching()), motion.clearance(), true);
        for (String name : List.of("t_shirt/torso", "shirt/torso", "denim_jacket/torso", "coat/chesterfield/torso")) {
            var upper = DualEnvelopeDeformerTest.part(name).hemGeometry();
            var rest = upper.quads(GarmentHemGeometry.dualSlope(standing));
            var crouched = upper.quads(GarmentHemGeometry.dualSlope(crouching));
            assertNotSame(rest, crouched, name);
            assertSame(rest, upper.quads(GarmentHemGeometry.dualSlope(standing)), name);
            assertSame(crouched, upper.quads(GarmentHemGeometry.dualSlope(crouching)), name);
            assertFalse(crouched.stream().anyMatch(q -> q.points().stream().anyMatch(p -> p.y() < 12)
                    && q.points().stream().anyMatch(p -> p.y() > 12)), name + " unsplit upper hem");
        }
    }

    @Test
    void frontBridgeCoversBothInnerThighsDuringCrouchingSteps() throws Exception {
        for (String name : MODELS) for (float pitch : new float[]{-.5F, 0, .5F}) {
            var player = crouching();
            player.rightLeg.xRot = pitch;
            player.leftLeg.xRot = -pitch;
            var motion = DualEnvelopeDeformerTest.part(name).legEnvelope().orElseThrow();
            var pose = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(player), motion.clearance(), true);
            var inverse = new Matrix4f().translation(player.body.x, player.body.y, player.body.z)
                    .rotateZYX(player.body.zRot, player.body.yRot, player.body.xRot).invert();
            for (int side = 0; side < 2; side++) {
                var leg = side == 0 ? player.rightLeg : player.leftLeg;
                var matrix = new Matrix4f(inverse).mul(new Matrix4f().translation(leg.x, leg.y, leg.z)
                        .rotateZYX(leg.zRot, leg.yRot, leg.xRot));
                for (float across : new float[]{-1.8F, 0, 1.8F}) for (float along : new float[]{.25F, .5F, 1}) {
                    var p = matrix.transformPosition(new Vector3f(across, along, -1.95F));
                    p.y = 24 - p.y;
                    float hem = frontHemHeight(pose, p.x);
                    if (Float.isFinite(hem) && p.y < hem) continue;
                    float outer = frontSurface(pose, p);
                    assertTrue(Float.isFinite(outer), name + " missing front at " + p + " pitch=" + pitch);
                    assertTrue(outer <= p.z, name + " exposed front at " + p + " clothZ=" + outer + " pitch=" + pitch);
                }
            }
        }
    }

    private static float frontSurface(DualEnvelopeDeformer.Pose pose, Vector3f p) {
        float outer = Float.NaN;
        var a = new Vector3f(); var b = new Vector3f(); var c = new Vector3f();
        for (var quad : pose.quads()) {
            if (!quad.panel().startsWith("front_")) continue;
            for (int[] indices : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                pose.shift(quad.points().get(indices[0]), a);
                pose.shift(quad.points().get(indices[1]), b);
                pose.shift(quad.points().get(indices[2]), c);
                float denominator = (b.x - c.x) * (a.y - c.y) + (c.y - b.y) * (a.x - c.x);
                if (Math.abs(denominator) < 1e-8F) continue;
                float u = ((b.x - c.x) * (p.y - c.y) + (c.y - b.y) * (p.x - c.x)) / denominator;
                float v = ((c.x - a.x) * (p.y - c.y) + (a.y - c.y) * (p.x - c.x)) / denominator;
                if (u < -.00001F || v < -.00001F || u + v > 1.00001F) continue;
                float z = u * a.z + v * b.z + (1 - u - v) * c.z;
                outer = Float.isFinite(outer) ? Math.min(outer, z) : z;
            }
        }
        return outer;
    }

    private static float frontHemHeight(DualEnvelopeDeformer.Pose pose, float x) {
        float height = Float.NaN;
        var a = new Vector3f(); var b = new Vector3f();
        for (var quad : pose.quads()) {
            if (!quad.panel().startsWith("front_") || quad.points().get(0).y() != pose.geometry().bottom()) continue;
            pose.shift(quad.points().get(0), a);
            pose.shift(quad.points().get(3), b);
            float t = (x - a.x) / (b.x - a.x);
            if (!(t >= 0 && t <= 1)) continue;
            float y = mix(a.y, b.y, t);
            height = Float.isFinite(height) ? Math.min(height, y) : y;
        }
        return height;
    }

    @Test
    void sittingKeepsTheVerifiedEnvelopeInsteadOfOpeningGapsBelowTheTorsoEdge() throws Exception {
        int candidateGaps = 0;
        for (String name : MODELS) {
            var player = DualEnvelopeDeformerTest.player();
            player.riding = true;
            player.leftLeg.xRot = player.rightLeg.xRot = -1.4137167F;
            player.leftLeg.yRot = -.31415927F;
            player.rightLeg.yRot = .31415927F;
            player.leftLeg.zRot = -.07853982F;
            player.rightLeg.zRot = .07853982F;
            var motion = DualEnvelopeDeformerTest.part(name).legEnvelope().orElseThrow();
            var reference = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(player), motion.clearance());
            var seated = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(player), motion.clearance(), true);
            assertFalse(seated.usesBodyEdges(), name);
            var a = new Vector3f(); var b = new Vector3f();
            for (var quad : seated.quads()) for (Vec3 p : quad.points()) {
                reference.shift(p, a); seated.shift(p, b);
                assertEquals(a, b, name + " seating regression");
            }
            player.crouching = true;
            player.riding = false;
            var candidate = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(player), motion.clearance(), true);
            for (int side = 0; side < 2; side++) {
                var leg = side == 0 ? player.rightLeg : player.leftLeg;
                var matrix = new Matrix4f().translation(leg.x, leg.y, leg.z).rotateZYX(leg.zRot, leg.yRot, leg.xRot);
                for (float z : new float[]{-1.8F, 0, 1.8F}) for (float along = .5F; along < 6; along += .5F) {
                    var p = matrix.transformPosition(new Vector3f(side == 0 ? -1.95F : 1.95F, along, z));
                    p.y = 24 - p.y;
                    float before = sideOuter(reference, side, p);
                    if (!Float.isFinite(before) || (side == 0 ? before > p.x : before < p.x)) continue;
                    float after = sideOuter(candidate, side, p);
                    if (!Float.isFinite(after) || (side == 0 ? after > p.x : after < p.x)) candidateGaps++;
                }
            }
        }
        assertTrue(candidateGaps > 0, "The torso-edge candidate must not replace a seating envelope that covers these points");
    }

    @Test
    void layeredCoatsStayOutsideCrouchingConnectionsAndMainPanels() throws Exception {
        for (String name : List.of("a_line_skirt/torso", "tight_skirt_micro/torso", "flared_skirt_long/torso")) {
            var player = crouching();
            player.rightLeg.xRot = .4F;
            player.leftLeg.xRot = -.4F;
            var legs = new LegEnvelopeGeometry(player);
            var motion = DualEnvelopeDeformerTest.part(name).legEnvelope().orElseThrow();
            var inner = new DualEnvelopeDeformer.Pose(motion.geometry(), legs, motion.clearance(), true);
            for (String coatName : List.of("chesterfield", "trench", "duffle", "pea")) {
                var coat = DualEnvelopeDeformerTest.part("coat/" + coatName + "/torso").legEnvelope().orElseThrow();
                var outer = new DualEnvelopeDeformer.Pose(coat.geometry(), legs, coat.clearance() + .05F);
                outer.enclose(inner, .05F);
                for (int side = 0; side < 2; side++) for (boolean front : new boolean[]{true, false}) {
                    for (float y = 8; y <= 13.5F; y += .5F) {
                        var a = edgeAtHeight(inner, side, front, y);
                        var b = edgeAtHeight(outer, side, front, y);
                        if (a == null || b == null) continue;
                        assertTrue(side == 0 ? b.x <= a.x + .001F : b.x >= a.x - .001F,
                                name + " " + coatName + " x y=" + y + " inner=" + a + " outer=" + b);
                        assertTrue(front ? b.z <= a.z + .001F : b.z >= a.z - .001F,
                                name + " " + coatName + " z y=" + y + " inner=" + a + " outer=" + b);
                    }
                }
            }
        }
    }

    private static Vector3f edgeAtHeight(DualEnvelopeDeformer.Pose pose, int side, boolean front, float height) {
        var g = pose.geometry();
        var point = new Vector3f();
        pose.shift(g.boundary(side, 1, true), g.bottom(), g.boundary(front ? 0 : 2, 1, false), point);
        if (height < point.y) return null;
        float low = 0, high = 1;
        for (int i = 0; i < 30; i++) {
            float t = (low + high) / 2;
            pose.shift(g.boundary(side, t, true), mix(g.top(), g.bottom(), t), g.boundary(front ? 0 : 2, t, false), point);
            if (point.y > height) low = t; else high = t;
        }
        return point;
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
