package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarmentElementGeometryTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "geometry");

    @Test
    void boxesKeepEveryFaceDirectionAndUvOrientation() {
        var element = ClientGarmentAssetLoader.parsePart(ID, model()).elements().get(0);
        var front = GarmentElementGeometry.faceVertices(element, Face.FRONT);
        assertEquals(List.of(new Vec3(0, 0, 0), new Vec3(0, 8, 0), new Vec3(4, 8, 0), new Vec3(4, 0, 0)), front);
        assertEquals(List.of(new Vec3(4, 0, 4), new Vec3(4, 8, 4), new Vec3(0, 8, 4), new Vec3(0, 0, 4)),
                GarmentElementGeometry.faceVertices(element, Face.BACK));
        assertEquals(List.of(new Vec3(0, 0, 4), new Vec3(0, 8, 4), new Vec3(0, 8, 0), new Vec3(0, 0, 0)),
                GarmentElementGeometry.faceVertices(element, Face.LEFT));
        assertEquals(List.of(new Vec3(4, 0, 0), new Vec3(4, 8, 0), new Vec3(4, 8, 4), new Vec3(4, 0, 4)),
                GarmentElementGeometry.faceVertices(element, Face.RIGHT));
        assertEquals(List.of(new Vec3(0, 8, 0), new Vec3(0, 8, 4), new Vec3(4, 8, 4), new Vec3(4, 8, 0)),
                GarmentElementGeometry.faceVertices(element, Face.TOP));
        assertEquals(List.of(new Vec3(0, 0, 4), new Vec3(0, 0, 0), new Vec3(4, 0, 0), new Vec3(4, 0, 4)),
                GarmentElementGeometry.faceVertices(element, Face.BOTTOM));
        Vec3[] normals = {new Vec3(0, 0, -1), new Vec3(0, 0, 1), new Vec3(-1, 0, 0),
                new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, -1, 0)};
        for (Face face : Face.values()) {
            assertVector(normals[face.ordinal()], GarmentElementGeometry.normal(GarmentElementGeometry.faceVertices(element, face)));
        }
        var uv = element.faces().get(Face.FRONT).corners();
        assertEquals(0, uv.get(0).u());
        assertEquals(8, uv.get(0).v());
        assertEquals(0, uv.get(1).v());
        assertEquals(4, uv.get(2).u());
        assertEquals(8, uv.get(3).v());
    }

    @Test
    void taperedFacesUseAuthoredCornersAndSlopedUnitNormals() {
        JsonObject json = model();
        element(json).add("top_inset", JsonParser.parseString("[0.5,0.5,0.5,0.5]"));
        element(json).getAsJsonObject("faces").getAsJsonObject("front")
                .add("uv_corners", JsonParser.parseString("[[0,8],[0.5,0],[3.5,0],[4,8]]"));
        var element = ClientGarmentAssetLoader.parsePart(ID, json).elements().get(0);
        assertEquals(new Vec3(0, 0, 0), element.corners().get(0));
        assertEquals(new Vec3(0.5F, 8, 0.5F), element.corners().get(4));
        assertEquals(0.5F, element.faces().get(Face.FRONT).corners().get(1).u());
        Vec3 normal = GarmentElementGeometry.normal(GarmentElementGeometry.faceVertices(element, Face.FRONT));
        assertTrue(normal.y() > 0);
        assertTrue(normal.z() < 0);
        assertEquals(1, normal.x() * normal.x() + normal.y() * normal.y() + normal.z() * normal.z(), 0.00001);
        assertEquals(0, normal.y() * 8 + normal.z() * 0.5, 0.00001);
    }

    @Test
    void bottomInsetsLeaveTheTopIntact() {
        JsonObject json = model();
        element(json).add("bottom_inset", JsonParser.parseString("[0.5,1,1.5,0.25]"));
        var element = ClientGarmentAssetLoader.parsePart(ID, json).elements().get(0);
        assertEquals(new Vec3(0.5F, 0, 1), element.corners().get(0));
        assertEquals(new Vec3(2.5F, 0, 3.75F), element.corners().get(2));
        assertEquals(new Vec3(0, 8, 0), element.corners().get(4));
    }

    @Test
    void raisedBottomCutsTaperedEdgesWithoutMovingTheTopOrWarpingFaces() {
        for (String offsets : List.of("[0.5,0]", "[0,0.5]", "[1,2]")) {
            JsonObject json = model();
            element(json).add("bottom_inset", JsonParser.parseString("[0.25,0.5,0.75,1]"));
            element(json).add("bottom_raise", JsonParser.parseString(offsets));
            var raised = ClientGarmentAssetLoader.parsePart(ID, json).elements().get(0);
            float frontRaise = JsonParser.parseString(offsets).getAsJsonArray().get(0).getAsFloat();
            assertVector(new Vec3(0.25F * (1 - frontRaise / 8), frontRaise,
                    0.5F * (1 - frontRaise / 8)), raised.corners().get(0));
            assertEquals(new Vec3(0, 8, 0), raised.corners().get(4));
            for (Face face : Face.values()) {
                var points = GarmentElementGeometry.faceVertices(raised, face);
                Vec3 normal = GarmentElementGeometry.normal(points);
                Vec3 a = points.get(0);
                Vec3 d = points.get(3);
                assertEquals(0, normal.x() * (d.x() - a.x()) + normal.y() * (d.y() - a.y())
                        + normal.z() * (d.z() - a.z()), 0.00001);
            }
        }
    }

    @Test
    void invalidBottomRaiseFailsAtLoadTime() {
        for (String offsets : List.of("[]", "[0]", "[0,0,0]", "[-1,0]", "[0,-1]", "[8,0]",
                "[0,9]", "[1e100,0]", "[0,1e100]")) {
            JsonObject json = model();
            element(json).add("bottom_raise", JsonParser.parseString(offsets));
            assertThrows(IllegalStateException.class, () -> ClientGarmentAssetLoader.parsePart(ID, json));
        }
    }

    @Test
    void invalidInsetsAndUvCornersFailAtLoadTime() {
        for (String inset : List.of("[-1,0,0,0]", "[2,0,2,0]", "[0,3,0,2]", "[0,0,0]", "[1e100,0,0,0]")) {
            JsonObject json = model();
            element(json).add("top_inset", JsonParser.parseString(inset));
            assertThrows(IllegalStateException.class, () -> ClientGarmentAssetLoader.parsePart(ID, json));
        }
        for (String corners : List.of("[]", "[[0],[0,0],[4,0],[4,8]]", "[[-1,8],[0,0],[4,0],[4,8]]")) {
            JsonObject json = model();
            element(json).getAsJsonObject("faces").getAsJsonObject("front").add("uv_corners", JsonParser.parseString(corners));
            assertThrows(IllegalStateException.class, () -> ClientGarmentAssetLoader.parsePart(ID, json));
        }
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 0.00001);
        assertEquals(expected.y(), actual.y(), 0.00001);
        assertEquals(expected.z(), actual.z(), 0.00001);
    }

    private static JsonObject element(JsonObject model) {
        return model.getAsJsonObject("geometry").getAsJsonArray("elements").get(0).getAsJsonObject();
    }

    private static JsonObject model() {
        return JsonParser.parseString("""
                {"render_type":"cutout", "texture_size":[16,16],
                 "geometry":{"pivot":[0,0,0], "elements":[
                   {"name":"test", "from":[0,0,0], "to":[4,8,4], "faces":{"front":{"uv":[0,0,4,8]}}}
                 ]}, "textures":{"variants":{"default":{"base":"test:base"}}}}
                """).getAsJsonObject();
    }
}
