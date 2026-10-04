package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.client.apparel.ApparelRenderSnapshot;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedSupportPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Rotation;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache;
import com.rexhub.createapparel.client.garment.FlatGarmentGeometry;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import com.rexhub.createapparel.client.garment.GarmentHemGeometry;
import com.rexhub.createapparel.client.garment.ApparelRenderFrame;
import com.rexhub.createapparel.client.garment.FirstPersonApparelState;
import com.rexhub.createapparel.client.garment.HudArmGeometry;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.BakedFace;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import com.rexhub.createapparel.garment.GarmentSlot;
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

    public static void renderFirstPersonArm(FirstPersonApparelState.ArmParts parts,
                                           ApparelRenderSnapshot snapshot, HumanoidArm side, ModelPart arm,
                                           PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        renderFirstPersonArm(parts, snapshot, side, arm, poseStack, buffer, light, overlay, false);
    }

    public static void renderFirstPersonArm(FirstPersonApparelState.ArmParts parts,
                                           ApparelRenderSnapshot snapshot, HumanoidArm side, ModelPart arm,
                                           PoseStack poseStack, MultiBufferSource buffer, int light, int overlay,
                                           boolean rolledSleeves) {
        poseStack.pushPose();
        arm.translateAndRotate(poseStack);
        poseStack.scale(MODEL_UNIT, MODEL_UNIT, MODEL_UNIT);
        poseStack.translate(side == HumanoidArm.RIGHT ? 5 : -5,
                snapshot.armProfile() == ArmProfile.SLIM ? -2.5F : -2.0F, 0);
        for (var part : parts.body()) {
            renderPart(part.model(), part.textureFile(), PartMapping.humanoidSupport(), null,
                    poseStack, buffer, light, overlay, snapshot.skinTone().tintRgb());
        }
        for (var part : parts.garments()) {
            if (rolledSleeves && !part.semanticName().contains("_hand_")) {
                var type = part.model().translucent() ? RenderType.entityTranslucent(snapshot.texture(part))
                        : RenderType.entityCutoutNoCull(snapshot.texture(part));
                renderFaces(HudArmGeometry.rolledSleeve(part.model()), part.model().textureWidth(), part.model().textureHeight(),
                        PartMapping.humanoidGarment(), poseStack, buffer.getBuffer(type), light, overlay, WHITE);
            } else {
                renderPart(part.model(), snapshot.texture(part), PartMapping.humanoidGarment(), null,
                        poseStack, buffer, light, overlay, WHITE);
            }
        }
        poseStack.popPose();
    }

    public static void renderItem(
            ResolvedGarment garment,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        var geometry = FlatGarmentGeometry.cached(garment.parts().stream().map(ResolvedPart::model).toList());
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        applyItemShape(poseStack, garment.definition().defaultSlot(), displayContext, geometry.scaleZ());
        renderCentered(garment, new Vec3(0, 0, 0), geometry.originalBounds().center(), 1,
                poseStack, buffer, packedLight, packedOverlay, RenderType::entityCutoutNoCull, RenderType::entityTranslucent,
                displayContext);
        poseStack.popPose();
    }

    private static void applyItemShape(PoseStack poseStack, GarmentSlot slot,
                                       ItemDisplayContext displayContext, float flatScaleZ) {
        if (slot == GarmentSlot.BACK && displayContext == ItemDisplayContext.GUI) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }
        if (slot == GarmentSlot.HEAD || slot == GarmentSlot.FACE || slot == GarmentSlot.BACK) {
            switch (displayContext) {
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                        THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND ->
                        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                default -> { }
            }
        } else if (slot != GarmentSlot.FEET) {
            poseStack.scale(1, 1, flatScaleZ);
        }
    }

    public static void renderHanger(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer,
                                    int packedLight, int packedOverlay) {
        ClientGarmentAssets.INSTANCE.resolve(stack).ifPresent(garment -> {
            var flat = FlatGarmentGeometry.cached(garment.parts().stream().map(ResolvedPart::model).toList());
            float height = flat.flattenedBounds().max().y() - flat.flattenedBounds().min().y();
            renderFlat(garment, new Vec3(0, -height * MODEL_UNIT / 2, 0),
                    poseStack, buffer, packedLight, packedOverlay);
        });
    }

    private static void renderFlat(ResolvedGarment garment, Vec3 anchor, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var flat = FlatGarmentGeometry.cached(garment.parts().stream().map(ResolvedPart::model).toList());
        renderCentered(garment, anchor, flat.originalBounds().center(), flat.scaleZ(),
                poseStack, buffer, packedLight, packedOverlay, RenderType::entityCutoutNoCull, RenderType::entityTranslucent);
    }

    public static void renderPreview(ResolvedGarment garment, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var geometry = FlatGarmentGeometry.cached(garment.parts().stream().map(ResolvedPart::model).toList());
        renderCentered(garment, new Vec3(0, 0, 0), geometry.originalBounds().center(), 1,
                poseStack, buffer, packedLight, packedOverlay, PreviewRenderType::garment, PreviewRenderType::translucentGarment);
    }

    private static void renderCentered(ResolvedGarment garment, Vec3 anchor, Vec3 center, float scaleZ,
                                       PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, int packedOverlay, Function<ResourceLocation, RenderType> renderType,
                                       Function<ResourceLocation, RenderType> translucentRenderType) {
        renderCentered(garment, anchor, center, scaleZ, poseStack, buffer, packedLight, packedOverlay,
                renderType, translucentRenderType, ItemDisplayContext.NONE);
    }

    private static void renderCentered(ResolvedGarment garment, Vec3 anchor, Vec3 center, float scaleZ,
                                       PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, int packedOverlay, Function<ResourceLocation, RenderType> renderType,
                                       Function<ResourceLocation, RenderType> translucentRenderType,
                                       ItemDisplayContext displayContext) {
        poseStack.pushPose();
        poseStack.translate(anchor.x(), anchor.y(), anchor.z());
        poseStack.scale(MODEL_UNIT, MODEL_UNIT, MODEL_UNIT);
        mirrorItemX(poseStack);
        poseStack.scale(1, 1, scaleZ);
        poseStack.translate(-center.x(), -center.y(), -center.z());
        for (ResolvedPart part : garment.parts()) {
            poseStack.pushPose();
            applyItemPartShape(poseStack, garment.definition().defaultSlot(), displayContext, part);
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
                    part.model().translucent() ? translucentRenderType : renderType
            );
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void applyItemPartShape(PoseStack poseStack, GarmentSlot slot,
                                           ItemDisplayContext displayContext, ResolvedPart part) {
        if (slot != GarmentSlot.BACK || !flattensBackStraps(displayContext)
                || !(part.semanticName().equals("torso") || part.semanticName().startsWith("torso_"))) {
            return;
        }
        var flat = FlatGarmentGeometry.cached(List.of(part.model()));
        float attachmentZ = part.semanticName().equals("torso_front")
                ? flat.originalBounds().min().z() : flat.originalBounds().max().z();
        poseStack.translate(0, 0, attachmentZ);
        poseStack.scale(1, 1, flat.scaleZ());
        poseStack.translate(0, 0, -attachmentZ);
    }

    private static boolean flattensBackStraps(ItemDisplayContext displayContext) {
        return switch (displayContext) {
            case GROUND, FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                    THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> true;
            default -> false;
        };
    }

    public static void renderHumanoidGarment(
            ResolvedGarment garment,
            GarmentSlot slot,
            ApparelRenderFrame frame,
            ApparelRenderSnapshot snapshot,
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
            ResourceLocation texture = snapshot.texture(part);
            if (frame.envelope(part.model()).isPresent()) {
                renderEnvelopePart(part.model(), texture, frame.envelope(part.model()).orElseThrow(),
                        humanoidGarmentPose(playerModel, armProfile, part), poseStack, buffer, packedLight, packedOverlay);
                continue;
            }
            if (frame.skirtSlope().isPresent() && part.model().hemGeometry().applies(
                    part.semanticName(), slot, frame.skirtSlope().get())) {
                renderHemPart(part.model(), texture, frame.skirtSlope().get().forSlot(slot),
                        humanoidGarmentPose(playerModel, armProfile, part), poseStack, buffer, packedLight, packedOverlay);
                continue;
            }
            renderPart(
                    part.model(),
                    texture,
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

    private static void renderEnvelopePart(GarmentPartModel part, ResourceLocation texture,
                                          com.rexhub.createapparel.client.garment.DualEnvelopeDeformer.Pose envelope,
                                          HumanoidPartPose humanoidPose, PoseStack poseStack, MultiBufferSource buffer,
                                          int packedLight, int packedOverlay) {
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        poseStack.pushPose();
        humanoidPose.apply(poseStack);
        Vector3f[] points = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        PoseStack.Pose pose = poseStack.last();
        for (var quad : envelope.quads()) {
            for (int i = 0; i < 4; i++) envelope.shift(quad.points().get(i), points[i]);
            float ux = points[1].x - points[0].x, uy = points[1].y - points[0].y, uz = points[1].z - points[0].z;
            float vx = points[2].x - points[0].x, vy = points[2].y - points[0].y, vz = points[2].z - points[0].z;
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 0.000001F) continue;
            for (int i = 0; i < 4; i++) {
                var point = points[i];
                var uv = quad.uv().get(i);
                vertices.vertex(pose.pose(), point.x, BODY_HEIGHT - point.y, point.z)
                        .color(255, 255, 255, 255).uv(uv.u() / part.textureWidth(), uv.v() / part.textureHeight())
                        .overlayCoords(packedOverlay).uv2(packedLight)
                        .normal(pose.normal(), nx / length, -ny / length, nz / length).endVertex();
            }
        }
        poseStack.popPose();
    }

    private static void renderHemPart(GarmentPartModel part, ResourceLocation texture, GarmentHemGeometry.Slope slope,
                                      HumanoidPartPose humanoidPose, PoseStack poseStack, MultiBufferSource buffer,
                                      int packedLight, int packedOverlay) {
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        poseStack.pushPose();
        humanoidPose.apply(poseStack);
        Vector3f[] points = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        PoseStack.Pose pose = poseStack.last();
        for (var quad : part.hemGeometry().quads(slope)) {
            for (int i = 0; i < 4; i++) {
                slope.shift(quad.points().get(i), quad.depths().get(i), points[i]);
            }
            float ux = points[1].x - points[0].x, uy = points[1].y - points[0].y, uz = points[1].z - points[0].z;
            float vx = points[2].x - points[0].x, vy = points[2].y - points[0].y, vz = points[2].z - points[0].z;
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            for (int i = 0; i < 4; i++) {
                var point = points[i];
                var uv = quad.uv().get(i);
                vertices.vertex(pose.pose(), point.x, BODY_HEIGHT - point.y, point.z)
                        .color(255, 255, 255, 255)
                        .uv(uv.u() / part.textureWidth(), uv.v() / part.textureHeight())
                        .overlayCoords(packedOverlay).uv2(packedLight)
                        .normal(pose.normal(), nx / length, -ny / length, nz / length).endVertex();
            }
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
                    part.textureFile(),
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
                tintRgb, part.translucent() ? RenderType::entityTranslucent : RenderType::entityCutoutNoCull);
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
            boolean rotated = element.rotation() != null && element.rotation().angle() != 0;
            if (rotated) {
                poseStack.pushPose();
                applyRotation(element.rotation(), mapping, poseStack);
            }
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
            if (rotated) {
                poseStack.popPose();
            }
        }
        poseStack.popPose();
    }

    private static HumanoidPartPose humanoidGarmentPose(
            PlayerModel<AbstractClientPlayer> model,
            ArmProfile armProfile,
            ResolvedPart part
    ) {
        String semanticName = part.semanticName();
        if (semanticName.equals("head") || semanticName.equals("head_back") || semanticName.equals("head_up") || semanticName.equals("hood_up")) {
            return new HumanoidPartPose(model.head, 0.0F, 0.0F, 0.0F);
        }
        if (FirstPersonApparelState.isGarmentArm(semanticName)) {
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
        renderFaces(element.bakedFaces(), textureWidth, textureHeight, mapping, poseStack, vertices,
                packedLight, packedOverlay, tintRgb);
    }

    private static void renderFaces(List<BakedFace> faces, int textureWidth, int textureHeight, PartMapping mapping,
                                    PoseStack poseStack, VertexConsumer vertices, int packedLight, int packedOverlay,
                                    int tintRgb) {
        PoseStack.Pose pose = poseStack.last();
        int red = tintRgb >> 16 & 255;
        int green = tintRgb >> 8 & 255;
        int blue = tintRgb & 255;
        for (var face : faces) {
            Vec3 normal = face.normal();
            float normalY = mapping.humanoid() ? -normal.y() : normal.y();
            for (var vertex : face.vertices()) {
                float y = mapping.humanoid() ? BODY_HEIGHT - vertex.y() : vertex.y();
                vertices.vertex(pose.pose(), vertex.x(), y, vertex.z())
                        .color(red, green, blue, 255)
                        .uv(vertex.u() / textureWidth, vertex.v() / textureHeight)
                        .overlayCoords(packedOverlay).uv2(packedLight)
                        .normal(pose.normal(), normal.x(), normalY, normal.z()).endVertex();
            }
        }
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
        private static final PartMapping ITEM = new PartMapping(false);
        private static final PartMapping HUMANOID = new PartMapping(true);

        private static PartMapping item() {
            return ITEM;
        }

        private static PartMapping humanoidGarment() {
            return HUMANOID;
        }

        private static PartMapping humanoidSupport() {
            return HUMANOID;
        }

        private Vec3 point(Vec3 point) {
            return humanoid
                    ? new Vec3(point.x(), BODY_HEIGHT - point.y(), point.z())
                    : point;
        }
    }

}
