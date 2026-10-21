package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CitizenSupportGeometryTest {
    @Test void everyBodyFaceRetainsItsIndexedCornersAndUv() throws Exception {
        var root = Path.of("src/main/resources/assets/createapparel/body_models/parts");
        int parts = 0;
        try (var files = Files.walk(root)) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var part = ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:test_body"),
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                check(part); parts++;
            }
        }
        assertTrue(parts >= 6);
    }

    @Test void defaultUnderwearPreservesFacesInsteadOfApplyingCoverageApproximation() throws Exception {
        var root = Path.of("src/main/resources/assets/createapparel/garment_models/parts");
        int parts = 0;
        try (var files = Files.walk(root)) {
            for (var file : files.filter(path -> path.toString().contains("default_underwear") && path.toString().endsWith(".json")).toList()) {
                var part = ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:test_underwear"),
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                check(part); parts++;
            }
        }
        assertTrue(parts >= 3);
    }

    private static void check(ClientGarmentAssets.GarmentPartModel part) {
        assertFalse(part.elements().isEmpty());
        for (var element : part.elements()) {
            int corner = 0;
            for (var face : element.bakedFaces()) for (var vertex : face.vertices()) {
                int position = element.mesh().positionIndex(corner) * 3;
                assertEquals(vertex.x(), element.mesh().coordinate(position));
                assertEquals(vertex.y(), element.mesh().coordinate(position + 1));
                assertEquals(vertex.z(), element.mesh().coordinate(position + 2));
                assertEquals(vertex.u(), element.mesh().u(corner));
                assertEquals(vertex.v(), element.mesh().v(corner));
                corner++;
            }
            assertEquals(element.faces().size() * 4, corner);
            assertEquals(corner, element.mesh().cornerCount());
        }
    }
}
