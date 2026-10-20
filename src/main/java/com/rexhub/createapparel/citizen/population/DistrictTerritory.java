package com.rexhub.createapparel.citizen.population;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

public final class DistrictTerritory {
    public static final int RULE_VERSION = 1;
    public static final int MAX_CACHED_TILES = 512;
    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public boolean intersectsChunk(long chunk) {
            int x = ChunkPos.getX(chunk) << 4, z = ChunkPos.getZ(chunk) << 4;
            return minX <= x + 15 && maxX >= x && minZ <= z + 15 && maxZ >= z;
        }
    }
    public record Seed(UUID districtId, int x, int z, int radius) {
        public Seed {
            if (radius < 1 || radius > 256) throw new IllegalArgumentException("Invalid territory radius");
        }
        public Bounds bounds() { return new Bounds(x - radius, z - radius, x + radius, z + radius); }
        public boolean contains(int blockX, int blockZ) { return Math.abs((long) blockX - x) <= radius && Math.abs((long) blockZ - z) <= radius; }
        public CompoundTag save() {
            var tag = new CompoundTag(); tag.putUUID("District", districtId); tag.putInt("X", x); tag.putInt("Z", z); tag.putInt("Radius", radius); return tag;
        }
    }
    public static final class Tile {
        private final long chunk;
        private final List<UUID> palette;
        private final int[] runs;
        private final short[] owners;
        private Tile(long chunk, List<UUID> palette, int[] runs) {
            this.chunk = chunk; this.palette = List.copyOf(palette); this.runs = runs.clone(); owners = new short[256];
            int offset = 0;
            for (int run : runs) {
                int size = run & 0xFFFF, index = run >>> 16;
                if (size == 0 || index > palette.size() || offset + size > 256) throw new IllegalArgumentException("Invalid territory run");
                Arrays.fill(owners, offset, offset + size, (short) index); offset += size;
            }
            if (offset != 256 || palette.size() > 256) throw new IllegalArgumentException("Invalid territory tile");
        }
        public long chunk() { return chunk; }
        public List<UUID> palette() { return palette; }
        @Nullable public UUID districtAt(int localX, int localZ) {
            if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) throw new IllegalArgumentException("Invalid tile coordinate");
            int index = owners[localZ * 16 + localX]; return index == 0 ? null : palette.get(index - 1);
        }
        public CompoundTag save() {
            var tag = new CompoundTag(); tag.putLong("Chunk", chunk);
            var entries = new ListTag(); for (var id : palette) { var entry = new CompoundTag(); entry.putUUID("District", id); entries.add(entry); }
            tag.put("Palette", entries); tag.putIntArray("Runs", runs); return tag;
        }
    }

    private final Map<UUID, Seed> seeds = new HashMap<>();
    private final Map<Long, Set<UUID>> cells = new HashMap<>();
    private final LinkedHashMap<Long, Tile> tiles = new LinkedHashMap<>(16, .75F, true);
    private final LinkedHashMap<Long, CompoundTag> restoredTiles = new LinkedHashMap<>();
    private final List<Bounds> changes = new ArrayList<>();
    private final Runnable markDirty;
    private long revision = 1;

    public DistrictTerritory() { this(() -> { }); }
    public DistrictTerritory(Runnable markDirty) { this.markDirty = markDirty; }

    public long revision() { return revision; }
    public Collection<Seed> seeds() { return Collections.unmodifiableCollection(seeds.values()); }
    public int cachedTileCount() { return tiles.size() + restoredTiles.size(); }
    public void put(Seed seed) {
        var previous = seeds.get(seed.districtId());
        if (seed.equals(previous)) return;
        if (previous != null) unindex(previous);
        seeds.put(seed.districtId(), seed);
        forCells(seed.bounds(), cell -> cells.computeIfAbsent(cell, ignored -> new HashSet<>()).add(seed.districtId()));
        changed(seed.bounds());
    }
    public void remove(UUID districtId) {
        var previous = seeds.remove(districtId);
        if (previous != null) unindex(previous);
    }
    private void unindex(Seed seed) {
        forCells(seed.bounds(), cell -> {
            var members = cells.get(cell);
            members.remove(seed.districtId()); if (members.isEmpty()) cells.remove(cell);
        });
        changed(seed.bounds());
    }
    private void changed(Bounds bounds) {
        revision++; changes.add(bounds);
        tiles.keySet().removeIf(bounds::intersectsChunk); restoredTiles.keySet().removeIf(bounds::intersectsChunk);
    }
    public List<Bounds> takeChanges() {
        var result = List.copyOf(changes); changes.clear(); return result;
    }
    private static void forCells(Bounds bounds, java.util.function.LongConsumer action) {
        for (int x = Math.floorDiv(bounds.minX, 64); x <= Math.floorDiv(bounds.maxX, 64); x++)
            for (int z = Math.floorDiv(bounds.minZ, 64); z <= Math.floorDiv(bounds.maxZ, 64); z++) action.accept(ChunkPos.asLong(x, z));
    }
    public List<Seed> candidates(Bounds bounds) {
        var ids = new HashSet<UUID>(); forCells(bounds, cell -> ids.addAll(cells.getOrDefault(cell, Set.of())));
        return ids.stream().map(seeds::get).filter(seed -> seed.bounds().minX <= bounds.maxX && seed.bounds().maxX >= bounds.minX
                && seed.bounds().minZ <= bounds.maxZ && seed.bounds().maxZ >= bounds.minZ).toList();
    }
    @Nullable public UUID districtAt(Vec3 point) { return districtAt(Mth.floor(point.x), Mth.floor(point.z)); }
    @Nullable public UUID districtAt(BlockPos pos) { return districtAt(pos.getX(), pos.getZ()); }
    @Nullable public UUID districtAt(int x, int z) {
        long chunk = ChunkPos.asLong(x >> 4, z >> 4);
        var tile = tiles.get(chunk);
        if (tile != null) return tile.districtAt(x & 15, z & 15);
        return resolve(x, z);
    }
    @Nullable private UUID resolve(int x, int z) {
        UUID owner = null; long nearest = Long.MAX_VALUE;
        for (var id : cells.getOrDefault(ChunkPos.asLong(x >> 6, z >> 6), Set.of())) {
            var seed = seeds.get(id); if (!seed.contains(x, z)) continue;
            long dx = (long) x - seed.x, dz = (long) z - seed.z, distance = dx * dx + dz * dz;
            if (distance < nearest || distance == nearest && owner != null && id.compareTo(owner) < 0) { owner = id; nearest = distance; }
        }
        return owner;
    }
    public Tile tile(int chunkX, int chunkZ) {
        long chunk = ChunkPos.asLong(chunkX, chunkZ);
        var cached = tiles.get(chunk); if (cached != null) return cached;
        var palette = new ArrayList<UUID>(); var values = new int[256];
        for (int index = 0; index < 256; index++) {
            var id = resolve((chunkX << 4) + (index & 15), (chunkZ << 4) + (index >> 4));
            if (id == null) continue;
            int paletteIndex = palette.indexOf(id); if (paletteIndex < 0) { paletteIndex = palette.size(); palette.add(id); }
            values[index] = paletteIndex + 1;
        }
        var runs = new ArrayList<Integer>();
        for (int start = 0; start < 256;) {
            int end = start + 1; while (end < 256 && values[end] == values[start]) end++;
            runs.add(values[start] << 16 | end - start); start = end;
        }
        var result = new Tile(chunk, palette, runs.stream().mapToInt(Integer::intValue).toArray());
        var restored = restoredTiles.remove(chunk);
        if (restored != null) {
            try {
                var ids = restored.getList("Palette", Tag.TAG_COMPOUND).stream().map(raw -> ((CompoundTag) raw).getUUID("District")).toList();
                var candidate = new Tile(chunk, ids, restored.getIntArray("Runs"));
                if (result.save().equals(candidate.save())) result = candidate;
            } catch (IllegalArgumentException ignored) { }
        }
        tiles.put(chunk, result);
        markDirty.run();
        while (cachedTileCount() > MAX_CACHED_TILES) {
            if (!restoredTiles.isEmpty()) restoredTiles.remove(restoredTiles.keySet().iterator().next());
            else tiles.remove(tiles.keySet().iterator().next());
        }
        return result;
    }
    private ListTag savedSeeds() {
        var entries = new ListTag(); seeds.values().stream().sorted(Comparator.comparing(Seed::districtId)).forEach(seed -> entries.add(seed.save())); return entries;
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("RuleVersion", RULE_VERSION); tag.putString("Rule", "nearest_center_square");
        tag.putLong("Revision", revision); tag.put("Seeds", savedSeeds());
        var entries = new ListTag(); restoredTiles.values().forEach(entry -> entries.add(entry.copy())); tiles.values().forEach(tile -> entries.add(tile.save()));
        tag.put("Tiles", entries); return tag;
    }
    public static DistrictTerritory load(CompoundTag tag) {
        var result = new DistrictTerritory();
        for (var raw : tag.getList("Seeds", Tag.TAG_COMPOUND)) {
            var seed = (CompoundTag) raw;
            try { result.put(new Seed(seed.getUUID("District"), seed.getInt("X"), seed.getInt("Z"), seed.getInt("Radius"))); }
            catch (IllegalArgumentException ignored) { }
        }
        result.restore(tag); return result;
    }
    public void restore(CompoundTag tag) {
        changes.clear(); revision = Math.max(1, tag.getLong("Revision"));
        if (tag.getInt("RuleVersion") != RULE_VERSION || !savedSeeds().equals(tag.getList("Seeds", Tag.TAG_COMPOUND))) { revision++; return; }
        for (var raw : tag.getList("Tiles", Tag.TAG_COMPOUND)) {
            if (restoredTiles.size() >= MAX_CACHED_TILES) break;
            var entry = (CompoundTag) raw;
            if (entry.getList("Palette", Tag.TAG_COMPOUND).size() > 256 || entry.getIntArray("Runs").length > 256) continue;
            restoredTiles.put(entry.getLong("Chunk"), entry.copy());
        }
    }
}
