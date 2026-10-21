package com.rexhub.createapparel.client.garment;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CitizenLodTest {
    @Test void distanceAndHysteresisAreStableWithoutAdditionalDistanceCulling() {
        assertEquals(0, select(-1, 11.99)); assertEquals(1, select(-1, 12));
        assertEquals(2, select(-1, 32)); assertEquals(3, select(-1, 64));
        assertEquals(0, select(0, 12.9)); assertEquals(1, select(0, 13));
        assertEquals(1, select(1, 11.1)); assertEquals(0, select(1, 10.9));
        assertEquals(3, select(0, 1000)); assertEquals(0, select(3, 0));
        assertEquals(3, CitizenRenderLod.select(-1, 100, 64, 12, 32, 1));
        assertEquals(0, CitizenRenderLod.select(3, 0, 1, 32, 64, 8));
    }
    private static int select(int previous, double distance) { return CitizenRenderLod.select(previous, distance, 12, 32, 64, 1); }

    @Test void movingCamerasAndCitizensPromoteRestoreAndDoNotChatter() {
        for (int population : new int[]{1, 50, 100, 300, 500, 1000}) {
            var levels = new int[population];
            java.util.Arrays.fill(levels, -1);
            for (int frame = 0; frame <= 1600; frame++) {
                double distance = frame <= 800 ? 80 - frame * .1 : (frame - 800) * .1;
                for (int i = 0; i < population; i++) {
                    levels[i] = select(levels[i], distance);
                    if (frame == 800) assertEquals(0, levels[i]);
                    if (frame == 1600) assertEquals(3, levels[i]);
                }
            }
        }
        for (double boundary : new double[]{12, 32, 64}) {
            int previous = select(-1, boundary - 2), initial = previous;
            for (int frame = 0; frame < 10000; frame++) {
                previous = select(previous, boundary + .9 * Math.sin(frame));
                assertEquals(initial, previous);
            }
        }
        assertEquals(0, select(3, 2));
        assertEquals(3, select(0, 200));
        assertEquals(0, select(-1, 2));
    }

    @Test void lodMeshesKeepOriginalDeformationFieldUvAndOpeningsForEveryEnvelope() throws Exception {
        var root = Path.of("src/main/resources/assets/createapparel/garment_models/parts");
        int count = 0;
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                if (!Files.readString(path).contains("leg_envelope")) continue;
                var part = DualEnvelopeDeformerTest.part(root.relativize(path).toString().replace('\\', '/').replace(".json", ""));
                var motion = part.legEnvelope().orElseThrow();
                count++;
                for (int scenario = 0; scenario < 3; scenario++) {
                    var model = DualEnvelopeDeformerTest.player();
                    if (scenario > 0) { model.leftLeg.xRot = -1.4F; model.rightLeg.xRot = .9F; }
                    if (scenario == 2) { model.crouching = true; model.body.xRot = .5F; model.leftLeg.z = model.rightLeg.z = 4; }
                    var full = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(model), motion.clearance(), true);
                    int previous = full.mesh().cornerCount();
                    for (int lod = 1; lod <= 2; lod++) {
                        var coarse = new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(model), motion.clearance(), true, lod);
                        assertSame(full.geometry(), coarse.geometry());
                        assertTrue(coarse.mesh().cornerCount() < previous, part.id().toString());
                        previous = coarse.mesh().cornerCount();
                        var positions = new float[coarse.mesh().positionCount() * 3];
                        coarse.positions(positions, new Vector3f());
                        var expected = new Vector3f();
                        for (int face = 0; face < coarse.quads().size(); face++) for (int i = 0; i < 4; i++) {
                            var quad = coarse.quads().get(face);
                            full.shift(quad.points().get(i), expected);
                            int p = coarse.mesh().positionIndex(face * 4 + i) * 3;
                            assertEquals(expected.x, positions[p]); assertEquals(expected.y, positions[p + 1]); assertEquals(expected.z, positions[p + 2]);
                            assertEquals(quad.uv().get(i).u(), coarse.mesh().u(face * 4 + i));
                        }
                        assertEquals(full.quads().stream().anyMatch(q -> q.panel().equals("front_bridge")),
                                coarse.quads().stream().anyMatch(q -> q.panel().equals("front_bridge")));
                    }
                }
            }
        }
        assertEquals(47, count);
    }
}
