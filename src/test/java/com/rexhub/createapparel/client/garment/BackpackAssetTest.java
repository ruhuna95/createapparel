package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.*;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.GarmentDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BackpackAssetTest {
    @Test
    void coatVariantsMoveBodiesAndBothSidesOfStrapsWithoutChangingTextures() throws Exception {
        for (String id : IDS) {
            var definition = GarmentDefinition.fromJson(resource(id), json(Path.of(
                    "src/main/resources/data/createapparel/garments/" + id + ".json")));
            var template = json(ASSETS.resolve("garment_models/templates/" + id + ".json"));
            assertEquals("default", definition.modelVariantFor(com.rexhub.createapparel.garment.GarmentWearContext.defaultsFor(definition)));
            assertEquals("over_coat", definition.modelVariantFor(new com.rexhub.createapparel.garment.GarmentWearContext(
                    com.rexhub.createapparel.garment.GarmentSlot.BACK, EquippedStyleState.defaultsFor(definition), true)));
            for (String semantic : template.getAsJsonObject("default").keySet()) {
                String path = template.getAsJsonObject("default").getAsJsonObject(semantic).get("model").getAsString().split(":")[1];
                var normal = part(path);
                var coat = part(path + "_over_coat");
                assertEquals(normal.variants(), coat.variants());
                assertEquals(normal.designs(), coat.designs());
                assertEquals(normal.textureWidth(), coat.textureWidth());
                var key = RuntimeGarmentTextureCache.class.getDeclaredMethod("key", GarmentPartModel.class,
                        ClientGarmentAssets.ResolvedTextures.class, com.rexhub.createapparel.garment.GarmentAppearance.class);
                key.setAccessible(true);
                var data = new com.rexhub.createapparel.garment.GarmentData(
                        com.rexhub.createapparel.material.FabricMaterial.COTTON, "plain",
                        com.rexhub.createapparel.material.FabricColor.WHITE, java.util.Optional.empty(), java.util.Optional.empty());
                assertEquals(key.invoke(null, normal, normal.resolveTextures(definition, data), data),
                        key.invoke(null, coat, coat.resolveTextures(definition, data), data));
                for (int i = 0; i < normal.elements().size(); i++) {
                    var before = normal.elements().get(i);
                    var after = coat.elements().get(i);
                    assertEquals(before.faces(), after.faces());
                    assertEquals(before.from().y(), after.from().y());
                    assertEquals(before.from().x(), after.from().x());
                    assertEquals(before.to().x(), after.to().x());
                    if (semantic.startsWith("torso")) {
                        assertEquals(before.from().z() + Math.signum(before.from().z()) * 0.375F, after.from().z(), EPS);
                        assertEquals(before.to().z() + Math.signum(before.to().z()) * 0.375F, after.to().z(), EPS);
                    } else {
                        float delta = semantic.endsWith("front") ? -0.375F : 0.375F;
                        assertEquals(before.from().z() + delta, after.from().z(), EPS);
                        assertEquals(before.to().z() + delta, after.to().z(), EPS);
                    }
                }
            }
        }
    }
    private static final Path ASSETS = Path.of("src/main/resources/assets/createapparel");
    private static final List<String> IDS = List.of("backpack", "daypack", "crossbody_bag");
    private static final float EPS = 0.00001F;

    @Test
    void definitionsResolveAllProfilesAndCarryStylesWithTheSpecifiedRecipes() throws Exception {
        for (String id : IDS) {
            var definition = GarmentDefinition.fromJson(resource(id), json(Path.of(
                    "src/main/resources/data/createapparel/garments/" + id + ".json")));
            assertEquals("back", definition.defaultSlot().getSerializedName());
            var mapping = json(ASSETS.resolve("garment_models/templates/" + id + ".json")).getAsJsonObject("default");
            assertTrue(mapping.keySet().containsAll(GarmentPartSelection.requiredStyleParts(definition.styles(), false)));
            assertEquals(id.equals("crossbody_bag") ? Map.of("carry", "back") : Map.of(),
                    EquippedStyleState.defaultsFor(definition).styles());
            for (var arm : ArmProfile.values()) {
                for (var chest : ChestProfile.values()) {
                    for (String carry : List.of("back", "front")) {
                        var styles = new EquippedStyleState(Map.of("carry", carry)).normalize(definition).styles();
                        var selected = GarmentPartSelection.select(styles, arm, chest, mapping.keySet());
                        assertEquals(2, selected.size());
                        for (String semantic : selected) {
                            var key = mapping.getAsJsonObject(semantic).get("model").getAsString().split(":")[1];
                            assertNotNull(part(key).variants().get(id));
                        }
                    }
                }
            }
            var recipe = json(Path.of("src/generated/resources/data/createapparel/recipes/" + id + ".json"));
            assertEquals(3, recipe.getAsJsonObject("fabric").get("count").getAsInt());
            assertEquals(2, recipe.getAsJsonObject("thread").get("count").getAsInt());
            var materials = new HashSet<String>();
            recipe.getAsJsonObject("fabric").getAsJsonArray("tag").forEach(tag -> materials.add(tag.getAsString()));
            assertEquals(Set.of("createapparel:fabrics/cotton", "createapparel:fabrics/hemp",
                    "createapparel:fabrics/leather", "createapparel:fabrics/synthetic"), materials);
        }
    }

    @Test
    void bagDimensionsAreNotInflatedAndDecorationsFollowTheQuarterPixelShell() throws Exception {
        for (String id : IDS) {
            var belt = part(id + (id.equals("crossbody_bag") ? "/torso_back" : "/torso")).elements().get(0);
            if (!id.equals("crossbody_bag")) {
                assertEquals(new Vec3(-4.02F, 11.98F, -2.8F), belt.from());
                assertEquals(new Vec3(4.02F, 24.02F, 2.8F), belt.to());
            } else {
                assertEquals(2, belt.to().x() - belt.from().x(), EPS);
                assertEquals(24.02, belt.to().y(), EPS);
            }
            var bag = part(id + (id.equals("crossbody_bag") ? "/back_back" : "/back"));
            var upper = element(bag, "upper");
            var lower = element(bag, "lower");
            if (!id.equals("crossbody_bag")) {
                int w = id.equals("backpack") ? 7 : 6;
                int d = id.equals("backpack") ? 4 : 3;
                int h = id.equals("backpack") ? 11 : 7;
                int top = id.equals("backpack") ? 23 : 22;
                assertEquals(w, lower.to().x() - lower.from().x(), EPS);
                assertEquals(d, lower.to().z() - lower.from().z(), EPS);
                assertEquals(h, upper.to().y() - lower.from().y(), EPS);
                assertEquals(top, upper.to().y(), EPS);
                assertEquals(w - 2, upper.corners().get(5).x() - upper.corners().get(4).x(), EPS);
                assertEquals(d - 1, upper.corners().get(7).z() - upper.corners().get(4).z(), EPS);
                assertEquals(lower.corners().subList(4, 8), upper.corners().subList(0, 4));
            } else {
                assertEquals(2, upper.to().y() - upper.from().y(), EPS);
                assertEquals(4, element(bag, "middle").to().y() - element(bag, "middle").from().y(), EPS);
                assertEquals(1, lower.to().y() - lower.from().y(), EPS);
                double highest = bag.elements().stream().filter(e -> !e.name().endsWith("decoration"))
                        .flatMap(e -> e.corners().stream().map(p -> FlatGarmentGeometry.rotate(p, e.rotation())))
                        .mapToDouble(Vec3::y).max().orElseThrow();
                assertEquals(22 + Math.cos(Math.toRadians(upper.rotation().angle())), highest, EPS);
                assertEquals(.48875, upper.rotation().origin().x(), EPS);
                assertEquals(19.819323, upper.rotation().origin().y(), EPS);
                assertEquals(90 + element(part("crossbody_bag/torso_back"), "front_belt").rotation().angle(), upper.rotation().angle(), EPS);
            }
            for (var shell : bag.elements()) {
                if (!shell.name().endsWith("_decoration")) continue;
                var body = element(bag, shell.name().replace("_decoration", ""));
                assertEquals(body.from().x() - .25F, shell.from().x(), EPS);
                assertEquals(body.to().x() + .25F, shell.to().x(), EPS);
                assertEquals(body.from().z() - .25F, shell.from().z(), EPS);
                assertEquals(body.to().z() + .25F, shell.to().z(), EPS);
                assertEquals(body.rotation(), shell.rotation());
            }
        }
    }

    @Test
    void frontCarryRotatesEveryVertexAndTexturePointByExactlyHalfATurn() throws Exception {
        for (String prefix : List.of("torso", "back")) {
            var back = part("crossbody_bag/" + prefix + "_back");
            var front = part("crossbody_bag/" + prefix + "_front");
            assertEquals(back.elements().size(), front.elements().size());
            for (int i = 0; i < back.elements().size(); i++) {
                var a = back.elements().get(i);
                var b = front.elements().get(i);
                for (var face : a.faces().keySet()) {
                    Face opposite = switch (face) {
                        case FRONT -> Face.BACK;
                        case BACK -> Face.FRONT;
                        case LEFT -> Face.RIGHT;
                        case RIGHT -> Face.LEFT;
                        default -> face;
                    };
                    var pa = GarmentElementGeometry.faceVertices(a, face);
                    var pb = GarmentElementGeometry.faceVertices(b, opposite);
                    for (int j = 0; j < 4; j++) {
                        int k = face == Face.TOP || face == Face.BOTTOM ? (j + 2) % 4 : j;
                        var va = FlatGarmentGeometry.rotate(pa.get(j), a.rotation());
                        var vb = FlatGarmentGeometry.rotate(pb.get(k), b.rotation());
                        assertEquals(-va.x(), vb.x(), EPS);
                        assertEquals(va.y(), vb.y(), EPS);
                        assertEquals(-va.z(), vb.z(), EPS);
                        assertEquals(a.faces().get(face).corners().get(j), b.faces().get(opposite).corners().get(k));
                    }
                }
            }
        }
    }

    @Test
    void textureLayersHaveValidPixelsAndEveryAuthoredFaceIsVisible() throws Exception {
        for (String id : IDS) {
            var template = json(ASSETS.resolve("garment_models/templates/" + id + ".json")).getAsJsonObject("default");
            for (var entry : template.entrySet()) {
                var model = part(entry.getValue().getAsJsonObject().get("model").getAsString().split(":")[1]);
                var variant = model.variants().get(id);
                var base = texture(variant.base());
                assertEquals(base.getWidth(), base.getHeight());
                assertEquals(0, base.getWidth() & (base.getWidth() - 1));
                assertTrue(variant.brightnessMask().isEmpty());
                Set<Integer> shades = new HashSet<>();
                for (int y = 0; y < base.getHeight(); y++) {
                    for (int x = 0; x < base.getWidth(); x++) {
                        int p = base.getRGB(x, y);
                        assertTrue(p >>> 24 == 0 || p >>> 24 == 255);
                        assertEquals(p & 255, p >> 8 & 255);
                        assertEquals(p & 255, p >> 16 & 255);
                        if (p >>> 24 != 0) shades.add(p & 255);
                    }
                }
                assertTrue(shades.size() >= 4);
                var overlay = variant.overlay().isPresent() ? texture(variant.overlay().get()) : null;
                if (overlay != null) {
                    assertEquals(base.getWidth(), overlay.getWidth());
                    assertEquals(base.getHeight(), overlay.getHeight());
                    Set<Integer> meshShades = new HashSet<>();
                    for (int y = 4; y < 15; y++) for (int x = 4; x < 11; x++) {
                        boolean border = x == 4 || x == 10 || y == 4 || y == 14;
                        int alpha = overlay.getRGB(x, y) >>> 24;
                        if (border) assertEquals(0, alpha);
                        else assertTrue(alpha >= 96 && alpha <= 128);
                        if (!border) meshShades.add(overlay.getRGB(x, y) & 255);
                    }
                    assertTrue(meshShades.stream().max(Integer::compareTo).orElseThrow()
                            - meshShades.stream().min(Integer::compareTo).orElseThrow() <= 25);
                }
                for (var element : model.elements()) for (var uv : element.faces().values()) {
                    assertTrue(uv.u0() >= 0 && uv.v0() >= 0 && uv.u1() <= base.getWidth() && uv.v1() <= base.getHeight());
                    boolean visible = false;
                    for (int y = (int) uv.v0(); y < uv.v1(); y++) for (int x = (int) uv.u0(); x < uv.u1(); x++) {
                        visible |= base.getRGB(x, y) >>> 24 != 0 || overlay != null && overlay.getRGB(x, y) >>> 24 != 0;
                    }
                    assertTrue(visible, id + "/" + element.name() + "/" + uv);
                }
                boolean accent = id.equals("daypack") && entry.getKey().equals("back");
                assertEquals(accent, model.designsFor(id).containsKey("plain"));
                if (accent) {
                    var mask = texture(model.designsFor(id).get("plain").accent().orElseThrow());
                    int count = 0;
                    for (int y = 0; y < mask.getHeight(); y++) for (int x = 0; x < mask.getWidth(); x++) {
                        int p = mask.getRGB(x, y);
                        assertEquals(255, p >>> 24);
                        assertEquals(p & 255, p >> 8 & 255);
                        assertEquals(p & 255, p >> 16 & 255);
                        if ((p & 255) > 0) { count++; assertEquals(255, base.getRGB(x, y) >>> 24); }
                    }
                    assertEquals(1, count);
                }
            }
        }
    }

    @Test
    void shoulderBeltsAreTwoPixelsWideAndCrossbodyPocketIsOnePixelWide() throws Exception {
        for (String id : IDS) {
            var belt = part(id + (id.equals("crossbody_bag") ? "/torso_back" : "/torso"));
            var image = texture(belt.defaultTexture());
            if (id.equals("crossbody_bag")) {
                for (var e : belt.elements()) for (var face : e.faces().values()) {
                    for (int y = (int) face.v0(); y < face.v1(); y++) for (int x = (int) face.u0(); x < face.u1(); x++) {
                        assertEquals(255, image.getRGB(x, y) >>> 24);
                    }
                }
                continue;
            }
            for (int y = 0; y < 4; y++) {
                int pixels = 0;
                for (int x = 4; x < 12; x++) if (image.getRGB(x, y) >>> 24 != 0) pixels++;
                assertEquals(id.equals("crossbody_bag") ? 2 : 4, pixels);
            }
            int rows = id.equals("backpack") ? 12 : 8;
            for (int row = 0; row < 12; row++) {
                int pixels = 0;
                for (int x = 4; x < 12; x++) if (image.getRGB(x, 4 + row) >>> 24 != 0) pixels++;
                assertEquals(row < rows ? (id.equals("crossbody_bag") ? 2 : 4) : 0, pixels);
                int rearPixels = 0;
                for (int x = 16; x < 24; x++) if (image.getRGB(x, 4 + row) >>> 24 != 0) rearPixels++;
                int expectedRear = switch (id) {
                    case "backpack" -> row < 3 ? 4 : 0;
                    case "daypack" -> row < 3 ? 4 : row >= 6 && row < 8 ? 2 : 0;
                    default -> row < 3 || row >= 6 && row < 8 ? 2 : 0;
                };
                assertEquals(expectedRear, rearPixels, id + "/rear/" + row);
            }
            int sideRow = id.equals("backpack") ? 14 : 10;
            for (int y = 4; y < 16; y++) {
                for (int x = 12; x < 16; x++) {
                    assertEquals(y >= sideRow && y < sideRow + 2 ? 255 : 0, image.getRGB(x, y) >>> 24);
                }
            }
        }
        var pocket = texture(part("crossbody_bag/back_back").defaultTexture());
        for (int y = 12; y < 17; y++) {
            int pixels = 0;
            for (int x = 7; x < 10; x++) if (pocket.getRGB(x, y) >>> 24 != 0) pixels++;
            assertEquals(1, pixels);
        }
    }

    @Test
    void daypackDiamondIsOneSquarePixelAtTheCenterOfTheSlopedFace() throws Exception {
        var model = part("daypack/back");
        var lower = element(model, "diamond_lower");
        var upper = element(model, "diamond_upper");
        var a = GarmentElementGeometry.faceVertices(lower, Face.BACK);
        var b = GarmentElementGeometry.faceVertices(upper, Face.BACK);
        assertEquals(a.get(1), b.get(0));
        assertEquals(a.get(2), b.get(3));
        Vec3 left = a.get(2), right = a.get(1), bottom = a.get(0), top = b.get(1);
        assertEquals(Math.sqrt(2), right.x() - left.x(), EPS * 4);
        assertEquals(Math.sqrt(2), Math.hypot(top.y() - bottom.y(), top.z() - bottom.z()), EPS * 4);
        assertEquals(0, (right.x() + left.x()) / 2, EPS);
        assertEquals(21.125, (top.y() + bottom.y()) / 2, EPS);
        assertEquals(5.57, (top.z() + bottom.z()) / 2, EPS);
        for (var p : List.of(left, right, bottom, top)) {
            assertEquals(6.07 - (p.y() - 20) / 2.25, p.z(), EPS);
        }
    }

    @Test
    void droppedAndHeldBagsFlattenOnlyTheirBeltsAndKeepTheAttachmentFace() throws Exception {
        var method = com.rexhub.createapparel.client.renderer.CommonGarmentRenderer.class.getDeclaredMethod(
                "applyItemPartShape", com.mojang.blaze3d.vertex.PoseStack.class,
                com.rexhub.createapparel.garment.GarmentSlot.class,
                net.minecraft.world.item.ItemDisplayContext.class, ResolvedPart.class);
        method.setAccessible(true);
        for (String semantic : List.of("torso", "torso_back", "torso_front", "back", "back_back", "back_front")) {
            String id = semantic.equals("torso") || semantic.equals("back") ? "backpack" : "crossbody_bag";
            var model = part(id + "/" + semantic);
            var bounds = FlatGarmentGeometry.cached(List.of(model)).originalBounds();
            for (var context : net.minecraft.world.item.ItemDisplayContext.values()) {
                for (var slot : com.rexhub.createapparel.garment.GarmentSlot.values()) {
                    var pose = new com.mojang.blaze3d.vertex.PoseStack();
                    method.invoke(null, pose, slot, context, new ResolvedPart(semantic, model, null));
                    var from = pose.last().pose().transformPosition(new org.joml.Vector3f(0, 0, bounds.min().z()));
                    var to = pose.last().pose().transformPosition(new org.joml.Vector3f(0, 0, bounds.max().z()));
                    boolean flat = slot == com.rexhub.createapparel.garment.GarmentSlot.BACK && semantic.startsWith("torso")
                            && switch (context) {
                        case GROUND, FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                                THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> true;
                        default -> false;
                    };
                    assertEquals(flat ? 2 : bounds.max().z() - bounds.min().z(), to.z() - from.z(), EPS);
                    if (semantic.equals("torso_front")) assertEquals(bounds.min().z(), from.z(), EPS);
                    else assertEquals(bounds.max().z(), to.z(), EPS);
                }
            }
        }
    }

    @Test
    void contactFacesHaveClearanceWithoutInflatingTheBagsOrTheTorso() throws Exception {
        for (String id : IDS) {
            var torso = part(id + (id.equals("crossbody_bag") ? "/torso_back" : "/torso"));
            var model = part(id + (id.equals("crossbody_bag") ? "/back_back" : "/back"));
            for (var body : model.elements()) {
                if (!Set.of("upper", "middle", "lower").contains(body.name())) continue;
                var contact = GarmentElementGeometry.faceVertices(body, Face.FRONT);
                for (var p : contact) {
                    assertEquals(.02, p.z() - 2.8, EPS);
                }
            }
        }
    }

    @Test
    void meshBlendsWithOpaqueDyedFabricAndSidePocketsAreMoreSubtle() throws Exception {
        var model = part("backpack/back");
        var base = texture(model.defaultTexture());
        var overlay = texture(model.variants().get("backpack").overlay().orElseThrow());
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            int mesh = overlay.getRGB(x, y);
            int alpha = mesh >>> 24;
            if (alpha == 0) continue;
            assertEquals(255, base.getRGB(x, y) >>> 24, "Mesh needs a dyed fabric underlay");
            boolean side = (x < 4 || x >= 11 && x < 15) && y >= 27;
            assertTrue(side ? alpha >= 48 && alpha <= 64 : alpha >= 96 && alpha <= 128);
            int shade = base.getRGB(x, y) & 255;
            int[] colors = {shade << 16 | shade * 4 / 10 << 8 | shade * 2 / 10,
                    shade * 2 / 10 << 16 | shade * 4 / 10 << 8 | shade};
            int a = GarmentTexturePixelOps.applyOverlay(0xff000000 | colors[0], java.util.OptionalInt.of(mesh));
            int b = GarmentTexturePixelOps.applyOverlay(0xff000000 | colors[1], java.util.OptionalInt.of(mesh));
            assertEquals(255, a >>> 24);
            assertEquals(255, b >>> 24);
            assertNotEquals(a, b, "Mesh must retain the selected fabric color");
        }
    }

    @Test
    void fabricAndBackpackZippersHaveSmoothShading() throws Exception {
        for (String id : IDS) {
            var current = texture(part(id + (id.equals("crossbody_bag") ? "/back_back" : "/back")).defaultTexture());
            int d = id.equals("backpack") ? 4 : id.equals("daypack") ? 3 : 2;
            int h = id.equals("backpack") ? 11 : id.equals("daypack") ? 7 : 7;
            assertTrue(roughness(current, 0, d + 2, d, h - 2) <= 4, id);
        }
        var backpack = texture(part("backpack/back").defaultTexture());
        for (int row : List.of(5)) {
            for (int x = 16; x < 20; x++) {
                assertTrue(Math.abs((backpack.getRGB(x, row) & 255) - (backpack.getRGB(x + 1, row) & 255)) <= 2);
            }
        }
    }

    @Test
    void fabricAndBeltsRetainSubtleNonuniformWeave() throws Exception {
        for (String id : IDS) {
            var fabric = texture(part(id + (id.equals("crossbody_bag") ? "/back_back" : "/back")).defaultTexture());
            int d = id.equals("backpack") ? 4 : id.equals("daypack") ? 3 : 2;
            int h = id.equals("backpack") ? 11 : id.equals("daypack") ? 7 : 7;
            Set<Integer> shades = new HashSet<>();
            for (int y = d + 2; y < d + h; y++) for (int x = 0; x < d; x++) {
                shades.add(fabric.getRGB(x, y) & 255);
            }
            assertTrue(shades.size() >= 4, id + " fabric must not be flat color");
            int range = shades.stream().mapToInt(Integer::intValue).max().orElseThrow()
                    - shades.stream().mapToInt(Integer::intValue).min().orElseThrow();
            assertTrue(range >= 4 && range <= 16, id + " weave must be visible but subtle");
            var belt = texture(part(id + (id.equals("crossbody_bag") ? "/torso_back" : "/torso")).defaultTexture());
            shades.clear();
            for (int y = 0; y < belt.getHeight(); y++) for (int x = 0; x < belt.getWidth(); x++) {
                if (belt.getRGB(x, y) >>> 24 != 0) shades.add(belt.getRGB(x, y) & 255);
            }
            assertTrue(shades.size() >= 6, id + " belt must retain textile detail");
            assertTrue(shades.stream().mapToInt(Integer::intValue).max().orElseThrow()
                    - shades.stream().mapToInt(Integer::intValue).min().orElseThrow() <= 20);
        }
    }

    @Test
    void darkerFabricAndBeltsRemainLighterThanTheZippers() throws Exception {
        for (String id : IDS) {
            var fabric = texture(part(id + (id.equals("crossbody_bag") ? "/back_back" : "/back")).defaultTexture());
            int d = id.equals("backpack") ? 4 : id.equals("daypack") ? 3 : 2;
            int zipper = fabric.getRGB(id.equals("backpack") ? 4 : id.equals("daypack") ? 3 : 8,
                    id.equals("crossbody_bag") ? 12 : 1) & 255;
            int total = 0, count = 0;
            for (int y = d + 2; y < d + 5; y++) for (int x = 0; x < d; x++) {
                int shade = fabric.getRGB(x, y) & 255;
                assertTrue(shade > zipper, id);
                total += shade;
                count++;
            }
            assertTrue(total / (double) count < 210, id + " fabric must be darker than the previous pale material");
            var belt = texture(part(id + (id.equals("crossbody_bag") ? "/torso_back" : "/torso")).defaultTexture());
            for (int y = 0; y < belt.getHeight(); y++) for (int x = 0; x < belt.getWidth(); x++) {
                if (belt.getRGB(x, y) >>> 24 != 0) assertTrue((belt.getRGB(x, y) & 255) > zipper, id);
            }
        }
    }

    @Test
    void daypackUpperZipperWrapsTheTopAndSidesAndKeepsTheLowerPocket() throws Exception {
        var current = texture(part("daypack/back").defaultTexture());
        for (int x = 3; x < 7; x++) assertTrue((current.getRGB(x, 1) & 255) < 180);
        for (int y : List.of(3, 4)) {
            assertTrue((current.getRGB(2, y) & 255) < 180);
            assertTrue((current.getRGB(9, y) & 255) < 180);
        }
        for (int x = 13; x < 17; x++) assertTrue((current.getRGB(x, 4) & 255) > 180);
        for (int y = 18; y < 20; y++) for (int x = 13; x < 17; x++) {
            assertEquals(0, current.getRGB(x, y) >>> 24);
        }
    }

    @Test
    void backpackTopZipperIsDarkAndLowerDecorationsFaceAwayFromThePlayer() throws Exception {
        var model = part("backpack/back");
        var base = texture(model.defaultTexture());
        var overlay = texture(model.variants().get("backpack").overlay().orElseThrow());
        for (int x = 4; x < 9; x++) assertTrue((base.getRGB(x, 1) & 255) < 160);
        var lower = element(model, "lower_decoration");
        assertFalse(lower.faces().containsKey(Face.FRONT));
        assertTrue(lower.faces().containsKey(Face.BACK));
        for (int y = 21; y < 30; y++) for (int x = 4; x < 11; x++) {
            assertEquals(0, base.getRGB(x, y) >>> 24);
            assertEquals(0, overlay.getRGB(x, y) >>> 24);
        }
        for (int y = 21; y < 30; y++) for (int x = 15; x < 22; x++) {
            assertEquals((x == 16 || x == 20) && y >= 23 && y < 28 ? 255 : 0, base.getRGB(x, y) >>> 24);
            assertEquals(0, overlay.getRGB(x, y) >>> 24);
        }
        for (int y : List.of(4, 5)) for (int x : List.of(2, 12)) {
            assertTrue((base.getRGB(x, y) & 255) < 160);
        }
        for (int x = 16; x < 21; x++) assertTrue((base.getRGB(x, 5) & 255) < 160);
        for (int y = 15; y < 18; y++) for (int x = 4; x < 9; x++) {
            assertEquals(y == 17 && x >= 5 && x <= 7 ? 255 : 0, base.getRGB(x, y) >>> 24);
        }
        for (int y = 15; y < 19; y++) for (int x = 11; x < 18; x++) {
            assertEquals(y == 16 && x >= 12 && x <= 14 ? 255 : 0, base.getRGB(x, y) >>> 24);
        }
    }

    @Test
    void webbingRibsRunAcrossTheBeltsInsteadOfFollowingTheBagFabric() throws Exception {
        for (String id : IDS) {
            var image = texture(part(id + (id.equals("crossbody_bag") ? "/torso_back" : "/torso")).defaultTexture());
            int u = id.equals("crossbody_bag") ? 2 : 5;
            int v = 4;
            boolean horizontal = id.equals("crossbody_bag");
            for (int row = 0; row < 6; row++) {
                int x = horizontal ? u + row : u;
                int y = horizontal ? v : v + row;
                assertEquals(2, Math.abs((image.getRGB(x, y) & 255)
                        - (image.getRGB(horizontal ? x : x + 1, horizontal ? y + 1 : y) & 255)));
            }
            int firstRib = image.getRGB(horizontal ? u + 1 : u, horizontal ? v : v + 1) & 255;
            int secondRib = image.getRGB(horizontal ? u + 2 : u, horizontal ? v : v + 2) & 255;
            assertEquals(6, firstRib - (image.getRGB(u, v) & 255));
            assertEquals(5, firstRib - secondRib);
            int sideU = id.equals("crossbody_bag") ? 16 : 0;
            int sideV = id.equals("crossbody_bag") ? 4 : id.equals("backpack") ? 14 : 10;
            for (int column = 0; column < 4; column++) {
                assertEquals(2, (image.getRGB(sideU + column, sideV + 1) & 255)
                        - (image.getRGB(sideU + column, sideV) & 255));
            }
            assertEquals(6, (image.getRGB(sideU + 1, sideV) & 255) - (image.getRGB(sideU, sideV) & 255));
        }
    }

    @Test
    void crossbodyBeltUvsPreserveOneTexelPerNominalPixelWithoutShear() throws Exception {
        for (String carry : List.of("back", "front")) {
            var model = part("crossbody_bag/torso_" + carry);
            for (var element : model.elements()) for (var entry : element.faces().entrySet()) {
                var uv = entry.getValue();
                if (element.name().endsWith("_attachment")) {
                    assertEquals(2, uv.u1() - uv.u0(), EPS);
                    assertEquals(1, uv.v1() - uv.v0(), EPS);
                    continue;
                }
                var vertices = GarmentElementGeometry.faceVertices(element, entry.getKey()).stream()
                        .map(v -> FlatGarmentGeometry.rotate(v, element.rotation())).toList();
                boolean offsetDepth = element.name().equals("shoulder_belt")
                        || element.name().equals("right_side_belt");
                for (int a = 0; a < 4; a++) for (int b = a + 1; b < 4; b++) {
                    var p = vertices.get(a);
                    var q = vertices.get(b);
                    double dx = q.x() - p.x(), dy = q.y() - p.y(), dz = q.z() - p.z();
                    if (offsetDepth) dz *= 4 / 5.6;
                    var t = uv.corners().get(a);
                    var s = uv.corners().get(b);
                    assertEquals(Math.sqrt(dx * dx + dy * dy + dz * dz),
                            Math.hypot(s.u() - t.u(), s.v() - t.v()), EPS * 4,
                            carry + "/" + element.name() + "/" + a + ":" + b);
                }
            }
        }
    }

    @Test
    void crossbodyCornerFragmentsShareTheMainBeltUvCoordinates() throws Exception {
        for (String carry : List.of("back", "front")) {
            var model = part("crossbody_bag/torso_" + carry);
            for (String prefix : List.of("front", "rear")) {
                var ribbons = model.elements().stream().filter(e -> e.name().startsWith(prefix + "_side_join")
                        || e.name().equals(prefix.equals("front") ? "front_belt" : "rear_lower_belt")).toList();
                int shared = 0;
                for (int a = 0; a < ribbons.size(); a++) for (int b = a + 1; b < ribbons.size(); b++) {
                    var first = ribbons.get(a);
                    var second = ribbons.get(b);
                    var firstFace = first.faces().entrySet().iterator().next();
                    var secondFace = second.faces().entrySet().iterator().next();
                    var p = GarmentElementGeometry.faceVertices(first, firstFace.getKey()).stream()
                            .map(v -> FlatGarmentGeometry.rotate(v, first.rotation())).toList();
                    var q = GarmentElementGeometry.faceVertices(second, secondFace.getKey()).stream()
                            .map(v -> FlatGarmentGeometry.rotate(v, second.rotation())).toList();
                    for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
                        if (Math.abs(p.get(i).x() - q.get(j).x()) > EPS * 4
                                || Math.abs(p.get(i).y() - q.get(j).y()) > EPS * 4
                                || Math.abs(p.get(i).z() - q.get(j).z()) > EPS * 4) continue;
                        var u = firstFace.getValue().corners().get(i);
                        var v = secondFace.getValue().corners().get(j);
                        assertEquals(u.u(), v.u(), EPS * 4, first.name() + "/" + second.name());
                        assertEquals(u.v(), v.v(), EPS * 4, first.name() + "/" + second.name());
                        shared++;
                    }
                }
                assertTrue(shared >= 4, carry + "/" + prefix);
            }
        }
    }

    @Test
    void backpackBeltsBridgeTheResizedBodyAtBothUpperAndLowerAttachments() throws Exception {
        var torso = part("backpack/torso");
        var body = part("backpack/back");
        var upperBody = element(body, "upper");
        var lowerBody = element(body, "lower");
        var image = texture(torso.defaultTexture());
        var rearBelt = element(torso, "shoulder_belt");
        double endpointY = rearBelt.to().y() - (rearBelt.to().y() - rearBelt.from().y()) * 3 / 12;
        for (String side : List.of("left", "right")) {
            var upper = element(torso, side + "_upper_attachment");
            assertEquals(2, upper.to().x() - upper.from().x(), EPS);
            assertEquals(endpointY, upper.to().y(), EPS);
            assertEquals(2.8, upper.from().z(), EPS);
            assertEquals(2.82, upper.to().z(), EPS);
            for (var p : GarmentElementGeometry.faceVertices(upper, Face.TOP)) {
                if (Math.abs(p.z() - 2.82) > EPS) continue;
                assertTrue(p.y() >= upperBody.from().y() && p.y() <= upperBody.to().y());
                double halfWidth = 3.5 - (p.y() - upperBody.from().y()) / 2;
                assertTrue(Math.abs(p.x()) <= halfWidth);
            }
            int u = side.equals("left") ? 17 : 21;
            for (int row = 4; row < 7; row++) for (int column = u; column < u + 2; column++) {
                assertEquals(255, image.getRGB(column, row) >>> 24);
            }
            var lower = element(torso, side + "_lower_attachment");
            Face face = side.equals("left") ? Face.RIGHT : Face.LEFT;
            double contactX = side.equals("left") ? lowerBody.from().x() : lowerBody.to().x();
            assertEquals(2, lower.to().y() - lower.from().y(), EPS);
            assertEquals(.02, lower.to().z() - lower.from().z(), EPS);
            var join = GarmentElementGeometry.faceVertices(element(torso, side + "_back_join"), Face.BACK);
            for (var p : GarmentElementGeometry.faceVertices(lower, face)) {
                assertEquals(contactX, p.x(), EPS);
                assertTrue(p.y() >= lowerBody.from().y() && p.y() <= lowerBody.to().y());
                if (Math.abs(p.z() - 2.8) < EPS) assertTrue(join.stream().anyMatch(v ->
                        Math.abs(v.x() - p.x()) < EPS && Math.abs(v.y() - p.y()) < EPS && Math.abs(v.z() - p.z()) < EPS));
                else assertEquals(2.82, p.z(), EPS);
            }
        }
    }

    @Test
    void strapSidePlanesAreJustOutsideTheOuterNormalSideAndTheirJoinsAreTwoPixelsHigh() throws Exception {
        for (String id : IDS) {
            var model = part(id + (id.equals("crossbody_bag") ? "/torso_back" : "/torso"));
            var right = element(model, "right_side_belt");
            assertEquals(4.51, right.to().x(), EPS);
            assertEquals(.01, right.to().x() - 4.5, EPS);
            assertEquals(2, right.to().y() - right.from().y(), EPS);
            if (!id.equals("crossbody_bag")) {
                var left = element(model, "left_side_belt");
                assertEquals(-4.51, left.from().x(), EPS);
                assertEquals(2, left.to().y() - left.from().y(), EPS);
                for (String side : List.of("left", "right")) for (String face : List.of("front", "back")) {
                    var join = element(model, side + "_" + face + "_join");
                    assertEquals(2, join.to().y() - join.from().y(), EPS);
                }
            }
        }
    }

    @Test
    void crossbodyGeometricRibbonsHaveTrueTwoPixelWidthsAndTouchTheBagAttachments() throws Exception {
        var model = part("crossbody_bag/torso_back");
        var front = element(model, "front_belt");
        var p = GarmentElementGeometry.faceVertices(front, Face.FRONT).stream()
                .map(v -> FlatGarmentGeometry.rotate(v, front.rotation())).toList();
        assertEquals(2, front.to().y() - front.from().y(), EPS * 4);
        double runX = p.get(3).x() - p.get(0).x(), runY = p.get(3).y() - p.get(0).y();
        assertTrue(Math.abs(runX) > 1);
        assertTrue(Math.abs(runY) > 1);
        assertEquals(2, Math.abs(runX * (p.get(1).y() - p.get(0).y())
                - runY * (p.get(1).x() - p.get(0).x())) / Math.hypot(runX, runY), EPS * 4);
        assertFalse(model.elements().stream().anyMatch(e -> e.name().startsWith("front_") && e.name().endsWith("_join")));
        var upper = element(model, "rear_upper_belt");
        var rear = GarmentElementGeometry.faceVertices(upper, Face.LEFT).stream()
                .map(v -> FlatGarmentGeometry.rotate(v, upper.rotation())).toList();
        assertEquals(2, Math.hypot(rear.get(0).x() - rear.get(3).x(), rear.get(0).y() - rear.get(3).y()), EPS * 4);
        var body = element(part("crossbody_bag/back_back"), "upper");
        var contact = GarmentElementGeometry.faceVertices(body, Face.FRONT).stream()
                .map(v -> FlatGarmentGeometry.rotate(v, body.rotation())).toList();
        for (var end : List.of(rear.get(0), rear.get(3))) {
            assertTrue(List.of(contact.get(1), contact.get(2)).stream()
                    .anyMatch(v -> Math.abs(end.x() - v.x()) < EPS * 4 && Math.abs(end.y() - v.y()) < EPS * 4));
        }
        var attachment = element(model, "upper_attachment");
        var join = GarmentElementGeometry.faceVertices(attachment, Face.TOP).stream()
                .map(v -> FlatGarmentGeometry.rotate(v, attachment.rotation())).toList();
        for (var v : List.of(contact.get(1), contact.get(2))) {
            assertTrue(join.stream().anyMatch(j -> Math.abs(j.x() - v.x()) < EPS * 4
                    && Math.abs(j.y() - v.y()) < EPS * 4 && Math.abs(j.z() - v.z()) < EPS * 4));
        }
        var lower = element(model, "rear_lower_belt");
        var lowerAttachment = element(model, "lower_attachment");
        assertEquals(2, lower.to().y() - lower.from().y(), EPS);
        assertEquals(lower.to().z(), lowerAttachment.from().z(), EPS);
        assertEquals(2.82, lowerAttachment.to().z(), EPS);
        var side = element(model, "right_side_belt");
        assertNull(side.rotation());
        assertEquals(2, side.to().y() - side.from().y(), EPS);
        assertEquals(front.rotation().angle(), lower.rotation().angle(), EPS);
        var lowerVertices = GarmentElementGeometry.faceVertices(lower, Face.BACK).stream()
                .map(v -> FlatGarmentGeometry.rotate(v, lower.rotation())).toList();
        var sideVertices = GarmentElementGeometry.faceVertices(side, Face.RIGHT);
        for (var vertex : sideVertices) {
            Face face = vertex.z() < 0 ? Face.FRONT : Face.BACK;
            String prefix = vertex.z() < 0 ? "front_side_join" : "rear_side_join";
            assertTrue(model.elements().stream().filter(e -> e.name().startsWith(prefix))
                    .flatMap(e -> GarmentElementGeometry.faceVertices(e, face).stream())
                    .anyMatch(v -> Math.abs(v.x() - vertex.x()) < EPS * 4
                            && Math.abs(v.y() - vertex.y()) < EPS * 4
                            && Math.abs(v.z() - vertex.z()) < EPS * 4));
        }
        for (var vertices : List.of(p, lowerVertices)) {
            Face face = vertices == p ? Face.FRONT : Face.BACK;
            String prefix = vertices == p ? "front_side_join" : "rear_side_join";
            for (var vertex : vertices) {
                if (Math.abs(vertex.x() - 4) > EPS * 4) continue;
                assertTrue(model.elements().stream().filter(e -> e.name().startsWith(prefix))
                        .flatMap(e -> GarmentElementGeometry.faceVertices(e, face).stream())
                        .anyMatch(v -> Math.abs(v.x() - vertex.x()) < EPS * 4
                                && Math.abs(v.y() - vertex.y()) < EPS * 4));
            }
        }
        var lowerJoin = GarmentElementGeometry.faceVertices(lowerAttachment, Face.BOTTOM).stream()
                .map(v -> FlatGarmentGeometry.rotate(v, lowerAttachment.rotation())).toList();
        for (var v : lowerVertices) {
            if (Math.abs(v.x() - 4) < EPS * 4) continue;
            assertTrue(lowerJoin.stream().anyMatch(q -> Math.abs(q.x() - v.x()) < EPS * 4
                    && Math.abs(q.y() - v.y()) < EPS * 4 && Math.abs(q.z() - v.z()) < EPS * 4));
        }
        var origin = body.rotation().origin();
        double slope = Math.tan(Math.toRadians(front.rotation().angle()));
        assertEquals(origin.y() + slope * (side.to().x() - origin.x()),
                (side.from().y() + side.to().y()) / 2, EPS * 4);
    }

    @Test
    void renderReviewSheetFromRuntimeGeometryAndTextures() throws Exception {
        var image = new BufferedImage(960, 640, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new java.awt.Color(237, 239, 242));
        graphics.fillRect(0, 0, 960, 640);
        graphics.dispose();
        for (int column = 0; column < 3; column++) for (int row = 0; row < 2; row++) {
            String id = IDS.get(column);
            var models = new ArrayList<GarmentPartModel>();
            models.add(part(id + (id.equals("crossbody_bag") ? "/torso_back" : "/torso")));
            models.add(part(id + (id.equals("crossbody_bag") ? "/back_back" : "/back")));
            render(image, models, column * 320 + 160, row * 320 + 165, row == 0 ? 155 : -25);
            var label = image.createGraphics();
            label.setColor(java.awt.Color.DARK_GRAY);
            label.drawString(id + (row == 0 ? " / rear" : " / front"), column * 320 + 30, row * 320 + 300);
            label.dispose();
        }
        Path output = Path.of("build/reports/backpacks-preview.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
        assertTrue(Files.size(output) > 1000);
    }

    private static void render(BufferedImage image, List<GarmentPartModel> parts, int cx, int cy, double yaw) throws Exception {
        double[] depth = new double[image.getWidth() * image.getHeight()];
        Arrays.fill(depth, -Double.MAX_VALUE);
        for (var part : parts) {
            var variant = part.variants().get("default");
            var base = texture(variant.base());
            var overlay = variant.overlay().isPresent() ? texture(variant.overlay().get()) : null;
            var masks = part.designsFor("default").get("plain");
            var accent = masks != null && masks.accent().isPresent() ? texture(masks.accent().get()) : null;
            for (var element : part.elements()) for (var face : element.faces().entrySet()) {
                var vertices = GarmentElementGeometry.faceVertices(element, face.getKey());
                double[][] p = new double[4][];
                for (int i = 0; i < 4; i++) p[i] = project(FlatGarmentGeometry.rotate(vertices.get(i), element.rotation()), yaw, cx, cy);
                var uv = face.getValue().corners();
                for (int[] triangle : List.of(new int[]{0, 1, 2}, new int[]{0, 2, 3})) {
                    var a = p[triangle[0]]; var b = p[triangle[1]]; var c = p[triangle[2]];
                    double area = edge(a, b, c[0], c[1]);
                    if (Math.abs(area) < .0001) continue;
                    int minX = Math.max(cx - 150, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
                    int maxX = Math.min(cx + 150, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
                    int minY = Math.max(cy - 150, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
                    int maxY = Math.min(cy + 130, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
                    for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
                        double wa = edge(b, c, x + .5, y + .5) / area;
                        double wb = edge(c, a, x + .5, y + .5) / area;
                        double wc = 1 - wa - wb;
                        if (Math.min(wa, Math.min(wb, wc)) < -1e-7) continue;
                        double z = wa * a[2] + wb * b[2] + wc * c[2];
                        int index = y * image.getWidth() + x;
                        if (z <= depth[index] + 0.00001) continue;
                        int u = Math.min(base.getWidth() - 1, (int) (wa * uv.get(triangle[0]).u() + wb * uv.get(triangle[1]).u() + wc * uv.get(triangle[2]).u()));
                        int v = Math.min(base.getHeight() - 1, (int) (wa * uv.get(triangle[0]).v() + wb * uv.get(triangle[1]).v() + wc * uv.get(triangle[2]).v()));
                        int pixel = base.getRGB(u, v);
                        int shade = pixel & 255;
                        int color = accent != null && (accent.getRGB(u, v) & 255) != 0
                                ? ((shade * 9 / 10) << 16 | (shade * 6 / 10) << 8 | shade * 3 / 10)
                                : ((shade * 4 / 10) << 16 | (shade * 6 / 10) << 8 | shade * 7 / 10);
                        color = GarmentTexturePixelOps.applyOverlay(pixel & 0xff000000 | color,
                                overlay == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(overlay.getRGB(u, v)));
                        if (color >>> 24 == 0) continue;
                        image.setRGB(x, y, color);
                        depth[index] = z;
                    }
                }
            }
        }
    }

    private static double edge(double[] a, double[] b, double x, double y) {
        return (b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]);
    }

    private static double roughness(BufferedImage image, int x, int y, int width, int height) {
        double sum = 0;
        int count = 0;
        for (int v = y; v < y + height; v++) for (int u = x; u < x + width; u++) {
            int shade = image.getRGB(u, v) & 255;
            if (u + 1 < x + width) { sum += Math.abs(shade - (image.getRGB(u + 1, v) & 255)); count++; }
            if (v + 1 < y + height) { sum += Math.abs(shade - (image.getRGB(u, v + 1) & 255)); count++; }
        }
        return sum / count;
    }

    private static double[] project(Vec3 p, double yaw, int cx, int cy) {
        double angle = Math.toRadians(yaw), pitch = Math.toRadians(18);
        double x = p.x() * Math.cos(angle) + p.z() * Math.sin(angle);
        double z = -p.x() * Math.sin(angle) + p.z() * Math.cos(angle);
        double y = p.y() - 17.5;
        return new double[]{cx + x * 15, cy - (y * Math.cos(pitch) - z * Math.sin(pitch)) * 15,
                -y * Math.sin(pitch) - z * Math.cos(pitch)};
    }

    private static Element element(GarmentPartModel part, String name) {
        return part.elements().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    private static BufferedImage texture(ResourceLocation id) throws Exception {
        return ImageIO.read(ASSETS.resolve("textures/" + id.getPath() + ".png").toFile());
    }

    private static GarmentPartModel part(String name) throws Exception {
        return ClientGarmentAssetLoader.parsePart(resource(name), json(ASSETS.resolve("garment_models/parts/" + name + ".json")));
    }

    private static ResourceLocation resource(String name) {
        return ResourceLocation.fromNamespaceAndPath("createapparel", name);
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
