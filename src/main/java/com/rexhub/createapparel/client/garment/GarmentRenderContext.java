package com.rexhub.createapparel.client.garment;

import net.minecraft.client.model.HumanoidModel;

public record GarmentRenderContext(HumanoidModel<?> bodyPose, GarmentRenderSource apparel, TargetType targetType) {
    public enum TargetType {
        PLAYER, MANNEQUIN, CITIZEN
    }
}
