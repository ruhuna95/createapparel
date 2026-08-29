package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedSupportPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Rotation;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Uv;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class CommonGarmentRenderer {
    private static final float MODEL_UNIT = 1.0F / 16.0F;
    private static final float BODY_HEIGHT = 24.0F;
    private static final float SLEEVE_OFFSET = 6.5F;
    private static final int WHITE = 0xFFFFFF;

    private CommonGarmentRenderer() {
    }

    public static void renderItem(
            ResolvedGarment garment,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.scale(MODEL_UNIT, MODEL_UNIT, MODEL_UNIT);
        for (ResolvedPart part : garment.parts()) {
            PartMapping mapping = PartMapping.item(part);
            renderPart(
                    part.model(),
                    RuntimeGarmentTextureCache.get(part.model(), part.textures(), garment.data()),
                    mapping,
                    null,
                    poseStack,
                    buffer,
                    packedLight,
                    packedOverlay,
                    WHITE
            );
        }
        poseStack.popPose();
    }

    public static void renderHumanoidGarment(
            ResolvedGarment garment,
            PlayerModel<AbstractClientPlayer> playerModel,
            ArmProfile armProfile,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        poseStack.pushPose();
        poseStack.scale(MODEL_UNIT, MODEL_UNIT, MODEL_UNIT);
        for (ResolvedPart part : garment.parts()) {
            PartMapping mapping = PartMapping.humanoidGarment(part);
            renderPart(
                    part.model(),
                    RuntimeGarmentTextureCache.get(part.model(), part.textures(), garment.data()),
                    mapping,
                    humanoidPose(playerModel, armProfile, part.model(), mapping),
                    poseStack,
                    buffer,
                    packedLight,
                    packedOverlay,
                    WHITE
            );
        }
        poseStack.popPose();
    }

    public static void renderHumanoidSupport(
            List<ResolvedSupportPart> parts,
            PlayerModel<AbstractClientPlayer> playerModel,
            ArmProfile armProfile,
            int tintRgb,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        poseStack.pushPose();
        poseStack.scale(MODEL_UNIT, MODEL_UNIT, MODEL_UNIT);
        for (ResolvedSupportPart part : parts) {
            PartMapping mapping = PartMapping.humanoidSupport();
            renderPart(
                    part.model(),
                    textureResource(part.texture()),
                    mapping,
                    humanoidPose(playerModel, armProfile, part.model(), mapping),
                    poseStack,
                    buffer,
                    packedLight,
                    packedOverlay,
                    tintRgb
            );
        }
        poseStack.popPose();
    }

    private static void renderPart(
            GarmentPartModel part,
            ResourceLocation texture,
            PartMapping mapping,
            HumanoidPartPose humanoidPose,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay,
            int tintRgb
    ) {
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        poseStack.pushPose();
        if (humanoidPose != null) {
            humanoidPose.apply(poseStack);
        }
        for (Element element : part.elements()) {
            poseStack.pushPose();
            applyRotation(element.rotation(), mapping, poseStack);
            renderElement(
                    element,
                    part.textureWidth(),
                    part.textureHeight(),
                    mapping,
                    poseStack,
                    vertices,
                    packedLight,
                    packedOverlay,
                    tintRgb
            );
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static HumanoidPartPose humanoidPose(
            PlayerModel<AbstractClientPlayer> model,
            ArmProfile armProfile,
            GarmentPartModel part,
            PartMapping mapping
    ) {
        Element reference = part.elements().get(0);
        float centerX = mapping.x((reference.from().x() + reference.to().x()) * 0.5F);
        String name = reference.name();
        if (name.contains("arm") || name.contains("sleeve")) {
            float armY = armProfile == ArmProfile.SLIM ? 2.5F : 2.0F;
            return centerX < 0.0F
                    ? new HumanoidPartPose(model.rightArm, -5.0F, armY, 0.0F)
                    : new HumanoidPartPose(model.leftArm, 5.0F, armY, 0.0F);
        }
        if (name.contains("leg")) {
            return centerX < 0.0F
                    ? new HumanoidPartPose(model.rightLeg, -1.9F, 12.0F, 0.0F)
                    : new HumanoidPartPose(model.leftLeg, 1.9F, 12.0F, 0.0F);
        }
        return new HumanoidPartPose(model.body, 0.0F, 0.0F, 0.0F);
    }

    private static void applyRotation(Rotation rotation, PartMapping mapping, PoseStack poseStack) {
        if (rotation == null || rotation.angle() == 0.0F) {
            return;
        }
        Vec3 origin = mapping.point(rotation.origin());
        poseStack.translate(origin.x(), origin.y(), origin.z());
        float angle = mapping.humanoid()
                && rotation.axis() != com.rexhub.createapparel.client.garment.ClientGarmentAssets.Axis.Y
                ? -rotation.angle()
                : rotation.angle();
        poseStack.mulPose(switch (rotation.axis()) {
            case X -> Axis.XP.rotationDegrees(angle);
            case Y -> Axis.YP.rotationDegrees(angle);
            case Z -> Axis.ZP.rotationDegrees(angle);
        });
        poseStack.translate(-origin.x(), -origin.y(), -origin.z());
    }

    private static void renderElement(
            Element element,
            int textureWidth,
            int textureHeight,
            PartMapping mapping,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            int tintRgb
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
                mapping,
                poseStack,
                vertices,
                packedLight,
                packedOverlay,
                tintRgb
        ));
    }

    private static void renderFace(
            Face face,
            Uv uv,
            Vec3 from,
            Vec3 to,
            int textureWidth,
            int textureHeight,
            PartMapping mapping,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            int tintRgb
    ) {
        float u0 = uv.u0() / textureWidth;
        float v0 = uv.v0() / textureHeight;
        float u1 = uv.u1() / textureWidth;
        float v1 = uv.v1() / textureHeight;
        switch (face) {
            case FRONT -> quad(
                    mapping, poseStack, vertices, packedLight, packedOverlay, tintRgb, 0.0F, 0.0F, -1.0F,
                    new Vertex(from.x(), from.y(), from.z(), u0, v1),
                    new Vertex(from.x(), to.y(), from.z(), u0, v0),
                    new Vertex(to.x(), to.y(), from.z(), u1, v0),
                    new Vertex(to.x(), from.y(), from.z(), u1, v1)
            );
            case BACK -> quad(
                    mapping, poseStack, vertices, packedLight, packedOverlay, tintRgb, 0.0F, 0.0F, 1.0F,
                    new Vertex(to.x(), from.y(), to.z(), u0, v1),
                    new Vertex(to.x(), to.y(), to.z(), u0, v0),
                    new Vertex(from.x(), to.y(), to.z(), u1, v0),
                    new Vertex(from.x(), from.y(), to.z(), u1, v1)
            );
            case LEFT -> quad(
                    mapping, poseStack, vertices, packedLight, packedOverlay, tintRgb, -1.0F, 0.0F, 0.0F,
                    new Vertex(from.x(), from.y(), to.z(), u0, v1),
                    new Vertex(from.x(), to.y(), to.z(), u0, v0),
                    new Vertex(from.x(), to.y(), from.z(), u1, v0),
                    new Vertex(from.x(), from.y(), from.z(), u1, v1)
            );
            case RIGHT -> quad(
                    mapping, poseStack, vertices, packedLight, packedOverlay, tintRgb, 1.0F, 0.0F, 0.0F,
                    new Vertex(to.x(), from.y(), from.z(), u0, v1),
                    new Vertex(to.x(), to.y(), from.z(), u0, v0),
                    new Vertex(to.x(), to.y(), to.z(), u1, v0),
                    new Vertex(to.x(), from.y(), to.z(), u1, v1)
            );
            case TOP -> quad(
                    mapping, poseStack, vertices, packedLight, packedOverlay, tintRgb, 0.0F, 1.0F, 0.0F,
                    new Vertex(from.x(), to.y(), from.z(), u0, v1),
                    new Vertex(from.x(), to.y(), to.z(), u0, v0),
                    new Vertex(to.x(), to.y(), to.z(), u1, v0),
                    new Vertex(to.x(), to.y(), from.z(), u1, v1)
            );
            case BOTTOM -> quad(
                    mapping, poseStack, vertices, packedLight, packedOverlay, tintRgb, 0.0F, -1.0F, 0.0F,
                    new Vertex(from.x(), from.y(), to.z(), u0, v1),
                    new Vertex(from.x(), from.y(), from.z(), u0, v0),
                    new Vertex(to.x(), from.y(), from.z(), u1, v0),
                    new Vertex(to.x(), from.y(), to.z(), u1, v1)
            );
        }
    }

    private static void quad(
            PartMapping mapping,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            int tintRgb,
            float normalX,
            float normalY,
            float normalZ,
            Vertex... faceVertices
    ) {
        PoseStack.Pose pose = poseStack.last();
        int red = tintRgb >> 16 & 255;
        int green = tintRgb >> 8 & 255;
        int blue = tintRgb & 255;
        float mappedNormalY = mapping.humanoid() ? -normalY : normalY;
        for (Vertex vertex : faceVertices) {
            Vec3 position = mapping.point(new Vec3(vertex.x(), vertex.y(), vertex.z()));
            vertices.vertex(pose.pose(), position.x(), position.y(), position.z())
                    .color(red, green, blue, 255)
                    .uv(vertex.u(), vertex.v())
                    .overlayCoords(packedOverlay)
                    .uv2(packedLight)
                    .normal(pose.normal(), normalX, mappedNormalY, normalZ)
                    .endVertex();
        }
    }

    private static ResourceLocation textureResource(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png");
    }

    private record HumanoidPartPose(ModelPart part, float restX, float restY, float restZ) {
        private void apply(PoseStack poseStack) {
            poseStack.translate(part.x, part.y, part.z);
            if (part.zRot != 0.0F) {
                poseStack.mulPose(Axis.ZP.rotation(part.zRot));
            }
            if (part.yRot != 0.0F) {
                poseStack.mulPose(Axis.YP.rotation(part.yRot));
            }
            if (part.xRot != 0.0F) {
                poseStack.mulPose(Axis.XP.rotation(part.xRot));
            }
            poseStack.translate(-restX, -restY, -restZ);
        }
    }

    private record PartMapping(boolean humanoid, float xOffset, float yOffset, float yAnchor) {
        private static PartMapping item(ResolvedPart part) {
            return new PartMapping(false, sleeveOffset(part.semanticName()), part.topAlignmentOffset(), 0.0F);
        }

        private static PartMapping humanoidGarment(ResolvedPart part) {
            float referenceTop = part.model().upperY() + part.topAlignmentOffset();
            return new PartMapping(true, sleeveOffset(part.semanticName()), part.topAlignmentOffset(), referenceTop);
        }

        private static PartMapping humanoidSupport() {
            return new PartMapping(true, 0.0F, 0.0F, BODY_HEIGHT);
        }

        private Vec3 point(Vec3 point) {
            return new Vec3(x(point.x()), y(point.y()), point.z());
        }

        private float x(float x) {
            return x + xOffset;
        }

        private float y(float y) {
            return humanoid ? yAnchor - (y + yOffset) : y + yOffset;
        }

        private static float sleeveOffset(String semanticName) {
            if (semanticName.startsWith("left_sleeve")) {
                return -SLEEVE_OFFSET;
            }
            return semanticName.startsWith("right_sleeve") ? SLEEVE_OFFSET : 0.0F;
        }
    }

    private record Vertex(float x, float y, float z, float u, float v) {
    }
}
