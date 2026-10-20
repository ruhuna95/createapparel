package com.rexhub.createapparel.client.renderer;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.CitizenGaugeBlock;
import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.citizen.population.GaugeSurface;
import com.rexhub.createapparel.citizen.population.GaugeWireOccupancy;
import com.rexhub.createapparel.citizen.population.GaugeWirePath;
import com.simibubi.create.content.logistics.factoryBoard.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.BiPredicate;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class GaugeWireRouter {
    private record Plane(Direction facing, int normal) {
        private static Plane of(BlockState state, BlockPos pos) {
            var facing = state.getValue(CitizenGaugeBlock.FACING);
            return new Plane(facing, facing.getAxis().choose(pos.getX(), pos.getY(), pos.getZ()));
        }
        private BlockPos project(Vec3 point) {
            return GaugeWirePath.project(facing, point);
        }
    }
    private static final Comparator<FactoryPanelPosition> POSITION_ORDER = Comparator
            .comparingLong((FactoryPanelPosition position) -> position.pos().asLong()).thenComparingInt(position -> position.slot().ordinal());
    private record Link(FactoryPanelPosition first, FactoryPanelPosition second) implements Comparable<Link> {
        private static Link of(FactoryPanelPosition a, FactoryPanelPosition b) {
            return POSITION_ORDER.compare(a, b) <= 0 ? new Link(a, b) : new Link(b, a);
        }
        @Override public int compareTo(Link other) {
            int order = POSITION_ORDER.compare(first, other.first);
            return order == 0 ? POSITION_ORDER.compare(second, other.second) : order;
        }
    }
    private record Wire(BlockState state, Link link) { }
    private record Fixed(BlockState state, FactoryPanelPosition own, List<Direction> path) { }
    private record Route(List<Direction> forward, List<Direction> backward) {
        private static Route of(List<Direction> path) {
            var reverse = new ArrayList<Direction>(path.size());
            for (int index = path.size() - 1; index >= 0; index--) reverse.add(path.get(index).getOpposite());
            return new Route(path, List.copyOf(reverse));
        }
    }
    private static final Map<Level, GaugeWireRouter> ROUTERS = new WeakHashMap<>();
    private final Map<ChunkPos, WeakReference<LevelChunk>> chunks = new HashMap<>();
    private final Map<Plane, LinkedHashMap<Link, Route>> routes = new HashMap<>();
    private final Map<Plane, SortedMap<Link, Wire>> wires = new HashMap<>();
    private final Map<Plane, List<Fixed>> fixed = new HashMap<>();
    private final Set<Plane> checked = new HashSet<>();
    private long checkedAt = -1;

    private GaugeWireRouter() { }
    public static void resetCaches() {
        for (var router : ROUTERS.values()) {
            router.routes.clear(); router.wires.clear(); router.fixed.clear(); router.checked.clear(); router.checkedAt = -1;
        }
    }

    @SubscribeEvent public static void loadChunk(ChunkEvent.Load event) {
        if (event.getLevel() instanceof Level level && level.isClientSide && event.getChunk() instanceof LevelChunk chunk)
            ROUTERS.computeIfAbsent(level, key -> new GaugeWireRouter()).chunks.put(chunk.getPos(), new WeakReference<>(chunk));
    }
    @SubscribeEvent public static void unloadChunk(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide) {
            var router = ROUTERS.get(level);
            if (router != null) router.chunks.remove(event.getChunk().getPos());
        }
    }
    @SubscribeEvent public static void unloadLevel(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide) ROUTERS.remove(level);
    }

    public static List<Direction> path(CitizenGaugeBlockEntity entity, CitizenGaugeBlockEntity.Connection edge) {
        var level = entity.getLevel();
        if (level == null) return List.of();
        var router = ROUTERS.computeIfAbsent(level, key -> new GaugeWireRouter());
        if (router.chunks.isEmpty()) {
            var chunk = level.getChunkSource().getChunk(entity.getBlockPos().getX() >> 4, entity.getBlockPos().getZ() >> 4,
                    net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
            if (chunk instanceof LevelChunk loaded) router.chunks.put(loaded.getPos(), new WeakReference<>(loaded));
        }
        long time = level.getGameTime();
        if (router.checkedAt < 0 || time < router.checkedAt || time - router.checkedAt >= 20) router.snapshot(level, time);
        var plane = Plane.of(entity.getBlockState(), entity.getBlockPos());
        if (router.checked.add(plane)) router.route(level, plane);
        var slots = FactoryPanelBlock.PanelSlot.values();
        var own = new FactoryPanelPosition(entity.getBlockPos(), slots[edge.slot()]);
        var link = Link.of(own, new FactoryPanelPosition(edge.target(), slots[edge.targetSlot()]));
        var paths = router.routes.get(plane); var route = paths == null ? null : paths.get(link);
        return route == null ? List.of() : own.equals(link.first()) ? route.forward() : route.backward();
    }

    private void snapshot(Level level, long time) {
        checkedAt = time; checked.clear(); wires.clear(); fixed.clear();
        var factories = new ArrayList<FactoryPanelBlockEntity>();
        for (var iterator = chunks.values().iterator(); iterator.hasNext();) {
            var chunk = iterator.next().get();
            if (chunk == null || !GaugeSurface.loaded(level, chunk.getPos().getWorldPosition())) { iterator.remove(); continue; }
            for (var entity : chunk.getBlockEntities().values()) {
                if (entity.isRemoved()) continue;
                var state = entity.getBlockState();
                if (entity instanceof CitizenGaugeBlockEntity gauge) {
                    var plane = Plane.of(state, entity.getBlockPos());
                    for (var edge : gauge.connections()) {
                        if (!gauge.hasPanel(edge.slot()) || !GaugeSurface.loaded(level, edge.target())
                                || !(level.getBlockEntity(edge.target()) instanceof CitizenGaugeBlockEntity target) || !target.hasPanel(edge.targetSlot())
                                || !GaugeSurface.compatible(state, entity.getBlockPos(), target.getBlockState(), edge.target())) continue;
                        var slots = FactoryPanelBlock.PanelSlot.values();
                        var link = Link.of(new FactoryPanelPosition(entity.getBlockPos(), slots[edge.slot()]),
                                new FactoryPanelPosition(edge.target(), slots[edge.targetSlot()]));
                        wires.computeIfAbsent(plane, key -> new TreeMap<>()).put(link, new Wire(state, link));
                    }
                } else if (entity instanceof FactoryPanelBlockEntity factory && state.getValue(FactoryPanelBlock.FACE) == AttachFace.WALL) {
                    factories.add(factory);
                }
            }
        }
        for (var factory : factories) {
            var state = factory.getBlockState(); var plane = Plane.of(state, factory.getBlockPos());
            if (!wires.containsKey(plane)) continue;
            for (var panel : factory.panels.values()) if (panel.isActive()) {
                var connections = new ArrayList<>(panel.targetedBy.values()); connections.addAll(panel.targetedByLinks.values());
                for (var connection : connections) fixed.computeIfAbsent(plane, key -> new ArrayList<>()).add(
                        new Fixed(state, panel.getPanelPosition(), List.copyOf(connection.getPath(level, state, panel.getPanelPosition()))));
            }
        }
        routes.keySet().retainAll(wires.keySet());
    }

    private void route(Level level, Plane plane) {
        var available = wires.getOrDefault(plane, Collections.emptySortedMap());
        var previous = routes.getOrDefault(plane, new LinkedHashMap<>());
        var order = new LinkedHashSet<Link>();
        previous.forEach((link, route) -> { if (!route.forward().isEmpty() && available.containsKey(link)) order.add(link); });
        order.addAll(available.keySet());
        var occupied = new GaugeWireOccupancy();
        for (var wire : fixed.getOrDefault(plane, List.of())) occupied.reserve(points(plane, wire.state(), wire.own(), wire.path()));
        var result = new LinkedHashMap<Link, Route>();
        for (var link : order) {
            var wire = available.get(link); var state = wire.state();
            var start = plane.project(GaugeWirePath.point(state, link.first(), BlockPos.ZERO));
            var end = plane.project(Vec3.atLowerCornerOf(link.second().pos()).add(CitizenGaugeBlock.slotCenter(state, link.second().slot().ordinal())));
            var projected = new HashMap<BlockPos, BlockPos>();
            java.util.function.Function<BlockPos, BlockPos> project = cell -> projected.computeIfAbsent(cell,
                    key -> plane.project(GaugeWirePath.point(state, link.first(), key)));
            BiPredicate<BlockPos, BlockPos> blocked = (from, to) -> occupied.blocks(project.apply(from), project.apply(to), start, end);
            var old = previous.get(link);
            var path = old != null && GaugeWirePath.clear(level, state, link.first(), old.forward(), blocked) ? old.forward()
                    : GaugeWirePath.find(level, state, link.first(), link.second(), blocked);
            result.put(link, old != null && old.forward() == path ? old : Route.of(path)); occupied.reserve(points(plane, state, link.first(), path));
        }
        routes.put(plane, result);
    }

    private static List<BlockPos> points(Plane plane, BlockState state, FactoryPanelPosition own, List<Direction> path) {
        if (path.isEmpty()) return List.of();
        var points = new ArrayList<BlockPos>(path.size() + 1); var cell = BlockPos.ZERO;
        points.add(plane.project(GaugeWirePath.point(state, own, cell)));
        for (var direction : path) { cell = cell.relative(direction); points.add(plane.project(GaugeWirePath.point(state, own, cell))); }
        return points;
    }
}
