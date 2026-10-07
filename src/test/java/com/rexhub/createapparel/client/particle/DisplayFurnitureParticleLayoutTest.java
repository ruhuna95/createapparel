package com.rexhub.createapparel.client.particle;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DisplayFurnitureParticleLayoutTest {
    @Test
    void manyShapePiecesNeverMultiplyParticleBudget() {
        var split = new ArrayList<AABB>();
        for (int index = 0; index < 1000; index++) split.add(new AABB(index / 1000.0, 0, 0, (index + 1) / 1000.0, 1, 1));
        var particles = DisplayFurnitureParticleLayout.sample(split, new Random(42));
        assertEquals(DisplayFurnitureParticleLayout.MAX_PARTICLES, particles.size());
        assertEquals(DisplayFurnitureParticleLayout.sample(List.of(new AABB(0, 0, 0, 1, 1, 1)), new Random(42)).size(), particles.size());
        for (var point : particles) assertTrue(split.stream().anyMatch(box -> box.contains(point)));
    }

    @Test
    void emptyOrInvisibleShapeHasNoParticles() {
        assertTrue(DisplayFurnitureParticleLayout.sample(List.of(), new Random(1)).isEmpty());
        assertTrue(DisplayFurnitureParticleLayout.sample(List.of(new AABB(0, 0, 0, 0, 1, 1)), new Random(1)).isEmpty());
    }

    @Test
    void thinHangerStillHasSmallVisibleBurstInsideItsGeometry() {
        var boxes = List.of(new AABB(0.1, 0.6, 0.65, 0.9, 0.65, 0.7));
        var particles = DisplayFurnitureParticleLayout.sample(boxes, new Random(42));
        assertEquals(8, particles.size());
        for (var point : particles) assertTrue(boxes.get(0).contains(point));
    }
}
