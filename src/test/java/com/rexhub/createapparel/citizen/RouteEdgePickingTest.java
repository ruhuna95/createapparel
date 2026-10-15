package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.route.RouteEdgePicking;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouteEdgePickingTest {
    @Test void perpendicularRayHitsTheEdgeCenterWithEitherEndpointOrder() {
        var start = new Vec3(4, 2, -2); var end = new Vec3(4, -2, 2);
        for (var a : new Vec3[]{Vec3.ZERO, new Vec3(8, 0, 0)}) {
            var b = a.x == 0 ? new Vec3(8, 0, 0) : Vec3.ZERO;
            var hit = RouteEdgePicking.intersect(start, end, a, b);
            assertNotNull(hit); assertEquals(new Vec3(4, 0, 0), hit.point()); assertEquals(0, hit.separation(), 0.000001);
        }
    }
    @Test void parallelRaysUseTheSameFinitePickThickness() {
        assertNotNull(RouteEdgePicking.intersect(new Vec3(1, 0.09, 0), new Vec3(4, 0.09, 0), Vec3.ZERO, new Vec3(8, 0, 0)));
        assertNull(RouteEdgePicking.intersect(new Vec3(1, 0.11, 0), new Vec3(4, 0.11, 0), Vec3.ZERO, new Vec3(8, 0, 0)));
    }
    @Test void behindPlayerBeyondReachAndOutsideEndpointCannotBePicked() {
        var a = new Vec3(-2, 0, 0); var b = new Vec3(2, 0, 0);
        assertNull(RouteEdgePicking.intersect(new Vec3(0, 0, 1), new Vec3(0, 0, 4), a, b));
        assertNull(RouteEdgePicking.intersect(new Vec3(0, 0, -4), new Vec3(0, 0, -1), a, b));
        assertNull(RouteEdgePicking.intersect(new Vec3(3, 0, -4), new Vec3(3, 0, 4), a, b));
    }
    @Test void verticalAndDiagonalEdgesArePickedWithoutAxisAssumptions() {
        for (var b : new Vec3[]{new Vec3(0, 4, 0), new Vec3(4, 4, 4)}) {
            var mid = b.scale(0.5);
            assertNotNull(RouteEdgePicking.intersect(mid.add(0, 0, -2), mid.add(0, 0, 2), Vec3.ZERO, b));
        }
    }
    @Test void degenerateRayOrEdgeDoesNotProduceNaNOrAFalseHit() {
        assertNull(RouteEdgePicking.intersect(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, new Vec3(8, 0, 0)));
        assertNull(RouteEdgePicking.intersect(Vec3.ZERO, new Vec3(8, 0, 0), Vec3.ZERO, Vec3.ZERO));
    }
}
