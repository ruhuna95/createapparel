package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face.*;
import static org.junit.jupiter.api.Assertions.*;

class ShortsAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");

    @Test
    void bottomDefinitionResolvesIndependentPartsForEveryProfile() throws IOException {
        var definition = json(Path.of("src/main/resources/data/createapparel/garments/shorts.json"));
        assertEquals("bottom", definition.get("default_slot").getAsString());
        assertEquals("[\"bottom\"]", definition.get("allowed_slots").toString());
        assertEquals("[\"plain\"]", definition.get("designs").toString());
        assertEquals(0, definition.getAsJsonObject("styles").size());
        var templateId = ResourceLocation.parse(definition.get("model_template").getAsString());
        var template = json(ASSETS.resolve("garment_models/templates/" + templateId.getPath() + ".json"))
                .getAsJsonObject(definition.get("model_variant").getAsString());
        for (ArmProfile arms : ArmProfile.values()) {
            for (ChestProfile chest : ChestProfile.values()) {
                var selected = GarmentPartSelection.select(Map.of(), arms, chest, template.keySet());
                assertEquals(List.of("torso", "left_leg", "right_leg"), selected);
                for (String name : selected) {
                    var id = ResourceLocation.parse(template.getAsJsonObject(name).get("model").getAsString());
                    assertEquals("shorts/" + name, id.getPath());
                    var part = ClientGarmentAssetLoader.parsePart(id,
                            json(ASSETS.resolve("garment_models/parts/" + id.getPath() + ".json")));
                    var texture = part.variants().get(definition.get("texture_variant").getAsString());
                    assertNotNull(texture);
                    assertTrue(texture.overlay().isEmpty());
                    assertTrue(texture.brightnessMask().isEmpty());
                    assertTrue(part.designs().isEmpty());
                    assertTrue(Files.exists(ASSETS.resolve("textures/" + texture.base().getPath() + ".png")));
                }
            }
        }
    }

    @Test
    void shortLegHemsStayVisibleOutsideBodyWithoutClosingMinimalFaces() throws IOException {
        for (String name : List.of("left_leg", "right_leg")) {
            var id = ResourceLocation.parse("createapparel:shorts/" + name);
            var part = ClientGarmentAssetLoader.parsePart(id,
                    json(ASSETS.resolve("garment_models/parts/" + id.getPath() + ".json")));
            assertEquals(2, part.elements().size());
            var body = part.elements().get(0);
            var hem = part.elements().get(1);
            assertEquals(7.995, body.from().y(), 0.00001);
            assertEquals(12.005, body.to().y(), 0.00001);
            assertEquals(4.255, body.to().x() - body.from().x(), 0.00001);
            assertEquals(name.equals("left_leg"), body.to().x() + body.from().x() > 0);
            boolean leftLeg = name.equals("left_leg");
            assertEquals(leftLeg ? -0.005 : -4.25, body.from().x(), 0.00001);
            assertEquals(leftLeg ? 4.25 : 0.005, body.to().x(), 0.00001);
            assertEquals(leftLeg ? -0.005 : -4.5, hem.from().x(), 0.00001);
            assertEquals(leftLeg ? 4.5 : 0.005, hem.to().x(), 0.00001);
            assertEquals(body.from().y(), hem.from().y());
            assertEquals(body.to().y(), hem.to().y());
            assertEquals(-2.5, hem.from().z(), 0.00001);
            assertEquals(2.5, hem.to().z(), 0.00001);
            assertTrue(hem.from().z() < body.from().z());
            assertTrue(hem.to().z() > body.to().z());
            assertFalse(body.faces().containsKey(BOTTOM));
            assertEquals(Set.of(FRONT, BACK, leftLeg ? RIGHT : LEFT), hem.faces().keySet());
            var outerFace = leftLeg ? RIGHT : LEFT;
            var bodyVertices = GarmentElementGeometry.faceVertices(body, outerFace);
            var hemVertices = GarmentElementGeometry.faceVertices(hem, outerFace);
            float direction = leftLeg ? 1 : -1;
            for (int i = 0; i < 4; i++) {
                boolean upper = i == 1 || i == 2;
                assertEquals(direction * (upper ? 4.005 : 4.25), bodyVertices.get(i).x(), 0.00001);
                assertEquals(direction * 0.25, hemVertices.get(i).x() - bodyVertices.get(i).x(), 0.00001);
                assertEquals(bodyVertices.get(i).y(), hemVertices.get(i).y());
            }
            var bodyNormal = GarmentElementGeometry.normal(bodyVertices);
            var hemNormal = GarmentElementGeometry.normal(hemVertices);
            assertEquals(bodyNormal.x(), hemNormal.x(), 0.00001);
            assertEquals(bodyNormal.y(), hemNormal.y(), 0.00001);
            assertEquals(bodyNormal.z(), hemNormal.z(), 0.00001);
            assertTrue(Math.abs(bodyNormal.y()) > 0.01);
            var base = ImageIO.read(ASSETS.resolve("textures/" + part.variants().get("default").base().getPath() + ".png").toFile());
            assertEquals(16, base.getWidth());
            assertEquals(part.textureWidth(), base.getWidth());
            assertEquals(part.textureHeight(), base.getHeight());
            for (var face : hem.faces().entrySet()) {
                var uv = face.getValue();
                var bodyUv = body.faces().get(face.getKey());
                assertEquals(bodyUv.u0(), uv.u0());
                assertEquals(bodyUv.u1(), uv.u1());
                assertEquals(bodyUv.v1(), uv.v0());
                for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                    for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                        int pixel = base.getRGB(x, y);
                        assertEquals(y == 11 ? 255 : 0, pixel >>> 24);
                        assertEquals(pixel & 255, pixel >> 8 & 255);
                        assertEquals(pixel & 255, pixel >> 16 & 255);
                        assertEquals(255, base.getRGB(x, y - 4) >>> 24);
                    }
                }
            }
        }
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
