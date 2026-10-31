package com.rexhub.createapparel.client.mirror;

import com.rexhub.createapparel.mirror.MirrorGeometry;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MirrorTransformContextTest {
    @Test void finalCompositePoseWithNonUniformScaleIsAppliedExactlyOnce() {
        var view = new Matrix4f().rotateX(0.2F).rotateY(0.5F);
        var composed = new Matrix4f().translation(3, 4, 5).rotateY(0.7F).scale(2, 3, 0.5F).rotateX(-0.3F);
        var context = MirrorTransformContext.capture("create_on_ship", composed, view, new Matrix4f(view).invert(), Vec3.ZERO);
        assertTrue(composed.equals(context.localToCamera(), 0.00001F));
        var result = MirrorGeometry.north(0, 0, 1, 1, 0).transform(context.localToCamera());
        var expected = new Matrix3f(composed).invert().transpose().transform(new Vector3f(0, 0, -1)).normalize();
        assertEquals(0, result.normal().distanceTo(new Vec3(expected)), 0.00001);
        assertEquals(0, result.vertices().get(1).subtract(result.origin()).normalize().dot(result.normal()), 0.00001);
    }
    @Test void capturesFinalRenderTransformWithoutApplyingShipOrContraptionTwice() {
        var view = new Matrix4f().rotateX(0.2F).rotateY(0.5F);
        var composed = new Matrix4f().translation(3, 4, 5).rotateY(0.7F).rotateX(-0.3F);
        var pose = new Matrix4f(composed);
        var context = MirrorTransformContext.capture("create_on_ship",
                pose, view, new Matrix4f(view).invert(), new Vec3(10000000, 64, 10000000));
        assertTrue(composed.equals(context.localToCamera(), 0.00001F));
        var local = MirrorGeometry.north(0, 0, 1, 1, 0.95);
        var world = local.transform(context.localToCamera()).move(context.cameraPosition());
        assertEquals(0, world.normal().distanceTo(local.transform(composed).normal()), 0.00001);
        assertTrue(world.vertices().get(0).x > 10000000);
        assertTrue(world.bounds().getXsize() > 0.5);
    }

    @Test void separateNativeModelViewKeepsTranslatedMirrorFacingTheViewer() {
        var view = new Matrix4f().rotateX((float) Math.toRadians(5)).rotateY((float) Math.PI);
        var eye = new Vec3(24, -56.38, -10);
        var pos = new Vec3(21, -60, 16);
        var pose = new Matrix4f().translation((float) (pos.x - eye.x), (float) (pos.y - eye.y), (float) (pos.z - eye.z));
        var context = MirrorTransformContext.capture("world", pose, view, new Matrix4f(view).invert(), eye);
        var geometry = MirrorGeometry.north(0, 0, 1, 1, 0).transform(context.localToCamera()).move(eye);
        assertEquals(0, geometry.origin().distanceTo(pos.add(1, 0, 0)), 0.00001);
        assertEquals(0, geometry.normal().distanceTo(new Vec3(0, 0, -1)), 0.00001);
        assertTrue(eye.subtract(geometry.origin()).dot(geometry.normal()) > 0);
    }
}
