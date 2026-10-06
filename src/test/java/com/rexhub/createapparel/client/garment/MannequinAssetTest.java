package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.model.MannequinModel;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;
import com.rexhub.createapparel.client.renderer.CommonGarmentRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MannequinAssetTest {
    private static final Path GENERATED = Path.of("src/generated/resources");

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static ModelPart.Cube firstCube(ModelPart part) {
        List<ModelPart.Cube> cubes = new ArrayList<>();
        part.visit(new PoseStack(), (pose, path, index, cube) -> cubes.add(cube));
        return cubes.get(0);
    }

    @Test
    void bodyScaleMatchesPlayerWhilePedestalsStayGroundedAndHeadStaysOneBlock() {
        assertEquals(0.9375F, MannequinForm.BODY_SCALE);
        assertEquals(1.875F, 32 * MannequinForm.BODY_SCALE / 16);
        for (var variant : MannequinVariant.values()) {
            var form = variant.form();
            var root = MannequinModel.createBodyLayer(variant.armProfile() == ArmProfile.SLIM).bakeRoot();
            var model = new MannequinModel(root);
            model.apply(form, form.defaultPose());
            var bodyPose = new PoseStack();
            bodyPose.translate(0, form.bodyOriginHeight(), 0);
            bodyPose.scale(-MannequinForm.BODY_SCALE, -MannequinForm.BODY_SCALE, MannequinForm.BODY_SCALE);
            List<Vector3f> body = new ArrayList<>();
            model.renderToBuffer(bodyPose, recordingConsumer(body), 0, 0, 1, 1, 1, 1);
            assertEquals(form.displayHeight(), body.stream().mapToDouble(point -> point.y).max().orElseThrow(), 0.00001);
            if (form.hasLegs()) {
                assertEquals(MannequinForm.PEDESTAL_HEIGHT,
                        body.stream().mapToDouble(point -> point.y).min().orElseThrow(), 0.00001);
            }
            var supportPose = new PoseStack();
            supportPose.translate(0, form.modelOriginPixels() / 16F, 0);
            supportPose.scale(-1, -1, 1);
            List<Vector3f> pedestal = new ArrayList<>();
            root.getChild("pedestal").render(supportPose, recordingConsumer(pedestal), 0, 0);
            assertEquals(0, pedestal.stream().mapToDouble(point -> point.y).min().orElseThrow(), 0.00001);
            assertEquals(MannequinForm.PEDESTAL_HEIGHT,
                    pedestal.stream().mapToDouble(point -> point.y).max().orElseThrow(), 0.00001);
            if (form == MannequinForm.HEAD || form == MannequinForm.UPPER) {
                List<Vector3f> support = new ArrayList<>();
                root.getChild(form == MannequinForm.HEAD ? "head_support" : "upper_support")
                        .render(supportPose, recordingConsumer(support), 0, 0);
                double top = support.stream().mapToDouble(point -> point.y).max().orElseThrow();
                float bodyBottom = form.bodyOriginHeight() - (form == MannequinForm.HEAD ? 2 : 12)
                        * MannequinForm.BODY_SCALE / 16;
                assertEquals(bodyBottom, top, 0.00001, "Support connects to the scaled body");
            }
        }
        assertEquals(1, MannequinForm.HEAD.displayHeight());
        assertEquals(1.9375F, MannequinForm.FULL.displayHeight());
        assertEquals(1.05859375F, MannequinForm.LOWER.displayHeight());
    }

    @Test
    void scaledWorldTransformsMatchNativeRenderingAndInversePickingAcrossPosesAndRotations() {
        for (var variant : MannequinVariant.values()) {
            var form = variant.form();
            var model = new MannequinModel(MannequinModel.createBodyLayer(variant.armProfile() == ArmProfile.SLIM).bakeRoot());
            for (var preset : form.poses()) {
                model.apply(form, preset);
                for (float yaw : new float[]{0, 45, 135, -170}) for (int step : new int[]{0, 1, 7, 15}) {
                    for (var part : MannequinInteraction.BodyPart.values()) {
                        ModelPart rendered = switch (part) {
                            case HEAD -> model.head;
                            case BODY -> model.body;
                            case LEFT_ARM -> model.leftArm;
                            case RIGHT_ARM -> model.rightArm;
                            case LEFT_LEG -> model.leftLeg;
                            case RIGHT_LEG -> model.rightLeg;
                        };
                        if (!rendered.visible) continue;
                        var pose = new PoseStack();
                        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180 - yaw));
                        pose.translate(0, form.bodyOriginHeight(), 0);
                        pose.scale(-MannequinForm.BODY_SCALE, -MannequinForm.BODY_SCALE, MannequinForm.BODY_SCALE);
                        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(step * 22.5F));
                        rendered.translateAndRotate(pose);
                        var actual = pose.last().pose().transformPosition(new Vector3f(1.2F, 3.4F, -2.1F).div(16));
                        var local = MannequinInteraction.partTransform(variant, preset, part)
                                .transformPosition(new Vector3f(1.2F, 3.4F, -2.1F));
                        var expected = form.modelToEntity(new net.minecraft.world.phys.Vec3(local.x, local.y, local.z), yaw + step * 22.5F);
                        assertEquals(expected.x, actual.x, 0.00001, variant + "/" + preset + "/" + yaw + "/" + step + "/" + part);
                        assertEquals(expected.y, actual.y, 0.00001);
                        assertEquals(expected.z, actual.z, 0.00001);
                        var restored = form.entityToModel(new net.minecraft.world.phys.Vec3(actual.x, actual.y, actual.z), yaw + step * 22.5F);
                        assertEquals(local.x, restored.x, 0.00003);
                        assertEquals(local.y, restored.y, 0.00003);
                        assertEquals(local.z, restored.z, 0.00003);
                    }
                }
            }
        }
    }

    @Test
    void serverHitTransformsMatchRenderedPartsForEveryProfileFormAndPreset() {
        for (var variant : MannequinVariant.values()) {
            var model = new MannequinModel(MannequinModel.createBodyLayer(variant.armProfile() == ArmProfile.SLIM).bakeRoot());
            for (var pose : variant.form().poses()) {
                model.apply(variant.form(), pose);
                for (var part : MannequinInteraction.BodyPart.values()) {
                    ModelPart rendered = switch (part) {
                        case HEAD -> model.head;
                        case BODY -> model.body;
                        case LEFT_ARM -> model.leftArm;
                        case RIGHT_ARM -> model.rightArm;
                        case LEFT_LEG -> model.leftLeg;
                        case RIGHT_LEG -> model.rightLeg;
                    };
                    if (!rendered.visible) continue;
                    var stack = new PoseStack();
                    rendered.translateAndRotate(stack);
                    var renderedPoint = stack.last().pose().transformPosition(new Vector3f(1.2F, 3.4F, -2.1F).div(16)).mul(16);
                    var hitPoint = MannequinInteraction.partTransform(variant, pose, part)
                            .transformPosition(new Vector3f(1.2F, 3.4F, -2.1F));
                    assertEquals(renderedPoint.x, hitPoint.x, 0.00001, variant + "/" + pose + "/" + part);
                    assertEquals(renderedPoint.y, hitPoint.y, 0.00001);
                    assertEquals(renderedPoint.z, hitPoint.z, 0.00001);
                }
            }
        }
    }

    @Test
    void pedestalAndSupportGeometryAreRenderedSeparatelyFromBodyRotation() {
        for (var form : MannequinForm.values()) {
            var model = new MannequinModel(MannequinModel.createBodyLayer(false).bakeRoot());
            model.apply(form, form.defaultPose());
            List<Vector3f> base = new ArrayList<>();
            List<Vector3f> body = new ArrayList<>();
            var baseConsumer = recordingConsumer(base);
            var bodyConsumer = recordingConsumer(body);
            var basePose = new PoseStack();
            model.renderSupport(basePose, baseConsumer, 0, 0);
            for (int step = 0; step < 16; step++) {
                List<Vector3f> repeatedBase = new ArrayList<>();
                model.renderSupport(basePose, recordingConsumer(repeatedBase), 0, 0);
                assertEquals(base, repeatedBase);
                var bodyPose = new PoseStack();
                bodyPose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(step * 22.5F));
                body.clear();
                model.renderToBuffer(bodyPose, bodyConsumer, 0, 0, 1, 1, 1, 1);
                assertFalse(body.isEmpty());
                assertTrue(body.stream().noneMatch(vertex -> vertex.y >= (form.modelOriginPixels() - 1) / 16F + 0.0001F),
                        "Rotating body excludes the pedestal bottom");
            }
            assertFalse(base.isEmpty());
        }
    }

    private static com.mojang.blaze3d.vertex.VertexConsumer recordingConsumer(List<Vector3f> positions) {
        return (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                MannequinAssetTest.class.getClassLoader(), new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("vertex")) {
                        positions.add(new Vector3f(((Number) args[0]).floatValue(),
                                ((Number) args[1]).floatValue(), ((Number) args[2]).floatValue()));
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
    }

    @Test
    void twelveIdentitiesHaveFixedProfilesAndPreciseFormSlots() {
        assertEquals(12, MannequinVariant.values().length);
        assertEquals(12, Arrays.stream(MannequinVariant.values()).map(MannequinVariant::id).distinct().count());
        assertEquals(EnumSet.allOf(GarmentSlot.class), MannequinForm.FULL.slots());
        assertEquals(Set.of(GarmentSlot.INNER_TOP, GarmentSlot.TOP, GarmentSlot.OUTER,
                GarmentSlot.HEAD, GarmentSlot.FACE, GarmentSlot.BACK, GarmentSlot.HANDS, GarmentSlot.NECK), MannequinForm.UPPER.slots());
        assertEquals(Set.of(GarmentSlot.INNER_BOTTOM, GarmentSlot.BOTTOM, GarmentSlot.LEGWEAR, GarmentSlot.FEET), MannequinForm.LOWER.slots());
        assertEquals(Set.of(GarmentSlot.HEAD, GarmentSlot.FACE), MannequinForm.HEAD.slots());
        for (var variant : MannequinVariant.values()) {
            boolean slim = variant.id().endsWith("_slim");
            assertEquals(slim ? ArmProfile.SLIM : ArmProfile.CLASSIC, variant.armProfile());
            assertEquals(slim ? ChestProfile.BUST : ChestProfile.FLAT, variant.chestProfile());
        }
    }

    @Test
    void recipesUseArmorStandPlanksAndMatchingDyeAndSlimIsConversionOnly() throws Exception {
        var recipes = GENERATED.resolve("data/createapparel/recipes");
        for (var variant : MannequinVariant.values()) {
            var direct = recipes.resolve(variant.id() + ".json");
            if (variant.armProfile() == ArmProfile.SLIM) {
                assertFalse(Files.exists(direct));
            } else {
                var recipe = json(direct);
                assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
                assertEquals("createapparel:" + variant.id(), recipe.getAsJsonObject("result").get("item").getAsString());
                var key = recipe.getAsJsonObject("key");
                assertEquals("minecraft:armor_stand", key.getAsJsonObject("A").get("item").getAsString());
                assertEquals("minecraft:planks", key.getAsJsonObject("P").get("tag").getAsString());
                assertEquals("minecraft:" + variant.color().id() + "_dye", key.getAsJsonObject("D").get("item").getAsString());
                String pattern = recipe.getAsJsonArray("pattern").asList().stream().map(v -> v.getAsString()).reduce("", String::concat);
                assertEquals(1, pattern.chars().filter(c -> c == 'A').count());
                assertEquals(1, pattern.chars().filter(c -> c == 'D').count());
                assertEquals(switch (variant.form()) { case FULL -> 6; case UPPER, LOWER -> 4; case HEAD -> 2; },
                        pattern.chars().filter(c -> c == 'P').count());
            }
            if (variant.form().hasTorso()) {
                var other = Arrays.stream(MannequinVariant.values()).filter(v -> v.form() == variant.form()
                        && v.color() == variant.color() && v.armProfile() != variant.armProfile()).findFirst().orElseThrow();
                var recipe = json(recipes.resolve(other.id() + "_from_" + variant.id() + ".json"));
                assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
                assertEquals(1, recipe.getAsJsonArray("ingredients").size());
                assertEquals("createapparel:" + variant.id(), recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
                assertEquals("createapparel:" + other.id(), recipe.getAsJsonObject("result").get("item").getAsString());
            }
        }
    }

    @Test
    void dedicatedBodyTexturesModelsAndTranslationsCoverEveryVariant() throws Exception {
        var en = json(GENERATED.resolve("assets/createapparel/lang/en_us.json"));
        var ja = json(GENERATED.resolve("assets/createapparel/lang/ja_jp.json"));
        for (var variant : MannequinVariant.values()) {
            var model = json(GENERATED.resolve("assets/createapparel/models/item/" + variant.id() + ".json"));
            assertEquals("createapparel:item/mannequin/" + variant.color().id(),
                    model.getAsJsonObject("textures").get("body").getAsString());
            assertArrayEquals(Files.readAllBytes(GENERATED.resolve("assets/createapparel/textures/entity/mannequin/" + variant.color().id() + ".png")),
                    Files.readAllBytes(GENERATED.resolve("assets/createapparel/textures/item/mannequin/" + variant.color().id() + ".png")));
            assertTrue(en.has("item.createapparel." + variant.id()) && ja.has("item.createapparel." + variant.id()));
            assertTrue(en.has("entity.createapparel." + variant.id()) && ja.has("entity.createapparel." + variant.id()));
        }
        var white = ImageIO.read(GENERATED.resolve("assets/createapparel/textures/entity/mannequin/white.png").toFile());
        var black = ImageIO.read(GENERATED.resolve("assets/createapparel/textures/entity/mannequin/black.png").toFile());
        assertEquals(64, white.getWidth());
        assertEquals(64, black.getHeight());
        assertEquals(0xFFF0EEE7, white.getRGB(32, 32));
        assertEquals(0xFF303237, black.getRGB(32, 32));
        for (var pose : PosePreset.values()) {
            assertTrue(en.has("pose.createapparel." + pose.id()) && ja.has("pose.createapparel." + pose.id()));
        }
    }

    @Test
    void bodyGeometryProfilesAndPresetVisibilityAreIndependentOfPlayerSkins() {
        var classicRoot = MannequinModel.createBodyLayer(false).bakeRoot();
        var slimRoot = MannequinModel.createBodyLayer(true).bakeRoot();
        assertFalse(classicRoot.getChild("body").hasChild("bust"));
        assertTrue(slimRoot.getChild("body").hasChild("bust"));
        assertEquals(2.0F, classicRoot.getChild("left_arm").y);
        assertEquals(2.5F, slimRoot.getChild("left_arm").y);
        for (var root : List.of(classicRoot, slimRoot)) {
            var model = new MannequinModel(root);
            for (var form : MannequinForm.values()) for (var pose : form.poses()) {
                model.apply(form, pose);
                assertEquals(form.hasHead(), model.head.visible);
                assertEquals(form.hasTorso(), model.body.visible);
                assertEquals(form.hasTorso(), model.leftArm.visible);
                assertEquals(form.hasLegs(), model.leftLeg.visible);
                assertFalse(model.hat.visible);
                assertEquals(form.hasLegs() ? Math.toRadians(pose.rotations().leftLeg().x()) : 0, model.leftLeg.xRot, 0.000001);
                assertEquals(form == MannequinForm.UPPER, root.getChild("upper_support").visible);
                assertEquals(form == MannequinForm.HEAD, root.getChild("head_support").visible);
                assertEquals(form == MannequinForm.LOWER, root.getChild("lower_body").visible);
                assertEquals(form == MannequinForm.HEAD, root.getChild("head_body").visible);
                assertEquals(form.modelOriginPixels() - 25, root.getChild("pedestal").y);
            }
        }
    }

    @Test
    void partialBodiesAndHeadSizeMatchEntityAndItemGeometry() throws Exception {
        var root = MannequinModel.createBodyLayer(false).bakeRoot();
        var lowerBody = firstCube(root.getChild("lower_body"));
        assertEquals(5, lowerBody.maxY - lowerBody.minY);
        var headBody = firstCube(root.getChild("head_body"));
        assertEquals(2, headBody.maxY - headBody.minY);
        assertEquals(1, MannequinForm.HEAD.height());
        assertEquals(18.0F / 16, MannequinForm.LOWER.height());
        var model = new MannequinModel(root);
        model.apply(MannequinForm.HEAD, PosePreset.FRONT);
        assertEquals(1, MannequinForm.HEAD.bodyOriginHeight()
                - firstCube(model.head).minY * MannequinForm.BODY_SCALE / 16);
        assertEquals(0, MannequinForm.HEAD.modelOriginPixels()
                - root.getChild("pedestal").y - firstCube(root.getChild("pedestal")).maxY);
        for (var variant : MannequinVariant.values()) {
            var elements = json(GENERATED.resolve("assets/createapparel/models/item/" + variant.id() + ".json"))
                    .getAsJsonArray("elements").asList();
            double bottom = elements.stream().mapToDouble(element -> element.getAsJsonObject().getAsJsonArray("from").get(1).getAsDouble()).min().orElseThrow();
            double top = elements.stream().mapToDouble(element -> element.getAsJsonObject().getAsJsonArray("to").get(1).getAsDouble()).max().orElseThrow();
            assertEquals(variant.form().displayHeight() * 16, top - bottom);
            var pedestal = elements.get(0).getAsJsonObject();
            assertEquals(12, pedestal.getAsJsonArray("to").get(0).getAsFloat() - pedestal.getAsJsonArray("from").get(0).getAsFloat());
            assertEquals(1, pedestal.getAsJsonArray("to").get(1).getAsFloat() - pedestal.getAsJsonArray("from").get(1).getAsFloat());
            if (variant.form().hasHead()) {
                var head = elements.get(1).getAsJsonObject();
                assertEquals(8 * MannequinForm.BODY_SCALE,
                        head.getAsJsonArray("to").get(0).getAsFloat() - head.getAsJsonArray("from").get(0).getAsFloat());
            }
        }
    }

    @Test
    void mannequinThighsFitPantsInEveryLegPoseWithoutChangingGarmentPivots() throws Exception {
        var garmentPoseMethod = CommonGarmentRenderer.class.getDeclaredMethod("humanoidGarmentPose",
                HumanoidModel.class, ArmProfile.class, ClientGarmentAssets.ResolvedPart.class);
        garmentPoseMethod.setAccessible(true);
        for (boolean slim : List.of(false, true)) {
            var model = new MannequinModel(MannequinModel.createBodyLayer(slim).bakeRoot());
            assertEquals(1.9F, model.leftLeg.x);
            assertEquals(-1.9F, model.rightLeg.x);
            for (var form : List.of(MannequinForm.FULL, MannequinForm.LOWER)) for (var preset : form.poses()) {
                model.apply(form, preset);
                for (boolean left : List.of(false, true)) {
                    ModelPart leg = left ? model.leftLeg : model.rightLeg;
                    ModelPart.Cube cube = firstCube(leg);
                    float referenceX = left ? 1.9F : -1.9F;
                    assertEquals(left ? 0 : -4, cube.minX + referenceX, 0.000001);
                    assertEquals(left ? 4 : 0, cube.maxX + referenceX, 0.000001);
                    String name = left ? "left_leg" : "right_leg";
                    for (String garment : List.of("denim_pants", "slacks", "shorts", "tights")) {
                        var part = DualEnvelopeDeformerTest.part(garment + "/" + name);
                        var bounds = part.elements().get(0);
                        Object garmentPose = garmentPoseMethod.invoke(null, model,
                                slim ? ArmProfile.SLIM : ArmProfile.CLASSIC,
                                new ClientGarmentAssets.ResolvedPart(name, part, null));
                        var apply = garmentPose.getClass().getDeclaredMethod("apply", PoseStack.class);
                        apply.setAccessible(true);
                        PoseStack clothingStack = new PoseStack();
                        clothingStack.scale(1.0F / 16, 1.0F / 16, 1.0F / 16);
                        apply.invoke(garmentPose, clothingStack);
                        Matrix4f toGarment = clothingStack.last().pose().invert(new Matrix4f());
                        PoseStack bodyStack = new PoseStack();
                        leg.translateAndRotate(bodyStack);
                        for (float x : List.of(cube.minX, cube.maxX)) for (float z : List.of(cube.minZ, cube.maxZ)) {
                            var point = new Vector3f(x / 16, cube.minY / 16, z / 16)
                                    .mulPosition(bodyStack.last().pose()).mulPosition(toGarment);
                            String label = garment + " " + form + " " + preset + " " + name;
                            assertTrue(point.x >= bounds.from().x() - 0.000001
                                    && point.x <= bounds.to().x() + 0.000001, label + " thigh width");
                            assertTrue(point.z >= bounds.from().z() - 0.000001
                                    && point.z <= bounds.to().z() + 0.000001, label + " thigh depth");
                        }
                    }
                }
            }
        }
    }

    @Test
    void itemLegsMatchBodyBaseBoundsWithoutInnerOverlap() throws Exception {
        for (var variant : MannequinVariant.values()) {
            if (!variant.form().hasLegs()) continue;
            var legs = json(GENERATED.resolve("assets/createapparel/models/item/" + variant.id() + ".json"))
                    .getAsJsonArray("elements").asList().stream().map(value -> value.getAsJsonObject())
                    .filter(element -> element.getAsJsonArray("from").get(1).getAsFloat() == 0
                            && element.getAsJsonArray("to").get(1).getAsFloat() == 12 * MannequinForm.BODY_SCALE)
                    .sorted(Comparator.comparingDouble(element -> element.getAsJsonArray("from").get(0).getAsDouble()))
                    .toList();
            assertEquals(2, legs.size(), variant.id());
            assertEquals(8 - 4 * MannequinForm.BODY_SCALE, legs.get(0).getAsJsonArray("from").get(0).getAsFloat());
            assertEquals(8, legs.get(0).getAsJsonArray("to").get(0).getAsFloat());
            assertEquals(8, legs.get(1).getAsJsonArray("from").get(0).getAsFloat());
            assertEquals(8 + 4 * MannequinForm.BODY_SCALE, legs.get(1).getAsJsonArray("to").get(0).getAsFloat());
        }
    }

    @Test
    void skirtDressAndCoatUseTheSameEnvelopeForHumanoidAndPlayerPoses() throws Exception {
        var definition = GarmentDefinition.fromJson(ResourceLocation.parse("createapparel:a_line_dress_midi"),
                json(Path.of("src/main/resources/data/createapparel/garments/a_line_dress_midi.json")));
        var dress = DualEnvelopeDeformerTest.part("a_line_dress/midi/torso");
        var skirt = DualEnvelopeDeformerTest.part("flared_skirt_maxi/torso");
        var coat = DualEnvelopeDeformerTest.part("coat/chesterfield/torso");
        for (var preset : MannequinForm.FULL.poses()) {
            var model = new MannequinModel(MannequinModel.createBodyLayer(false).bakeRoot());
            model.apply(MannequinForm.FULL, preset);
            var player = new net.minecraft.client.model.PlayerModel<Mannequin>(
                    net.minecraft.client.model.PlayerModel.createMesh(
                            net.minecraft.client.model.geom.builders.CubeDeformation.NONE, false).getRoot().bake(64, 64), false);
            model.copyPropertiesTo(player);
            java.util.function.Function<GarmentSlot, Optional<ClientGarmentAssets.ResolvedGarment>> resolver = slot -> {
                var part = switch (slot) { case TOP -> dress; case BOTTOM -> skirt; case OUTER -> coat; default -> null; };
                return part == null ? Optional.empty() : Optional.of(new ClientGarmentAssets.ResolvedGarment(
                        definition, null, List.of(new ClientGarmentAssets.ResolvedPart("torso", part, null))));
            };
            var targetFrame = new ApparelRenderFrame(resolver, model);
            var playerFrame = new ApparelRenderFrame(resolver, player);
            for (var part : List.of(dress, skirt, coat)) {
                var targetPose = targetFrame.envelope(part).orElseThrow();
                var playerPose = playerFrame.envelope(part).orElseThrow();
                for (var quad : targetPose.quads()) for (var point : quad.points()) {
                    var targetPoint = new Vector3f();
                    var playerPoint = new Vector3f();
                    targetPose.shift(point, targetPoint);
                    playerPose.shift(point, playerPoint);
                    assertEquals(playerPoint, targetPoint, preset.name());
                    assertTrue(Float.isFinite(targetPoint.x) && Float.isFinite(targetPoint.y) && Float.isFinite(targetPoint.z));
                }
            }
        }
    }
}
