package com.rexhub.createapparel.client.garment;

import net.minecraft.client.model.HumanoidModel;

public record GarmentRenderContext(HumanoidModel<?> bodyPose, GarmentRenderSource apparel, TargetType targetType,
                                   int lod, @javax.annotation.Nullable ApparelRenderFrame preparedFrame) {
    public GarmentRenderContext(HumanoidModel<?> bodyPose, GarmentRenderSource apparel, TargetType targetType) {
        this(bodyPose, apparel, targetType, 0, null);
    }
    public enum TargetType {
        PLAYER, MANNEQUIN, CITIZEN
    }
}
