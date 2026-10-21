package com.rexhub.createapparel.client.garment;

import net.minecraft.client.model.HumanoidModel;

public record GarmentRenderContext(HumanoidModel<?> bodyPose, GarmentRenderSource apparel,
                                   int lod, @javax.annotation.Nullable ApparelRenderFrame preparedFrame) {
    public GarmentRenderContext(HumanoidModel<?> bodyPose, GarmentRenderSource apparel) {
        this(bodyPose, apparel, 0, null);
    }
}
