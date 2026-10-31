package com.rexhub.createapparel.client.advertisement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.advertisement.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class AdvertisementPanelRenderer implements BlockEntityRenderer<AdvertisementPanelBlockEntity> {
    public AdvertisementPanelRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public void render(AdvertisementPanelBlockEntity panel, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        boolean fallback = !panel.isController();
        if (fallback && panel.controller() != null) return;
        boolean signage = panel.kind() == AdvertisementPanelBlock.Kind.SIGNAGE;
        var scenes = fallback ? java.util.List.of(AdvertisementScenes.defaultSnapshot(panel.groupId().hashCode()))
                : signage ? ClientAdvertisementChannels.scenes(panel) : java.util.List.of(panel.localSnapshot());
        long time = panel.getLevel() == null ? 0 : panel.getLevel().getGameTime();
        int index = signage ? AdvertisementScenes.index(time, scenes.size()) : 0;
        var image = PhotoRenderCache.image(scenes.get(index).displayPhoto(), panel.width(), panel.height());
        if (image == null) return;
        draw(panel, pose, buffers, light, fallback, image.texture(), 255);
        if (signage && scenes.size() > 1) {
            var next = PhotoRenderCache.image(scenes.get((index + 1) % scenes.size()).displayPhoto(), panel.width(), panel.height());
            float fade = AdvertisementScenes.fade(time, partialTick);
            if (next != null && fade > 0) draw(panel, pose, buffers, light, fallback, next.texture(), Math.round(255 * fade));
        }
    }
    private static void draw(AdvertisementPanelBlockEntity panel, PoseStack pose, MultiBufferSource buffers, int light,
                             boolean fallback, ResourceLocation texture, int alpha) {
        var facing = panel.getBlockState().getValue(AdvertisementPanelBlock.FACING); var right = PanelGroupManager.right(panel);
        double depth = panel.kind() == AdvertisementPanelBlock.Kind.POSTER ? 0.4365 : 0.374;
        Vec3 origin = new Vec3(0.5 - right.getStepX() * 0.5 - facing.getStepX() * depth, 0,
                0.5 - right.getStepZ() * 0.5 - facing.getStepZ() * depth);
        int width = fallback ? 1 : panel.width(), height = fallback ? 1 : panel.height();
        Vec3 end = origin.add(right.getStepX() * width, 0, right.getStepZ() * width);
        var offset = panel.getBlockPos().subtract(panel.controllerPos());
        float u0 = fallback ? (float) (offset.getX() * right.getStepX() + offset.getZ() * right.getStepZ()) / panel.width() : 0;
        float u1 = fallback ? u0 + 1f / panel.width() : 1;
        float v0 = fallback ? (float) offset.getY() / panel.height() : 0;
        float v1 = fallback ? v0 + 1f / panel.height() : 1;
        var consumer = buffers.getBuffer(alpha == 255 ? RenderType.entityCutoutNoCull(texture) : RenderType.entityTranslucent(texture));
        int illumination = panel.kind() == AdvertisementPanelBlock.Kind.SIGNAGE ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light;
        vertex(consumer, pose, origin, u0, v0, facing, illumination, alpha);
        vertex(consumer, pose, end, u1, v0, facing, illumination, alpha);
        vertex(consumer, pose, end.add(0, height, 0), u1, v1, facing, illumination, alpha);
        vertex(consumer, pose, origin.add(0, height, 0), u0, v1, facing, illumination, alpha);
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer consumer, PoseStack pose, Vec3 point,
                               float u, float v, net.minecraft.core.Direction facing, int light, int alpha) {
        consumer.addVertex(pose.last().pose(), (float) point.x, (float) point.y, (float) point.z).setColor(255, 255, 255, alpha)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose.last(), facing.getStepX(), 0, facing.getStepZ());
    }
    @Override public int getViewDistance() { return 128; }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(AdvertisementPanelBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }
}
