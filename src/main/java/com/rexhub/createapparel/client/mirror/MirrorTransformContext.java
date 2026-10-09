package com.rexhub.createapparel.client.mirror;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public record MirrorTransformContext(Object owner, Matrix4f localToCamera, Vec3 cameraPosition) {
    public static MirrorTransformContext capture(Object owner, Matrix4f renderPose,
                                                 Matrix4f inverseView, Vec3 cameraPosition) {
        var transform = new Matrix4f(inverseView).mul(renderPose);
        return new MirrorTransformContext(owner, transform, cameraPosition);
    }
}
