package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlatGarmentGeometryTest {
    @Test
    void rotatedBustAndDecorationsFlattenTogetherWithoutChangingWornGeometry() throws IOException {
        var parts = List.of(part("shirt/torso"), part("shirt/bust"), part("shirt/left_long_sleeve_classic"));
        var before = parts.stream().flatMap(p -> p.elements().stream()).toList();
        var flat = FlatGarmentGeometry.fromParts(parts);
        var original = flat.originalBounds();
        var bounds = flat.flattenedBounds();
        assertEquals(original.min().x(), bounds.min().x());
        assertEquals(original.max().x(), bounds.max().x());
        assertEquals(original.min().y(), bounds.min().y());
        assertEquals(original.max().y(), bounds.max().y());
        assertEquals(original.center().x(), bounds.center().x(), 0.00001);
        assertEquals(original.center().y(), bounds.center().y(), 0.00001);
        assertEquals(original.center().z(), bounds.center().z(), 0.00001);
        assertEquals(2, bounds.max().z() - bounds.min().z(), 0.00001);
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (var element : before) {
            for (var corner : element.corners()) {
                var rotated = FlatGarmentGeometry.rotate(corner, element.rotation());
                var point = flat.transform(corner, element.rotation());
                assertEquals(rotated.x(), point.x());
                assertEquals(rotated.y(), point.y());
                assertEquals((rotated.z() - original.center().z()) * flat.scaleZ(),
                        point.z() - bounds.center().z(), 0.00001);
                minZ = Math.min(minZ, point.z());
                maxZ = Math.max(maxZ, point.z());
            }
        }
        assertEquals(bounds.min().z(), minZ, 0.00001);
        assertEquals(bounds.max().z(), maxZ, 0.00001);
        assertEquals(before, parts.stream().flatMap(p -> p.elements().stream()).toList());
        for (var element : before) {
            assertEquals(element.faces(), parts.stream().flatMap(p -> p.elements().stream())
                    .filter(e -> e.name().equals(element.name())).findFirst().orElseThrow().faces());
        }
    }

    @Test
    void thinGeometryIsNotExpanded() {
        var json = JsonParser.parseString("{\"render_type\":\"cutout\",\"texture_size\":[16,16],"
                + "\"geometry\":{\"pivot\":[0,0,0],\"elements\":[{\"name\":\"thin\",\"from\":[0,0,0],\"to\":[4,4,1],"
                + "\"faces\":{\"front\":{\"uv\":[0,0,4,4]}}}]},"
                + "\"textures\":{\"variants\":{\"default\":{\"base\":\"createapparel:test\"}}}}").getAsJsonObject();
        var model = ClientGarmentAssetLoader.parsePart(ResourceLocation.fromNamespaceAndPath("createapparel", "thin"), json);
        var flat = FlatGarmentGeometry.fromParts(List.of(model));
        assertEquals(1, flat.scaleZ());
        assertEquals(flat.originalBounds(), flat.flattenedBounds());
    }

    private static GarmentPartModel part(String name) throws IOException {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.fromNamespaceAndPath("createapparel", name),
                JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/createapparel/garment_models/parts/" + name + ".json"))).getAsJsonObject());
    }
}
