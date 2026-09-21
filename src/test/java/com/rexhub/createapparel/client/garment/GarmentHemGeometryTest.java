package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GarmentHemGeometryTest {
    private static GarmentPartModel model(String name) throws Exception {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:" + name),
                JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/assets/createapparel/garment_models/parts/" + name + ".json"))).getAsJsonObject());
    }

    private static GarmentHemGeometry.Slope slope() throws Exception {
        return GarmentHemGeometry.slope(model("a_line_skirt/torso").elements().get(1));
    }

    @Test
    void skirtGeometryDeterminesSlopeWithoutTopSpecificConfiguration() throws Exception {
        var slope = slope();
        assertEquals(1 / 8.004F, slope.east(), 0.00001);
        assertEquals(-1 / 8.004F, slope.north(), 0.00001);
        var top = model("t_shirt/torso");
        var result = GarmentHemGeometry.conform(top, "torso", GarmentSlot.TOP, slope);
        float hem = top.elements().stream().map(e -> e.from().y()).min(Float::compare).orElseThrow();
        float cut = Math.max(hem + 2, slope.min().y());
        assertNotEquals(top, result);
        for (var element : result.elements()) {
            for (Vec3 point : element.corners()) {
                assertTrue(point.y() >= hem);
                if (point.y() < cut) {
                    assertTrue(element.corners().stream().allMatch(p -> p.y() <= cut));
                }
            }
        }
        var original = top.elements().get(0);
        Vec3 bottom = original.corners().get(0);
        Vec3 expected = slope.forSlot(GarmentSlot.TOP).shift(bottom, cut - bottom.y());
        assertTrue(result.elements().stream().flatMap(e -> e.corners().stream()).anyMatch(expected::equals));
        assertEquals(top.variants(), result.variants());
        assertEquals(top.designs(), result.designs());
    }

    @Test
    void upperVerticesAndTextureCoordinatesAreUnchangedAndCutIsContinuous() throws Exception {
        var top = model("t_shirt/torso");
        var original = top.elements().get(0);
        float cut = Math.max(original.from().y() + 2, slope().min().y());
        var result = GarmentHemGeometry.conform(top, "torso", GarmentSlot.TOP, slope());
        var frontUv = original.faces().get(Face.FRONT);
        float cutV = frontUv.v0() + (frontUv.v1() - frontUv.v0())
                * (original.to().y() - cut) / (original.to().y() - original.from().y());
        int cutVertices = 0;
        for (var element : result.elements()) {
            for (var face : element.faces().entrySet()) {
                var positions = GarmentElementGeometry.faceVertices(element, face.getKey());
                var uv = face.getValue().corners();
                for (int i = 0; i < 4; i++) {
                    Vec3 point = positions.get(i);
                    if (point.y() > cut) {
                        assertTrue(original.corners().contains(point));
                    }
                    if (point.y() == cut && point.z() == original.from().z()) {
                        assertEquals(cutV, uv.get(i).v(), 0.00001);
                        cutVertices++;
                    }
                }
                var normal = GarmentElementGeometry.normal(positions);
                assertTrue(Float.isFinite(normal.x()) && Float.isFinite(normal.y()) && Float.isFinite(normal.z()));
            }
        }
        assertTrue(cutVertices >= 4);
    }

    @Test
    void sleevesAndOtherSlotsAreExcluded() throws Exception {
        var part = model("t_shirt/torso");
        for (var slot : List.of(GarmentSlot.BOTTOM, GarmentSlot.INNER_TOP, GarmentSlot.HEAD)) {
            assertSame(part, GarmentHemGeometry.conform(part, "torso", slot, slope()));
        }
        assertSame(part, GarmentHemGeometry.conform(part, "left_sleeve", GarmentSlot.TOP, slope()));
        assertSame(part, GarmentHemGeometry.conform(part, "bust", GarmentSlot.OUTER, slope()));
    }

    @Test
    void existingDecoratedTopsAndOutersKeepFiniteConnectedSurfaces() throws Exception {
        for (String name : List.of("shirt/torso", "shirt_outer/torso",
                "hoodie/torso_pullover", "hoodie_outer/torso_zipup_opened")) {
            var part = model(name);
            var result = GarmentHemGeometry.conform(part, "torso", GarmentSlot.OUTER, slope());
            assertNotEquals(part, result);
            assertEquals(part.variants(), result.variants());
            for (var element : result.elements()) {
                for (var face : element.faces().entrySet()) {
                    var positions = GarmentElementGeometry.faceVertices(element, face.getKey());
                    var normal = GarmentElementGeometry.normal(positions);
                    assertTrue(Float.isFinite(normal.x()) && Float.isFinite(normal.y()) && Float.isFinite(normal.z()), name);
                    for (var uv : face.getValue().corners()) {
                        assertTrue(uv.u() >= 0 && uv.u() <= part.textureWidth());
                        assertTrue(uv.v() >= 0 && uv.v() <= part.textureHeight());
                    }
                }
            }
        }
    }

    @Test
    void tuckedTorsoNeverConformsEvenWhenItsHemOverlapsTheSkirt() throws Exception {
        var slope = slope();
        for (String name : List.of("t_shirt/tucked_torso", "shirt/tucked_torso",
                "shirt/tucked_torso_opened", "blouse/tucked_torso")) {
            var tucked = model(name);
            String semantic = name.substring(name.indexOf('/') + 1);
            for (var slot : List.of(GarmentSlot.TOP, GarmentSlot.OUTER)) {
                assertFalse(tucked.hemGeometry().applies(semantic, slot, slope), name);
                assertSame(tucked, GarmentHemGeometry.conform(tucked, semantic, slot, slope), name);
            }
        }
        var part = model("shirt/torso");
        for (var slot : List.of(GarmentSlot.TOP, GarmentSlot.OUTER)) {
            for (String semantic : List.of("tucked_torso", "tucked_torso_opened")) {
                assertFalse(part.hemGeometry().applies(semantic, slot, slope));
                assertSame(part, GarmentHemGeometry.conform(part, semantic, slot, slope));
            }
            for (String semantic : List.of("torso", "torso_opened")) {
                assertTrue(part.hemGeometry().applies(semantic, slot, slope));
                assertNotSame(part, GarmentHemGeometry.conform(part, semantic, slot, slope));
            }
        }
    }


    @Test
    void changingSkirtSlopeChangesBothTopAndOuterAutomatically() throws Exception {
        var part = model("t_shirt/torso");
        var slope = slope();
        var wider = new GarmentHemGeometry.Slope(slope.min(), slope.max(),
                slope.west() * 2, slope.east() * 2, slope.north() * 2, slope.south() * 2);
        var regular = GarmentHemGeometry.conform(part, "torso", GarmentSlot.TOP, slope);
        var wide = GarmentHemGeometry.conform(part, "torso", GarmentSlot.TOP, wider);
        assertNotEquals(regular, wide);
        assertNotEquals(wide, GarmentHemGeometry.conform(part, "torso", GarmentSlot.OUTER, wider));
    }
}
