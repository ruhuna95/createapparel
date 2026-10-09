package com.rexhub.createapparel.client.mirror;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

public final class MirrorReflectionCamera extends Camera {
    public void configure(BlockGetter level, Entity entity, float partialTick, Camera original, Vec3 origin, Vec3 normal) {
        setup(level, entity, true, false, partialTick);
        setPosition(MirrorReflectionMath.reflectPoint(original.getPosition(), origin, normal));
        Vec3 forward = MirrorReflectionMath.reflectDirection(new Vec3(original.getLookVector()), normal).normalize();
        Vec3 up = MirrorReflectionMath.reflectDirection(new Vec3(original.getUpVector()), normal).normalize();
        Vec3 left = up.cross(forward).normalize();
        setRotation((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)),
                (float) -Math.toDegrees(Math.asin(forward.y)));
        var basis = new Matrix3f().setColumn(0, vector(left)).setColumn(1, vector(up)).setColumn(2, vector(forward));
        rotation().setFromNormalized(basis);
        getLookVector().set(vector(forward));
        getUpVector().set(vector(up));
        getLeftVector().set(vector(left));
    }

    private static Vector3f vector(Vec3 value) { return new Vector3f((float) value.x, (float) value.y, (float) value.z); }
}
