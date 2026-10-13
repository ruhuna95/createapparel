package com.rexhub.createapparel.client.model;

import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.PosePreset;
import com.rexhub.createapparel.entity.CitizenLookMode;
import net.minecraft.util.Mth;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;

public final class CitizenModel extends HumanoidModel<Citizen> {
    public static final float BODY_SCALE = 0.9375F;

    public CitizenModel(ModelPart root) { super(root); }

    public static LayerDefinition createBodyLayer() {
        var mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -8, -4, 8, 8, 8), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("hat", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.25F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    public void apply(Citizen entity, float partialTick) {
        applyAge(entity.citizenState().age());
        head.setPos(0, 0, 0);
        double time = entity.level().getGameTime() + partialTick;
        double phase = entity.getId() * 2.399963229728653;
        head.yRot = (float) (Math.sin(time / 94 + phase) * Math.toRadians(12));
        head.xRot = (float) (Math.sin(time / 137 + phase * 1.7) * Math.toRadians(5));
        head.zRot = 0;
        hat.copyFrom(head);
        body.setRotation(0, 0, 0);
        rightArm.setRotation(0, 0, 0);
        leftArm.setRotation(0, 0, 0);
        rightLeg.setRotation(0, 0, 0);
        leftLeg.setRotation(0, 0, 0);
        boolean slim = entity.citizenState().appearance().armProfile() == com.rexhub.createapparel.apparel.ArmProfile.SLIM;
        rightArm.y = leftArm.y = slim ? 2.5F : 2;
        var behavior = entity.citizenState().behavior();
        if (behavior.mode() == CitizenBehaviorMode.POSE) {
            applyPose(behavior.pose());
        } else if (behavior.mode() == CitizenBehaviorMode.STAND && behavior.lookMode() != CitizenLookMode.IDLE) {
            head.yRot = Mth.wrapDegrees(Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot)
                    - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)) * Mth.DEG_TO_RAD;
            head.xRot = Mth.lerp(partialTick, entity.xRotO, entity.getXRot()) * Mth.DEG_TO_RAD;
            hat.copyFrom(head);
        } else if (behavior.mode().moves()) {
            float swing = entity.walkAnimation.position(partialTick);
            float amount = entity.walkAnimation.speed(partialTick);
            rightArm.xRot = net.minecraft.util.Mth.cos(swing * 0.6662F + (float) Math.PI) * amount;
            leftArm.xRot = net.minecraft.util.Mth.cos(swing * 0.6662F) * amount;
            rightLeg.xRot = net.minecraft.util.Mth.cos(swing * 0.6662F) * 1.4F * amount;
            leftLeg.xRot = net.minecraft.util.Mth.cos(swing * 0.6662F + (float) Math.PI) * 1.4F * amount;
        }
    }

    public void applyPose(PosePreset preset) {
        var pose = preset.rotations();
        HumanoidPose.rotate(head, pose.head());
        HumanoidPose.rotate(body, pose.body());
        HumanoidPose.rotate(leftArm, pose.leftArm());
        HumanoidPose.rotate(rightArm, pose.rightArm());
        HumanoidPose.rotate(leftLeg, pose.leftLeg());
        HumanoidPose.rotate(rightLeg, pose.rightLeg());
        hat.copyFrom(head);
    }

    public void applyAge(CitizenAge age) {
        head.xScale = head.yScale = head.zScale = age.headScale() / age.scale();
        hat.copyFrom(head);
    }
}
