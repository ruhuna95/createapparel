package com.rexhub.createapparel.client.mirror;

import com.rexhub.createapparel.mirror.MirrorGeometry;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MirrorTransformContextTest {
    @Test void capturesFinalRenderTransformWithoutApplyingShipOrContraptionTwice() {
        var view = new Matrix4f().rotateX(0.2F).rotateY(0.5F);
        var composed = new Matrix4f().translation(3, 4, 5).rotateY(0.7F).rotateX(-0.3F);
        var pose = new Matrix4f(view).mul(composed);
        var context = MirrorTransformContext.capture("create_on_ship",
                pose, new Matrix4f(view).invert(), new Vec3(10000000, 64, 10000000));
        assertTrue(composed.equals(context.localToCamera(), 0.00001F));
        var local = MirrorGeometry.north(0, 0, 1, 1, 0.95);
        var world = local.transform(context.localToCamera()).move(context.cameraPosition());
        assertEquals(0, world.normal().distanceTo(local.transform(composed).normal()), 0.00001);
        assertTrue(world.vertices().get(0).x > 10000000);
        assertTrue(world.bounds().getXsize() > 0.5);
    }
}
