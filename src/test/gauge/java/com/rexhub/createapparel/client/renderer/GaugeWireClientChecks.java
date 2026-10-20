package com.rexhub.createapparel.client.renderer;

import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.citizen.population.GaugeWirePath;
import com.rexhub.createapparel.citizen.population.GaugeWireOccupancy;
import com.rexhub.createapparel.citizen.population.CitizenPopulationManager;
import com.rexhub.createapparel.init.ModBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;

public final class GaugeWireClientChecks {
    private GaugeWireClientChecks() { }
    private static final BlockPos LEFT = new BlockPos(14, 73, 0), RIGHT = new BlockPos(16, 73, 0);
    private static final BlockPos BOTTOM = new BlockPos(15, 71, 0), TOP = new BlockPos(15, 75, 0);

    public static void installCrossing(ServerLevel level, boolean factory) {
        for (int x = 13; x <= 17; x++) for (int y = 71; y <= 75; y++) level.setBlock(new BlockPos(x, y, 1), Blocks.STONE.defaultBlockState(), 3);
        for (var pos : List.of(LEFT, RIGHT, BOTTOM, TOP)) {
            level.destroyBlock(pos, false);
            if (factory && (pos.equals(LEFT) || pos.equals(RIGHT))) {
                level.setBlock(pos, com.simibubi.create.AllBlocks.FACTORY_GAUGE.getDefaultState()
                        .setValue(FactoryPanelBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                        .setValue(FactoryPanelBlock.FACING, Direction.NORTH), 3);
                ((com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity) level.getBlockEntity(pos))
                        .addPanel(FactoryPanelBlock.PanelSlot.values()[0], null);
            } else {
                level.setBlock(pos, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
                ((CitizenGaugeBlockEntity) level.getBlockEntity(pos)).initialize(null);
            }
        }
        var manager = CitizenPopulationManager.get(level);
        if (factory) {
            var panel = ((com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity) level.getBlockEntity(LEFT))
                    .panels.get(FactoryPanelBlock.PanelSlot.values()[0]);
            var target = new FactoryPanelPosition(RIGHT, FactoryPanelBlock.PanelSlot.values()[0]);
            var connection = new FactoryPanelConnection(target, 1); connection.arrowBendMode = 0;
            panel.targetedBy.put(target, connection); panel.blockEntity.sendData();
        } else if (!manager.link(manager.data().at(LEFT).id(), manager.data().at(RIGHT).id()))
            throw new AssertionError("Create horizontal Citizen wire");
        if (!manager.link(manager.data().at(BOTTOM).id(), manager.data().at(TOP).id())) throw new AssertionError("Create crossing Citizen wire");
    }

    private record Wire(CitizenGaugeBlockEntity owner, CitizenGaugeBlockEntity.Connection edge, List<Direction> path) { }
    private static Wire citizenWire(BlockPos a, BlockPos b) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(a) instanceof CitizenGaugeBlockEntity first)
                || !(level.getBlockEntity(b) instanceof CitizenGaugeBlockEntity second)) throw new AssertionError("Missing crossing endpoint");
        var owner = first.connections().stream().anyMatch(edge -> edge.target().equals(b)) ? first : second;
        var target = owner == first ? b : a;
        var edge = owner.connections().stream().filter(connection -> connection.target().equals(target)).findFirst().orElseThrow();
        return new Wire(owner, edge, GaugeWireRouter.path(owner, edge));
    }
    private static List<BlockPos> grid(net.minecraft.world.level.block.state.BlockState state, FactoryPanelPosition own, List<Direction> path) {
        var result = new ArrayList<BlockPos>(); var cell = BlockPos.ZERO;
        result.add(GaugeWirePath.project(Direction.NORTH, GaugeWirePath.point(state, own, cell)));
        for (var direction : path) { cell = cell.relative(direction); result.add(GaugeWirePath.project(Direction.NORTH, GaugeWirePath.point(state, own, cell))); }
        return result;
    }
    private static FactoryPanelPosition own(Wire wire) {
        return new FactoryPanelPosition(wire.owner().getBlockPos(), FactoryPanelBlock.PanelSlot.values()[wire.edge().slot()]);
    }
    public static void assertCrossing(boolean factory) {
        var vertical = citizenWire(BOTTOM, TOP);
        if (vertical.path().isEmpty()) throw new AssertionError("Crossing wire needs a visible detour");
        if (vertical.path() != GaugeWireRouter.path(vertical.owner(), vertical.edge())) throw new AssertionError("Reuse plane route cache");
        var occupied = new GaugeWireOccupancy();
        if (factory) {
            var level = Minecraft.getInstance().level;
            if (level == null || !(level.getBlockEntity(LEFT) instanceof com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity entity))
                throw new AssertionError("Missing native Factory Gauge");
            var panel = entity.panels.get(FactoryPanelBlock.PanelSlot.values()[0]);
            var connection = panel.targetedBy.values().stream().findFirst().orElseThrow();
            occupied.reserve(grid(entity.getBlockState(), panel.getPanelPosition(), connection.getPath(level, entity.getBlockState(), panel.getPanelPosition())));
        } else {
            var horizontal = citizenWire(LEFT, RIGHT);
            if (horizontal.path().isEmpty()) throw new AssertionError("Existing wire must remain visible");
            occupied.reserve(grid(horizontal.owner().getBlockState(), own(horizontal), horizontal.path()));
            if (vertical.path() != GaugeWireRouter.path(vertical.owner(), vertical.edge())) throw new AssertionError("Drawing order must not change routes");
        }
        var points = grid(vertical.owner().getBlockState(), own(vertical), vertical.path());
        for (int index = 1; index < points.size(); index++) if (occupied.blocks(points.get(index - 1), points.get(index), points.get(0), points.get(points.size() - 1)))
            throw new AssertionError("Citizen wire crosses or overlaps " + (factory ? "Factory" : "Citizen") + " wire");
        if (vertical.path().size() <= 8) throw new AssertionError("Crossing must take a longer route around the fixed wire");
    }
    public static void assertDetour(BlockPos a, BlockPos b) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(a) instanceof CitizenGaugeBlockEntity first)
                || !(level.getBlockEntity(b) instanceof CitizenGaugeBlockEntity second)) throw new AssertionError("Missing wire endpoint");
        var owner = first.connections().stream().anyMatch(edge -> edge.target().equals(b)) ? first : second;
        var targetPos = owner == first ? b : a;
        var edge = owner.connections().stream().filter(connection -> connection.target().equals(targetPos)).findFirst().orElseThrow();
        var path = GaugeWireRouter.path(owner, edge);
        if (path.isEmpty() || path != GaugeWireRouter.path(owner, edge)) throw new AssertionError("Renderer must reuse a cached wire path");
        var slots = FactoryPanelBlock.PanelSlot.values(); var own = new FactoryPanelPosition(owner.getBlockPos(), slots[edge.slot()]);
        var target = new FactoryPanelPosition(targetPos, slots[edge.targetSlot()]);
        var connection = new FactoryPanelConnection(target, 1); connection.arrowBendMode = 0;
        var direct = connection.getPath(level, owner.getBlockState(), own);
        if (GaugeWirePath.clear(level, owner.getBlockState(), own, direct) || path.size() <= direct.size()
                || !GaugeWirePath.clear(level, owner.getBlockState(), own, path))
            throw new AssertionError("Actual cached wire must detour around the existing panel after relocation");
        var cell = BlockPos.ZERO;
        for (var direction : path) {
            cell = cell.relative(direction);
            if (!owner.getRenderBoundingBox().contains(GaugeWirePath.point(owner.getBlockState(), own, cell)))
                throw new AssertionError("Detour must remain within the wire renderer's visibility bounds");
        }
    }
}
