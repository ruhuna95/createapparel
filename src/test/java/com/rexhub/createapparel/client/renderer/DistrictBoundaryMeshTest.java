package com.rexhub.createapparel.client.renderer;

import com.rexhub.createapparel.citizen.population.DistrictTerritory;
import com.rexhub.createapparel.client.citizen.DistrictBoundaryMesh;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class DistrictBoundaryMeshTest {
    @Test void sharedBoundaryHasBothColorsOnOppositeSidesOfTheSameBlockEdge() {
        var territory = new DistrictTerritory(); var first = new UUID(0, 1); var second = new UUID(0, 2);
        territory.put(new DistrictTerritory.Seed(first, 14, 0, 1));
        territory.put(new DistrictTerritory.Seed(second, 17, 0, 1));
        var lines = Stream.concat(DistrictBoundaryMesh.build(territory, 0, 0).stream(), DistrictBoundaryMesh.build(territory, 1, 0).stream()).toList();
        var left = lines.stream().filter(line -> line.district().equals(first) && line.columnX() == 15 && line.columnZ() == 0 && line.x() == line.endX() && line.x() > 15.5).toList();
        var right = lines.stream().filter(line -> line.district().equals(second) && line.columnX() == 16 && line.columnZ() == 0 && line.x() == line.endX() && line.x() < 16.5).toList();
        assertEquals(1, left.size()); assertEquals(1, right.size());
        assertEquals(16 - DistrictBoundaryMesh.INSET, left.get(0).x());
        assertEquals(16 + DistrictBoundaryMesh.INSET, right.get(0).x());
    }

    @Test void eachSideIsInsetIntoItsOwnBlockIncludingNegativeCoordinates() {
        var id = UUID.randomUUID(); var territory = new DistrictTerritory();
        territory.put(new DistrictTerritory.Seed(id, -2, -2, 1));
        var lines = DistrictBoundaryMesh.build(territory, -1, -1);
        assertEquals(12, lines.size());
        for (var line : lines) {
            double x = (line.x() + line.endX()) / 2, z = (line.z() + line.endZ()) / 2;
            assertTrue(x > line.columnX() && x < line.columnX() + 1);
            assertTrue(z > line.columnZ() && z < line.columnZ() + 1);
            assertEquals(id, line.district());
        }
    }

    @Test void neighboringBlocksInTheSameDistrictDoNotCreateInteriorLines() {
        var territory = new DistrictTerritory(); var id = UUID.randomUUID();
        territory.put(new DistrictTerritory.Seed(id, 4, 4, 1));
        assertEquals(12, DistrictBoundaryMesh.build(territory, 0, 0).size());
    }
    @Test void insetRemainsOneOver256EvenNearTheWorldBorder() {
        assertEquals(1 / 256F, DistrictBoundaryMesh.INSET);
        var line = DistrictBoundaryMesh.edge(29999999, -29999999, 30000000, -29999999, 29999999, -29999999, UUID.randomUUID());
        assertEquals(-29999999 + 1 / 256D, line.z());
    }
}
