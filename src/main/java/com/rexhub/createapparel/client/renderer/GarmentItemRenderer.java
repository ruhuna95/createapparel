package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Rotation;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Uv;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class GarmentItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float MODEL_UNIT = 1.0F / 16.0F;
    private static final float SLEEVE_OFFSET = 6.5F;

    public GarmentItemRenderer(
            BlockEntityRenderDispatcher blockEntityRenderDispatcher,
            EntityModelSet entityModelSet
    ) {
        super(blockEntityRenderDispatcher, entityModelSet);
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        ClientGarmentAssets.INSTANCE.resolve(stack).ifPresent(garment -> {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.scale(MODEL_UNIT, MODEL_UNIT, MODEL_UNIT);
            for (ResolvedPart part : garment.parts()) {
                renderPart(part, garment.data(), poseStack, buffer, packedLight, packedOverlay);
            }
            poseStack.popPose();
        });
    }

    private static void renderPart(
            ResolvedPart resolvedPart,
            com.rexhub.createapparel.garment.GarmentData data,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        ResourceLocation texture = RuntimeGarmentTextureCache.get(
                resolvedPart.model(),
                resolvedPart.textures(),
                data
        );
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        poseStack.pushPose();
        applyPartOffset(resolvedPart, poseStack);
        for (Element element : resolvedPart.model().elements()) {
            poseStack.pushPose();
            applyRotation(element.rotation(), poseStack);
            renderElement(
                    element,
                    resolvedPart.model().textureWidth(),
                    resolvedPart.model().textureHeight(),
                    poseStack,
                    vertices,
                    packedLight,
                    packedOverlay
            );
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void applyPartOffset(ResolvedPart part, PoseStack poseStack) {
        poseStack.translate(0.0F, part.topAlignmentOffset(), 0.0F);
        String semanticName = part.semanticName();
        if (semanticName.startsWith("left_sleeve")) {
            poseStack.translate(-SLEEVE_OFFSET, 0.0F, 0.0F);
        } else if (semanticName.startsWith("right_sleeve")) {
            poseStack.translate(SLEEVE_OFFSET, 0.0F, 0.0F);
        }
    }

    private static void applyRotation(Rotation rotation, PoseStack poseStack) {
        if (rotation == null || rotation.angle() == 0.0F) {
            return;
        }
        Vec3 origin = rotation.origin();
        poseStack.translate(origin.x(), origin.y(), origin.z());
        poseStack.mulPose(switch (rotation.axis()) {
            case X -> com.mojang.math.Axis.XP.rotationDegrees(rotation.angle());
            case Y -> com.mojang.math.Axis.YP.rotationDegrees(rotation.angle());
            case Z -> com.mojang.math.Axis.ZP.rotationDegrees(rotation.angle());
        });
        poseStack.translate(-origin.x(), -origin.y(), -origin.z());
    }

    private static void renderElement(
            Element element,
            int textureWidth,
            int textureHeight,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay
    ) {
        Vec3 from = element.from();
        Vec3 to = element.to();
        element.faces().forEach((face, uv) -> renderFace(
                face,
                uv,
                from,
                to,
                textureWidth,
                textureHeight,
                poseStack,
                vertices,
                packedLight,
                packedOverlay
        ));
    }

    private static void renderFace(
            Face face,
            Uv uv,
            Vec3 from,
            Vec3 to,
            int textureWidth,
            int textureHeight,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay
    ) {
        float u0 = uv.u0() / textureWidth;
        float v0 = uv.v0() / textureHeight;
        float u1 = uv.u1() / textureWidth;
        float v1 = uv.v1() / textureHeight;
        switch (face) {
            case FRONT -> quad(
                    poseStack, vertices, packedLight, packedOverlay, 0.0F, 0.0F, -1.0F,
                    new Vertex(from.x(), from.y(), from.z(), u0, v1),
                    new Vertex(from.x(), to.y(), from.z(), u0, v0),
                    new Vertex(to.x(), to.y(), from.z(), u1, v0),
                    new Vertex(to.x(), from.y(), from.z(), u1, v1)
            );
            case BACK -> quad(
                    poseStack, vertices, packedLight, packedOverlay, 0.0F, 0.0F, 1.0F,
                    new Vertex(to.x(), from.y(), to.z(), u0, v1),
                    new Vertex(to.x(), to.y(), to.z(), u0, v0),
                    new Vertex(from.x(), to.y(), to.z(), u1, v0),
                    new Vertex(from.x(), from.y(), to.z(), u1, v1)
            );
            case LEFT -> quad(
                    poseStack, vertices, packedLight, packedOverlay, -1.0F, 0.0F, 0.0F,
                    new Vertex(from.x(), from.y(), to.z(), u0, v1),
                    new Vertex(from.x(), to.y(), to.z(), u0, v0),
                    new Vertex(from.x(), to.y(), from.z(), u1, v0),
                    new Vertex(from.x(), from.y(), from.z(), u1, v1)
            );
            case RIGHT -> quad(
                    poseStack, vertices, packedLight, packedOverlay, 1.0F, 0.0F, 0.0F,
                    new Vertex(to.x(), from.y(), from.z(), u0, v1),
                    new Vertex(to.x(), to.y(), from.z(), u0, v0),
                    new Vertex(to.x(), to.y(), to.z(), u1, v0),
                    new Vertex(to.x(), from.y(), to.z(), u1, v1)
            );
            case TOP -> quad(
                    poseStack, vertices, packedLight, packedOverlay, 0.0F, 1.0F, 0.0F,
                    new Vertex(from.x(), to.y(), from.z(), u0, v1),
                    new Vertex(from.x(), to.y(), to.z(), u0, v0),
                    new Vertex(to.x(), to.y(), to.z(), u1, v0),
                    new Vertex(to.x(), to.y(), from.z(), u1, v1)
            );
            case BOTTOM -> quad(
                    poseStack, vertices, packedLight, packedOverlay, 0.0F, -1.0F, 0.0F,
                    new Vertex(from.x(), from.y(), to.z(), u0, v1),
                    new Vertex(from.x(), from.y(), from.z(), u0, v0),
                    new Vertex(to.x(), from.y(), from.z(), u1, v0),
                    new Vertex(to.x(), from.y(), to.z(), u1, v1)
            );
        }
    }

    private static void quad(
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            float normalX,
            float normalY,
            float normalZ,
            Vertex... faceVertices
    ) {
        PoseStack.Pose pose = poseStack.last();
        for (Vertex vertex : faceVertices) {
            vertices.vertex(pose.pose(), vertex.x(), vertex.y(), vertex.z())
                    .color(255, 255, 255, 255)
                    .uv(vertex.u(), vertex.v())
                    .overlayCoords(packedOverlay)
                    .uv2(packedLight)
                    .normal(pose.normal(), normalX, normalY, normalZ)
                    .endVertex();
        }
    }

    private record Vertex(float x, float y, float z, float u, float v) {
    }
}
