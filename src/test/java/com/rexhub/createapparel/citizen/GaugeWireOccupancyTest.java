package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.population.GaugeWireOccupancy;
import com.rexhub.createapparel.citizen.population.GaugeWirePath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GaugeWireOccupancyTest {
    private static List<BlockPos> points(BlockPos start, List<Direction> path) {
        var result = new ArrayList<BlockPos>(); result.add(start);
        for (var direction : path) { start = start.relative(direction); result.add(start); }
        return result;
    }
    private static void assertClear(GaugeWireOccupancy occupied, BlockPos start, List<Direction> path) {
        var positions = points(start, path); var end = positions.get(positions.size() - 1);
        for (int index = 1; index < positions.size(); index++)
            assertFalse(occupied.blocks(positions.get(index - 1), positions.get(index), start, end));
    }
    @Test void collinearWireDetoursWithoutReusingAnySegment() {
        var occupied = new GaugeWireOccupancy(); var end = new BlockPos(4, 0, 0);
        occupied.reserve(points(BlockPos.ZERO, List.of(Direction.EAST, Direction.EAST, Direction.EAST, Direction.EAST)));
        var path = GaugeWirePath.detour(end, ignored -> false, (a, b) -> occupied.blocks(a, b, BlockPos.ZERO, end));
        assertTrue(path.size() > 4); assertClear(occupied, BlockPos.ZERO, path);
    }
    @Test void crossingWireGoesAroundTheExistingLine() {
        var occupied = new GaugeWireOccupancy(); var start = new BlockPos(2, 0, -3); var end = new BlockPos(2, 0, 3);
        occupied.reserve(points(BlockPos.ZERO, List.of(Direction.EAST, Direction.EAST, Direction.EAST, Direction.EAST)));
        var path = GaugeWirePath.detour(end.subtract(start), ignored -> false,
                (a, b) -> occupied.blocks(a.offset(start), b.offset(start), start, end));
        assertTrue(path.size() > 6); assertClear(occupied, start, path);
    }
    @Test void sharedGaugeEndpointIsAllowedButNotASharedFirstSegment() {
        var occupied = new GaugeWireOccupancy();
        occupied.reserve(List.of(BlockPos.ZERO, new BlockPos(1, 0, 0)));
        assertFalse(occupied.blocks(BlockPos.ZERO, new BlockPos(0, 0, 1), BlockPos.ZERO, new BlockPos(0, 0, 1)));
        assertTrue(occupied.blocks(BlockPos.ZERO, new BlockPos(1, 0, 0), BlockPos.ZERO, new BlockPos(1, 0, 0)));
        assertTrue(occupied.blocks(new BlockPos(1, 0, 0), BlockPos.ZERO, new BlockPos(1, 0, 0), BlockPos.ZERO));
    }
    @Test void unrelatedWireEndpointCannotBeCrossedAndInteriorCannotBecomeAJunction() {
        var occupied = new GaugeWireOccupancy();
        occupied.reserve(List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)));
        assertTrue(occupied.blocks(new BlockPos(0, 0, -1), BlockPos.ZERO, new BlockPos(0, 0, -2), new BlockPos(0, 0, 2)));
        assertTrue(occupied.blocks(new BlockPos(1, 0, -1), new BlockPos(1, 0, 0), new BlockPos(1, 0, -1), new BlockPos(1, 0, 0)));
    }
    @Test void parallelNeighbouringWiresRemainIndependent() {
        var occupied = new GaugeWireOccupancy();
        occupied.reserve(points(BlockPos.ZERO, List.of(Direction.EAST, Direction.EAST)));
        assertClear(occupied, new BlockPos(0, 0, 1), List.of(Direction.EAST, Direction.EAST));
    }
    @Test void occupiedStartInteriorHasNoCrossingFallback() {
        var occupied = new GaugeWireOccupancy();
        occupied.reserve(List.of(new BlockPos(-1, 0, 0), BlockPos.ZERO, new BlockPos(1, 0, 0)));
        var end = new BlockPos(0, 0, 3);
        assertTrue(GaugeWirePath.detour(end, ignored -> false, (a, b) -> occupied.blocks(a, b, BlockPos.ZERO, end)).isEmpty());
    }
}
