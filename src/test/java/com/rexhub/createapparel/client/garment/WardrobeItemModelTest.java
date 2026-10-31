package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.ExtendedBlockModelDeserializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WardrobeItemModelTest {
    @Test
    void itemMatchesBothClosedBlockHalvesIncludingFaceUvs() throws IOException {
        var item = json("src/generated/resources/assets/createapparel/models/item/wardrobe.json");
        assertFalse(item.has("loader"));
        int index = 0;
        for (String half : List.of("lower", "upper")) {
            var block = json("src/main/resources/assets/createapparel/models/block/wardrobe/" + half + "_closed.json");
            int yOffset = half.equals("upper") ? 16 : 0;
            for (var value : block.getAsJsonArray("elements")) {
                var expected = value.getAsJsonObject();
                var actual = item.getAsJsonArray("elements").get(index++).getAsJsonObject();
                for (String endpoint : List.of("from", "to")) {
                    for (int axis = 0; axis < 3; axis++) {
                        assertEquals(expected.getAsJsonArray(endpoint).get(axis).getAsDouble() + (axis == 1 ? yOffset : 0),
                                actual.getAsJsonArray(endpoint).get(axis).getAsDouble(), 0.000001);
                    }
                }
                assertEquals(expected.getAsJsonObject("faces").keySet(), actual.getAsJsonObject("faces").keySet());
                var expectedElement = ExtendedBlockModelDeserializer.INSTANCE.fromJson(expected, BlockElement.class);
                var actualElement = ExtendedBlockModelDeserializer.INSTANCE.fromJson(actual, BlockElement.class);
                for (var face : expected.getAsJsonObject("faces").entrySet()) {
                    var direction = Direction.byName(face.getKey());
                    var expectedFace = expectedElement.faces.get(direction);
                    var actualFace = actualElement.faces.get(direction);
                    assertArrayEquals(expectedFace.uv().uvs, actualFace.uv().uvs, 0.000001f);
                    assertEquals(expectedFace.texture(), actualFace.texture());
                    assertEquals(expectedFace.uv().rotation, actualFace.uv().rotation);
                    String texture = face.getValue().getAsJsonObject().get("texture").getAsString().substring(1);
                    assertEquals(block.getAsJsonObject("textures").get(texture), item.getAsJsonObject("textures").get(texture));
                }
            }
        }
        assertEquals(index, item.getAsJsonArray("elements").size());
        assertFalse(Files.exists(Path.of("src/main/resources/assets/createapparel/models/item/wardrobe.json")));
    }

    private static JsonObject json(String path) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
    }
}
