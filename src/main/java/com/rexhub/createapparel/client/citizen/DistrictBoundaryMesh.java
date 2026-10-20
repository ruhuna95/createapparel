package com.rexhub.createapparel.client.citizen;

import com.rexhub.createapparel.citizen.population.DistrictTerritory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DistrictBoundaryMesh {
    public static final float INSET = 1 / 256F;
    public record Line(double x, double z, double endX, double endZ, int columnX, int columnZ, UUID district) { }
    private DistrictBoundaryMesh() { }

    public static Line edge(int x, int z, int endX, int endZ, int columnX, int columnZ, UUID district) {
        double dx = -(endZ - z) * INSET, dz = (endX - x) * INSET;
        return new Line(x + dx, z + dz, endX + dx, endZ + dz, columnX, columnZ, district);
    }

    public static List<Line> build(DistrictTerritory territory, int chunkX, int chunkZ) {
        var tile = territory.tile(chunkX, chunkZ); var lines = new ArrayList<Line>();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            var id = tile.districtAt(x, z); if (id == null) continue;
            int px = (chunkX << 4) + x, pz = (chunkZ << 4) + z;
            if (!id.equals(territory.districtAt(px, pz - 1))) lines.add(edge(px, pz, px + 1, pz, px, pz, id));
            if (!id.equals(territory.districtAt(px, pz + 1))) lines.add(edge(px + 1, pz + 1, px, pz + 1, px, pz, id));
            if (!id.equals(territory.districtAt(px - 1, pz))) lines.add(edge(px, pz + 1, px, pz, px, pz, id));
            if (!id.equals(territory.districtAt(px + 1, pz))) lines.add(edge(px + 1, pz, px + 1, pz + 1, px, pz, id));
        }
        return List.copyOf(lines);
    }
}
