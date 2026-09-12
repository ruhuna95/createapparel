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
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.TexturePoint;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Uv;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache;
import com.rexhub.createapparel.client.garment.FlatGarmentGeometry;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import net.minecraft.world.item.ItemStack;
import com.rexhub.createapparel.client.garment.GarmentElementGeometry;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Function;

public final class CommonGarmentRenderer {
    private static final float MODEL_UNIT = 1.0F / 16.0F;
    private static final float BODY_HEIGHT = 24.0F;
    private static final Matrix4f ITEM_X_REFLECTION = new Matrix4f().scaling(-1.0F, 1.0F, 1.0F);
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
        renderFlat(garment, new Vec3(0.5F, 0.5F, 0.5F), poseStack, buffer, packedLight, packedOverlay);
    }

    public static void renderHanger(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer,
                                    int packedLight, int packedOverlay) {
        ClientGarmentAssets.INSTANCE.resolve(stack).ifPresent(garment -> {
            var flat = FlatGarmentGeometry.fromParts(garment.parts().stream().map(ResolvedPart::model).toList());
            float height = flat.flattenedBounds().max().y() - flat.flattenedBounds().min().y();
            renderFlat(garment, new Vec3(0, -height * MODEL_UNIT / 2, 0),
                    poseStack, buffer, packedLight, packedOverlay);
        });
    }

    private static void renderFlat(ResolvedGarment garment, Vec3 anchor, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var flat = FlatGarmentGeometry.fromParts(garment.parts().stream().map(ResolvedPart::model).toList());
        renderCentered(garment, anchor, flat.originalBounds().center(), flat.scaleZ(),
                poseStack, buffer, packedLight, packedOverlay, RenderType::entityCutoutNoCull);
    }

    public static void renderPreview(ResolvedGarment garment, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var geometry = FlatGarmentGeometry.fromParts(garment.parts().stream().map(ResolvedPart::model).toList());
        renderCentered(garment, new Vec3(0, 0, 0), geometry.originalBounds().center(), 1,
                poseStack, buffer, packedLight, packedOverlay, PreviewRenderType::garment);
    }

    private static void renderCentered(ResolvedGarment garment, Vec3 anchor, Vec3 center, float scaleZ,
                                       PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, int packedOverlay, Function<ResourceLocation, RenderType> renderType) {
        poseStack.pushPose();
        poseStack.translate(anchor.x(), anchor.y(), anchor.z());
        poseStack.scale(MODEL_UNIT, MODEL_UNIT, MODEL_UNIT);
        mirrorItemX(poseStack);
        poseStack.scale(1, 1, scaleZ);
        poseStack.translate(-center.x(), -center.y(), -center.z());
        for (ResolvedPart part : garment.parts()) {
            renderPart(
                    part.model(),
                    RuntimeGarmentTextureCache.get(part.model(), part.textures(), garment.data()),
                    PartMapping.item(),
                    null,
                    poseStack,
                    buffer,
                    packedLight,
                    packedOverlay,
                    WHITE,
                    renderType
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
            PartMapping mapping = PartMapping.humanoidGarment();
            renderPart(
                    part.model(),
                    RuntimeGarmentTextureCache.get(part.model(), part.textures(), garment.data()),
                    mapping,
                    humanoidGarmentPose(playerModel, armProfile, part),
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
                    humanoidSupportPose(playerModel, armProfile, part.model()),
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
        renderPart(part, texture, mapping, humanoidPose, poseStack, buffer, packedLight, packedOverlay,
                tintRgb, RenderType::entityCutoutNoCull);
    }

    private static void renderPart(
            GarmentPartModel part, ResourceLocation texture, PartMapping mapping, HumanoidPartPose humanoidPose,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay, int tintRgb,
            Function<ResourceLocation, RenderType> renderType
    ) {
        VertexConsumer vertices = buffer.getBuffer(renderType.apply(texture));
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

    private static HumanoidPartPose humanoidGarmentPose(
            PlayerModel<AbstractClientPlayer> model,
            ArmProfile armProfile,
            ResolvedPart part
    ) {
        String semanticName = part.semanticName();
        if (semanticName.equals("hood_up")) {
            return new HumanoidPartPose(model.head, 0.0F, 0.0F, 0.0F);
        }
        if (semanticName.startsWith("left_sleeve") || semanticName.startsWith("right_sleeve")) {
            float armY = armProfile == ArmProfile.SLIM ? 2.5F : 2.0F;
            float centerX = partCenterX(part.model());
            return centerX < 0.0F
                    ? new HumanoidPartPose(model.rightArm, -5.0F, armY, 0.0F)
                    : new HumanoidPartPose(model.leftArm, 5.0F, armY, 0.0F);
        }
        if (semanticName.contains("leg")) {
            return partCenterX(part.model()) < 0.0F
                    ? new HumanoidPartPose(model.rightLeg, -1.9F, 12.0F, 0.0F)
                    : new HumanoidPartPose(model.leftLeg, 1.9F, 12.0F, 0.0F);
        }
        return new HumanoidPartPose(model.body, 0.0F, 0.0F, 0.0F);
    }

    private static HumanoidPartPose humanoidSupportPose(
            PlayerModel<AbstractClientPlayer> model,
            ArmProfile armProfile,
            GarmentPartModel part
    ) {
        Element reference = part.elements().get(0);
        float centerX = (reference.from().x() + reference.to().x()) * 0.5F;
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

    private static float partCenterX(GarmentPartModel part) {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        for (Element element : part.elements()) {
            minX = Math.min(minX, Math.min(element.from().x(), element.to().x()));
            maxX = Math.max(maxX, Math.max(element.from().x(), element.to().x()));
        }
        return (minX + maxX) * 0.5F;
    }

    private static void mirrorItemX(PoseStack poseStack) {
        poseStack.mulPoseMatrix(ITEM_X_REFLECTION);
        poseStack.last().normal().scale(-1.0F, 1.0F, 1.0F);
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
        element.faces().forEach((face, uv) -> renderFace(
                face,
                uv,
                element,
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
        List<Vec3> points = GarmentElementGeometry.faceVertices(element, face);
        Vec3 normal = GarmentElementGeometry.normal(points);
        Vertex[] faceVertices = new Vertex[4];
        for (int index = 0; index < faceVertices.length; index++) {
            TexturePoint texturePoint = uv.corners().get(index);
            faceVertices[index] = vertex(
                    points.get(index), texturePoint.u() / textureWidth, texturePoint.v() / textureHeight
            );
        }
        quad(
                mapping, poseStack, vertices, packedLight, packedOverlay, tintRgb,
                normal.x(), normal.y(), normal.z(), faceVertices
        );
    }

    private static Vertex vertex(Vec3 point, float u, float v) {
        return new Vertex(point.x(), point.y(), point.z(), u, v);
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

    private record PartMapping(boolean humanoid) {
        private static PartMapping item() {
            return new PartMapping(false);
        }

        private static PartMapping humanoidGarment() {
            return new PartMapping(true);
        }

        private static PartMapping humanoidSupport() {
            return new PartMapping(true);
        }

        private Vec3 point(Vec3 point) {
            return humanoid
                    ? new Vec3(point.x(), BODY_HEIGHT - point.y(), point.z())
                    : point;
        }
    }

    private record Vertex(float x, float y, float z, float u, float v) {
    }
}
