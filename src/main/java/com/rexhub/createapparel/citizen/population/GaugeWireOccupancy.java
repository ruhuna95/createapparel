package com.rexhub.createapparel.citizen.population;

import net.minecraft.core.BlockPos;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GaugeWireOccupancy {
    private record Segment(BlockPos first, BlockPos second) {
        private static Segment between(BlockPos a, BlockPos b) {
            return a.compareTo(b) <= 0 ? new Segment(a, b) : new Segment(b, a);
        }
    }
    private final Set<BlockPos> vertices = new HashSet<>();
    private final Set<BlockPos> endpoints = new HashSet<>();
    private final Set<Segment> segments = new HashSet<>();

    public void reserve(List<BlockPos> points) {
        if (points.size() < 2) return;
        endpoints.add(points.get(0)); endpoints.add(points.get(points.size() - 1));
        vertices.addAll(points);
        for (int index = 1; index < points.size(); index++) segments.add(Segment.between(points.get(index - 1), points.get(index)));
    }

    public boolean blocks(BlockPos from, BlockPos to, BlockPos start, BlockPos end) {
        return segments.contains(Segment.between(from, to)) || occupied(from, start, end) || occupied(to, start, end);
    }

    private boolean occupied(BlockPos point, BlockPos start, BlockPos end) {
        return vertices.contains(point) && !(endpoints.contains(point) && (point.equals(start) || point.equals(end)));
    }
}
