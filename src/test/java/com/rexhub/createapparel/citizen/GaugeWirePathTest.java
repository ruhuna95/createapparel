package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.population.GaugeWirePath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GaugeWirePathTest {
    private static BlockPos end(List<Direction> path, Set<BlockPos> blocked) {
        var cell = BlockPos.ZERO;
        for (int index = 0; index < path.size(); index++) {
            cell = cell.relative(path.get(index));
            if (index < path.size() - 1) assertFalse(blocked.contains(cell), "Wire must not cross a blocked panel");
        }
        return cell;
    }
    @Test void straightWireDetoursAroundAnIntermediatePanel() {
        var target = new BlockPos(8, 0, 0); var blocked = Set.of(new BlockPos(4, 0, 0));
        var path = GaugeWirePath.detour(target, blocked::contains);
        assertEquals(10, path.size()); assertEquals(target, end(path, blocked));
        assertEquals(path, GaugeWirePath.detour(target, blocked::contains));
    }
    @Test void negativeAndDiagonalTargetsPreserveTheirExactEndpoint() {
        var blocked = Set.of(new BlockPos(-2, 0, 0), new BlockPos(-4, 0, -2));
        var target = new BlockPos(-7, 0, -5);
        var path = GaugeWirePath.detour(target, blocked::contains);
        assertFalse(path.isEmpty()); assertEquals(target, end(path, blocked));
    }
    @Test void occupiedTargetIsAllowedButOtherPanelsAreNot() {
        var target = new BlockPos(1, 0, 0);
        assertEquals(List.of(Direction.EAST), GaugeWirePath.detour(target, ignored -> true));
    }
    @Test void sealedStartDoesNotFallBackToDrawingThroughPanels() {
        assertTrue(GaugeWirePath.detour(new BlockPos(8, 0, 0), ignored -> true).isEmpty());
    }
    @Test void unreachableSearchHasABoundedObstacleBudget() {
        int[] probes = {0};
        var target = new BlockPos(80, 0, 80);
        var path = GaugeWirePath.detour(target, cell -> { probes[0]++; return cell.distManhattan(target) == 1; });
        assertTrue(path.isEmpty()); assertTrue(probes[0] <= GaugeWirePath.MAX_VISITED * 4);
    }
}
