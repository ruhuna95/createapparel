package com.rexhub.createapparel.client.citizen;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.IntToDoubleFunction;

public final class DistrictBoundarySurface {
    public static final int COLUMN_BUDGET = 64;
    public static final int CACHE_LIMIT = 8192;
    private static final int REFRESH_TICKS = 20;
    private record Sample(double ceiling, double height, long tick) { }
    @FunctionalInterface public interface Reader { double height(int x, int z, double ceiling); }
    private final LinkedHashMap<Long, Sample> samples = new LinkedHashMap<>(128, .75F, true);
    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    public void unloadChunk(long chunk) {
        samples.keySet().removeIf(column -> ChunkPos.getX(column) >> 4 == ChunkPos.getX(chunk) && ChunkPos.getZ(column) >> 4 == ChunkPos.getZ(chunk));
        pending.removeIf(column -> ChunkPos.getX(column) >> 4 == ChunkPos.getX(chunk) && ChunkPos.getZ(column) >> 4 == ChunkPos.getZ(chunk));
    }

    public void request(int x, int z, double ceiling, long tick) {
        long key = ChunkPos.asLong(x, z);
        var sample = samples.get(key);
        if ((sample == null || sample.ceiling != ceiling || tick - sample.tick >= REFRESH_TICKS)
                && pending.size() < CACHE_LIMIT) pending.add(key);
    }

    public int update(double ceiling, long tick, Reader reader) {
        int processed = 0;
        while (!pending.isEmpty() && processed < COLUMN_BUDGET) {
            var iterator = pending.iterator(); long key = iterator.next(); iterator.remove();
            double height = reader.height(ChunkPos.getX(key), ChunkPos.getZ(key), ceiling);
            samples.put(key, new Sample(ceiling, height, tick)); processed++;
            if (samples.size() > CACHE_LIMIT) samples.remove(samples.keySet().iterator().next());
        }
        return processed;
    }

    public double height(int x, int z, double ceiling) {
        var sample = samples.get(ChunkPos.asLong(x, z));
        return sample == null || !Double.isFinite(sample.height) || sample.height > ceiling
                ? Double.NaN : Math.min(sample.height + .003, ceiling);
    }

    public static double highestSurface(double ceiling, int minY, int maxY, IntToDoubleFunction top) {
        for (int y = Math.min(maxY, (int) Math.floor(ceiling)); y >= minY; y--) {
            double height = top.applyAsDouble(y);
            if (Double.isFinite(height) && height <= ceiling) return height;
        }
        return Double.NaN;
    }

    public static double read(ClientLevel level, int x, int z, double ceiling) {
        var chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
        if (chunk == null) return Double.NaN;
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        var pos = new BlockPos.MutableBlockPos(x, 0, z);
        return highestSurface(ceiling, level.getMinBuildHeight(), top, y -> {
            var section = chunk.getSection(chunk.getSectionIndex(y));
            if (section.hasOnlyAir()) return Double.NaN;
            pos.setY(y);
            var shape = chunk.getBlockState(pos).getCollisionShape(level, pos);
            return shape.isEmpty() ? Double.NaN : y + shape.max(net.minecraft.core.Direction.Axis.Y);
        });
    }
}
