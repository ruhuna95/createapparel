package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.*;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ApparelRenderOptimizationTest {
    private static GarmentPartModel model(String name) throws Exception {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:" + name),
                JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/assets/createapparel/garment_models/parts/" + name + ".json"))).getAsJsonObject());
    }

    private static ResolvedGarment garment(GarmentPartModel model, GarmentSlot slot) {
        var definition = new GarmentDefinition(model.id(), slot, model.id(), "default", "default", Set.of("plain"), Map.of());
        var textures = new ResolvedTextures(model.id(), Optional.empty(), Optional.empty(), Optional.empty(),
                1, Optional.empty());
        // Frame geometry does not access item data or Minecraft's color registries.
        return new ResolvedGarment(definition, null, List.of(new ResolvedPart("torso", model, textures)));
    }

    @Test
    void resolutionAndSkirtPoseAreSharedWithinOneRenderAndIsolatedBetweenRenders() throws Exception {
        var skirt = model("a_line_skirt/torso");
        var top = model("t_shirt/torso");
        var resolved = Map.of(GarmentSlot.BOTTOM, garment(skirt, GarmentSlot.BOTTOM),
                GarmentSlot.TOP, garment(top, GarmentSlot.TOP), GarmentSlot.OUTER, garment(top, GarmentSlot.OUTER));
        for (var leg : List.of(new Vec3(2, 12, -8), new Vec3(-2, 12, 8))) {
            Map<GarmentSlot, Integer> resolutions = new EnumMap<>(GarmentSlot.class);
            AtomicInteger animations = new AtomicInteger();
            var expected = SkirtPoseGeometry.deform(skirt, List.of(leg));
            var frame = new ApparelRenderFrame(slot -> {
                resolutions.merge(slot, 1, Integer::sum);
                return Optional.ofNullable(resolved.get(slot));
            }, part -> {
                animations.incrementAndGet();
                assertSame(skirt, part);
                return expected;
            });
            for (int render = 0; render < 3; render++) {
                assertSame(expected, frame.posed(skirt));
                assertSame(top, frame.posed(top));
                assertSame(resolved.get(GarmentSlot.BOTTOM), frame.garment(GarmentSlot.BOTTOM).orElseThrow());
                assertEquals(GarmentHemGeometry.animatedSlope(skirt.elements().get(1), expected.elements().get(1)),
                        frame.skirtSlope().orElseThrow());
            }
            assertEquals(1, animations.get());
            assertEquals(GarmentSlot.values().length, resolutions.size());
            resolutions.values().forEach(count -> assertEquals(1, count));
        }
        var noSkirt = new ApparelRenderFrame(slot -> Optional.ofNullable(slot == GarmentSlot.TOP
                ? resolved.get(slot) : null), part -> { fail("Non-skirt animation"); return part; });
        assertTrue(noSkirt.skirtSlope().isEmpty());
        assertSame(top, noSkirt.posed(top));
    }

    @Test
    void preparedVerticesUvsAndNormalsMatchMaterializedReferenceAcrossPosesAndReloads() throws Exception {
        var skirt = model("a_line_skirt/torso");
        Vector3f target = new Vector3f();
        for (String name : List.of("t_shirt/torso", "shirt/torso", "shirt_outer/torso",
                "hoodie/torso_pullover", "hoodie_outer/torso_zipup_opened")) {
            var part = model(name);
            var prepared = part.hemGeometry();
            assertNotSame(prepared, model(name).hemGeometry());
            for (var legs : List.of(List.<Vec3>of(), List.of(new Vec3(2, 12, -9)),
                    List.of(new Vec3(-4, 12, 8), new Vec3(4, 12, -8)))) {
                var posed = SkirtPoseGeometry.deform(skirt, legs);
                var slope = GarmentHemGeometry.animatedSlope(skirt.elements().get(1), posed.elements().get(1));
                for (var slot : List.of(GarmentSlot.TOP, GarmentSlot.OUTER)) {
                    var surface = slope.forSlot(slot);
                    var reference = GarmentHemGeometry.conform(part, "torso", slot, slope);
                    assertSame(prepared, part.hemGeometry());
                    List<List<Vec3>> positions = new ArrayList<>();
                    List<List<TexturePoint>> uvs = new ArrayList<>();
                    for (var element : reference.elements()) {
                        element.faces().forEach((face, uv) -> {
                            positions.add(GarmentElementGeometry.faceVertices(element, face).stream()
                                    .map(point -> FlatGarmentGeometry.rotate(point, element.rotation())).toList());
                            uvs.add(uv.corners());
                        });
                    }
                    assertEquals(positions.size(), prepared.quads().size());
                    for (int q = 0; q < positions.size(); q++) {
                        var quad = prepared.quads().get(q);
                        assertEquals(uvs.get(q), quad.uv());
                        List<Vec3> actual = new ArrayList<>();
                        for (int i = 0; i < 4; i++) {
                            surface.shift(quad.points().get(i), quad.depths().get(i), target);
                            Vec3 expected = positions.get(q).get(i);
                            assertEquals(expected.x(), target.x, 0.00001, name);
                            assertEquals(expected.y(), target.y, 0.00001, name);
                            assertEquals(expected.z(), target.z, 0.00001, name);
                            actual.add(new Vec3(target.x, target.y, target.z));
                        }
                        var normal = GarmentElementGeometry.normal(actual);
                        var expected = GarmentElementGeometry.normal(positions.get(q));
                        assertEquals(expected.x(), normal.x(), 0.00001);
                        assertEquals(expected.y(), normal.y(), 0.00001);
                        assertEquals(expected.z(), normal.z(), 0.00001);
                    }
                }
            }
        }
    }
}
