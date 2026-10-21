package com.rexhub.createapparel.citizen.population;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.apparel.*;
import com.rexhub.createapparel.citizen.population.pipeline.PipelineBuffers;
import com.rexhub.createapparel.client.garment.GarmentDiagnostics;
import com.rexhub.createapparel.client.renderer.CitizenRenderer;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

final class CitizenPipelineImageChecks {
    private CitizenPipelineImageChecks() { }

    static void run(CitizenRenderer renderer) throws Exception {
        run(renderer, false);
    }

    static void runAtlasPack(CitizenRenderer renderer) throws Exception {
        run(renderer, true);
    }

    private static void run(CitizenRenderer renderer, boolean packFallback) throws Exception {
        var mc = Minecraft.getInstance();
        var target = new TextureTarget(256, 320, true, Minecraft.ON_OSX);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var view = RenderSystem.getModelViewStack(); view.pushPose();
        int forced = GarmentDiagnostics.debugForceCitizenLod;
        var entity = ModEntities.CITIZEN.get().create(mc.level);
        var poses = new PosePreset[]{PosePreset.STANDING, PosePreset.WALKING, PosePreset.RUNWAY, PosePreset.HAND_ON_HIP, PosePreset.ARMS_CROSSED, PosePreset.CASUAL};
        int[] scenes = new int[5], differences = new int[5];
        int firstVariant = packFallback ? 4 : 1;
        var skinTones = packFallback ? new SkinTone[]{SkinTone.values()[0], SkinTone.values()[SkinTone.values().length / 2], SkinTone.values()[SkinTone.values().length - 1]}
                : new SkinTone[]{CitizenAppearance.DEFAULT.skinTone()};
        try {
            view.setIdentity(); view.translate(0, 0, -2000); RenderSystem.applyModelViewMatrix();
            mc.gameRenderer.resetProjectionMatrix(new Matrix4f().setOrtho(0, 256, 320, 0, 1000, 3000));
            for (var skin : skinTones) for (var arm : ArmProfile.values()) for (var chest : ChestProfile.values()) for (int outfit = 0; outfit < 7; outfit++)
                for (var pose : poses) for (int angle = 0; angle < 6; angle++) for (int lod = 0; lod < 4; lod++) {
                    var appearance = CitizenAppearance.DEFAULT;
                    entity.citizenState().setAppearance(new CitizenAppearance(skin, arm, chest, appearance.hairStyleId(), appearance.hairColorRgb(), appearance.eyeColorRgb()));
                    entity.citizenState().loadout().deserializeNBT(EnvelopeRenderClientChecks.outfit(outfit).serializeNBT());
                    entity.citizenState().setBehavior(CitizenBehaviorState.posed(pose));
                    entity.citizenState().setAge(outfit % 2 == 0 ? CitizenAge.ADULT : CitizenAge.CHILD);
                    entity.yBodyRot = entity.yBodyRotO = angle * 90;
                    GarmentDiagnostics.debugForceCitizenLod = lod;
                    float pitch = angle < 4 ? 0 : angle == 4 ? 60 : -60;
                    draw(target, entity, renderer, 0, .7F, pitch);
                    try (var expected = Screenshot.takeScreenshot(target)) {
                        int foreground = 0, background = expected.getPixelRGBA(0, 0);
                        for (int y = 0; y < expected.getHeight(); y++) for (int x = 0; x < expected.getWidth(); x++)
                            if (expected.getPixelRGBA(x, y) != background) foreground++;
                        if (foreground < 100) throw new AssertionError("Empty pipeline reference");
                        for (int variant = firstVariant; variant <= 4; variant++) {
                            draw(target, entity, renderer, variant, .7F, pitch);
                            try (var actual = Screenshot.takeScreenshot(target)) {
                                scenes[variant]++;
                                for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++)
                                    if (expected.getPixelRGBA(x, y) != actual.getPixelRGBA(x, y)) differences[variant]++;
                            }
                        }
                    }
                }
            for (int variant = firstVariant; variant <= 4; variant++) {
                com.mojang.logging.LogUtils.getLogger().info("CITIZEN_PIPELINE_IMAGES mode={} scenes={} changedPixels={} exact={}", variant, scenes[variant], differences[variant], differences[variant] == 0);
                if (differences[variant] != 0) throw new AssertionError("Pipeline altered pixels: mode=" + variant);
            }
            entity.citizenState().setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, net.minecraft.core.BlockPos.ZERO, mc.level.dimension(), 0));
            int temporal = 0;
            for (int outfit = 0; outfit < 7; outfit++) for (int frame = 0; frame < 60; frame++) {
                entity.citizenState().loadout().deserializeNBT(EnvelopeRenderClientChecks.outfit(outfit).serializeNBT());
                entity.tickCount = frame / 3;
                if (frame % 3 == 0) entity.walkAnimation.update(.6F, 1);
                entity.yBodyRot = entity.yBodyRotO = frame * 6;
                GarmentDiagnostics.debugForceCitizenLod = frame < 30 ? 3 - frame / 8 : Math.min(3, (frame - 30) / 8);
                float partial = frame % 3 / 3F;
                draw(target, entity, renderer, 0, partial);
                try (var expected = Screenshot.takeScreenshot(target)) {
                    for (int variant = firstVariant; variant <= 4; variant++) {
                        draw(target, entity, renderer, variant, partial);
                        try (var actual = Screenshot.takeScreenshot(target)) {
                            for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++)
                                if (expected.getPixelRGBA(x, y) != actual.getPixelRGBA(x, y)) throw new AssertionError("Moving pipeline pixels differ: mode=" + variant + " frame=" + frame);
                            temporal++;
                        }
                    }
                }
            }
            com.mojang.logging.LogUtils.getLogger().info("CITIZEN_PIPELINE_TEMPORAL_PASSED comparisons={} 60FpsInput=true rotations=true allLodRestoration=true changedPixels=0", temporal);
            translucentOrdering(target);
            if (packFallback) com.mojang.logging.LogUtils.getLogger().info("CITIZEN_ATLAS_RESOURCE_PACK_IMAGES_PASSED scenes={} skinTones={} temporalComparisons={} changedPixels=0", scenes[4], skinTones.length, temporal);
        } finally {
            PipelineBuffers.close(); PipelineBuffers.verifyClosed();
            renderer.forget(entity);
            GarmentDiagnostics.debugForceCitizenLod = forced;
            view.popPose(); RenderSystem.applyModelViewMatrix(); mc.gameRenderer.resetProjectionMatrix(projection);
            target.destroyBuffers(); mc.getMainRenderTarget().bindWrite(true);
        }
    }
    private static void translucentOrdering(TextureTarget target) throws Exception {
        var mc = Minecraft.getInstance();
        var texture = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createapparel", "textures/body/body_tint_base.png");
        float u = -1, v = -1;
        try (var stream = mc.getResourceManager().getResource(texture).orElseThrow().open(); var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
            for (int y = 0; y < image.getHeight() && u < 0; y++) for (int x = 0; x < image.getWidth(); x++) if (image.getPixelRGBA(x, y) >>> 24 == 255) {
                u = (x + .5F) / image.getWidth(); v = (y + .5F) / image.getHeight(); break;
            }
        }
        if (u < 0) throw new AssertionError("No opaque synthetic reference texel");
        for (int depth = 2; depth <= 4; depth += 2) {
            layers(target, 0, texture, u, v, depth);
            try (var expected = Screenshot.takeScreenshot(target)) {
                for (int variant = 1; variant <= 4; variant++) {
                    layers(target, variant, texture, u, v, depth);
                    try (var actual = Screenshot.takeScreenshot(target)) {
                        for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++)
                            if (expected.getPixelRGBA(x, y) != actual.getPixelRGBA(x, y)) throw new AssertionError("Translucent/opaque ordering differs: mode=" + variant + " depth=" + depth);
                    }
                }
            }
        }
        com.mojang.logging.LogUtils.getLogger().info("CITIZEN_PIPELINE_TRANSLUCENCY_PASSED comparisons=8 depthWritingTranslucent=true changedPixels=0");
    }
    private static void layers(TextureTarget target, int mode, net.minecraft.resources.ResourceLocation texture, float u, float v, int depth) {
        PipelineBuffers.mode(mode);
        target.setClearColor(.12F, .15F, .18F, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true); RenderSystem.enableDepthTest();
        var original = Minecraft.getInstance().renderBuffers().bufferSource();
        var source = PipelineBuffers.wrap(original);
        var cutout = net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture);
        var translucent = net.minecraft.client.renderer.RenderType.entityTranslucent(texture);
        quad(source.getBuffer(cutout), 24, 232, 0, 1, 1, 1, 1, u, v);
        quad(source.getBuffer(translucent), 48, 208, 3, 1, .2F, .3F, .5F, u, v);
        quad(source.getBuffer(cutout), 96, 160, depth, .2F, .3F, 1, 1, u, v);
        quad(source.getBuffer(translucent), 64, 192, 5, .2F, 1, .3F, .5F, u, v);
        PipelineBuffers.finishCitizen(); original.endBatch();
    }
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer consumer, float left, float right, float z,
            float r, float g, float b, float a, float u, float v) {
        for (int corner = 0; corner < 4; corner++) consumer.vertex(corner < 2 ? left : right, corner % 3 == 0 ? 32 : 288, z,
                r, g, b, a, u, v, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0, 0, 1);
    }
    private static void draw(TextureTarget target, Citizen entity, CitizenRenderer renderer, int mode) {
        draw(target, entity, renderer, mode, .7F);
    }
    private static void draw(TextureTarget target, Citizen entity, CitizenRenderer renderer, int mode, float partial) {
        draw(target, entity, renderer, mode, partial, 0);
    }
    private static void draw(TextureTarget target, Citizen entity, CitizenRenderer renderer, int mode, float partial, float pitch) {
        PipelineBuffers.mode(mode);
        target.setClearColor(.12F, .15F, .18F, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
        RenderSystem.enableDepthTest();
        var pose = new PoseStack(); pose.translate(128, 244, 0); pose.scale(100, -100, 100);
        if (pitch != 0) { pose.translate(0, .8, 0); pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch)); pose.translate(0, -.8, 0); }
        renderer.render(entity, 0, partial, pose, Minecraft.getInstance().renderBuffers().bufferSource(), LightTexture.FULL_BRIGHT);
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }
}
