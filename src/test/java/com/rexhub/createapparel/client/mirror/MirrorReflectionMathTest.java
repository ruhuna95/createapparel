package com.rexhub.createapparel.client.mirror;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MirrorReflectionMathTest {
    @Test void recursiveVisibilityRejectsFacesEntirelyBehindTheParentMirrorPlane() {
        var projection = new Matrix4f().perspective(1.2F, 1.5F, 0.05F, 128);
        var clipped = MirrorReflectionMath.clippedProjection(projection, new Matrix4f(), new Vec3(0, 0, -3), new Vec3(0, 0, -1));
        var hidden = com.rexhub.createapparel.mirror.MirrorGeometry.north(-0.5, -0.5, 0.5, 0.5, -2.9995);
        var visible = hidden.move(new Vec3(0, 0, -0.01));
        assertTrue(MirrorReflectionMath.visiblePolygon(hidden, Vec3.ZERO, clipped).isEmpty());
        assertEquals(4, MirrorReflectionMath.visiblePolygon(visible, Vec3.ZERO, clipped).size());
    }

    @Test void facesCrossingTheCameraPlaneAreClippedInsteadOfBecomingFullScreen() {
        var projection = new Matrix4f().perspective(1.2F, 1.5F, 0.05F, 128);
        var geometry = new com.rexhub.createapparel.mirror.MirrorGeometry(java.util.List.of(
                new Vec3(-0.1, -0.1, -1), new Vec3(0.1, -0.1, 0.1), new Vec3(0.1, 0.1, 0.1), new Vec3(-0.1, 0.1, -1)), new Vec3(0, 0, -1));
        var polygon = MirrorReflectionMath.visiblePolygon(geometry, Vec3.ZERO, projection);
        assertFalse(polygon.isEmpty());
        for (var vertex : polygon) {
            var clip = projection.transform(new Vector4f((float) vertex.x, (float) vertex.y, (float) vertex.z, 1));
            assertTrue(clip.w > 0);
            assertTrue(Math.abs(clip.x) <= clip.w + 0.000001);
            assertTrue(Math.abs(clip.y) <= clip.w + 0.000001);
            assertTrue(Math.abs(clip.z) <= clip.w + 0.000001);
        }
    }

    @Test void facingMirrorsComposeTwoDistinctReflectedEyesAndRestoreViewParity() {
        var eye = new Vec3(0, 1.6, -2);
        var north = new Vec3(0, 0, -1);
        var south = new Vec3(0, 0, 1);
        var firstEye = MirrorReflectionMath.reflectPoint(eye, Vec3.ZERO, north);
        var secondEye = MirrorReflectionMath.reflectPoint(firstEye, new Vec3(0, 0, -4), south);
        assertEquals(2, firstEye.z, 1e-9);
        assertEquals(-10, secondEye.z, 1e-9);
        var original = new Matrix4f().rotateY(0.4F);
        var firstView = MirrorReflectionMath.reflectedView(original, north);
        var secondView = MirrorReflectionMath.reflectedView(firstView, south);
        assertEquals(-1, firstView.determinant(), 1e-6);
        assertEquals(1, secondView.determinant(), 1e-6);
        assertTrue(original.equals(secondView, 1e-6F));
    }

    @Test void captureHasAnOverscanGuardBandWithoutChangingDepthPlanes() {
        var projection = new Matrix4f().perspective(1.2F, 1.5F, 0.05F, 128);
        var capture = MirrorReflectionMath.captureProjection(projection);
        assertEquals(projection.m00() / 1.15F, capture.m00(), 1e-6F);
        assertEquals(projection.m11() / 1.15F, capture.m11(), 1e-6F);
        assertEquals(projection.m22(), capture.m22());
        assertEquals(projection.m32(), capture.m32());
    }

    @Test void staleCaptureCoverageChecksOnlyTheCurrentlyVisibleMirrorPolygon() {
        var quad = com.rexhub.createapparel.mirror.MirrorGeometry.north(-2, -0.5, 2, 0.5, 0);
        var full = new double[]{0, 0, 1, 1};
        assertTrue(MirrorReflectionMath.coversVisibleMirror(java.util.List.of(quad), Vec3.ZERO, new Matrix4f(), new Matrix4f(), full));
        assertFalse(MirrorReflectionMath.coversVisibleMirror(java.util.List.of(quad), Vec3.ZERO, new Matrix4f(), new Matrix4f().translation(0.1F, 0, 0), full));
        assertTrue(MirrorReflectionMath.coversVisibleMirror(java.util.List.of(quad), Vec3.ZERO, new Matrix4f(), new Matrix4f().scaling(1 / 1.15F, 1 / 1.15F, 1), full));
        assertFalse(MirrorReflectionMath.coversVisibleMirror(java.util.List.of(quad), Vec3.ZERO, new Matrix4f(), new Matrix4f(), new double[]{0.2, 0.2, 0.8, 0.8}));
    }

    @Test void clipDoesNotCutAwayObjectsWithinTwoMillimetersOfTheMirror() {
        var projection = new Matrix4f().perspective(1.2F, 1.5F, 0.05F, 128);
        var clipped = MirrorReflectionMath.clippedProjection(projection, new Matrix4f(), new Vec3(0, 0, -3), new Vec3(0, 0, -1));
        var nearSurface = clipped.transform(new Vector4f(0, 0, -3.0005F, 1));
        assertTrue(nearSurface.z >= -nearSurface.w);
    }
    @Test void croppedCullingRejectsOutsideMirrorWithoutChangingDepthPlanes() {
        var projection = new Matrix4f().perspective(1.2F, 1.5F, 0.05F, 128);
        var cropped = MirrorReflectionMath.croppedCullingProjection(projection, new double[]{-0.2, -0.3, 0.4, 0.5});
        assertTrue(cropped.isFinite());
        for (float ndcX : new float[]{-0.7F, 0.1F, 0.7F}) {
            var point = new Matrix4f(projection).invert().transform(new Vector4f(ndcX, 0.1F, 0.5F, 1));
            var clip = cropped.transform(new Vector4f(point));
            assertEquals(ndcX == 0.1F, Math.abs(clip.x) <= clip.w);
            assertEquals(0.5F, clip.z / clip.w, 1e-5F);
        }
    }

    @Test void tinyMirrorCullingIsFiniteAndDoesNotRequireAFullCameraCube() {
        var projection = new Matrix4f().perspective(1.2F, 1.5F, 0.05F, 128);
        var cropped = MirrorReflectionMath.croppedCullingProjection(projection, new double[]{0.1, 0.1, 0.101, 0.101});
        assertTrue(cropped.isFinite());
        assertEquals(projection.m22(), cropped.m22());
        assertEquals(projection.m32(), cropped.m32());
    }

    @Test void obliqueProjectionCanPreventVanillaCameraCubeOffsetFromTerminating() {
        var projection = new Matrix4f().perspective((float) Math.toRadians(70), 1.5F, 0.05F, 128);
        Vec3 eye = new Vec3(0.4, 1.6, 0.7), origin = new Vec3(0, 0, -3), normal = new Vec3(0, 0, 1);
        boolean reproduced = false;
        for (int degrees = -80; degrees <= 80; degrees += 5) {
            var view = new Matrix4f().rotateY((float) Math.toRadians(degrees));
            var reflectedView = MirrorReflectionMath.reflectedView(view, normal);
            var reflectedEye = MirrorReflectionMath.reflectPoint(eye, origin, normal);
            var clipped = MirrorReflectionMath.clippedProjection(projection, reflectedView, origin.subtract(reflectedEye), normal);
            if (cameraCubeOffsetIterations(reflectedView, clipped, reflectedEye) < 0) reproduced = true;
            assertTrue(cameraCubeOffsetIterations(reflectedView, projection, reflectedEye) >= 0,
                    "Unclipped culling projection must terminate at yaw " + degrees);
        }
        assertTrue(reproduced, "Clipped culling projection reproduces the unbounded vanilla loop");
    }

    private static int cameraCubeOffsetIterations(Matrix4f view, Matrix4f projection, Vec3 eye) {
        var matrix = new Matrix4f(projection).mul(view);
        var intersection = new FrustumIntersection(matrix);
        var step = matrix.transformTranspose(new Vector4f(0, 0, 1, 0)).mul(4);
        double minX = Math.floor(eye.x / 8) * 8, minY = Math.floor(eye.y / 8) * 8, minZ = Math.floor(eye.z / 8) * 8;
        double maxX = Math.ceil(eye.x / 8) * 8, maxY = Math.ceil(eye.y / 8) * 8, maxZ = Math.ceil(eye.z / 8) * 8;
        double x = eye.x, y = eye.y, z = eye.z;
        for (int iteration = 0; iteration < 256; iteration++) {
            if (intersection.intersectAab((float) (minX - x), (float) (minY - y), (float) (minZ - z),
                    (float) (maxX - x), (float) (maxY - y), (float) (maxZ - z)) == FrustumIntersection.INSIDE) return iteration;
            x -= step.x; y -= step.y; z -= step.z;
        }
        return -1;
    }

    @Test void unclippedCullingTerminatesForFloorCeilingAndTiltedMovingMirrors() {
        var projection = new Matrix4f().perspective((float) Math.toRadians(90), 1.8F, 0.05F, 128);
        Vec3 eye = new Vec3(-2.031, -58.4, 3.168), origin = new Vec3(-1, -58, -1);
        for (var normal : new Vec3[]{new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(1, 0, 0),
                new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(1, 2, -3).normalize()}) {
            Vec3 reflectedEye = MirrorReflectionMath.reflectPoint(eye, origin, normal);
            for (int yaw = 0; yaw < 360; yaw += 30) for (int pitch = -80; pitch <= 80; pitch += 20) {
                var view = new Matrix4f().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
                var reflectedView = MirrorReflectionMath.reflectedView(view, normal);
                assertTrue(cameraCubeOffsetIterations(reflectedView, projection, reflectedEye) >= 0,
                        "Culling must terminate at yaw " + yaw + ", pitch " + pitch + ", normal " + normal);
            }
        }
    }

    @Test void reflectionPreservesTangentsAndReversesNormalDistance() {
        Vec3 normal = new Vec3(1, 2, -3).normalize();
        Vec3 origin = new Vec3(12000000, 5, -14000000);
        Vec3 eye = origin.add(4, 2, -8);
        Vec3 reflected = MirrorReflectionMath.reflectPoint(eye, origin, normal);
        assertEquals(-eye.subtract(origin).dot(normal), reflected.subtract(origin).dot(normal), 1.0E-7);
        assertEquals(0, MirrorReflectionMath.reflectPoint(reflected, origin, normal).distanceTo(eye), 1.0E-7);
        Vec3 tangent = normal.cross(new Vec3(0, 1, 0)).normalize();
        assertEquals(0, MirrorReflectionMath.reflectDirection(tangent, normal).distanceTo(tangent), 1.0E-10);
    }

    @Test void reflectedViewReversesHandednessForAnyMirrorPlane() {
        var view = new Matrix4f().rotateX(0.3F).rotateY(-0.7F);
        for (var normal : new Vec3[]{new Vec3(0, 0, -1), new Vec3(0, 1, 0), new Vec3(1, 2, 3).normalize()}) {
            var reflected = MirrorReflectionMath.reflectedView(view, normal);
            assertEquals(-1, reflected.determinant(), 0.00001);
            var twice = MirrorReflectionMath.reflectedView(reflected, normal);
            assertTrue(view.equals(twice, 0.00001F));
        }
    }

    @Test void obliqueClipRejectsObjectsBehindMirrorAndPreservesFrontObjects() {
        var projection = new Matrix4f().perspective((float) Math.toRadians(70), 1.5F, 0.05F, 128);
        Vec3 origin = new Vec3(0, 0, -3), normal = new Vec3(0, 0, -1);
        var clipped = MirrorReflectionMath.clippedProjection(projection, new Matrix4f(), origin, normal);
        var behind = clipped.transform(new Vector4f(0, 0, -2, 1));
        var front = clipped.transform(new Vector4f(0, 0, -4, 1));
        assertTrue(behind.z < -behind.w);
        assertTrue(front.z >= -front.w && front.z <= front.w);
        assertEquals(projection.m00(), clipped.m00());
        assertEquals(projection.m11(), clipped.m11());
    }

    @Test void clipSupportsFloorCeilingAndTiltedPlanes() {
        var projection = new Matrix4f().perspective(1.2F, 1.5F, 0.05F, 128);
        for (var normal : new Vec3[]{new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(1, 1, -2).normalize()}) {
            Vec3 origin = new Vec3(0, 0, -4);
            var view = new Matrix4f().lookAt(0, 0, 0, (float) normal.x, (float) normal.y, (float) normal.z, 0, 0, 1);
            if (Math.abs(normal.z) > 0.9) continue;
            var clipped = MirrorReflectionMath.clippedProjection(projection, view, origin, normal);
            assertTrue(clipped.isFinite());
        }
    }
}
