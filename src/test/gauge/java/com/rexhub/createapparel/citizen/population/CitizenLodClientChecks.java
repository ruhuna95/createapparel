package com.rexhub.createapparel.citizen.population;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.garment.LoadoutRenderSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import org.joml.Matrix4f;

import java.nio.file.Files;
import java.nio.file.Path;

final class CitizenLodClientChecks {
    private CitizenLodClientChecks() { }

    static void run() throws Exception {
        var mc = Minecraft.getInstance();
        checkVanillaCulling();
        var output = Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build", "citizen-lod-images");
        Files.createDirectories(output);
        var target = new TextureTarget(256, 320, true, Minecraft.ON_OSX);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        int scenes = 0;
        double maxSilhouette = 0;
        String worst = "";
        var results = new StringBuilder("scene,lod,foreground,changedPixels,silhouettePixels,silhouetteFraction\n");
        try {
            modelView.identity(); modelView.translate(0, 0, -2000);
            RenderSystem.applyModelViewMatrix();
            mc.gameRenderer.resetProjectionMatrix(new Matrix4f().setOrtho(0, 256, 320, 0, 1000, 3000));
            for (var arm : ArmProfile.values()) for (var chest : ChestProfile.values()) {
                for (int pose = 0; pose < 6; pose++) for (int outfit = 0; outfit < 7; outfit++) for (int view = 0; view < 6; view++) {
                    var model = new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE, arm == ArmProfile.SLIM).getRoot().bake(64, 64), arm == ArmProfile.SLIM);
                    model.leftLeg.xRot = new float[]{0, -.7F, -1.4F, .9F, -.6F, -1.57F}[pose];
                    model.rightLeg.xRot = new float[]{0, .7F, 1.4F, -.3F, .4F, -1.57F}[pose];
                    model.leftArm.xRot = -model.leftLeg.xRot; model.rightArm.xRot = -model.rightLeg.xRot;
                    if (pose == 3) { model.leftArm.zRot = -.9F; model.rightArm.yRot = .5F; }
                    if (pose == 4) { model.crouching = true; model.body.xRot = .5F; model.leftLeg.z = model.rightLeg.z = 4; }
                    var source = new LoadoutRenderSnapshot(EnvelopeRenderClientChecks.outfit(outfit), arm, chest);
                    String name = arm + "-" + chest + "-pose" + pose + "-outfit" + outfit + "-view" + view;
                    float yaw = view < 4 ? view * 90 : 25, pitch = view == 4 ? 35 : view == 5 ? -25 : 0;
                    for (int lod = 0; lod < 4; lod++) {
                        float scale = lod == 0 ? 140 : lod == 1 ? 30 : 13;
                        EnvelopeRenderClientChecks.draw(target, model, source, false, 0, yaw, pitch, scale);
                        int textures = com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache.size();
                        try (var before = Screenshot.takeScreenshot(target)) {
                                var vertices = EnvelopeRenderClientChecks.draw(target, model, source, true, lod, yaw, pitch, scale);
                            if (textures != com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache.size()) throw new AssertionError("LOD switch generated another texture");
                            try (var after = Screenshot.takeScreenshot(target)) {
                                RenderReferenceChecks.check("lod-" + name + "-" + lod, after, vertices);
                                int changed = 0, foreground = 0, silhouette = 0, background = before.getPixelRGBA(0, 0);
                                for (int y = 0; y < before.getHeight(); y++) for (int x = 0; x < before.getWidth(); x++) {
                                    int a = before.getPixelRGBA(x, y), b = after.getPixelRGBA(x, y);
                                    if (a != b) changed++;
                                    if (a != background) foreground++;
                                    if ((a != background) != (b != background)) silhouette++;
                                }
                                double fraction = silhouette / (double) Math.max(1, foreground);
                                results.append(name).append(',').append(lod).append(',').append(foreground).append(',').append(changed).append(',').append(silhouette).append(',').append(fraction).append('\n');
                                if (fraction > maxSilhouette) { maxSilhouette = fraction; worst = name + "-lod" + lod; }
                                boolean failed = foreground < 10 || lod == 0 && changed != 0 || fraction > .08;
                                if (failed || pose == 2 && outfit == 5) {
                                    before.writeToFile(output.resolve(name + "-lod" + lod + "-reference.png"));
                                    after.writeToFile(output.resolve(name + "-lod" + lod + "-actual.png"));
                                }
                                if (failed) throw new AssertionError(name + " lod=" + lod + " foreground=" + foreground + " changed=" + changed + " silhouette=" + fraction);
                                scenes++;
                            }
                        }
                    }
                }
            }
            com.mojang.logging.LogUtils.getLogger().info("CITIZEN_LOD_IMAGES_PASSED scenes={} LOD0exactPixels=true maxSilhouetteFraction={} worst={} profiles=4 poses=6 outfits=7 views=6", scenes, maxSilhouette, worst);
        } finally {
            Files.writeString(output.resolve("comparison.csv"), results);
            modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); mc.gameRenderer.resetProjectionMatrix(projection);
            target.destroyBuffers(); mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void checkVanillaCulling() {
        var mc = Minecraft.getInstance();
        var entity = java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(com.rexhub.createapparel.entity.Citizen.class::isInstance)
                .map(com.rexhub.createapparel.entity.Citizen.class::cast).findFirst().orElseThrow();
        var renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        var calls = new int[1];
        var visible = new boolean[]{true};
        var frustum = new net.minecraft.client.renderer.culling.Frustum(new Matrix4f(), new Matrix4f()) {
            @Override public boolean isVisible(net.minecraft.world.phys.AABB bounds) {
                calls[0]++;
                if (!bounds.equals(entity.getBoundingBoxForCulling().inflate(.5))) throw new AssertionError("Unexpected culling bounds");
                return visible[0];
            }
        };
        if (!renderer.shouldRender(entity, frustum, entity.getX(), entity.getY(), entity.getZ()) || calls[0] != 1) throw new AssertionError("Duplicate/missing frustum test");
        visible[0] = false; calls[0] = 0;
        if (renderer.shouldRender(entity, frustum, entity.getX(), entity.getY(), entity.getZ()) || calls[0] != 1) throw new AssertionError("Frustum rejection ignored");
        calls[0] = 0;
        if (renderer.shouldRender(entity, frustum, 1e6, 1e6, 1e6) || calls[0] != 0) throw new AssertionError("Vanilla distance rejection ignored");
        com.mojang.logging.LogUtils.getLogger().info("CITIZEN_VANILLA_CULLING_PASSED singleFrustum=true distanceRejectedBeforeFrustum=true bounds={}", entity.getBoundingBoxForCulling().inflate(.5));
    }
}
