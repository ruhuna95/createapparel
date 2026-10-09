package com.rexhub.createapparel.client.mirror;

import com.rexhub.createapparel.mirror.MirrorGeometry;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class MirrorSurfaceClusters {
    public record Entry(Object owner, Object key, MirrorGeometry geometry, boolean merge) { }
    private static final double EPSILON = 0.001;

    private MirrorSurfaceClusters() { }

    public static List<List<Entry>> build(List<Entry> entries, boolean merge) {
        var result = new ArrayList<List<Entry>>();
        var remaining = new ArrayList<>(entries);
        while (!remaining.isEmpty()) {
            var cluster = new ArrayList<Entry>();
            cluster.add(remaining.remove(remaining.size() - 1));
            for (int i = 0; i < cluster.size(); i++) {
                Entry current = cluster.get(i);
                for (int j = remaining.size() - 1; j >= 0; j--) {
                    if (merge && adjacent(current, remaining.get(j))) cluster.add(remaining.remove(j));
                }
            }
            result.add(List.copyOf(cluster));
        }
        return List.copyOf(result);
    }

    private static boolean adjacent(Entry a, Entry b) {
        if (!a.merge || !b.merge || !a.owner.equals(b.owner)) return false;
        var normal = a.geometry.normal();
        if (normal.dot(b.geometry.normal()) < 0.99999
                || Math.abs(a.geometry.origin().subtract(b.geometry.origin()).dot(normal)) > EPSILON) return false;
        AABB first = a.geometry.bounds(), second = b.geometry.bounds();
        double[] overlaps = {Math.min(first.maxX, second.maxX) - Math.max(first.minX, second.minX),
                Math.min(first.maxY, second.maxY) - Math.max(first.minY, second.minY),
                Math.min(first.maxZ, second.maxZ) - Math.max(first.minZ, second.minZ)};
        int touching = 0;
        for (double overlap : overlaps) {
            if (overlap < -EPSILON) return false;
            if (overlap <= EPSILON) touching++;
        }
        return touching >= 1;
    }
}
