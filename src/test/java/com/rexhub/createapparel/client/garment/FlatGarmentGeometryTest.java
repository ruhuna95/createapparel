package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedTextures;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

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

    @Test
    void hangerHoodStylesDoNotChangeBodyFlatteningAcrossTemplatesAndProfiles() throws IOException {
        for (String template : List.of("hoodie", "coat")) {
            var variants = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/createapparel/garment_models/templates/" + template + ".json"))).getAsJsonObject();
            for (var variant : variants.entrySet()) {
                var entries = variant.getValue().getAsJsonObject();
                if (!entries.has("hood_down")) continue;
                var models = new LinkedHashMap<String, GarmentPartModel>();
                for (var entry : entries.entrySet()) {
                    var id = ResourceLocation.parse(entry.getValue().getAsJsonObject().get("model").getAsString());
                    models.put(entry.getKey(), part(id.getPath()));
                }
                for (var arm : ArmProfile.values()) for (var chest : ChestProfile.values())
                    for (String open : List.of("closed", "opened")) for (String hood : List.of("down", "up")) {
                        if (!models.containsKey("hood_" + hood) || open.equals("opened") && !models.containsKey("torso_opened")) continue;
                        var parts = GarmentPartSelection.select(Map.of("open", open, "hood", hood), arm, chest, models.keySet())
                                .stream().map(name -> resolved(name, models.get(name))).toList();
                        var bodyParts = parts.stream().filter(p -> !p.semanticName().startsWith("hood_"))
                                .map(ResolvedPart::model).toList();
                        var body = FlatGarmentGeometry.cached(bodyParts);
                        var full = FlatGarmentGeometry.cached(parts.stream().map(ResolvedPart::model).toList());
                        var hanger = FlatGarmentGeometry.cachedHanger(parts);
                        var torso = FlatGarmentGeometry.cached(parts.stream().filter(p -> p.semanticName().startsWith("torso"))
                                .map(ResolvedPart::model).toList()).originalBounds();
                        var attachment = FlatGarmentGeometry.hangerAttachmentPoint(parts);
                        assertEquals(torso.center().x(), attachment.x());
                        assertEquals(torso.max().y(), attachment.y());
                        assertEquals(torso.center().z(), attachment.z());
                        assertEquals(body.scaleZ(), hanger.scaleZ(), 1.0E-7, template + "/" + variant.getKey());
                        assertEquals(full.originalBounds(), hanger.originalBounds());
                        assertEquals(full.flattenedBounds().min().y(), hanger.flattenedBounds().min().y());
                        assertEquals(full.flattenedBounds().max().y(), hanger.flattenedBounds().max().y());
                        for (var part : parts) for (var element : part.model().elements()) for (var corner : element.corners()) {
                            var rotated = FlatGarmentGeometry.rotate(corner, element.rotation());
                            var flatPoint = hanger.transform(corner, element.rotation());
                            assertEquals(rotated.x(), flatPoint.x());
                            assertEquals(rotated.y(), flatPoint.y());
                            assertEquals((rotated.z() - full.originalBounds().center().z()) * body.scaleZ(),
                                    flatPoint.z() - hanger.flattenedBounds().center().z(), 1.0E-5);
                        }
                    }
            }
        }
    }

    @Test
    void hangerCacheSeparatesHoodDepthFromUnchangedItemDepthAndReusesGeometry() throws IOException {
        var torso = resolved("torso", part("hoodie/torso_pullover"));
        var hood = resolved("hood_up", part("hoodie/hood_up"));
        var parts = List.of(torso, hood);
        var before = parts.stream().flatMap(p -> p.model().elements().stream()).toList();
        var item = FlatGarmentGeometry.cached(parts.stream().map(ResolvedPart::model).toList());
        var hanger = FlatGarmentGeometry.cachedHanger(parts);
        assertSame(hanger, FlatGarmentGeometry.cachedHanger(List.copyOf(parts)));
        assertSame(item, FlatGarmentGeometry.cached(parts.stream().map(ResolvedPart::model).toList()));
        assertTrue(hanger.scaleZ() > item.scaleZ());
        assertEquals(2, item.flattenedBounds().max().z() - item.flattenedBounds().min().z(), 1.0E-5);
        assertTrue(hanger.flattenedBounds().max().z() - hanger.flattenedBounds().min().z() > 2);
        assertSame(item, FlatGarmentGeometry.cachedHanger(List.of(torso, resolved("decoration", hood.model()))));
        assertEquals(before, parts.stream().flatMap(p -> p.model().elements().stream()).toList());
        assertSame(FlatGarmentGeometry.cached(List.of(torso.model())), FlatGarmentGeometry.cachedHanger(List.of(torso)));
        FlatGarmentGeometry.clearCache();
        assertNotSame(hanger, FlatGarmentGeometry.cachedHanger(parts));
    }

    @Test
    void hangerWithoutBodyPartsFallsBackSafely() throws IOException {
        var hood = resolved("hood", part("hoodie/hood_down"));
        assertSame(FlatGarmentGeometry.cached(List.of(hood.model())), FlatGarmentGeometry.cachedHanger(List.of(hood)));
        var empty = FlatGarmentGeometry.cachedHanger(List.of());
        assertEquals(1, empty.scaleZ());
        assertEquals(empty.originalBounds(), empty.flattenedBounds());
    }

    @Test
    void torsoAttachmentHandlesSemanticVariantsAndPreservesHoodlessAndMissingTorsoPlacement() throws IOException {
        var model = part("hoodie/torso_pullover");
        var torsoBounds = FlatGarmentGeometry.cached(List.of(model)).originalBounds();
        var hoodUp = resolved("hood_up", part("hoodie/hood_up"));
        var hoodDown = resolved("hood_down", part("hoodie/hood_down"));
        var expected = new ClientGarmentAssets.Vec3(torsoBounds.center().x(), torsoBounds.max().y(), torsoBounds.center().z());
        for (String name : List.of("torso", "torso_opened", "tucked_torso", "tucked_torso_opened")) {
            var torso = resolved(name, model);
            assertEquals(expected, FlatGarmentGeometry.hangerAttachmentPoint(List.of(torso, hoodUp)));
            assertEquals(expected, FlatGarmentGeometry.hangerAttachmentPoint(List.of(torso, hoodDown)));
            var hoodless = List.of(torso, resolved("decoration", hoodUp.model()));
            var bounds = FlatGarmentGeometry.cached(hoodless.stream().map(ResolvedPart::model).toList()).originalBounds();
            var attachment = FlatGarmentGeometry.hangerAttachmentPoint(hoodless);
            assertEquals(bounds.center().x(), attachment.x());
            assertEquals(bounds.max().y(), attachment.y());
            assertEquals(bounds.center().z(), attachment.z());
        }
        var hoodBounds = FlatGarmentGeometry.cached(List.of(hoodUp.model())).originalBounds();
        assertEquals(new ClientGarmentAssets.Vec3(hoodBounds.center().x(), hoodBounds.max().y(), hoodBounds.center().z()),
                FlatGarmentGeometry.hangerAttachmentPoint(List.of(hoodUp)));
        assertEquals(new ClientGarmentAssets.Vec3(0, 0, 0), FlatGarmentGeometry.hangerAttachmentPoint(List.of()));
    }

    private static ResolvedPart resolved(String name, GarmentPartModel model) {
        return new ResolvedPart(name, model, new ResolvedTextures(model.defaultTexture(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 1, Optional.empty()));
    }

    private static GarmentPartModel part(String name) throws IOException {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.fromNamespaceAndPath("createapparel", name),
                JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/createapparel/garment_models/parts/" + name + ".json"))).getAsJsonObject());
    }
}
