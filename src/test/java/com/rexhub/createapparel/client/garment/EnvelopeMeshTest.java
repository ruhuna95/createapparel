package com.rexhub.createapparel.client.garment;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnvelopeMeshTest {
    @Test
    void preparedMeshIsSharedButPoseResultsAndBodyEdgeTopologyAreNotAliased() throws Exception {
        var part = DualEnvelopeDeformerTest.part("a_line_skirt/torso");
        var model = DualEnvelopeDeformerTest.player();
        var first = DualEnvelopeDeformerTest.pose(part, model);
        var before = new float[first.mesh().positionCount() * 3];
        first.positions(before, new Vector3f());
        model.leftLeg.xRot = -1.4F;
        var second = DualEnvelopeDeformerTest.pose(part, model);
        assertSame(first.mesh(), second.mesh());
        compare(second, "second citizen pose");
        var after = new float[before.length];
        first.positions(after, new Vector3f());
        assertArrayEquals(before, after);

        model.crouching = true; model.body.xRot = .5F;
        model.leftLeg.z = model.rightLeg.z = 4;
        var motion = part.legEnvelope().orElseThrow();
        var crouch = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(model), motion.clearance(), true);
        assertTrue(crouch.usesBodyEdges());
        var retained = crouch.mesh();
        motion.geometry().quadsAtBodyEdge(11.75F);
        assertSame(retained, crouch.mesh());
        compare(crouch, "retained topology after another body-edge height");
        var next = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(model), motion.clearance(), true);
        assertNotSame(retained, next.mesh());
        compare(next, "rebuilt topology");
        compare(crouch, "old pose after cache replacement");
    }

    @Test
    void allEnvelopeAssetsMatchLegacyPositionsUvAndFaceNormalsExactly() throws Exception {
        var root = Path.of("src/main/resources/assets/createapparel/garment_models/parts");
        int assets = 0, comparisons = 0;
        try (var files = Files.walk(root)) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                if (!Files.readString(file).contains("leg_envelope")) continue;
                String name = root.relativize(file).toString().replace('\\', '/').replace(".json", "");
                var part = DualEnvelopeDeformerTest.part(name);
                if (part.legEnvelope().isEmpty()) continue;
                assets++;
                for (boolean slim : new boolean[]{false, true}) for (int scenario = 0; scenario < 8; scenario++) {
                    var player = new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE, slim).getRoot().bake(64, 64), slim);
                    player.leftLeg.xRot = new float[]{0, -.7F, -1.4F, 1.4F, -1.57F, -.6F, -1.2F, -.7F}[scenario];
                    player.rightLeg.xRot = new float[]{0, .7F, 1.4F, -.8F, -1.57F, .4F, 1, .8F}[scenario];
                    if (scenario >= 5) {
                        player.crouching = true;
                        player.body.xRot = .5F; player.leftLeg.z = player.rightLeg.z = 4;
                        player.leftLeg.yRot = -.3F; player.rightLeg.yRot = .3F;
                        player.leftLeg.zRot = -.5F; player.rightLeg.zRot = .5F;
                    }
                    var motion = part.legEnvelope().orElseThrow();
                    var pose = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(player), motion.clearance(), scenario >= 5);
                    compare(pose, name + " slim=" + slim + " pose=" + scenario);
                    if (scenario == 7) {
                        var innerPart = DualEnvelopeDeformerTest.part("a_line_skirt/torso").legEnvelope().orElseThrow();
                        var inner = new DualEnvelopeDeformer.Pose(innerPart.geometry(), new LegEnvelopeGeometry(player), innerPart.clearance(), true);
                        pose.enclose(inner, .15F);
                        compare(pose, name + " layered");
                    }
                    comparisons++;
                }
            }
        }
        assertTrue(assets >= 40, "Must cover all skirt/dress/coat assets");
        System.out.println("ENVELOPE_MESH_EXACT assets=" + assets + " poses=" + comparisons);
    }

    private static void compare(DualEnvelopeDeformer.Pose pose, String label) {
        var mesh = pose.mesh();
        assertEquals(pose.quads().size(), mesh.quadCount());
        assertTrue(mesh.positionCount() < mesh.cornerCount(), label);
        var positions = new float[mesh.positionCount() * 3];
        var normals = new float[mesh.quadCount() * 4];
        pose.positions(positions, new Vector3f());
        mesh.normals(positions, normals);
        var points = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        for (int face = 0; face < mesh.quadCount(); face++) {
            var quad = pose.quads().get(face);
            for (int i = 0; i < 4; i++) {
                pose.shift(quad.points().get(i), points[i]);
                int corner = face * 4 + i, p = mesh.positionIndex(corner) * 3;
                exact(points[i].x, positions[p], label + " x");
                exact(points[i].y, positions[p + 1], label + " y");
                exact(points[i].z, positions[p + 2], label + " z");
                exact(quad.uv().get(i).u(), mesh.u(corner), label + " u");
                exact(quad.uv().get(i).v(), mesh.v(corner), label + " v");
            }
            float ux = points[1].x - points[0].x, uy = points[1].y - points[0].y, uz = points[1].z - points[0].z;
            float vx = points[2].x - points[0].x, vy = points[2].y - points[0].y, vz = points[2].z - points[0].z;
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            assertEquals(length < .000001F ? 0 : 1, normals[face * 4 + 3], label);
            if (length < .000001F) continue;
            exact(nx / length, normals[face * 4], label + " nx");
            exact(-ny / length, normals[face * 4 + 1], label + " ny");
            exact(nz / length, normals[face * 4 + 2], label + " nz");
        }
    }

    private static void exact(float expected, float actual, String label) {
        assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(actual), label + " expected=" + expected + " actual=" + actual);
    }
}
