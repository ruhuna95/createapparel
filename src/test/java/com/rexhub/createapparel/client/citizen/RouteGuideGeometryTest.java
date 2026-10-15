package com.rexhub.createapparel.client.citizen;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouteGuideGeometryTest {
    @Test void edgeRunsBetweenNodeCentersAndClipsToTheRenderSphere() {
        var a = new Vec3(0, 0.15, 0); var b = new Vec3(8, 0.15, 0);
        var segment = RouteGuideGeometry.clipEdge(a, b, a, 64);
        assertNotNull(segment); assertEquals(a, segment.from()); assertEquals(b, segment.to());
        var clipped = RouteGuideGeometry.clipEdge(a, b, a, 4);
        assertNotNull(clipped); assertEquals(4, clipped.to().distanceTo(a), 0.000001);
    }

    @Test void previewRunsFromNodeCenterToCursorWithoutTrimmingDiagonals() {
        var from = new Vec3(0, 0.15, 0); var to = new Vec3(4, 2.15, 4);
        var preview = RouteGuideGeometry.clipEdge(from, to, from, 64);
        assertNotNull(preview); assertEquals(from, preview.from()); assertEquals(to, preview.to());
    }

    @Test void degenerateAndOffscreenEdgesAreNotDrawnButShortEdgesKeepTheirCenters() {
        assertNull(RouteGuideGeometry.clipEdge(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 64));
        assertNull(RouteGuideGeometry.clipEdge(new Vec3(80, 0, 0), new Vec3(100, 0, 0), Vec3.ZERO, 64));
        assertNotNull(RouteGuideGeometry.clipEdge(Vec3.ZERO, new Vec3(0.5, 0, 0), Vec3.ZERO, 64));
    }

    @Test void verticalEdgesKeepBothCentersInEitherDirection() {
        for (double y : new double[]{4, -4}) {
            var a = new Vec3(0, 0.15, 0); var b = a.add(0, y, 0);
            var segment = RouteGuideGeometry.clipEdge(a, b, a, 64);
            assertNotNull(segment); assertEquals(a, segment.from()); assertEquals(b, segment.to());
        }
    }

    @Test void longCrossingSegmentsAreClippedEvenWhenBothEndpointsAreOutsideView() {
        var segment = RouteGuideGeometry.clipEdge(new Vec3(-200, 0, 0), new Vec3(200, 0, 0), Vec3.ZERO, 64);
        assertNotNull(segment); assertEquals(-64, segment.from().x, 0.000001); assertEquals(64, segment.to().x, 0.000001);
    }

    @Test void ribbonsKeepTheirThicknessAndRemainFiniteWhenViewedAlongTheEdge() {
        for (var to : new Vec3[]{new Vec3(8, 0, 0), new Vec3(0, 8, 0), new Vec3(8, 4, 8)}) {
            var segment = new RouteGuideGeometry.Segment(Vec3.ZERO, to);
            var side = RouteGuideGeometry.ribbonSide(segment, to.scale(2));
            assertEquals(RouteGuideGeometry.EDGE_WIDTH / 2, side.length(), 0.000001);
            assertEquals(0, side.dot(to), 0.000001);
        }
    }

    @Test void renderedRibbonMeetsTheNodeSilhouetteWithoutCoveringItFromDifferentCameraAngles() {
        var a = new Vec3(0, 0.15, 0); var b = new Vec3(8, 0.15, 0);
        var boxes = java.util.List.of(RouteGuideGeometry.markerBox(Vec3.ZERO), RouteGuideGeometry.markerBox(new Vec3(8, 0, 0)));
        for (var camera : new Vec3[]{new Vec3(4, 1.6, -3), new Vec3(4, 3, 3), new Vec3(4, 0.15, -3)}) {
            var pieces = RouteGuideGeometry.ribbonOutsideNodes(new RouteGuideGeometry.Segment(a, b), camera, boxes);
            assertFalse(pieces.isEmpty());
            var first = pieces.stream().flatMap(java.util.Collection::stream).min(java.util.Comparator.comparingDouble(point -> point.x)).orElseThrow();
            var last = pieces.stream().flatMap(java.util.Collection::stream).max(java.util.Comparator.comparingDouble(point -> point.x)).orElseThrow();
            assertTrue(boxes.get(0).inflate(0.000001).clip(camera, camera.add(first.subtract(camera).scale(100))).isPresent());
            assertTrue(boxes.get(1).inflate(0.000001).clip(camera, camera.add(last.subtract(camera).scale(100))).isPresent());
            for (var polygon : pieces) for (int i = 1; i + 1 < polygon.size(); i++) {
                var center = polygon.get(0).add(polygon.get(i)).add(polygon.get(i + 1)).scale(1.0 / 3);
                for (var box : boxes) assertTrue(box.inflate(-0.000001).clip(camera, camera.add(center.subtract(camera).scale(100))).isEmpty());
            }
        }
    }

    @Test void diagonalsAndSteepRibbonsNeverEnterEitherNodeVolume() {
        var a = new Vec3(0, 0.15, 0);
        for (var b : new Vec3[]{new Vec3(4, 3.15, 4), new Vec3(0, 4.15, 0), new Vec3(0, -3.85, 0)}) {
            var boxes = java.util.List.of(RouteGuideGeometry.markerBox(a.add(0, -0.15, 0)), RouteGuideGeometry.markerBox(b.add(0, -0.15, 0)));
            var pieces = RouteGuideGeometry.ribbonOutsideNodes(new RouteGuideGeometry.Segment(a, b), new Vec3(2, 3, -3), boxes);
            assertFalse(pieces.isEmpty());
            for (var polygon : pieces) for (int i = 1; i + 1 < polygon.size(); i++) {
                var center = polygon.get(0).add(polygon.get(i)).add(polygon.get(i + 1)).scale(1.0 / 3);
                for (var box : boxes) assertFalse(box.inflate(-0.000001).contains(center));
            }
        }
    }
}
