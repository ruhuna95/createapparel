package com.rexhub.createapparel.client.mirror;

import com.rexhub.createapparel.mirror.MirrorGeometry;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MirrorGeometryTransformTest {
    static Stream<Matrix4f> transforms() {
        return Stream.of(new Matrix4f().rotateXYZ(0.3F, 0.6F, -0.2F),
                new Matrix4f().scale(3), new Matrix4f().scale(2, 3, 0.5F),
                new Matrix4f().rotateXYZ(0.3F, 0.6F, -0.2F).scale(2, 3, 0.5F),
                new Matrix4f().translation(3, -4, 7).rotateXYZ(0.3F, 0.6F, -0.2F).scale(2, 3, 0.5F),
                new Matrix4f().scale(2, 3, 0.5F).rotateXYZ(0.3F, 0.6F, -0.2F),
                new Matrix4f().m10(0.7F).m20(-0.4F));
    }

    @ParameterizedTest @MethodSource("transforms")
    void transformedNormalIsPerpendicularToBothPlaneTangents(Matrix4f transform) {
        var source = MirrorGeometry.north(0, 0, 1, 1, 0.95).transform(new Matrix4f().rotateX(0.4F).rotateY(0.3F));
        var result = source.transform(transform);
        assertEquals(1, result.normal().length(), 0.00001);
        for (int vertex : new int[]{1, 3}) {
            var tangent = result.vertices().get(vertex).subtract(result.origin()).normalize();
            assertEquals(0, tangent.dot(result.normal()), 0.00001);
        }
        for (int i = 0; i < 4; i++) {
            var vertex = source.vertices().get(i);
            var expected = transform.transformPosition(new Vector3f((float) vertex.x, (float) vertex.y, (float) vertex.z));
            assertEquals(new Vec3(expected), result.vertices().get(i));
        }
    }

    @Test void pureRotationRetainsThePreviousDirectionTransform() {
        var source = MirrorGeometry.north(0, 0, 1, 1, 0);
        var matrix = new Matrix4f().rotateXYZ(0.3F, -0.7F, 0.2F);
        var expected = matrix.transformDirection(new Vector3f(0, 0, -1)).normalize();
        assertEquals(0, source.transform(matrix).normal().distanceTo(new Vec3(expected)), 0.00001);
    }

    @Test void singularAndUnderflowingMatricesKeepAFiniteUnitNormal() {
        var source = MirrorGeometry.north(0, 0, 1, 1, 0);
        for (var matrix : new Matrix4f[]{new Matrix4f().scale(0), new Matrix4f().scale(1, 1, 0),
                new Matrix4f().scale(0, 1, 1).rotateY(0.3F), new Matrix4f().scale(1.0E-30F)}) {
            var result = assertDoesNotThrow(() -> source.transform(matrix));
            assertTrue(Double.isFinite(result.normal().x));
            assertTrue(Double.isFinite(result.normal().y));
            assertTrue(Double.isFinite(result.normal().z));
            assertEquals(1, result.normal().length(), 0.00001);
        }
    }
}
