package com.rexhub.createapparel.client.particle;

import com.rexhub.createapparel.block.FurnitureShape;
import com.rexhub.createapparel.block.OrientedBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class DisplayFurnitureParticleLayout {
    public static final int MAX_PARTICLES = 32;

    private DisplayFurnitureParticleLayout() { }

    public static List<Vec3> sampleFurniture(FurnitureShape shape, RandomGenerator random) {
        double volume = shape.parts().stream().mapToDouble(OrientedBox::volume).sum();
        if (volume <= 0) return List.of();
        int count = Math.min(MAX_PARTICLES, Math.max(8, (int) Math.ceil(volume * 64)));
        List<Vec3> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double remaining = random.nextDouble() * volume;
            for (var part : shape.parts()) {
                remaining -= part.volume();
                if (remaining > 0) continue;
                points.add(part.sample(random));
                break;
            }
        }
        return List.copyOf(points);
    }

    public static List<Vec3> sample(List<AABB> boxes, RandomGenerator random) {
        double volume = boxes.stream().mapToDouble(DisplayFurnitureParticleLayout::volume).sum();
        if (volume <= 0) return List.of();
        int count = Math.min(MAX_PARTICLES, Math.max(8, (int) Math.ceil(volume * 64)));
        List<Vec3> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double remaining = random.nextDouble() * volume;
            for (var box : boxes) {
                double boxVolume = volume(box);
                if (boxVolume <= 0) continue;
                remaining -= boxVolume;
                if (remaining > 0) continue;
                points.add(new Vec3(box.minX + random.nextDouble() * box.getXsize(),
                        box.minY + random.nextDouble() * box.getYsize(), box.minZ + random.nextDouble() * box.getZsize()));
                break;
            }
        }
        return List.copyOf(points);
    }

    private static double volume(AABB box) { return box.getXsize() * box.getYsize() * box.getZsize(); }
}
