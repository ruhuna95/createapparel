package com.rexhub.createapparel.citizen.population;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rexhub.createapparel.apparel.*;
import com.rexhub.createapparel.client.garment.*;
import com.rexhub.createapparel.client.renderer.CommonGarmentRenderer;
import com.rexhub.createapparel.garment.*;
import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Matrix4f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

final class EnvelopeRenderClientChecks {
    private EnvelopeRenderClientChecks() { }

    static void run() throws Exception {
        var mc = Minecraft.getInstance();
        var output = Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build", "envelope-images");
        Files.createDirectories(output);
        var target = new TextureTarget(256, 320, true, Minecraft.ON_OSX);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        int scenes = 0;
        try {
            modelView.identity();
            modelView.translate(0, 0, -2000);
            RenderSystem.applyModelViewMatrix();
            mc.gameRenderer.resetProjectionMatrix(new Matrix4f().setOrtho(0, 256, 320, 0, 1000, 3000));
            for (var arm : ArmProfile.values()) for (var chest : ChestProfile.values()) {
                for (int pose = 0; pose < 6; pose++) for (int outfit = 0; outfit < 7; outfit++) {
                    var model = new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE, arm == ArmProfile.SLIM).getRoot().bake(64, 64), arm == ArmProfile.SLIM);
                    model.leftLeg.xRot = new float[]{0, -.7F, -1.4F, .9F, -.6F, -1.57F}[pose];
                    model.rightLeg.xRot = new float[]{0, .7F, 1.4F, -.3F, .4F, -1.57F}[pose];
                    model.leftArm.xRot = -model.leftLeg.xRot;
                    model.rightArm.xRot = -model.rightLeg.xRot;
                    if (pose == 3) { model.leftArm.zRot = -.9F; model.rightArm.yRot = .5F; }
                    if (pose == 4) { model.crouching = true; model.body.xRot = .5F; model.leftLeg.z = model.rightLeg.z = 4; }
                    var source = new LoadoutRenderSnapshot(outfit(outfit), arm, chest);
                    String name = arm + "-" + chest + "-pose" + pose + "-outfit" + outfit;
                    var vertices = draw(target, model, source, true);
                    try (var image = Screenshot.takeScreenshot(target)) {
                        int foreground = 0, background = image.getPixelRGBA(0, 0);
                        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                            if (image.getPixelRGBA(x, y) != background) foreground++;
                        if (foreground < 100 || vertices.isEmpty()) throw new AssertionError("Empty garment rendering: " + name);
                        RenderReferenceChecks.check("envelope-" + name, image, vertices);
                        image.writeToFile(output.resolve(name + ".png"));
                        scenes++;
                    }
                }
            }
            checkEntities(target);
            checkCurrentAnimation(target);
            checkTranslucentSupport(target);
            com.mojang.logging.LogUtils.getLogger().info("ENVELOPE_RENDER_QUALITY_PASSED scenes={} vertexAttributesRecorded=true resolution=256x320 profiles=4 poses=6 outfits=7", scenes);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            mc.gameRenderer.resetProjectionMatrix(projection);
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void checkEntities(TextureTarget target) throws Exception {
        var mc = Minecraft.getInstance();
        var entity = Objects.requireNonNull(com.rexhub.createapparel.init.ModEntities.CITIZEN.get().create(mc.level));
        entity.setId(900001);
        var renderer = (com.rexhub.createapparel.client.renderer.CitizenRenderer) mc.getEntityRenderDispatcher().getRenderer(entity);
        long time = mc.level.getGameTime();
        try {
            mc.level.setGameTime(1000);
            for (var age : com.rexhub.createapparel.entity.CitizenAge.values())
                for (var arm : ArmProfile.values()) for (var chest : ChestProfile.values())
                    for (int outfit = 0; outfit < 7; outfit++) for (var preset : com.rexhub.createapparel.entity.MannequinForm.FULL.poses()) {
                        var base = com.rexhub.createapparel.entity.CitizenAppearance.DEFAULT;
                        entity.citizenState().setAppearance(new com.rexhub.createapparel.entity.CitizenAppearance(
                                SkinTone.values()[outfit % SkinTone.values().length], arm, chest, base.hairStyleId(), base.hairColorRgb(), base.eyeColorRgb()));
                        entity.citizenState().setAge(age);
                        entity.citizenState().loadout().deserializeNBT(com.rexhub.createapparel.TestRegistries.lookup(), outfit(outfit).serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()));
                        entity.citizenState().setBehavior(com.rexhub.createapparel.entity.CitizenBehaviorState.posed(preset));
                        for (int lod = 3; lod >= 0; lod--) {
                            var camera = mc.gameRenderer.getMainCamera().getPosition();
                            entity.setPos(camera.x, camera.y, camera.z + new int[]{4, 20, 45, 80}[lod]);
                            captureEntity(target, entity, renderer, "citizen-" + age + "-" + arm + "-" + chest + "-" + outfit + "-" + preset + "-" + lod);
                        }
                    }
            for (var entry : com.rexhub.createapparel.init.ModEntities.mannequins().entrySet()) {
                var mannequin = Objects.requireNonNull(entry.getValue().get().create(mc.level));
                var mannequinRenderer = mc.getEntityRenderDispatcher().getRenderer(mannequin);
                for (int outfit = 0; outfit < 7; outfit++) {
                    mannequin.mannequinState().loadout().deserializeNBT(com.rexhub.createapparel.TestRegistries.lookup(), outfit(outfit).serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()));
                    captureEntity(target, mannequin, mannequinRenderer, "mannequin-" + entry.getKey() + "-" + outfit);
                }
            }
        } finally {
            renderer.forget(entity);
            mc.level.setGameTime(time);
        }
    }

    private static <T extends net.minecraft.world.entity.Entity> void captureEntity(TextureTarget target, T entity,
            net.minecraft.client.renderer.entity.EntityRenderer<T> renderer, String scene) throws Exception {
        target.setClearColor(.12F, .15F, .18F, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
        var pose = new PoseStack(); pose.translate(128, 280, 0); pose.scale(110, -110, 110);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var recording = new RecordingBuffers(buffers);
        renderer.render(entity, 0, .6F, pose, recording, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        try (var image = Screenshot.takeScreenshot(target)) {
            if (recording.values.isEmpty()) throw new AssertionError("Entity submitted no vertices: " + scene);
            RenderReferenceChecks.check(scene, image, recording.values);
        }
    }

    private static void checkCurrentAnimation(TextureTarget target) throws Exception {
        var mc = Minecraft.getInstance();
        var entity = Objects.requireNonNull(com.rexhub.createapparel.init.ModEntities.CITIZEN.get().create(mc.level));
        entity.setId(900002);
        entity.citizenState().loadout().deserializeNBT(com.rexhub.createapparel.TestRegistries.lookup(), outfit(0).serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()));
        entity.citizenState().setBehavior(com.rexhub.createapparel.entity.CitizenBehaviorState.anchored(
                com.rexhub.createapparel.entity.CitizenBehaviorMode.WANDER, net.minecraft.core.BlockPos.ZERO, mc.level.dimension(), 0));
        var renderer = (com.rexhub.createapparel.client.renderer.CitizenRenderer) mc.getEntityRenderDispatcher().getRenderer(entity);
        var modelField = renderer.getClass().getDeclaredField("model"); modelField.setAccessible(true);
        var actual = (com.rexhub.createapparel.client.model.CitizenModel) modelField.get(renderer);
        var expected = new com.rexhub.createapparel.client.model.CitizenModel(com.rexhub.createapparel.client.model.CitizenModel.createBodyLayer().bakeRoot());
        long time = mc.level.getGameTime();
        try {
            var camera = mc.gameRenderer.getMainCamera().getPosition();
            for (var age : com.rexhub.createapparel.entity.CitizenAge.values()) {
                entity.citizenState().setAge(age);
                for (int lod = 0; lod < 4; lod++) for (int frame = 0; frame < 60; frame++) {
                    entity.setPos(camera.x, camera.y, camera.z + new int[]{4, 20, 45, 80}[lod]);
                    entity.tickCount = frame / 3;
                    mc.level.setGameTime(1000 + frame / 3);
                    entity.walkAnimation.update(.5F, 1);
                    target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                    var pose = new PoseStack(); pose.translate(128, 280, 0); pose.scale(110, -110, 110);
                    var buffers = mc.renderBuffers().bufferSource();
                    renderer.render(entity, 0, frame % 3 / 3F, pose, buffers, LightTexture.FULL_BRIGHT); buffers.endBatch();
                    expected.apply(entity, frame % 3 / 3F);
                    var a = new net.minecraft.client.model.geom.ModelPart[]{actual.head, actual.hat, actual.body, actual.leftArm, actual.rightArm, actual.leftLeg, actual.rightLeg};
                    var b = new net.minecraft.client.model.geom.ModelPart[]{expected.head, expected.hat, expected.body, expected.leftArm, expected.rightArm, expected.leftLeg, expected.rightLeg};
                    for (int i = 0; i < a.length; i++)
                        if (a[i].x != b[i].x || a[i].y != b[i].y || a[i].z != b[i].z
                                || a[i].xRot != b[i].xRot || a[i].yRot != b[i].yRot || a[i].zRot != b[i].zRot
                                || a[i].xScale != b[i].xScale || a[i].yScale != b[i].yScale || a[i].zScale != b[i].zScale)
                            throw new AssertionError("Renderer did not apply the current animation pose at LOD " + lod);
                }
            }
            com.mojang.logging.LogUtils.getLogger().info("CITIZEN_CURRENT_ANIMATION_PASSED frames=480 allLods=true adultAndChild=true everyFramePose=true");
        } finally { renderer.forget(entity); mc.level.setGameTime(time); }
    }

    private static void checkTranslucentSupport(TextureTarget target) throws Exception {
        var mc = Minecraft.getInstance();
        var part = ClientGarmentAssets.INSTANCE.resolveBody(ArmProfile.CLASSIC, ChestProfile.FLAT).orElseThrow().stream()
                .filter(candidate -> candidate.semanticName().equals("torso")).findFirst().orElseThrow();
        var base = part.model();
        var id = ResourceLocation.fromNamespaceAndPath("createapparel", "test/alpha_support");
        var image = new com.mojang.blaze3d.platform.NativeImage(1, 1, false);
        image.setPixelRGBA(0, 0, 0x800000FF);
        var texture = new net.minecraft.client.renderer.texture.DynamicTexture(image);
        mc.getTextureManager().register(id, texture);
        com.mojang.blaze3d.platform.NativeImage opaque = null;
        try {
            for (boolean translucent : new boolean[]{false, true}) {
                var model = new ClientGarmentAssets.GarmentPartModel(base.id(), translucent, base.textureWidth(), base.textureHeight(),
                        base.pivot(), base.elements(), base.variants(), base.designs(), base.materialOverrides(), base.legEnvelope(), base.hemGeometry());
                var support = new ClientGarmentAssets.ResolvedSupportPart(part.semanticName(), model, id, id);
                target.setClearColor(.12F, .15F, .18F, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                var pose = new PoseStack(); pose.translate(128, 160, 0); pose.scale(-140, 140, 140);
                var buffers = mc.renderBuffers().bufferSource();
                var recording = new RecordingBuffers(buffers);
                var player = new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE, false).getRoot().bake(64, 64), false);
                CommonGarmentRenderer.renderHumanoidSupport(List.of(support), player, ArmProfile.CLASSIC, 0xFFFFFF,
                        pose, recording, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                buffers.endBatch();
                String expectedType = translucent ? "RenderType[entity_translucent:" : "RenderType[entity_cutout_no_cull:";
                if (recording.types.size() != 1 || !recording.types.get(0).startsWith(expectedType))
                    throw new AssertionError("Support selected the wrong transparency RenderType");
                if (!translucent) opaque = Screenshot.takeScreenshot(target);
                else try (var actual = Screenshot.takeScreenshot(target)) {
                    int changed = 0;
                    for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++)
                        if (opaque.getPixelRGBA(x, y) != actual.getPixelRGBA(x, y)) changed++;
                    if (changed < 10) throw new AssertionError("Translucent support did not blend its pixels");
                }
            }
            com.mojang.logging.LogUtils.getLogger().info("CITIZEN_TRANSLUCENT_SUPPORT_PASSED renderType=true nativeBlend=true");
        } finally {
            if (opaque != null) opaque.close();
            mc.getTextureManager().release(id);
            if (texture.getPixels() != null) throw new AssertionError("Translucent QA texture leaked");
        }
    }

    static ApparelLoadout outfit(int scenario) {
        var loadout = new ApparelLoadout();
        var names = switch (scenario) {
            case 0 -> List.of("a_line_dress_knee");
            case 1 -> List.of("a_line_skirt_knee", "t_shirt");
            case 2 -> List.of("chesterfield_coat");
            case 3 -> List.of("beret", "a_line_dress_knee");
            case 4 -> List.of("backpack", "a_line_dress_knee");
            case 5 -> List.of("a_line_skirt_knee", "t_shirt", "chesterfield_coat", "beret", "backpack");
            default -> List.of("a_line_dress_knee");
        };
        for (String name : names) {
            var id = ResourceLocation.fromNamespaceAndPath("createapparel", name);
            var definition = GarmentDefinitionManager.get(id).orElseThrow();
            var stack = new ItemStack(Objects.requireNonNull(BuiltInRegistries.ITEM.get(id)));
            var data = GarmentData.defaultFor(definition);
            if (scenario == 6) {
                String design = definition.designs().stream().sorted().reduce((a, b) -> b).orElseThrow();
                data = new GarmentData(data.material(), design, FabricColor.values()[5], data.secondaryColor(), data.accentColor());
            }
            data.write(stack);
            loadout.setStackInSlot(definition.defaultSlot().ordinal(), stack);
        }
        return loadout;
    }

    private static List<Long> draw(TextureTarget target, PlayerModel<?> model, LoadoutRenderSnapshot source, boolean record) {
        return draw(target, model, source, record, 0, 25, 0, 140);
    }

    static List<Long> draw(TextureTarget target, PlayerModel<?> model, LoadoutRenderSnapshot source, boolean record,
                           int lod, float yaw, float pitch, float scale) {
        target.setClearColor(.12F, .15F, .18F, 1);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.enableDepthTest();
        var pose = new PoseStack();
        pose.translate(128, 160 - scale * .6, 0);
        pose.scale(-scale, scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var recording = new RecordingBuffers(buffers);
        net.minecraft.client.renderer.MultiBufferSource submission = record ? recording : buffers;
        var frame = new ApparelRenderFrame(source::garment, model, lod);
        ClientGarmentAssets.INSTANCE.resolveBody(source.armProfile(), source.chestProfile()).ifPresent(parts ->
                CommonGarmentRenderer.renderHumanoidSupport(parts, model, source.armProfile(), 0xDBAC84,
                        pose, submission, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY));
        ClientGarmentAssets.INSTANCE.resolveDefaultUnderwear(source.chestProfile(),
                source.garment(GarmentSlot.TOP).isEmpty() && source.garment(GarmentSlot.INNER_TOP).isEmpty(),
                source.garment(GarmentSlot.INNER_BOTTOM).isEmpty()).ifPresent(parts ->
                CommonGarmentRenderer.renderHumanoidSupport(parts, model, source.armProfile(), 0xFFFFFF,
                        pose, submission, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY));
        CommonGarmentRenderer.renderHumanoid(new GarmentRenderContext(model, source, lod, frame),
                pose, submission, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        buffers.endBatch();
        return recording.values;
    }

    private static final class RecordingBuffers implements net.minecraft.client.renderer.MultiBufferSource {
        private final net.minecraft.client.renderer.MultiBufferSource delegate;
        private final List<Long> values = new java.util.ArrayList<>();
        private final List<String> types = new java.util.ArrayList<>();

        private RecordingBuffers(net.minecraft.client.renderer.MultiBufferSource delegate) { this.delegate = delegate; }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(net.minecraft.client.renderer.RenderType type) {
            types.add(type.toString());
            values.add((long) type.toString().replaceAll("dynamic/(garment|citizen)/\\d+", "dynamic/$1")
                    .replaceAll("\\$\\$Lambda\\$\\d+/0x[0-9a-fA-F]+@[0-9a-fA-F]+", "lambda").hashCode());
            var target = delegate.getBuffer(type);
            return new com.mojang.blaze3d.vertex.VertexConsumer() {
                @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
                    values.add(-1L); values.add(Double.doubleToLongBits(x)); values.add(Double.doubleToLongBits(y)); values.add(Double.doubleToLongBits(z));
                    target.addVertex(x, y, z); return this;
                }
                @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) {
                    values.add((long) (r << 24 | g << 16 | b << 8 | a)); target.setColor(r, g, b, a); return this;
                }
                @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) {
                    values.add((long) Float.floatToIntBits(u)); values.add((long) Float.floatToIntBits(v)); target.setUv(u, v); return this;
                }
                @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) {
                    values.add((long) (u | v << 16)); target.setUv1(u, v); return this;
                }
                @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) {
                    values.add((long) (u | v << 16)); target.setUv2(u, v); return this;
                }
                @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) {
                    values.add((long) Float.floatToIntBits(x)); values.add((long) Float.floatToIntBits(y)); values.add((long) Float.floatToIntBits(z));
                    target.setNormal(x, y, z); return this;
                }
            };
        }
    }
}
