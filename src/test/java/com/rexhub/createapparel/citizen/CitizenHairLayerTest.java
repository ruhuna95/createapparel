package com.rexhub.createapparel.citizen;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.client.citizen.CitizenHairLayers;
import org.junit.jupiter.api.Test;
import com.rexhub.createapparel.client.citizen.CitizenHairGravity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CitizenHairLayerTest {
    private static CitizenHairLayers read(String style) throws Exception {
        return CitizenHairLayers.read(JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/assets/createapparel/citizen/hair/extensions.json"))).getAsJsonObject());
    }

    @Test
    void outlineDarkensHairWithoutInheritingSkinHue() {
        assertEquals(0x0B1F34, CitizenHeadPixels.outlineColor(0x123456));
        assertEquals(0, CitizenHeadPixels.outlineColor(0));
        for (SkinTone skin : SkinTone.values()) for (int hair : new int[]{0, 0xFFFFFF, 0x808080}) {
            int outline = CitizenHeadPixels.tintFace(0xFF0C0C0C, 9, 12, skin.tintRgb(), hair, 0) & 0xFFFFFF;
            assertEquals(outline >> 16 & 255, outline >> 8 & 255);
            assertEquals(outline >> 8 & 255, outline & 255, "Neutral hair must not become skin-tinted brown");
            assertEquals(CitizenHeadPixels.outlineColor(hair), outline);
        }
        assertEquals(0x999999, CitizenHeadPixels.outlineColor(0xFFFFFF));
        assertEquals(0x4D4D4D, CitizenHeadPixels.outlineColor(0x808080));
    }

    @Test
    void fringesHaveDistinctLayeredSilhouettesAndLeaveEveryEyeMaskPixelClear() {
        var silhouettes = new HashSet<List<Integer>>();
        for (String style : List.of("short", "bob", "long_straight", "ponytail", "twintails")) {
            var tips = new ArrayList<Integer>();
            int differences = 0;
            for (int x = 8; x < 16; x++) {
                int tip = 0;
                for (int y = 8; y < 16; y++) {
                    int head = CitizenHairPixels.pixel(style, x, y), hat = CitizenHairPixels.pixel(style, x, y + 16);
                    if (head != hat) differences++;
                    if (hat != 0) tip = y - 7;
                    if (CitizenHeadPixels.maskPixel(x, y) != CitizenHeadPixels.SKIN_MASK) {
                        assertEquals(0, head);
                        assertEquals(0, hat);
                    }
                }
                tips.add(tip);
            }
            assertTrue(differences >= 8, "HAT must not duplicate HEAD");
            assertTrue(new HashSet<>(tips).size() >= 3, "Staggered bangs, not a straight edge");
            silhouettes.add(tips);
        }
        assertEquals(5, silhouettes.size());
    }

    @Test
    void sharedSheetsExtendAllFourHeadAndHatEdgesWithOneToOnePixelScale() throws Exception {
        var layers = read("extensions").layers();
        assertEquals(8, layers.size());
        for (int i = 0; i < layers.size(); i++) {
            var layer = layers.get(i);
            assertEquals(i < 4 ? 0 : 0.25F, layer.y0());
            assertEquals(16, layer.y1() - layer.y0());
            assertEquals(32 + (i % 4) * 8, layer.u0());
            assertEquals(layer.u0() + 8, layer.u1());
            assertEquals(i < 4 ? 0 : 16, layer.v0());
            assertEquals(layer.v0() + 16, layer.v1());
            assertEquals(i % 4 == 0 || i % 4 == 3, layer.mirrorU());
            assertTrue(layer.x0() == layer.x1() ^ layer.z0() == layer.z1());
        }
    }

    @Test
    void onlyEntirelyTransparentSheetsAreRemovedAndPartialSheetsRemainSingleQuads() throws Exception {
        var sheets = read("extensions");
        assertEquals(8, sheets.visibleLayerCount());
        assertEquals(0, sheets.withOpacity((u, v) -> false).visibleLayerCount());
        assertEquals(1, sheets.withOpacity((u, v) -> u == 56 && v == 16).visibleLayerCount());
        assertEquals(4, sheets.withOpacity((u, v) -> v >= 16).visibleLayerCount());
    }

    @Test
    void sheetsRenderExactlyOneQuadEachWithValidUvsAndNonzeroNormals() throws Exception {
        var layer = read("ponytail");
        var points = new ArrayList<float[]>();
        var normals = new ArrayList<float[]>();
        var uv = new ArrayList<float[]>();
        var vertices = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{VertexConsumer.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("addVertex") && arguments.length == 4) {
                        points.add(new float[]{(float) arguments[1], (float) arguments[2], (float) arguments[3]});
                    } else if (method.getName().equals("setNormal") && arguments.length == 4) {
                        normals.add(new float[]{(float) arguments[1], (float) arguments[2], (float) arguments[3]});
                    } else if (method.getName().equals("setUv")) {
                        uv.add(new float[]{(float) arguments[0], (float) arguments[1]});
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
        layer.render(new PoseStack(), vertices, 0, 0);
        assertEquals(32, points.size());
        assertEquals(layer.visibleLayerCount() * 4, points.size());
        assertEquals(points.size(), normals.size());
        assertEquals(points.size(), uv.size());
        for (float[] normal : normals) assertEquals(1, Math.abs(normal[0]) + Math.abs(normal[2]));
        for (float[] value : uv) assertTrue(value[0] >= 0.5F && value[0] <= 1 && value[1] >= 0 && value[1] <= 1);
    }

    @Test
    void invalidOrVolumetricExtensionDefinitionsAreRejected() {
        for (String invalid : List.of(
                "{\"layers\":[]}",
                "{\"cubes\":[]}",
                "{\"layers\":[{\"from\":[0,0,0],\"to\":[1,1,1],\"uv\":[32,0,40,8]}]}",
                "{\"layers\":[{\"from\":[0,0,0],\"to\":[0,1,0],\"uv\":[32,0,40,8]}]}",
                "{\"layers\":[{\"from\":[0,0,0],\"to\":[0,1,1],\"uv\":[31,0,40,8]}]}",
                "{\"layers\":[{\"from\":[0,0,0],\"to\":[0,33,1],\"uv\":[32,0,40,8]}]}")) {
            assertThrows(RuntimeException.class, () -> CitizenHairLayers.read(JsonParser.parseString(invalid).getAsJsonObject()));
        }
    }

    @Test
    void rootsFollowHeadRotationButEveryStrandDropsVertically() throws Exception {
        for (var layer : read("extensions").layers()) for (float yaw : new float[]{-0.21F, 0, 0.21F}) {
            var rotation = new Quaternionf().rotationZYX(0.04F, yaw, 0.09F);
            var origin = new Vector3f(0, -0.1F, 0);
            for (float across : new float[]{0, 0.3F, 1}) {
                var expected = new Vector3f(layer.x0() + (layer.x1() - layer.x0()) * across,
                        layer.y0(), layer.z0() + (layer.z1() - layer.z0()) * across).rotate(rotation).add(origin);
                var root = CitizenHairGravity.point(layer, across, 0, rotation, origin, CitizenHairGravity.Torso.NONE);
                var tip = CitizenHairGravity.point(layer, across, 16, rotation, origin, CitizenHairGravity.Torso.NONE);
                assertEquals(expected.x, root.x, 0.00001);
                assertEquals(expected.y, root.y, 0.00001);
                assertEquals(expected.z, root.z, 0.00001);
                assertEquals(root.x, tip.x);
                assertEquals(root.z, tip.z);
                assertEquals(root.y + 16, tip.y, 0.00001);
            }
        }
    }

    @Test
    void frontBackHeadAndHatStripsAvoidFlatAndBustTorsoAcrossIdleHeadAngles() throws Exception {
        for (float front : new float[]{-2.2F, -4.8F}) {
            var torso = new CitizenHairGravity.Torso(-4.2F, -0.01F, front, 4.2F, 12.2F, 2.8F);
            for (var layer : read("extensions").layers()) for (float yaw : new float[]{-0.21F, 0, 0.21F}) {
                if (layer.x0() == layer.x1()) continue;
                var rotation = new Quaternionf().rotationZYX(0.03F, yaw, 0.09F);
                for (int u = 0; u <= 32; u++) for (int drop = 0; drop <= 16; drop++) {
                    var point = CitizenHairGravity.point(layer, u / 32F, drop, rotation, new Vector3f(), torso);
                    boolean inside = point.x > torso.minX() && point.x < torso.maxX()
                            && point.y > torso.minY() && point.y < torso.maxY()
                            && point.z > torso.minZ() && point.z < torso.maxZ();
                    assertFalse(inside, layer + " / " + point);
                }
            }
        }
    }

    @Test
    void headAndHatSidesStayOnRotatedEdgesAndDropVerticallyEvenInsideTorso() throws Exception {
        var layers = read("extensions").layers();
        var origin = new Vector3f(0, -0.1F, 0);
        for (float front : new float[]{-2.2F, -4.8F}) {
            var torso = new CitizenHairGravity.Torso(-5, -0.01F, front, 5, 12.2F, 2.8F);
            int overlappingPoints = 0;
            for (var layer : List.of(layers.get(0), layers.get(2), layers.get(4), layers.get(6))) {
                for (float yaw : new float[]{-1.57F, -0.21F, 0, 0.21F, 1.57F}) {
                    for (float pitch : new float[]{-0.35F, 0, 0.35F}) {
                        var rotation = new Quaternionf().rotationZYX(0.03F, yaw, pitch);
                        for (int u = 0; u <= 16; u++) for (int drop = 0; drop <= 16; drop++) {
                            var expected = new Vector3f(layer.x0(), layer.y0(),
                                    layer.z0() + (layer.z1() - layer.z0()) * u / 16F)
                                    .rotate(rotation).add(origin).add(0, drop, 0);
                            var actual = CitizenHairGravity.point(layer, u / 16F, drop, rotation, origin, torso);
                            assertEquals(expected.x, actual.x, 0.00001);
                            assertEquals(expected.y, actual.y, 0.00001);
                            assertEquals(expected.z, actual.z, 0.00001);
                            if (actual.x > torso.minX() && actual.x < torso.maxX()
                                    && actual.y > torso.minY() && actual.y < torso.maxY()
                                    && actual.z > torso.minZ() && actual.z < torso.maxZ()) overlappingPoints++;
                        }
                    }
                }
            }
            assertTrue(overlappingPoints > 0, "HEAD and HAT sides allow torso overlap instead of detaching from the edge");
        }
    }

    @Test
    void renderedHeadAndHatSideSheetsKeepEdgeAttachmentUvsAndFiniteNormals() throws Exception {
        var sheets = read("extensions").withOpacity((u, v) -> (u == 32 || u == 48) && (v == 0 || v == 16));
        var points = new ArrayList<Vector3f>();
        var normals = new ArrayList<Vector3f>();
        var uvs = new ArrayList<float[]>();
        var vertices = (VertexConsumer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{VertexConsumer.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("addVertex") && arguments.length == 4) {
                        points.add(new Vector3f((float) arguments[1], (float) arguments[2], (float) arguments[3]).mul(16));
                    } else if (method.getName().equals("setNormal") && arguments.length == 4) {
                        normals.add(new Vector3f((float) arguments[1], (float) arguments[2], (float) arguments[3]));
                    } else if (method.getName().equals("setUv")) {
                        uvs.add(new float[]{(float) arguments[0], (float) arguments[1]});
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
        var rotation = new Quaternionf().rotationZYX(0.03F, 0.21F, 0.09F);
        var origin = new Vector3f(0, -0.1F, 0);
        var torso = new CitizenHairGravity.Torso(-8, -1, -8, 8, 20, 8);
        sheets.render(new PoseStack(), vertices, 0, 0, rotation, origin, torso, true);
        assertEquals(16, points.size());
        assertEquals(16, normals.size());
        assertEquals(16, uvs.size());
        for (int layerIndex = 0; layerIndex < 4; layerIndex++) for (int vertex = 0; vertex < 4; vertex++) {
            int side = layerIndex % 2;
            boolean hat = layerIndex >= 2;
            float halfWidth = hat ? 4.25F : 4;
            float across = vertex == 0 || vertex == 3 ? 0 : 1;
            if (side == 0) across = 1 - across;
            int drop = vertex >= 2 ? 16 : 0;
            var expected = new Vector3f(side == 0 ? -halfWidth : halfWidth, hat ? 0.25F : 0, -halfWidth + 2 * halfWidth * across)
                    .rotate(rotation).add(origin).add(0, drop, 0);
            int index = layerIndex * 4 + vertex;
            assertEquals(expected.x, points.get(index).x, 0.00001);
            assertEquals(expected.y, points.get(index).y, 0.00001);
            assertEquals(expected.z, points.get(index).z, 0.00001);
            assertEquals(1, normals.get(index).length(), 0.00001);
            assertEquals(0, normals.get(index).y, 0.00001);
            assertEquals(((side == 0 ? 32 : 48) + (vertex == 1 || vertex == 2 ? 8 : 0)) / 64F, uvs.get(index)[0]);
            assertEquals(((hat ? 16 : 0) + drop) / 32F, uvs.get(index)[1]);
        }
        var headPoints = List.copyOf(points.subList(0, 8));
        points.clear();
        sheets.render(new PoseStack(), vertices, 0, 0, rotation, origin, torso, false);
        assertEquals(headPoints, points, "Hidden HAT suppresses only its own side extensions");
    }

    @Test
    void frontBackSheetsStayOutsideTorsoEvenWhenOnlyTheirInteriorCrossesItsHeight() throws Exception {
        var torso = new CitizenHairGravity.Torso(-8, -1, -8, 8, 1, 8);
        for (var layer : read("extensions").layers()) {
            if (layer.x0() == layer.x1()) continue;
            for (float yaw : new float[]{-1.57F, -0.21F, 0, 0.21F, 1.57F}) {
                var rotation = new Quaternionf().rotationZYX(0.7F, yaw, 0.9F);
                var roots = CitizenHairGravity.roots(layer, rotation, new Vector3f(), torso);
                for (int u = 0; u <= 32; u++) for (int drop = 0; drop <= 16; drop++) {
                    var actual = CitizenHairGravity.point(layer, u / 32F, drop, rotation, new Vector3f(), torso);
                    var rendered = new Vector3f(roots.start()).lerp(roots.end(), u / 32F).add(0, drop, 0);
                    assertEquals(rendered.x, actual.x, 0.00001);
                    assertEquals(rendered.y, actual.y, 0.00001);
                    assertEquals(rendered.z, actual.z, 0.00001);
                    assertFalse(actual.x > torso.minX() && actual.x < torso.maxX()
                            && actual.y > torso.minY() && actual.y < torso.maxY()
                            && actual.z > torso.minZ() && actual.z < torso.maxZ());
                }
            }
        }
    }
}
