package com.rexhub.createapparel.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rexhub.createapparel.entity.Mannequin;
import com.rexhub.createapparel.entity.MannequinForm;
import com.rexhub.createapparel.entity.PosePreset;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

public final class MannequinModel extends HumanoidModel<Mannequin> {
    private final ModelPart pedestal;
    private final ModelPart upperSupport;
    private final ModelPart headSupport;
    private final ModelPart lowerBody;
    private final ModelPart headBody;

    public MannequinModel(ModelPart root) {
        super(root);
        pedestal = root.getChild("pedestal");
        upperSupport = root.getChild("upper_support");
        headSupport = root.getChild("head_support");
        lowerBody = root.getChild("lower_body");
        headBody = root.getChild("head_body");
    }

    public static LayerDefinition createBodyLayer(boolean slim) {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        var root = mesh.getRoot();
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48)
                .addBox(-1.9F, 0, -2, 4, 12, 4), PartPose.offset(1.9F, 12, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-2.1F, 0, -2, 4, 12, 4), PartPose.offset(-1.9F, 12, 0));
        if (slim) {
            root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48)
                    .addBox(-1, -2, -2, 3, 12, 4), PartPose.offset(5, 2.5F, 0));
            root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16)
                    .addBox(-2, -2, -2, 3, 12, 4), PartPose.offset(-5, 2.5F, 0));
            root.getChild("body").addOrReplaceChild("bust", CubeListBuilder.create().texOffs(20, 20)
                    .addBox(-3.5F, 1.48F, -0.49F, 7, 4, 3),
                    PartPose.offsetAndRotation(0, -1, -0.9F, -(float) Math.PI / 6, 0, 0));
        }
        root.addOrReplaceChild("pedestal", CubeListBuilder.create().texOffs(0, 48)
                .addBox(-6, 24, -6, 12, 1, 12), PartPose.ZERO);
        float upperSupportHeight = MannequinForm.UPPER.supportHeightPixels();
        float headSupportHeight = MannequinForm.HEAD.supportHeightPixels();
        root.addOrReplaceChild("upper_support", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.75F, 24 - upperSupportHeight, -0.75F, 1.5F, upperSupportHeight, 1.5F), PartPose.ZERO);
        root.addOrReplaceChild("head_support", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.75F, 7 - headSupportHeight, -0.75F, 1.5F, headSupportHeight, 1.5F), PartPose.ZERO);
        root.addOrReplaceChild("lower_body", CubeListBuilder.create().texOffs(16, 23)
                .addBox(-4, 7, -2, 8, 5, 4), PartPose.ZERO);
        root.addOrReplaceChild("head_body", CubeListBuilder.create().texOffs(16, 16)
                .addBox(-4, 0, -2, 8, 2, 4), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Mannequin entity, float swing, float amount, float age, float headYaw, float headPitch) {
        apply(entity.variant().form(), entity.mannequinState().pose());
    }

    public void apply(MannequinForm form, PosePreset preset) {
        var pose = preset.rotations();
        rotate(head, form.hasHead() ? pose.head() : PosePreset.Rotation.ZERO);
        rotate(body, form.hasTorso() ? pose.body() : PosePreset.Rotation.ZERO);
        rotate(leftArm, form.hasTorso() ? pose.leftArm() : PosePreset.Rotation.ZERO);
        rotate(rightArm, form.hasTorso() ? pose.rightArm() : PosePreset.Rotation.ZERO);
        rotate(leftLeg, form.hasLegs() ? pose.leftLeg() : PosePreset.Rotation.ZERO);
        rotate(rightLeg, form.hasLegs() ? pose.rightLeg() : PosePreset.Rotation.ZERO);
        head.visible = form.hasHead();
        hat.visible = false;
        body.visible = leftArm.visible = rightArm.visible = form.hasTorso();
        leftLeg.visible = rightLeg.visible = form.hasLegs();
        upperSupport.visible = form == MannequinForm.UPPER;
        headSupport.visible = form == MannequinForm.HEAD;
        lowerBody.visible = form == MannequinForm.LOWER;
        headBody.visible = form == MannequinForm.HEAD;
        pedestal.y = form.modelOriginPixels() - 25;
        crouching = riding = young = false;
    }

    private static void rotate(ModelPart part, PosePreset.Rotation rotation) {
        HumanoidPose.rotate(part, rotation);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertices, int light, int overlay,
                               int color) {
        super.renderToBuffer(poseStack, vertices, light, overlay, color);
        lowerBody.render(poseStack, vertices, light, overlay, color);
        headBody.render(poseStack, vertices, light, overlay, color);
    }

    public void renderSupport(PoseStack poseStack, VertexConsumer vertices, int light, int overlay) {
        pedestal.render(poseStack, vertices, light, overlay);
        upperSupport.render(poseStack, vertices, light, overlay);
        headSupport.render(poseStack, vertices, light, overlay);
    }
}
