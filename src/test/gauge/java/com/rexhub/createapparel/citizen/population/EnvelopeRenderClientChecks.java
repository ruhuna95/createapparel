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
import net.minecraftforge.registries.ForgeRegistries;
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
        boolean legacy = GarmentDiagnostics.debugUseLegacyEnvelopeRendering;
        boolean legacyRigid = GarmentDiagnostics.debugUseLegacyRigidRendering;
        modelView.pushPose();
        int scenes = 0;
        try {
            modelView.setIdentity();
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
                    GarmentDiagnostics.debugUseLegacyEnvelopeRendering = true;
                    GarmentDiagnostics.debugUseLegacyRigidRendering = true;
                    draw(target, model, source, false);
                    try (var before = Screenshot.takeScreenshot(target)) {
                        GarmentDiagnostics.debugUseLegacyEnvelopeRendering = false;
                        GarmentDiagnostics.debugUseLegacyRigidRendering = false;
                        draw(target, model, source, false);
                        try (var after = Screenshot.takeScreenshot(target)) {
                            int changed = 0, foreground = 0;
                            int background = before.getPixelRGBA(0, 0);
                            for (int y = 0; y < before.getHeight(); y++) for (int x = 0; x < before.getWidth(); x++) {
                                if (before.getPixelRGBA(x, y) != after.getPixelRGBA(x, y)) changed++;
                                if (before.getPixelRGBA(x, y) != background) foreground++;
                            }
                            before.writeToFile(output.resolve(name + "-legacy.png"));
                            after.writeToFile(output.resolve(name + "-indexed.png"));
                            if (foreground < 100 || changed != 0) throw new AssertionError(name + " foreground=" + foreground + " changedPixels=" + changed);
                            scenes++;
                        }
                    }
                    GarmentDiagnostics.debugUseLegacyEnvelopeRendering = true;
                    GarmentDiagnostics.debugUseLegacyRigidRendering = true;
                    var legacyVertices = draw(target, model, source, true);
                    GarmentDiagnostics.debugUseLegacyEnvelopeRendering = false;
                    GarmentDiagnostics.debugUseLegacyRigidRendering = false;
                    var indexedVertices = draw(target, model, source, true);
                    if (!legacyVertices.equals(indexedVertices)) throw new AssertionError(name + " vertex stream differs");
                }
            }
            com.mojang.logging.LogUtils.getLogger().info("ENVELOPE_IMAGE_COMPARISON_PASSED scenes={} changedPixels=0 resolution=256x320 profiles=4 poses=6 outfits=7", scenes);
        } finally {
            GarmentDiagnostics.debugUseLegacyEnvelopeRendering = legacy;
            GarmentDiagnostics.debugUseLegacyRigidRendering = legacyRigid;
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            mc.gameRenderer.resetProjectionMatrix(projection);
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
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
            var stack = new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(id)));
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
        return draw(target, model, source, record, 0, false, 25, 0, 140);
    }

    static List<Long> draw(TextureTarget target, PlayerModel<?> model, LoadoutRenderSnapshot source, boolean record,
                           int lod, boolean occlusion, float yaw, float pitch, float scale) {
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
        var coverage = occlusion ? new CommonGarmentRenderer.SupportOcclusion(source, model) : null;
        if (coverage != null && !coverage.applicable()) coverage = null;
        var activeCoverage = coverage;
        ClientGarmentAssets.INSTANCE.resolveBody(source.armProfile(), source.chestProfile()).ifPresent(parts ->
                CommonGarmentRenderer.renderHumanoidSupport(parts, model, source.armProfile(), 0xDBAC84,
                        pose, submission, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, activeCoverage, frame, false));
        ClientGarmentAssets.INSTANCE.resolveDefaultUnderwear(source.chestProfile(),
                source.garment(GarmentSlot.TOP).isEmpty() && source.garment(GarmentSlot.INNER_TOP).isEmpty(),
                source.garment(GarmentSlot.INNER_BOTTOM).isEmpty()).ifPresent(parts ->
                CommonGarmentRenderer.renderHumanoidSupport(parts, model, source.armProfile(), 0xFFFFFF,
                        pose, submission, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, activeCoverage, frame, true));
        CommonGarmentRenderer.renderHumanoid(new GarmentRenderContext(model, source, GarmentRenderContext.TargetType.CITIZEN, lod, frame),
                pose, submission, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        buffers.endBatch();
        return recording.values;
    }

    private static final class RecordingBuffers implements net.minecraft.client.renderer.MultiBufferSource {
        private final net.minecraft.client.renderer.MultiBufferSource delegate;
        private final List<Long> values = new java.util.ArrayList<>();

        private RecordingBuffers(net.minecraft.client.renderer.MultiBufferSource delegate) { this.delegate = delegate; }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(net.minecraft.client.renderer.RenderType type) {
            values.add((long) System.identityHashCode(type));
            var target = delegate.getBuffer(type);
            return new com.mojang.blaze3d.vertex.VertexConsumer() {
                @Override public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x, double y, double z) {
                    values.add(Double.doubleToLongBits(x)); values.add(Double.doubleToLongBits(y)); values.add(Double.doubleToLongBits(z));
                    target.vertex(x, y, z); return this;
                }
                @Override public com.mojang.blaze3d.vertex.VertexConsumer color(int r, int g, int b, int a) {
                    values.add((long) (r << 24 | g << 16 | b << 8 | a)); target.color(r, g, b, a); return this;
                }
                @Override public com.mojang.blaze3d.vertex.VertexConsumer uv(float u, float v) {
                    values.add((long) Float.floatToIntBits(u)); values.add((long) Float.floatToIntBits(v)); target.uv(u, v); return this;
                }
                @Override public com.mojang.blaze3d.vertex.VertexConsumer overlayCoords(int u, int v) {
                    values.add((long) (u | v << 16)); target.overlayCoords(u, v); return this;
                }
                @Override public com.mojang.blaze3d.vertex.VertexConsumer uv2(int u, int v) {
                    values.add((long) (u | v << 16)); target.uv2(u, v); return this;
                }
                @Override public com.mojang.blaze3d.vertex.VertexConsumer normal(float x, float y, float z) {
                    values.add((long) Float.floatToIntBits(x)); values.add((long) Float.floatToIntBits(y)); values.add((long) Float.floatToIntBits(z));
                    target.normal(x, y, z); return this;
                }
                @Override public void endVertex() { values.add(-1L); target.endVertex(); }
                @Override public void defaultColor(int r, int g, int b, int a) { throw new AssertionError("Unexpected default color"); }
                @Override public void unsetDefaultColor() { throw new AssertionError("Unexpected default color reset"); }
            };
        }
    }
}
