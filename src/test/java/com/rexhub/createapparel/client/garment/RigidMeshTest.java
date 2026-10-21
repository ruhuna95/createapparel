package com.rexhub.createapparel.client.garment;

import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RigidMeshTest {
    @Test
    void allAssetMeshesPreserveEveryCornerUvAndFaceNormalExactly() throws Exception {
        var root = Path.of("src/main/resources/assets/createapparel/garment_models/parts");
        int elements = 0;
        try (var files = Files.walk(root)) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                String name = root.relativize(file).toString().replace('\\', '/').replace(".json", "");
                var part = DualEnvelopeDeformerTest.part(name);
                for (var element : part.elements()) {
                    var mesh = element.mesh();
                    assertSame(mesh, element.mesh());
                    assertTrue(mesh.positionCount() <= mesh.cornerCount());
                    int corner = 0;
                    for (int face = 0; face < element.bakedFaces().size(); face++) {
                        var baked = element.bakedFaces().get(face);
                        assertEquals(baked.normal().x(), mesh.normal(face * 3));
                        assertEquals(baked.normal().y(), mesh.normal(face * 3 + 1));
                        assertEquals(baked.normal().z(), mesh.normal(face * 3 + 2));
                        for (var vertex : baked.vertices()) {
                            int p = mesh.positionIndex(corner) * 3;
                            assertEquals(vertex.x(), mesh.coordinate(p)); assertEquals(vertex.y(), mesh.coordinate(p + 1)); assertEquals(vertex.z(), mesh.coordinate(p + 2));
                            assertEquals(vertex.u(), mesh.u(corner)); assertEquals(vertex.v(), mesh.v(corner));
                            for (boolean humanoid : new boolean[]{false, true}) {
                                var matrix = new Matrix4f().translate(.3F, -.7F, 2).rotateXYZ(.7F, -.3F, 1.1F).scale(-.9375F, -.9375F, .9375F);
                                var old = matrix.transform(new Vector4f(vertex.x(), humanoid ? 24 - vertex.y() : vertex.y(), vertex.z(), 1));
                                var indexed = matrix.transform(new Vector4f(mesh.coordinate(p), humanoid ? 24 - mesh.coordinate(p + 1) : mesh.coordinate(p + 1), mesh.coordinate(p + 2), 1));
                                assertEquals(old, indexed);
                            }
                            corner++;
                        }
                    }
                    assertEquals(corner, mesh.cornerCount()); elements++;
                }
            }
        }
        assertTrue(elements > 100);
    }
}
