package com.rexhub.createapparel.fashion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class FashionWorldIndex {
    private static final Map<ServerLevel, FashionWorldIndex> LEVELS = new IdentityHashMap<>();
    private final Map<Long, Set<BlockPos>> registers = new HashMap<>();
    private final Map<Long, Set<BlockPos>> advertisements = new HashMap<>();
    private final ServerLevel level;
    private FashionWorldIndex(ServerLevel level) { this.level = level; }
    public static FashionWorldIndex get(ServerLevel level) { return LEVELS.computeIfAbsent(level, FashionWorldIndex::new); }
    public static void unload(ServerLevel level) { LEVELS.remove(level); }
    public void addRegister(BlockPos pos) { registers.computeIfAbsent(cell(pos), ignored -> new HashSet<>()).add(pos.immutable()); }
    public void removeRegister(BlockPos pos) { remove(registers, pos); }
    public void addAdvertisement(BlockPos pos) { advertisements.computeIfAbsent(cell(pos), ignored -> new HashSet<>()).add(pos.immutable()); }
    public void removeAdvertisement(BlockPos pos) { remove(advertisements, pos); }
    private static long cell(BlockPos pos) { return net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4); }
    private static void remove(Map<Long, Set<BlockPos>> index, BlockPos pos) {
        var entries = index.get(cell(pos));
        if (entries != null) { entries.remove(pos); if (entries.isEmpty()) index.remove(cell(pos)); }
    }
    public List<BlockPos> advertisements(Vec3 point, int radius) { return nearby(advertisements, point, radius); }
    public List<CashRegisterBlockEntity> registers(Vec3 point, int radius) {
        return nearby(registers, point, radius).stream().map(level::getBlockEntity)
                .filter(CashRegisterBlockEntity.class::isInstance).map(CashRegisterBlockEntity.class::cast).toList();
    }
    private List<BlockPos> nearby(Map<Long, Set<BlockPos>> index, Vec3 point, int radius) {
        var center = BlockPos.containing(point); List<BlockPos> result = new ArrayList<>();
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++) {
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
                for (var pos : index.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x, z), Set.of())) {
                    if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && pos.distToCenterSqr(point) <= radius * radius) result.add(pos);
                    if (result.size() >= 64) return result;
                }
            }
        }
        return result;
    }
}
