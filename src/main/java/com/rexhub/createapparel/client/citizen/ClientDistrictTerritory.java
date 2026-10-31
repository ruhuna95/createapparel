package com.rexhub.createapparel.client.citizen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rexhub.createapparel.citizen.population.CitizenGauge;
import com.rexhub.createapparel.citizen.population.DistrictTerritory;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

import java.util.*;
import com.rexhub.createapparel.client.citizen.DistrictBoundaryMesh.Line;
import static com.rexhub.createapparel.client.citizen.DistrictBoundaryMesh.edge;

public final class ClientDistrictTerritory {
    private static final int VIEW_CHUNKS = 8;
    private final DistrictTerritory territory = new DistrictTerritory();
    private final DistrictBoundarySurface surfaces = new DistrictBoundarySurface();
    private final LinkedHashMap<Long, List<Line>> meshes = new LinkedHashMap<>();
    private final ArrayDeque<Long> pending = new ArrayDeque<>();
    @Nullable private BlockPos previewCenter;
    private List<Line> preview = List.of();
    private int viewX = Integer.MIN_VALUE, viewZ;
    private long builtRevision = -1;
    public void chunkChanged(long chunk) { meshes.remove(chunk); surfaces.unloadChunk(chunk); builtRevision = -1; }

    public void preview(@Nullable BlockPos center, int radius) {
        if (Objects.equals(center, previewCenter)) return;
        previewCenter = center;
        if (center == null) { preview = List.of(); return; }
        var lines = new ArrayList<Line>(); var id = new UUID(0, 0);
        int minX = center.getX() - radius, maxX = center.getX() + radius;
        int minZ = center.getZ() - radius, maxZ = center.getZ() + radius;
        for (int x = minX; x <= maxX; x++) {
            lines.add(edge(x, minZ, x + 1, minZ, x, minZ, id));
            lines.add(edge(x + 1, maxZ + 1, x, maxZ + 1, x, maxZ, id));
        }
        for (int z = minZ; z <= maxZ; z++) {
            lines.add(edge(minX, z + 1, minX, z, minX, z, id));
            lines.add(edge(maxX + 1, z, maxX + 1, z + 1, maxX, z, id));
        }
        preview = List.copyOf(lines);
    }

    public void accept(List<CitizenGauge> gauges) {
        var ids = new HashSet<UUID>();
        for (var gauge : gauges) if (!gauge.source()) {
            ids.add(gauge.districtId());
            territory.put(new DistrictTerritory.Seed(gauge.districtId(), gauge.center().getX(), gauge.center().getZ(), gauge.range()));
        }
        for (var seed : List.copyOf(territory.seeds())) if (!ids.contains(seed.districtId())) territory.remove(seed.districtId());
        for (var bounds : territory.takeChanges()) {
            var halo = new DistrictTerritory.Bounds(bounds.minX() - 1, bounds.minZ() - 1, bounds.maxX() + 1, bounds.maxZ() + 1);
            meshes.keySet().removeIf(halo::intersectsChunk);
        }
    }
    public void tick(ClientLevel level, Vec3 viewer) {
        int x = net.minecraft.util.Mth.floor(viewer.x) >> 4, z = net.minecraft.util.Mth.floor(viewer.z) >> 4;
        if (x != viewX || z != viewZ || builtRevision != territory.revision()) {
            viewX = x; viewZ = z; builtRevision = territory.revision(); pending.clear();
            for (int dx = -VIEW_CHUNKS; dx <= VIEW_CHUNKS; dx++) for (int dz = -VIEW_CHUNKS; dz <= VIEW_CHUNKS; dz++) {
                int cx = x + dx, cz = z + dz; long chunk = ChunkPos.asLong(cx, cz);
                if (!meshes.containsKey(chunk) && !territory.candidates(new DistrictTerritory.Bounds(cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15)).isEmpty()) pending.add(chunk);
            }
            var ordered = new ArrayList<>(pending); ordered.sort(Comparator.comparingLong(chunk -> {
                long dx = ChunkPos.getX(chunk) - viewX, dz = ChunkPos.getZ(chunk) - viewZ; return dx * dx + dz * dz;
            })); pending.clear(); pending.addAll(ordered);
        }
        if (!pending.isEmpty()) {
            long chunk = pending.removeFirst(); meshes.put(chunk, DistrictBoundaryMesh.build(territory, ChunkPos.getX(chunk), ChunkPos.getZ(chunk)));
        }
        while (meshes.size() > DistrictTerritory.MAX_CACHED_TILES) meshes.remove(meshes.keySet().iterator().next());
        for (var entry : meshes.entrySet()) {
            if (!visible(entry.getKey())) continue;
            for (var line : entry.getValue()) request(line, viewer.y, level.getGameTime());
        }
        for (var line : preview) request(line, viewer.y, level.getGameTime());
        surfaces.update(viewer.y, level.getGameTime(), (cx, cz, ceiling) -> DistrictBoundarySurface.read(level, cx, cz, ceiling));
    }
    private void request(Line line, double ceiling, long tick) {
        surfaces.request(line.columnX(), line.columnZ(), ceiling, tick);
    }
    private boolean visible(long chunk) {
        return Math.abs(ChunkPos.getX(chunk) - viewX) <= VIEW_CHUNKS && Math.abs(ChunkPos.getZ(chunk) - viewZ) <= VIEW_CHUNKS;
    }
    public void render(VertexConsumer consumer, PoseStack.Pose pose, double ceiling, net.minecraft.world.phys.Vec3 camera) {
        for (var entry : meshes.entrySet()) {
            if (!visible(entry.getKey())) continue;
            for (var line : entry.getValue()) draw(consumer, pose, line, ceiling, DistrictGaugeIcon.color(line.district()), camera);
        }
    }
    public void renderPreview(VertexConsumer consumer, PoseStack.Pose pose, double ceiling, int color, net.minecraft.world.phys.Vec3 camera) {
        for (var line : preview) draw(consumer, pose, line, ceiling, color, camera);
    }
    private void draw(VertexConsumer consumer, PoseStack.Pose pose, Line line, double ceiling, int color, net.minecraft.world.phys.Vec3 camera) {
        double y = surfaces.height(line.columnX(), line.columnZ(), ceiling);
        if (!Double.isFinite(y)) return;
        float r = (color >>> 16) / 255F, g = (color >>> 8 & 255) / 255F, b = (color & 255) / 255F;
        float nx = (float) (line.endX() - line.x()), nz = (float) (line.endZ() - line.z());
        consumer.addVertex(pose.pose(), (float) (line.x() - camera.x), (float) (y - camera.y), (float) (line.z() - camera.z)).setColor(r, g, b, .8F).setNormal(pose, nx, 0, nz);
        consumer.addVertex(pose.pose(), (float) (line.endX() - camera.x), (float) (y - camera.y), (float) (line.endZ() - camera.z)).setColor(r, g, b, .8F).setNormal(pose, nx, 0, nz);
    }
}
