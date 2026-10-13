package com.rexhub.createapparel.client.model;

import com.rexhub.createapparel.entity.PosePreset;
import net.minecraft.client.model.geom.ModelPart;

final class HumanoidPose {
    private HumanoidPose() { }

    static void rotate(ModelPart part, PosePreset.Rotation rotation) {
        float radians = (float) Math.PI / 180;
        part.setRotation(rotation.x() * radians, rotation.y() * radians, rotation.z() * radians);
    }
}
