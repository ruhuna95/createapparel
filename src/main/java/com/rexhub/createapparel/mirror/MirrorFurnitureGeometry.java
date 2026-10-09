package com.rexhub.createapparel.mirror;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class MirrorFurnitureGeometry {
    public static final float FULL_TILT = 10;
    public static final float TABLE_TILT = 15;
    private MirrorFurnitureGeometry() { }

    public static Matrix4f tilt(float degrees, float pivotY, float pivotZ) {
        return new Matrix4f().translation(0.5F, pivotY / 16, pivotZ / 16)
                .rotateX((float) Math.toRadians(degrees)).translate(-0.5F, -pivotY / 16, -pivotZ / 16);
    }

    public static MirrorGeometry fullSurface() {
        return MirrorGeometry.north(2 / 16.0, 1 / 16.0, 14 / 16.0, 29 / 16.0, 7 / 16.0 - 0.0005)
                .transform(tilt(FULL_TILT, 0, 9));
    }

    public static MirrorGeometry tableSurface() {
        return MirrorGeometry.north(4 / 16.0, 1 / 16.0, 12 / 16.0, 9 / 16.0, 7 / 16.0 - 0.0005)
                .transform(tilt(TABLE_TILT, 0, 9));
    }

    public static Vec3 supportAttachment(boolean full) {
        var point = new Vector3f(8 / 16F, (full ? 15 : 5) / 16F, 9 / 16F);
        tilt(full ? FULL_TILT : TABLE_TILT, 0, 9).transformPosition(point);
        return new Vec3(point).scale(16);
    }
}
