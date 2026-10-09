package com.rexhub.createapparel.client.mirror;

import com.rexhub.createapparel.mirror.MirrorGeometry;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MirrorSurfaceClustersTest {
    private static MirrorSurfaceClusters.Entry panel(Object owner, int x, int y) {
        return new MirrorSurfaceClusters.Entry(owner, x + ":" + y, MirrorGeometry.north(x, y, x + 1, y + 1, 0), true);
    }

    @Test void adjacentThreeByTwoPanelsShareOneReflection() {
        var entries = IntStream.range(0, 6).mapToObj(i -> panel("world", i % 3, i / 3)).toList();
        var clusters = MirrorSurfaceClusters.build(entries, true);
        assertEquals(1, clusters.size());
        assertEquals(6, clusters.get(0).size());
        assertEquals(6, MirrorSurfaceClusters.build(entries, false).size());
    }

    @Test void gapsOwnersPlanesAndNormalsDoNotMerge() {
        var first = panel("world", 0, 0);
        assertEquals(2, MirrorSurfaceClusters.build(List.of(first, panel("world", 2, 0)), true).size());
        assertEquals(2, MirrorSurfaceClusters.build(List.of(first, panel("ship", 1, 0)), true).size());
        var offset = new MirrorSurfaceClusters.Entry("world", "offset", panel("world", 1, 0).geometry().move(new Vec3(0, 0, 0.01)), true);
        assertEquals(2, MirrorSurfaceClusters.build(List.of(first, offset), true).size());
        var reversed = new MirrorSurfaceClusters.Entry("world", "opposite", new MirrorGeometry(first.geometry().vertices(), new Vec3(0, 0, 1)), true);
        assertEquals(2, MirrorSurfaceClusters.build(List.of(first, reversed), true).size());
    }

    @Test void diagonallyTouchingPanelsShareOneCaptureWithoutFillingTheEmptyCorners() {
        var entries = List.of(panel("world", 1, 0), panel("world", 0, 1));
        var cluster = MirrorSurfaceClusters.build(entries, true);
        assertEquals(1, cluster.size());
        assertEquals(entries.size(), cluster.get(0).size());
        assertTrue(cluster.get(0).containsAll(entries));
        assertEquals(2, MirrorSurfaceClusters.build(entries, false).size());
    }

    @Test void diagonalChainsMergeTransitivelyButActualGapsDoNot() {
        var entries = IntStream.range(0, 5).mapToObj(i -> panel("world", i, i)).toList();
        assertEquals(1, MirrorSurfaceClusters.build(entries, true).size());
        assertEquals(5, MirrorSurfaceClusters.build(entries, true).get(0).size());
        assertEquals(2, MirrorSurfaceClusters.build(List.of(panel("world", 0, 0), panel("world", 2, 2)), true).size());
        assertEquals(2, MirrorSurfaceClusters.build(List.of(panel("world", 0, 0), panel("ship", 1, 1)), true).size());
    }

    @Test void irregularClusterKeepsIndividualQuadsInsteadOfFillingHoles() {
        var entries = List.of(panel("contraption", 0, 0), panel("contraption", 1, 0), panel("contraption", 0, 1));
        var cluster = MirrorSurfaceClusters.build(entries, true).get(0);
        assertEquals(3, cluster.size());
        var rotation = new Matrix4f().rotateY((float) Math.PI / 2).translate(10, 0, 0);
        for (var entry : cluster) {
            var world = entry.geometry().transform(rotation);
            assertEquals(-1, world.normal().x, 0.00001);
            assertEquals(4, world.vertices().size());
        }
    }
}
