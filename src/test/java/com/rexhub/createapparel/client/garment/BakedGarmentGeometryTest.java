package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BakedGarmentGeometryTest {
    private static ClientGarmentAssets.GarmentPartModel load(Path path) throws Exception {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:test"),
                JsonParser.parseString(Files.readString(path)).getAsJsonObject());
    }

    @Test
    void allGarmentAndBodyFacesMatchOriginalVerticesUvsAndNormals() throws Exception {
        for (String folder : List.of("garment_models", "body_models")) {
            try (var paths = Files.walk(Path.of("src/main/resources/assets/createapparel/" + folder + "/parts"))) {
                for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var part = load(path);
                    for (var element : part.elements()) {
                        int index = 0;
                        for (var face : element.faces().entrySet()) {
                            var baked = element.bakedFaces().get(index++);
                            var points = GarmentElementGeometry.faceVertices(element, face.getKey());
                            assertEquals(GarmentElementGeometry.normal(points), baked.normal(), path.toString());
                            for (int i = 0; i < 4; i++) {
                                var vertex = baked.vertices().get(i);
                                assertEquals(points.get(i), new ClientGarmentAssets.Vec3(vertex.x(), vertex.y(), vertex.z()));
                                assertEquals(face.getValue().corners().get(i), new ClientGarmentAssets.TexturePoint(vertex.u(), vertex.v()));
                            }
                        }
                        assertEquals(element.faces().size(), element.bakedFaces().size());
                        assertSame(element.bakedFaces(), element.bakedFaces());
                    }
                }
            }
        }
    }

    @Test
    void flatBoundsReuseIdentityAndInvalidateOnReload() throws Exception {
        var path = Path.of("src/main/resources/assets/createapparel/garment_models/parts/t_shirt/torso.json");
        var part = load(path);
        var expected = FlatGarmentGeometry.fromParts(List.of(part));
        var cached = FlatGarmentGeometry.cached(List.of(part));
        assertEquals(expected, cached);
        assertSame(cached, FlatGarmentGeometry.cached(List.of(part)));
        assertNotSame(cached, FlatGarmentGeometry.cached(List.of(load(path))));
        FlatGarmentGeometry.clearCache();
        assertNotSame(cached, FlatGarmentGeometry.cached(List.of(part)));
    }
}
