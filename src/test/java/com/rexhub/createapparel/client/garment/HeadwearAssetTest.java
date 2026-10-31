package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.renderer.CommonGarmentRenderer;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.GarmentDefinition;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face.*;
import static org.junit.jupiter.api.Assertions.*;

class HeadwearAssetTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final List<String> IDS = List.of("cap", "beanie", "beret", "hat", "casquette", "sun_visor");

    @Test
    void catalogResolvesHeadForEveryProfileWithOnlyPrimaryColor() throws IOException {
        for (String id : IDS) {
            var definition = json(Path.of("src/main/resources/data/createapparel/garments/" + id + ".json"));
            assertEquals("head", definition.get("default_slot").getAsString());
            assertEquals(JsonParser.parseString("[\"head\"]"), definition.get("allowed_slots"));
            assertEquals("createapparel:" + id, definition.get("model_template").getAsString());
            assertEquals("default", definition.get("model_variant").getAsString());
            assertEquals(id, definition.get("texture_variant").getAsString());
            DesignVariationAssetTest.assertDefinitionDesigns(definition, id);
            assertEquals(id.equals("cap") ? JsonParser.parseString("{\"wear\":[\"front\",\"back\"]}")
                    : new JsonObject(), definition.getAsJsonObject("styles"));
            var template = json(ASSETS.resolve("garment_models/templates/" + id + ".json"));
            assertEquals(Set.of("default"), template.keySet());
            var names = template.getAsJsonObject("default");
            assertEquals(id.equals("cap") ? Set.of("head", "head_back") : Set.of("head"), names.keySet());
            assertEquals("createapparel:" + id + "/head", names.getAsJsonObject("head").get("model").getAsString());
            for (var arms : ArmProfile.values()) {
                for (var chest : ChestProfile.values()) {
                    assertEquals(List.of("head"), GarmentPartSelection.select(Map.of(), arms, chest, names.keySet()));
                }
            }
            var model = part(id);
            assertFalse(model.designsFor(id).containsKey("plain"));
            assertEquals(Set.of("default", id), model.variants().keySet());
        }
    }

    @Test
    void texturesHaveBinaryAlphaGrayscaleAndVisibleReferencedFaces() throws IOException {
        for (String id : IDS) {
            var model = part(id);
            var texture = model.variants().get(id);
            assertTrue(texture.overlay().isEmpty());
            assertTrue(texture.brightnessMask().isEmpty());
            assertEquals(1F, texture.brightness());
            var image = ImageIO.read(ASSETS.resolve("textures/" + texture.base().getPath() + ".png").toFile());
            assertEquals(32, image.getWidth());
            assertEquals(32, image.getHeight());
            Set<Integer> shades = new HashSet<>();
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    int pixel = image.getRGB(x, y);
                    assertTrue(pixel >>> 24 == 0 || pixel >>> 24 == 255);
                    assertEquals(pixel >> 16 & 255, pixel >> 8 & 255);
                    assertEquals(pixel >> 8 & 255, pixel & 255);
                    if (pixel >>> 24 != 0) shades.add(pixel & 255);
                }
            }
            assertTrue(shades.size() >= 4, id);
            for (var element : model.elements()) {
                for (var uv : element.faces().values()) {
                    assertTrue(uv.u0() >= 0 && uv.v0() >= 0 && uv.u1() <= 32 && uv.v1() <= 32);
                    int opaque = 0;
                    for (int y = (int) uv.v0(); y < uv.v1(); y++) {
                        for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                            if (image.getRGB(x, y) >>> 24 == 255) opaque++;
                        }
                    }
                    assertTrue(opaque > 0, id + " / " + element.name());
                    if (element.name().equals("folded_rib")) assertEquals(8, opaque);
                }
            }
            if (Set.of("cap", "beret", "casquette").contains(id)) {
                assertTrue((image.getRGB(12, 15) & 255) + 40 < (image.getRGB(12, 4) & 255));
            }
        }
    }

    @Test
    void capBackHasASteppedOpeningAndContinuousTexture() throws IOException {
        var cap = part("cap");
        assertEquals(Set.of(TOP, FRONT, LEFT, RIGHT), element(cap, "head_crown").faces().keySet());
        var panels = cap.elements().stream().filter(e -> e.faces().containsKey(BACK)).toList();
        assertEquals(6, panels.size());
        assertEquals(-2F, element(cap, "back_left").to().x());
        assertEquals(2F, element(cap, "back_right").from().x());
        assertEquals(31.4F, element(cap, "back_top").from().y());
        assertEquals(30.4F, element(cap, "back_upper_left").from().y());
        assertEquals(30.4F, element(cap, "back_upper_right").from().y());
        assertEquals(29.4F, element(cap, "back_band").to().y());
        float area = 0;
        for (var panel : panels) {
            assertEquals(Set.of(BACK), panel.faces().keySet());
            assertTrue(panel.to().x() <= -2 || panel.from().x() >= 2
                    || panel.to().y() <= 29.4F || panel.from().y() >= 30.4F);
            assertTrue(panel.to().x() <= -1 || panel.from().x() >= 1
                    || panel.to().y() <= 30.4F || panel.from().y() >= 31.4F);
            var points = GarmentElementGeometry.faceVertices(panel, BACK);
            var uv = panel.faces().get(BACK).corners();
            for (int i = 0; i < 4; i++) {
                var point = points.get(i);
                assertEquals(4.6F, point.z());
                assertEquals(24 + (4.6F - point.x()) * 8 / 9.2F, uv.get(i).u(), 0.00001);
                assertEquals(8 + (32.6F - point.y()) * 3 / 4.2F, uv.get(i).v(), 0.00001);
            }
            area += (panel.to().x() - panel.from().x()) * (panel.to().y() - panel.from().y());
        }
        assertEquals(9.2F * 4.2F - 6, area, 0.0001);
    }

    @Test
    void capWearStylesDefaultToFrontAndSelectExactlyOneHead() throws IOException {
        var definition = GarmentDefinition.fromJson(resource("cap"),
                json(Path.of("src/main/resources/data/createapparel/garments/cap.json")));
        assertEquals(Map.of("wear", "front"), EquippedStyleState.defaultsFor(definition).styles());
        assertEquals(Map.of("wear", "front"), new EquippedStyleState(Map.of("wear", "invalid"))
                .normalize(definition).styles());
        assertEquals(Map.of("wear", "back"), new EquippedStyleState(Map.of("wear", "back"))
                .normalize(definition).styles());
        var template = json(ASSETS.resolve("garment_models/templates/cap.json")).getAsJsonObject("default");
        assertEquals("createapparel:cap/head_back", template.getAsJsonObject("head_back").get("model").getAsString());
        assertEquals(Set.of("head", "head_back"), GarmentPartSelection.requiredStyleParts(definition.styles(), false));
        for (var arms : ArmProfile.values()) {
            for (var chest : ChestProfile.values()) {
                for (String wear : List.of("front", "back")) {
                    String expected = wear.equals("back") ? "head_back" : "head";
                    assertEquals(List.of(expected), GarmentPartSelection.select(
                            Map.of("wear", wear), arms, chest, template.keySet()));
                }
                assertEquals(List.of("head_back"), GarmentPartSelection.select(
                        Map.of("wear", "back"), arms, chest, Set.of("head")));
            }
        }
    }

    @Test
    void backwardCapRotatesEveryVertexAndTextureWithTheBrim() throws IOException {
        var front = part("cap");
        var back = ClientGarmentAssetLoader.parsePart(resource("cap/head_back"),
                json(ASSETS.resolve("garment_models/parts/cap/head_back.json")));
        assertEquals(front.variants(), back.variants());
        assertEquals(front.elements().size(), back.elements().size());
        for (var original : front.elements()) {
            var rotated = element(back, original.name());
            assertEquals(original.faces().size(), rotated.faces().size());
            for (var face : original.faces().keySet()) {
                var opposite = switch (face) {
                    case FRONT -> BACK;
                    case BACK -> FRONT;
                    case LEFT -> RIGHT;
                    case RIGHT -> LEFT;
                    default -> face;
                };
                var points = GarmentElementGeometry.faceVertices(original, face);
                var reversed = GarmentElementGeometry.faceVertices(rotated, opposite);
                for (int i = 0; i < 4; i++) {
                    int j = face == TOP || face == BOTTOM ? (i + 2) % 4 : i;
                    var a = FlatGarmentGeometry.rotate(points.get(i), original.rotation());
                    var b = FlatGarmentGeometry.rotate(reversed.get(j), rotated.rotation());
                    assertEquals(-a.x(), b.x(), 0.00001);
                    assertEquals(a.y(), b.y(), 0.00001);
                    assertEquals(-a.z(), b.z(), 0.00001);
                    assertEquals(original.faces().get(face).corners().get(i), rotated.faces().get(opposite).corners().get(j));
                }
            }
        }
    }

    @Test
    void frontBrimsShareTheCapAngleAndKeepTheirRootsAndLengths() throws IOException {
        var cap = element(part("cap"), "brim");
        for (String id : List.of("cap", "casquette", "sun_visor")) {
            var brim = element(part(id), "brim");
            assertEquals(cap.rotation(), brim.rotation());
            var root = FlatGarmentGeometry.rotate(brim.corners().get(7), brim.rotation());
            var tip = FlatGarmentGeometry.rotate(brim.corners().get(4), brim.rotation());
            assertEquals(-4.6F, root.z(), 0.00001);
            assertEquals(28.4F, root.y(), 0.00001);
            assertTrue(tip.y() < root.y());
            int length = id.equals("cap") ? 4 : id.equals("casquette") ? 2 : 3;
            assertEquals(length, Math.hypot(tip.y() - root.y(), tip.z() - root.z()), 0.00001);
            assertEquals(7.5, Math.toDegrees(Math.atan2(root.y() - tip.y(), root.z() - tip.z())), 0.0001);
        }
    }

    @Test
    void crownsBrimsStepsAndButtonsUseConnectedResourceGeometry() throws IOException {
        for (String id : List.of("cap", "beanie")) {
            var crown = element(part(id), "head_crown");
            assertEquals(new ClientGarmentAssets.Vec3(-4.6F, 28.4F, -4.6F), crown.from());
            assertEquals(new ClientGarmentAssets.Vec3(4.6F, 32.6F, 4.6F), crown.to());
            assertFalse(crown.faces().containsKey(BOTTOM));
        }
        for (String id : List.of("cap", "casquette", "sun_visor")) {
            var brim = element(part(id), "brim");
            assertEquals(id.equals("cap") ? 4 : id.equals("casquette") ? 2 : 3,
                    brim.to().z() - brim.from().z(), 0.00001);
            assertEquals(-4.6F, brim.to().z());
            assertEquals(28.4F, brim.to().y());
            assertEquals(Set.of(TOP), brim.faces().keySet());
        }
        var beret = element(part("beret"), "head_crown");
        assertEquals(-4.85F, beret.from().x());
        assertEquals(29.4F, beret.from().y());
        assertEquals(32.35F, beret.to().y());
        assertTrue(beret.faces().containsKey(BOTTOM));
        assertFalse(beret.faces().containsKey(TOP));
        assertEquals(1F, beret.faces().get(FRONT).v1() - beret.faces().get(FRONT).v0());
        var bottomNormal = GarmentElementGeometry.normal(GarmentElementGeometry.faceVertices(beret, BOTTOM));
        assertTrue(bottomNormal.y() < 0);
        assertTrue(bottomNormal.z() < 0);
        for (int i = 0; i < 4; i++) {
            var bottom = beret.corners().get(i);
            var top = beret.corners().get(i + 4);
            float raise = i < 2 ? 0.5F : 0F;
            float extent = 4.6F + 0.25F * raise / (32.35F - 29.4F);
            assertEquals(extent, Math.abs(bottom.x()), 0.00001);
            assertEquals(extent, Math.abs(bottom.z()), 0.00001);
            assertEquals(29.4F + raise, bottom.y(), 0.00001);
            assertEquals(4.85F, Math.abs(top.x()), 0.00001);
            assertEquals(4.85F, Math.abs(top.z()), 0.00001);
        }
        var capBrim = element(part("cap"), "brim");
        assertEquals(-7.5F, capBrim.rotation().angle());
        var capRoot = FlatGarmentGeometry.rotate(capBrim.corners().get(7), capBrim.rotation());
        var capTip = FlatGarmentGeometry.rotate(capBrim.corners().get(4), capBrim.rotation());
        assertEquals(-4.6F, capRoot.z(), 0.00001);
        assertEquals(28.4F, capRoot.y(), 0.00001);
        assertTrue(capTip.y() < capRoot.y());
        assertTrue(capRoot.y() - capTip.y() < 0.6F);
        assertEquals(4, Math.hypot(capTip.y() - capRoot.y(), capTip.z() - capRoot.z()), 0.00001);
        assertEquals(4, capBrim.faces().get(TOP).v1() - capBrim.faces().get(TOP).v0());
        var casquette = part("casquette");
        var upper = element(casquette, "head_crown");
        var lower = element(casquette, "head_band");
        assertEquals(upper.from().y(), lower.to().y());
        assertEquals(-4.85F, upper.from().x());
        assertEquals(-4.6F, lower.from().x());
        assertEquals(4, casquette.elements().stream().filter(e -> e.name().startsWith("crown_step_")).count());
        for (var step : casquette.elements().stream().filter(e -> e.name().startsWith("crown_step_")).toList()) {
            assertEquals(Set.of(BOTTOM), step.faces().keySet());
            assertEquals(lower.to().y(), step.from().y());
        }
        var hat = part("hat");
        var sloped = element(hat, "sloped_brim");
        var crown = element(hat, "head_crown");
        assertEquals(crown.from().y(), sloped.to().y());
        assertEquals(29.4F, crown.from().y());
        assertEquals(32.6F, crown.to().y());
        assertEquals(2, crown.faces().get(FRONT).v1() - crown.faces().get(FRONT).v0());
        assertEquals(new ClientGarmentAssets.Vec3(-6.6F, 27.9F, -6.6F), sloped.from());
        assertEquals(1.5F, sloped.to().y() - sloped.from().y(), 0.00001);
        assertEquals(crown.from().x(), sloped.corners().get(4).x(), 0.00001);
        assertEquals(crown.from().z(), sloped.corners().get(4).z(), 0.00001);
        assertEquals(2F, sloped.corners().get(4).x() - sloped.from().x(), 0.00001);
        for (var face : sloped.faces().keySet()) {
            assertTrue(GarmentElementGeometry.normal(GarmentElementGeometry.faceVertices(sloped, face)).y() > 0);
        }
        for (String id : List.of("cap", "beret", "casquette")) {
            var button = element(part(id), "crown_button");
            assertEquals(-0.5F, button.from().x());
            assertEquals(0.5F, button.to().x());
            assertEquals(-0.5F, button.from().z());
            assertEquals(0.5F, button.to().z());
            var surface = element(part(id), id.equals("beret") ? "crown_plate" : "head_crown");
            assertEquals(surface.to().y() + 0.25F, button.to().y(), 0.00001);
        }
        var plate = element(part("beret"), "crown_plate");
        assertEquals(beret.to().y(), plate.from().y());
        assertEquals(33.35F, plate.to().y());
        assertEquals(1F, plate.to().y() - plate.from().y(), 0.00001);
        for (int i = 0; i < 4; i++) {
            assertEquals(beret.corners().get(i + 4), plate.corners().get(i));
            assertEquals(1.85F, Math.abs(plate.corners().get(i + 4).x()), 0.00001);
            assertEquals(1.85F, Math.abs(plate.corners().get(i + 4).z()), 0.00001);
        }
        assertEquals(6F, beret.to().x() - beret.from().x()
                - (plate.corners().get(5).x() - plate.corners().get(4).x()), 0.00001);
        for (var face : List.of(FRONT, BACK, LEFT, RIGHT)) {
            assertTrue(GarmentElementGeometry.normal(GarmentElementGeometry.faceVertices(plate, face)).y() > 0);
        }
        assertEquals(-plate.from().x(), plate.to().x());
        assertEquals(-plate.from().z(), plate.to().z());
        assertEquals(Set.of(TOP, FRONT, BACK, LEFT, RIGHT), plate.faces().keySet());
        var visor = part("sun_visor");
        assertEquals(2, visor.elements().size());
        assertEquals(Set.of(FRONT, BACK, LEFT, RIGHT), element(visor, "head_band").faces().keySet());
        assertEquals(30.6F, element(visor, "head_band").to().y());
    }

    @Test
    void headAndRaisedHoodFollowHeadWhileTorsoKeepsBodyPose() throws Exception {
        var player = new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE, false).getRoot().bake(64, 64), false);
        var method = CommonGarmentRenderer.class.getDeclaredMethod("humanoidGarmentPose",
                net.minecraft.client.model.HumanoidModel.class, ArmProfile.class, ResolvedPart.class);
        method.setAccessible(true);
        for (String semantic : List.of("head", "head_back", "head_up", "hood_up", "torso")) {
            var result = method.invoke(null, player, ArmProfile.CLASSIC, new ResolvedPart(semantic, part("cap"), null));
            var accessor = result.getClass().getDeclaredMethod("part");
            accessor.setAccessible(true);
            assertSame(semantic.equals("torso") ? player.body : player.head, accessor.invoke(result));
        }
    }

    @Test
    void sewingAndItemModelsExposeAllSixHats() throws IOException {
        for (String id : IDS) {
            var recipe = json(Path.of("src/generated/resources/data/createapparel/recipe/" + id + ".json"));
            assertEquals("createapparel:sewing", recipe.get("type").getAsString());
            assertEquals("createapparel:" + id, recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(1, recipe.getAsJsonObject("fabric").get("count").getAsInt());
            assertEquals(1, recipe.getAsJsonObject("thread").get("count").getAsInt());
            var expected = new HashSet<>(Set.of("cotton", "synthetic"));
            if (Set.of("cap", "beanie", "beret", "hat", "casquette").contains(id)) expected.add("wool");
            if (!Set.of("beanie", "beret").contains(id)) expected.addAll(Set.of("linen", "hemp"));
            var actual = new HashSet<String>();
            recipe.getAsJsonObject("fabric").getAsJsonArray("tag").forEach(tag ->
                    actual.add(tag.getAsString().replace("createapparel:fabrics/", "")));
            assertEquals(expected, actual);
            var model = json(Path.of("src/generated/resources/assets/createapparel/models/item/" + id + ".json"));
            assertEquals("minecraft:builtin/entity", model.get("parent").getAsString());
        }
    }

    private static Element element(GarmentPartModel part, String name) {
        return part.elements().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    private static GarmentPartModel part(String id) throws IOException {
        return ClientGarmentAssetLoader.parsePart(resource(id + "/head"),
                json(ASSETS.resolve("garment_models/parts/" + id + "/head.json")));
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath("createapparel", path);
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
