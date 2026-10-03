package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.client.apparel.ClientApparelInventories;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.WatchTime;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Quaternionf;
import org.joml.Matrix3fc;
import org.joml.Vector3f;

public final class WatchHud {
    private static final ResourceLocation DIAL = ResourceLocation.fromNamespaceAndPath(
            CreateApparel.MOD_ID, "textures/gui/watch_dial.png");

    private WatchHud() {
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null || minecraft.options.hideGui
                || !minecraft.options.keyShift.isDown()
                || player.isInvisible() || player.isSpectator()
                || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return;
        var snapshot = ClientApparelInventories.snapshot(player);
        if (!snapshot.stackCopy(GarmentSlot.HANDS).is(ModItems.WATCH.get())) return;
        var garment = snapshot.garment(GarmentSlot.HANDS).orElse(null);
        if (garment == null) return;
        for (var part : garment.parts()) {
            for (Element element : part.model().elements()) {
                if (!element.name().equals("watch_dial")) continue;
                var side = (element.from().x() + element.to().x()) < 0 ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
                float armX = side == HumanoidArm.RIGHT ? 5 : -5;
                float armY = snapshot.armProfile() == ArmProfile.SLIM ? 2.5F : 2;
                float centerX = (side == HumanoidArm.LEFT ? element.to().x() : element.from().x()) + armX;
                float centerY = 24 - (element.from().y() + element.to().y()) / 2 - armY;
                float centerZ = (element.from().z() + element.to().z()) / 2;
                var layout = layout(side, width, height);
                var pose = graphics.pose();
                graphics.flush();
                gui.setupOverlayRenderState(true, false);
                pose.pushPose();
                try {
                    pose.translate(layout.x(), layout.y(), 100);
                    scaleArm(pose, layout.scale());
                    pose.mulPose(armRotation(side));
                    pose.translate(-centerX / 16, -centerY / 16, -centerZ / 16);
                    RenderSystem.enableDepthTest();
                    var lights = armLights(side, pose.last().normal());
                    RenderSystem.setShaderLights(lights.first(), lights.second());
                    FirstPersonApparelRenderer.renderHudArm(player, side, pose,
                            minecraft.renderBuffers().bufferSource(), LightTexture.FULL_BRIGHT);
                    minecraft.renderBuffers().bufferSource().endBatch();
                    pose.translate((centerX + (side == HumanoidArm.LEFT ? 0.001F : -0.001F)) / 16,
                            centerY / 16, centerZ / 16);
                    pose.mulPose(dialRotation(side));
                    pose.scale(1F / 512, 1F / 512, 1F / 512);
                    RenderSystem.disableDepthTest();
                    graphics.blit(DIAL, -16, -16, 0, 0, 32, 32, 32, 32);
                    var time = WatchTime.fromDayTime(minecraft.level.getDayTime(),
                            minecraft.level.dimensionType().natural());
                    hand(graphics, time.hourAngle(), 7, 0xFF333B45);
                    hand(graphics, time.minuteAngle(), 11, 0xFF303844);
                    graphics.fill(RenderType.guiOverlay(), -1, -1, 1, 1, 0xFFB6924D);
                    graphics.flush();
                } finally {
                    pose.popPose();
                    Lighting.setupFor3DItems();
                    RenderSystem.disableDepthTest();
                }
                return;
            }
        }
    }

    static Quaternionf armRotation(HumanoidArm side) {
        float direction = side == HumanoidArm.LEFT ? 1 : -1;
        return new Quaternionf().rotationZ((float) Math.toRadians(-110 * direction))
                .rotateX((float) Math.toRadians(-12))
                .rotateY((float) Math.toRadians(70 * direction));
    }

    static void scaleArm(PoseStack pose, float scale) {
        pose.scale(scale, scale, scale);
        pose.last().pose().scale(1, 1, -1);
        pose.last().normal().scale(1, 1, -1);
    }

    static HudLights armLights(HumanoidArm side, Matrix3fc normalMatrix) {
        var outside = new Vector3f(side == HumanoidArm.LEFT ? 1 : -1, 0, 0).mul(normalMatrix).normalize();
        var front = new Vector3f(0, 0, -1).mul(normalMatrix).normalize();
        return new HudLights(new Vector3f(outside).mul(0.6F).fma(0.8F, front),
                new Vector3f(outside).mul(0.6F).fma(-0.8F, front));
    }

    record HudLights(Vector3f first, Vector3f second) {
    }

    static Quaternionf dialRotation(HumanoidArm side) {
        float direction = side == HumanoidArm.LEFT ? 1 : -1;
        return new Quaternionf().rotationY((float) Math.toRadians(-90 * direction))
                .rotateZ((float) Math.toRadians(90 * direction));
    }

    static Layout layout(HumanoidArm side, int width, int height) {
        float x = side == HumanoidArm.LEFT ? Math.max(32, width / 2F - 113)
                : Math.min(width - 32, width / 2F + 113);
        return new Layout(x, height - 46, Math.min(416, Math.max(320, height * 1.4F)));
    }

    record Layout(float x, float y, float scale) {
    }

    private static void hand(GuiGraphics graphics, float angle, int length, int color) {
        double radians = Math.toRadians(angle);
        for (int distance = 0; distance <= length; distance++) {
            int x = (int) Math.round(Math.sin(radians) * distance);
            int y = (int) Math.round(-Math.cos(radians) * distance);
            graphics.fill(RenderType.guiOverlay(), x, y, x + 1, y + 1, color);
        }
    }
}
