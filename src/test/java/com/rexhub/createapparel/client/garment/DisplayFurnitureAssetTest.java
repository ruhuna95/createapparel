package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.rexhub.createapparel.block.HangerGeometry;
import com.rexhub.createapparel.garment.HangerType;

import static org.junit.jupiter.api.Assertions.*;

class DisplayFurnitureAssetTest {
    private static final Path MODELS = Path.of("src/generated/resources/assets/createapparel/models/block");

    @Test
    void furnitureUsesHalfPixelPlacementAndOnlySmallJointOffsetsWithoutTextureStretching() throws IOException {
        try (var paths = Files.list(MODELS)) {
            for (var path : paths.filter(path -> path.getFileName().toString().startsWith("clothing_rack")
                    || path.getFileName().toString().equals("wall_hanger.json")
                    || path.getFileName().toString().equals("common_hanger.json")
                    || path.getFileName().toString().equals("clip_wall_hanger.json")
                    || path.getFileName().toString().equals("common_clip_hanger.json")).toList()) {
                var model = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (!model.has("elements")) continue;
                for (var entry : model.getAsJsonArray("elements")) {
                    var element = entry.getAsJsonObject();
                    for (String key : new String[]{"from", "to"}) for (var component : element.getAsJsonArray(key))
                        assertHalfPixelOrJointOffset(component.getAsDouble());
                    if (element.has("rotation")) {
                        var rotation = element.getAsJsonObject("rotation");
                        assertFalse(rotation.has("rescale") && rotation.get("rescale").getAsBoolean(), "No rotation rescaling distorts pixel density");
                        for (var component : rotation.getAsJsonArray("origin"))
                            assertHalfPixelOrJointOffset(component.getAsDouble());
                    }
                    double[] size = new double[3];
                    for (int axis = 0; axis < 3; axis++) size[axis] = element.getAsJsonArray("to").get(axis).getAsDouble()
                            - element.getAsJsonArray("from").get(axis).getAsDouble();
                    for (var faceEntry : element.getAsJsonObject("faces").entrySet()) {
                        String face = faceEntry.getKey();
                        var uv = faceEntry.getValue().getAsJsonObject().getAsJsonArray("uv");
                        if (uv == null) continue;
                        for (var component : uv) {
                            assertHalfPixelOrJointOffset(component.getAsDouble());
                            assertTrue(component.getAsDouble() >= 0 && component.getAsDouble() <= 16);
                        }
                        double uSize = Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble());
                        double vSize = Math.abs(uv.get(3).getAsDouble() - uv.get(1).getAsDouble());
                        boolean vertical = !face.equals("up") && !face.equals("down");
                        assertEquals(face.equals("east") || face.equals("west") ? size[2] : size[0], uSize, 1.0E-5);
                        assertEquals(vertical ? size[1] : size[2], vSize, 1.0E-5);
                    }
                }
            }
        }
    }

    @Test
    void hangerModelUsesTheSharedCenteredRenderParts() throws IOException {
        for (var type : HangerType.values()) {
            String name = type == HangerType.CLIP ? "common_clip_hanger" : "common_hanger";
            var model = JsonParser.parseString(Files.readString(MODELS.resolve(name + ".json"))).getAsJsonObject();
            var elements = model.getAsJsonArray("elements");
            assertEquals(HangerGeometry.parts(type).size(), elements.size());
            for (int index = 0; index < elements.size(); index++) {
                var part = HangerGeometry.parts(type).get(index);
                var element = elements.get(index).getAsJsonObject();
                assertArrayEquals(new double[]{part.x1(), part.y1(), part.z1()}, coordinates(element, "from"), 1.0E-6);
                assertArrayEquals(new double[]{part.x2(), part.y2(), part.z2()}, coordinates(element, "to"), 1.0E-6);
                assertEquals(part.angle(), element.has("rotation") ? element.getAsJsonObject("rotation").get("angle").getAsFloat() : 0);
            }
        }
    }

    @Test
    void wallHangerUsesSameCompactHookBehindCenteredWallBracket() throws IOException {
        for (var type : HangerType.values()) {
            String commonName = type == HangerType.CLIP ? "common_clip_hanger" : "common_hanger";
            String wallName = type == HangerType.CLIP ? "clip_wall_hanger" : "wall_hanger";
            var common = JsonParser.parseString(Files.readString(MODELS.resolve(commonName + ".json"))).getAsJsonObject().getAsJsonArray("elements");
            var wall = JsonParser.parseString(Files.readString(MODELS.resolve(wallName + ".json"))).getAsJsonObject().getAsJsonArray("elements");
            var delta = HangerGeometry.WALL_ANCHOR.subtract(HangerGeometry.MODEL_ANCHOR).scale(16);
            double[] offset = {delta.x, delta.y, delta.z};
            for (int index = 0; index < common.size(); index++) {
                var original = common.get(index).getAsJsonObject();
                var shifted = wall.get(index).getAsJsonObject();
                for (String key : new String[]{"from", "to"}) {
                    var expected = coordinates(original, key);
                    for (int axis = 0; axis < 3; axis++) expected[axis] += offset[axis];
                    assertArrayEquals(expected, coordinates(shifted, key), 1.0E-6);
                }
                for (var face : original.getAsJsonObject("faces").keySet()) {
                    assertEquals(original.getAsJsonObject("faces").getAsJsonObject(face).get("texture"),
                            shifted.getAsJsonObject("faces").getAsJsonObject(face).get("texture"));
                    assertArrayEquals(faceUvs(original, face), faceUvs(shifted, face), 1.0E-6,
                            "Height changes must preserve texture sampling");
                }
                if (original.has("rotation")) {
                    var expected = coordinates(original.getAsJsonObject("rotation"), "origin");
                    for (int axis = 0; axis < 3; axis++) expected[axis] += offset[axis];
                    assertArrayEquals(expected, coordinates(shifted.getAsJsonObject("rotation"), "origin"), 1.0E-6);
                    assertEquals(original.getAsJsonObject("rotation").get("angle"), shifted.getAsJsonObject("rotation").get("angle"));
                }
            }
            var bracket = wall.get(common.size() + 1).getAsJsonObject();
            var hook = HangerGeometry.WALL_HOOK;
            var from = coordinates(bracket, "from");
            var to = coordinates(bracket, "to");
            assertArrayEquals(new double[]{hook.x1(), hook.y1(), hook.z1()}, from, 1.0E-6);
            assertArrayEquals(new double[]{hook.x2(), hook.y2(), hook.z2()}, to, 1.0E-6);
            assertEquals(8, (from[0] + to[0]) / 2);
            assertTrue(from[2] < HangerGeometry.WALL_ANCHOR.z * 16);
            assertTrue(to[2] > HangerGeometry.WALL_ANCHOR.z * 16);
        }
    }

    private static void assertHalfPixelOrJointOffset(double value) {
        assertTrue(Math.abs(value - Math.rint(value * 2) / 2) <= 2 * HangerGeometry.JOINT_CLEARANCE_PIXELS + 1.0E-5,
                "Half-pixel placement, with small Z-fighting clearance: " + value);
    }

    private static double[] coordinates(JsonObject object, String key) {
        var array = object.getAsJsonArray(key);
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static double[] faceUvs(JsonObject element, String side) {
        var face = element.getAsJsonObject("faces").getAsJsonObject(side);
        if (face.has("uv")) {
            var uv = face.getAsJsonArray("uv");
            return new double[]{uv.get(0).getAsDouble(), uv.get(1).getAsDouble(), uv.get(2).getAsDouble(), uv.get(3).getAsDouble()};
        }
        var from = coordinates(element, "from");
        var to = coordinates(element, "to");
        return switch (side) {
            case "down" -> new double[]{from[0], 16 - to[2], to[0], 16 - from[2]};
            case "up" -> new double[]{from[0], from[2], to[0], to[2]};
            case "north" -> new double[]{16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
            case "south" -> new double[]{from[0], 16 - to[1], to[0], 16 - from[1]};
            case "west" -> new double[]{from[2], 16 - to[1], to[2], 16 - from[1]};
            case "east" -> new double[]{16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
            default -> throw new IllegalArgumentException("Unknown face: " + side);
        };
    }

    @Test
    void rackItemUpperHalfUsesBlockLocalTextureCoordinates() throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(MODELS.resolve("clothing_rack_item.json"))).getAsJsonObject();
        boolean upper = false;
        for (var entry : model.getAsJsonArray("elements")) {
            var element = entry.getAsJsonObject();
            upper |= element.getAsJsonArray("from").get(1).getAsFloat() >= 16;
            for (var face : element.getAsJsonObject("faces").entrySet()) {
                var uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                if (element.getAsJsonArray("from").get(1).getAsFloat() >= 16
                        && !face.getKey().equals("up") && !face.getKey().equals("down"))
                    assertNotNull(uv, "Upper side UV avoids implicit 2-block coordinates");
                if (uv != null) for (var component : uv) assertTrue(component.getAsFloat() >= 0 && component.getAsFloat() <= 16);
            }
        }
        assertTrue(upper, "Item still renders both halves");
    }

    @Test
    void wallRackPoleProjectsOutwardAndLowerIsInvisible() throws IOException {
        var upper = JsonParser.parseString(Files.readString(MODELS.resolve("clothing_rack_wall_upper.json"))).getAsJsonObject();
        var pole = upper.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals(0, pole.getAsJsonArray("from").get(2).getAsInt());
        assertEquals(15, pole.getAsJsonArray("to").get(2).getAsInt());
        assertEquals(2, pole.getAsJsonArray("to").get(0).getAsInt() - pole.getAsJsonArray("from").get(0).getAsInt());
        var lower = JsonParser.parseString(Files.readString(MODELS.resolve("clothing_rack_wall_lower.json"))).getAsJsonObject();
        assertFalse(lower.has("elements"));
    }

    @Test
    void wallExtensionModelsNeverAddSidewaysConnectors() throws IOException {
        for (int anchored = 0; anchored < 2; anchored++) for (int connections = 0; connections < 4; connections++) {
            String name = anchored == 1 && connections == 0 ? "clothing_rack_wall_upper"
                    : "clothing_rack_wall_upper_" + anchored + "_" + connections;
            var model = JsonParser.parseString(Files.readString(MODELS.resolve(name + ".json"))).getAsJsonObject();
            assertEquals(1 + anchored, model.getAsJsonArray("elements").size());
            assertEquals("minecraft:block/iron_block", model.getAsJsonObject("textures").get("particle").getAsString());
        }
    }
}
