package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.block.CitizenGaugeBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.BiPredicate;

public final class GaugeWirePath {
    public static final int MAX_VISITED = 4096;
    private static final int PADDING = 4;
    private static final List<Direction> DIRECTIONS = List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
    private record Visit(BlockPos cell, int distance, int score) { }
    private record Previous(BlockPos cell, Direction direction) { }
    private GaugeWirePath() { }

    public static List<Direction> find(Level level, BlockState state, FactoryPanelPosition own, FactoryPanelPosition target) {
        return find(level, state, own, target, (from, to) -> false);
    }

    public static List<Direction> find(Level level, BlockState state, FactoryPanelPosition own, FactoryPanelPosition target,
                                       BiPredicate<BlockPos, BlockPos> blockedStep) {
        var connection = new FactoryPanelConnection(target, 1);
        List<Direction> direct = List.of();
        for (int mode = 0; mode <= 4; mode++) {
            connection.arrowBendMode = mode;
            var candidate = List.copyOf(connection.getPath(level, state, own));
            if (mode == 0) direct = candidate;
            if (clear(level, state, own, candidate, blockedStep)) return candidate;
        }
        var end = BlockPos.ZERO;
        for (var direction : direct) end = end.relative(direction);
        return detour(end, cell -> blocked(level, state, own, cell), blockedStep);
    }

    public static boolean clear(Level level, BlockState state, FactoryPanelPosition own, List<Direction> path) {
        return clear(level, state, own, path, (from, to) -> false);
    }

    public static boolean clear(Level level, BlockState state, FactoryPanelPosition own, List<Direction> path,
                                BiPredicate<BlockPos, BlockPos> blockedStep) {
        var cell = BlockPos.ZERO;
        for (int index = 0; index < path.size(); index++) {
            var next = cell.relative(path.get(index));
            if (blockedStep.test(cell, next) || index < path.size() - 1 && blocked(level, state, own, next)) return false;
            cell = next;
        }
        return !path.isEmpty();
    }

    public static Vec3 point(BlockState state, FactoryPanelPosition own, BlockPos cell) {
        var offset = Vec3.atLowerCornerOf(cell).scale(0.5);
        offset = VecHelper.rotate(offset, 180, Direction.Axis.Y);
        offset = VecHelper.rotate(offset, 90, Direction.Axis.X);
        offset = VecHelper.rotate(offset, Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(state), Direction.Axis.Y);
        return Vec3.atLowerCornerOf(own.pos()).add(CitizenGaugeBlock.slotCenter(state, own.slot().ordinal())).add(offset);
    }

    public static BlockPos project(Direction facing, Vec3 point) {
        double horizontal = facing.getAxis() == Direction.Axis.X ? point.z : point.x;
        return new BlockPos((int) Math.round(horizontal * 2 - .5), 0, (int) Math.round(point.y * 2 - .5));
    }

    private static boolean blocked(Level level, BlockState state, FactoryPanelPosition own, BlockPos cell) {
        var point = point(state, own, cell);
        return !GaugeSurface.loaded(level, BlockPos.containing(point)) || !level.noCollision(new AABB(point, point).inflate(1 / 128.0));
    }

    public static List<Direction> detour(BlockPos end, Predicate<BlockPos> blocked) {
        return detour(end, blocked, (from, to) -> false);
    }

    public static List<Direction> detour(BlockPos end, Predicate<BlockPos> blocked, BiPredicate<BlockPos, BlockPos> blockedStep) {
        int minX = Math.min(0, end.getX()) - PADDING, maxX = Math.max(0, end.getX()) + PADDING;
        int minZ = Math.min(0, end.getZ()) - PADDING, maxZ = Math.max(0, end.getZ()) + PADDING;
        var queue = new PriorityQueue<Visit>(Comparator.comparingInt(Visit::score).thenComparingInt(Visit::distance)
                .thenComparingInt(visit -> visit.cell().getX()).thenComparingInt(visit -> visit.cell().getZ()));
        var distances = new HashMap<BlockPos, Integer>(); var previous = new HashMap<BlockPos, Previous>();
        var obstacles = new HashMap<BlockPos, Boolean>();
        queue.add(new Visit(BlockPos.ZERO, 0, end.distManhattan(BlockPos.ZERO))); distances.put(BlockPos.ZERO, 0);
        for (int visited = 0; !queue.isEmpty() && visited < MAX_VISITED; visited++) {
            var current = queue.remove();
            if (current.distance() != distances.get(current.cell())) continue;
            if (current.cell().equals(end)) {
                var result = new ArrayList<Direction>(); var cursor = end;
                while (!cursor.equals(BlockPos.ZERO)) {
                    var step = previous.get(cursor); result.add(step.direction()); cursor = step.cell();
                }
                Collections.reverse(result); return List.copyOf(result);
            }
            for (var direction : DIRECTIONS) {
                var next = current.cell().relative(direction); int distance = current.distance() + 1;
                if (next.getX() < minX || next.getX() > maxX || next.getZ() < minZ || next.getZ() > maxZ
                        || distance >= distances.getOrDefault(next, Integer.MAX_VALUE)
                        || !next.equals(end) && obstacles.computeIfAbsent(next, blocked::test)
                        || blockedStep.test(current.cell(), next)) continue;
                distances.put(next, distance); previous.put(next, new Previous(current.cell(), direction));
                queue.add(new Visit(next, distance, distance + next.distManhattan(end)));
            }
        }
        return List.of();
    }
}
