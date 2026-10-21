package com.rexhub.createapparel.citizen.population;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.garment.GarmentDiagnostics;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

import java.nio.file.Files;
import java.nio.file.Path;

final class CitizenRepresentationImageChecks {
    private CitizenRepresentationImageChecks() { }

    static void run(CitizenRepresentationPrototype prototype) throws Exception {
        var mc = Minecraft.getInstance();
        var output = Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build", "citizen-representation-images");
        Files.createDirectories(output);
        var target = new TextureTarget(256, 320, true, Minecraft.ON_OSX);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var view = RenderSystem.getModelViewStack(); view.pushPose();
        int forced = GarmentDiagnostics.debugForceCitizenLod;
        int scenes = 0, nearChanged = 0;
        double meshSilhouette = 0, spriteSilhouette = 0;
        var csv = new StringBuilder("scene,mode,near,changed,foreground,silhouetteFraction\n");
        var entity = ModEntities.CITIZEN.get().create(mc.level);
        var poses = new PosePreset[]{PosePreset.STANDING, PosePreset.WALKING, PosePreset.RUNWAY, PosePreset.HAND_ON_HIP, PosePreset.ARMS_CROSSED, PosePreset.CASUAL};
        try {
            view.setIdentity(); view.translate(0, 0, -2000); RenderSystem.applyModelViewMatrix();
            mc.gameRenderer.resetProjectionMatrix(new Matrix4f().setOrtho(0, 256, 320, 0, 1000, 3000));
            for (var arm : ArmProfile.values()) for (var chest : ChestProfile.values()) for (int outfit = 0; outfit < 7; outfit++)
                for (int pose = 0; pose < poses.length; pose++) for (int angle = 0; angle < 4; angle++) {
                    var appearance = CitizenAppearance.DEFAULT;
                    entity.citizenState().setAppearance(new CitizenAppearance(appearance.skinTone(), arm, chest, appearance.hairStyleId(), appearance.hairColorRgb(), appearance.eyeColorRgb()));
                    entity.citizenState().loadout().deserializeNBT(EnvelopeRenderClientChecks.outfit(outfit).serializeNBT());
                    entity.citizenState().setBehavior(CitizenBehaviorState.posed(poses[pose]));
                    entity.yBodyRot = entity.yBodyRotO = angle * 90;
                    for (var mode : new CitizenRepresentationPrototype.Mode[]{CitizenRepresentationPrototype.Mode.MESH, CitizenRepresentationPrototype.Mode.IMPOSTOR}) {
                        prototype.mode(mode);
                        for (boolean near : new boolean[]{false, true}) {
                            entity.setPos(mc.gameRenderer.getMainCamera().getPosition().add(0, 0, near ? 4 : 100));
                            GarmentDiagnostics.debugForceCitizenLod = near ? 0 : 3;
                            draw(target, entity, prototype, true, near);
                            int garmentTextures = com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache.size();
                            try (var before = Screenshot.takeScreenshot(target)) {
                                draw(target, entity, prototype, false, near);
                                try (var after = Screenshot.takeScreenshot(target)) {
                                    int foreground = 0, changed = 0, silhouette = 0, background = before.getPixelRGBA(0, 0);
                                    for (int y = 0; y < before.getHeight(); y++) for (int x = 0; x < before.getWidth(); x++) {
                                        int a = before.getPixelRGBA(x, y), b = after.getPixelRGBA(x, y);
                                        if (a != b) changed++;
                                        if (a != background) foreground++;
                                        if ((a != background) != (b != background)) silhouette++;
                                    }
                                    if (foreground == 0) throw new AssertionError("Empty reference image");
                                    double fraction = silhouette / (double) foreground;
                                    if (near) { nearChanged += changed; if (changed != 0) throw new AssertionError("LOD0 did not fully restore " + mode + " changed=" + changed); }
                                    else if (mode == CitizenRepresentationPrototype.Mode.MESH) meshSilhouette = Math.max(meshSilhouette, fraction);
                                    else spriteSilhouette = Math.max(spriteSilhouette, fraction);
                                    String scene = arm + "-" + chest + "-outfit" + outfit + "-pose" + pose + "-angle" + angle;
                                    csv.append(scene).append(',').append(mode).append(',').append(near).append(',').append(changed).append(',').append(foreground).append(',').append(fraction).append('\n');
                                    if (!near && pose == 2 && outfit == 5) {
                                        before.writeToFile(output.resolve(scene + "-" + mode + "-reference.png"));
                                        after.writeToFile(output.resolve(scene + "-" + mode + "-actual.png"));
                                    }
                                    scenes++;
                                }
                            }
                            if (garmentTextures != com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache.size()) throw new AssertionError("Representation switch regenerated garment textures");
                        }
                    }
                }
            com.mojang.logging.LogUtils.getLogger().info("CITIZEN_REPRESENTATION_IMAGE_MEASUREMENT_COMPLETE scenes={} LOD0changedPixels={} meshMaxSilhouetteFraction={} impostorMaxSilhouetteFraction={} (LOD0 restoration passed; prototype quality is not adoption approval)",
                    scenes, nearChanged, meshSilhouette, spriteSilhouette);
            for (var mode : new CitizenRepresentationPrototype.Mode[]{CitizenRepresentationPrototype.Mode.MESH, CitizenRepresentationPrototype.Mode.IMPOSTOR}) {
                prototype.mode(mode);
                entity.citizenState().setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, net.minecraft.core.BlockPos.ZERO, mc.level.dimension(), 0));
                entity.setPos(mc.gameRenderer.getMainCamera().getPosition().add(0, 0, 100));
                entity.walkAnimation.update(.5F, 1);
                GarmentDiagnostics.debugForceCitizenLod = 3;
                draw(target, entity, prototype, false, false);
                entity.walkAnimation.update(.1F, 1);
                draw(target, entity, prototype, true, false);
                try (var expected = Screenshot.takeScreenshot(target)) {
                    draw(target, entity, prototype, false, false);
                    try (var actual = Screenshot.takeScreenshot(target)) {
                        int changed = 0;
                        for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++)
                            if (expected.getPixelRGBA(x, y) != actual.getPixelRGBA(x, y)) changed++;
                        expected.writeToFile(output.resolve("animation-" + mode + "-reference.png"));
                        actual.writeToFile(output.resolve("animation-" + mode + "-actual.png"));
                        com.mojang.logging.LogUtils.getLogger().info("CITIZEN_REPRESENTATION_ANIMATION mode={} changedPixels={} {} (same animation bucket; mismatch is retained as an experimental limitation)", mode, changed, prototype.report());
                    }
                }
            }
        } finally {
            prototype.mode(CitizenRepresentationPrototype.Mode.ORIGINAL);
            GarmentDiagnostics.debugForceCitizenLod = forced;
            Files.writeString(output.resolve("comparison.csv"), csv);
            view.popPose(); RenderSystem.applyModelViewMatrix(); mc.gameRenderer.resetProjectionMatrix(projection);
            target.destroyBuffers(); mc.getMainRenderTarget().bindWrite(true);
        }
    }
    private static void draw(TextureTarget target, Citizen entity, CitizenRepresentationPrototype prototype, boolean original, boolean near) {
        var mc = Minecraft.getInstance();
        target.setClearColor(.12F, .15F, .18F, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
        var pose = new PoseStack();
        pose.translate(128, near ? 280 : 175, 0);
        float scale = near ? 120 : 13;
        pose.scale(scale, -scale, scale);
        var buffers = mc.renderBuffers().bufferSource();
        if (original) prototype.original().render(entity, 0, 0, pose, buffers, LightTexture.FULL_BRIGHT);
        else prototype.render(entity, 0, 0, pose, buffers, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
    }
}
