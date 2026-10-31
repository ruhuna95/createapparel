package com.rexhub.createapparel.client.citizen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class DistrictGaugeIcon {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "textures/item/gauge_district_icon.png");
    public static final ResourceLocation INFLUX = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "textures/item/gauge_influx_icon.png");
    public static final ResourceLocation OUTFLOW = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "textures/item/gauge_outflow_icon.png");
    private DistrictGaugeIcon() { }

    public static int color(UUID districtId) {
        int rgb = districtId.hashCode() & 0xFFFFFF;
        int red = rgb >>> 16, green = rgb >>> 8 & 255, blue = rgb & 255;
        int maximum = Math.max(red, Math.max(green, blue));
        if (maximum == 0) return 0xE6E6E6;
        int divisor = maximum * 10, rounding = divisor / 2;
        red = (red * 9 * 255 + rounding) / divisor;
        green = (green * 9 * 255 + rounding) / divisor;
        blue = (blue * 9 * 255 + rounding) / divisor;
        return red << 16 | green << 8 | blue;
    }

    public static void render(GuiGraphics graphics, UUID districtId, int x, int y) {
        int color = color(districtId);
        render(graphics, TEXTURE, color, x, y);
    }
    public static void render(GuiGraphics graphics, ResourceLocation texture, int color, int x, int y) {
        graphics.setColor((color >>> 16 & 255) / 255F, (color >>> 8 & 255) / 255F, (color & 255) / 255F, 1);
        graphics.blit(texture, x, y, 0, 0, 16, 16, 16, 16);
        graphics.setColor(1, 1, 1, 1);
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, UUID districtId, int light, int overlay) {
        int color = color(districtId);
        render(pose, buffers, TEXTURE, color, light, overlay);
    }
    public static void render(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, int color, int light, int overlay) {
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        var transform = pose.last();
        vertices.addVertex(transform.pose(), -.5F, -.5F, 0).setColor(color >>> 16 & 255, color >>> 8 & 255, color & 255, 255)
                .setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(transform, 0, 0, 1);
        vertices.addVertex(transform.pose(), .5F, -.5F, 0).setColor(color >>> 16 & 255, color >>> 8 & 255, color & 255, 255)
                .setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(transform, 0, 0, 1);
        vertices.addVertex(transform.pose(), .5F, .5F, 0).setColor(color >>> 16 & 255, color >>> 8 & 255, color & 255, 255)
                .setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(transform, 0, 0, 1);
        vertices.addVertex(transform.pose(), -.5F, .5F, 0).setColor(color >>> 16 & 255, color >>> 8 & 255, color & 255, 255)
                .setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(transform, 0, 0, 1);
    }
}
