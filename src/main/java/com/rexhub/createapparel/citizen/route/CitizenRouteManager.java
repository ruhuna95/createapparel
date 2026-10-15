package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.block.CitizenRouteMarkerBlock;
import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.citizen.CitizenNavigation;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.*;

public final class CitizenRouteManager {
    private static final Map<ServerLevel, CitizenRouteManager> LEVELS = new IdentityHashMap<>();
    private final ServerLevel level;
    private final CitizenRouteSavedData data;
    private final EdgeRuntimeState runtime = new EdgeRuntimeState();
    private final Map<EndpointKey, EndpointCache> endpoints = new LinkedHashMap<>();
    private final CitizenRoutePlanner planner;
    private long observedVersion = -1;
    private long loadRevision;
    private long conditionRevision, conditionVersion = -1, nextConditionPoll;
    private int conditionCursor;
    private List<RouteEdge> conditionalEdges = List.of();
    private final Map<UUID, Boolean> conditionStates = new HashMap<>();
    private record Removed(com.rexhub.createapparel.network.RouteViewPacket.Edge edge, long expires) { }
    private record Notice(BlockPos pos, int edges, boolean support, long expires) { }
    private final LinkedHashMap<UUID, Removed> removedEdges = new LinkedHashMap<>();
    private final Map<net.minecraft.server.level.ServerPlayer, Notice> notices = new WeakHashMap<>();
    private final Map<net.minecraft.server.level.ServerPlayer, Long> noticeTimes = new WeakHashMap<>();
    private long maintenanceRevision;
    private CitizenGatewayAccess gateways = CitizenGatewayAccess.UNAVAILABLE;
    private record EndpointKey(UUID from, UUID to, boolean child, long version, long loaded) { }
    private record EndpointCache(List<BlockPos> positions, long expires) { }

    private CitizenRouteManager(ServerLevel level) {
        this.level = level;
        data = level.getDataStorage().computeIfAbsent(CitizenRouteSavedData::load, CitizenRouteSavedData::new, CitizenRouteSavedData.NAME);
        planner = new CitizenRoutePlanner(this);
    }

    public static CitizenRouteManager get(ServerLevel level) { return LEVELS.computeIfAbsent(level, CitizenRouteManager::new); }
    public static void unload(ServerLevel level) { LEVELS.remove(level); }
    public static void clear() { LEVELS.clear(); }
    public ServerLevel level() { return level; }
    public CitizenRouteSavedData data() { refresh(); return data; }
    public CitizenRoutePlanner planner() { refresh(); return planner; }
    public EdgeRuntimeState runtime() { refresh(); return runtime; }
    public long loadRevision() { return loadRevision; }
    public long maintenanceRevision() { return maintenanceRevision; }
    public List<com.rexhub.createapparel.network.RouteViewPacket.Edge> removedEdges() { return removedEdges.values().stream().map(Removed::edge).toList(); }
    public void tickMaintenance() {
        if (removedEdges.values().removeIf(entry -> entry.expires <= level.getGameTime())) maintenanceRevision++;
        for (var player : level.players()) {
            var notice = notices.get(player);
            if (notice == null) continue;
            if (notice.expires <= level.getGameTime() || !RouteMarkerPermissions.holdingPhone(player)) { notices.remove(player); continue; }
            if (noticeTimes.getOrDefault(player, Long.MIN_VALUE / 2) + 20 > level.getGameTime()) continue;
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.createapparel.route."
                    + (notice.support ? "support_removed" : "marker_removed"), notice.pos.toShortString(), notice.edges), true);
            noticeTimes.put(player, level.getGameTime()); notices.remove(player);
        }
    }
    public long conditionRevision() {
        if (conditionVersion != data.version()) {
            conditionVersion = data.version(); conditionalEdges = data.conditionalEdges();
            conditionStates.clear(); conditionCursor = 0; conditionRevision++; nextConditionPoll = 0;
        }
        if (level.getGameTime() >= nextConditionPoll) {
            nextConditionPoll = level.getGameTime() + 5;
            for (int i = 0; i < Math.min(256, conditionalEdges.size()); i++) {
                var edge = conditionalEdges.get(conditionCursor);
                conditionCursor = (conditionCursor + 1) % conditionalEdges.size();
                boolean open = crossingOpen(edge);
                var previous = conditionStates.put(edge.id(), open);
                if (previous != null && previous != open) conditionRevision++;
            }
        }
        return conditionRevision;
    }
    public void setGatewayAccess(CitizenGatewayAccess access) { gateways = Objects.requireNonNull(access); loadChanged(); }

    private void refresh() {
        if (observedVersion != data.version()) {
            observedVersion = data.version();
            endpoints.clear();
            planner.clear();
        }
        runtime.update(data.version(), level.getGameTime());
    }

    public void loadChanged() { loadRevision++; endpoints.clear(); planner.clear(); }

    public RouteMarker register(CitizenRouteMarkerBlockEntity entity) {
        var existing = data.at(entity.getBlockPos());
        boolean created = existing == null;
        var type = ((CitizenRouteMarkerBlock) entity.getBlockState().getBlock()).type();
        UUID id = entity.markerId();
        if (existing == null) {
            if (id == null || data.marker(id) != null) id = UUID.randomUUID();
            existing = RouteMarker.create(id, entity.getBlockPos(), type);
            data.put(existing);
        } else if (existing.type() != type) { existing = existing.withType(type); data.put(existing); }
        if (!existing.id().equals(entity.markerId())) {
            entity.setMarkerId(existing.id());
            if (!created) loadChanged();
        }
        return existing;
    }

    public void reconcile(LevelChunk chunk) {
        for (var marker : data.inChunk(chunk.getPos().toLong())) {
            if (!(chunk.getBlockState(marker.pos()).getBlock() instanceof CitizenRouteMarkerBlock)) removeAt(marker.pos());
        }
        for (var entity : List.copyOf(chunk.getBlockEntities().values())) {
            if (entity instanceof CitizenRouteMarkerBlockEntity marker) register(marker);
        }
        loadChanged();
    }

    public boolean loaded(@Nullable RouteMarker marker) {
        if (marker == null || !CitizenNavigation.hasLoadedRegion(level, marker.pos(), 0)) return false;
        return level.getBlockEntity(marker.pos()) instanceof CitizenRouteMarkerBlockEntity entity && marker.id().equals(entity.markerId());
    }

    public List<RouteMarker> nearby(Vec3 point, int radius) {
        return data.nearby(BlockPos.containing(point), radius).stream().filter(this::loaded)
                .sorted(Comparator.comparingDouble(marker -> marker.center().distanceToSqr(point))).toList();
    }

    public boolean removeAt(BlockPos pos) {
        var marker = data.at(pos);
        if (marker == null) return false;
        var removed = new ArrayList<com.rexhub.createapparel.network.RouteViewPacket.Edge>();
        for (var edge : data.connected(marker.id())) {
            var a = data.marker(edge.a()); var b = data.marker(edge.b());
            if (a != null && b != null) removed.add(new com.rexhub.createapparel.network.RouteViewPacket.Edge(edge.id(), a.pos(), b.pos(),
                    edge.conditional(), false, com.rexhub.createapparel.network.RouteViewPacket.Health.REMOVED));
        }
        if (!data.remove(marker.id())) return false;
        for (var edge : removed) {
            if (removedEdges.size() >= 128) removedEdges.remove(removedEdges.keySet().iterator().next());
            removedEdges.put(edge.id(), new Removed(edge, level.getGameTime() + 200));
        }
        maintenanceRevision++;
        if (!removed.isEmpty()) for (var player : level.players()) {
            if (RouteMarkerPermissions.holdingPhone(player) && player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= 4096) {
                var previous = notices.get(player);
                notices.put(player, new Notice(pos, removed.size() + (previous == null ? 0 : previous.edges),
                        !CitizenRouteMarkerBlock.supported(level, pos), level.getGameTime() + 200));
            }
        }
        return true;
    }

    public boolean delete(UUID id) {
        var marker = data.marker(id);
        if (!loaded(marker)) return false;
        return level.removeBlock(marker.pos(), false);
    }

    public boolean changeType(UUID id, RouteMarkerType type) {
        var marker = data.marker(id);
        if (!loaded(marker) || marker.type() == type) return false;
        var block = switch (type) {
            case NORMAL -> ModBlocks.CITIZEN_ROUTE_MARKER.get();
            case GATEWAY -> ModBlocks.GATEWAY_ROUTE_MARKER.get();
            case CROSSING -> ModBlocks.CROSSING_ROUTE_MARKER.get();
        };
        if (!level.setBlock(marker.pos(), block.defaultBlockState(), 3)) return false;
        data.put(marker.withType(type));
        return true;
    }

    public boolean crossingOpen(RouteEdge edge) {
        if (!edge.conditional()) return true;
        var a = data.marker(edge.a());
        var b = data.marker(edge.b());
        return loaded(a) && loaded(b) && a.type() == RouteMarkerType.CROSSING && b.type() == RouteMarkerType.CROSSING
                && a.condition().open(level) && b.condition().open(level);
    }

    public boolean gatewayEnabled(RouteMarker marker) {
        return marker.type() == RouteMarkerType.GATEWAY && marker.districtId() != null && loaded(marker) && gateways.enabled(marker.districtId());
    }

    public boolean transitGateway(Citizen citizen, UUID source, UUID nextDistrict) {
        var departure = data.marker(source);
        if (!gatewayEnabledSafe(departure) || !departure.contains(citizen.position())
                || citizen.level() != level || !citizen.routeMovement().gatewayReady(level.getGameTime())
                || !gateways.linkedDistricts(departure.districtId()).contains(nextDistrict) || !gateways.enabled(nextDistrict)) return false;
        var arrivals = new ArrayList<>(data.markers().stream().filter(marker -> nextDistrict.equals(marker.districtId()))
                .filter(this::gatewayEnabled).toList());
        Collections.shuffle(arrivals, new Random(citizen.getRandom().nextLong()));
        for (var arrival : arrivals) {
            for (var pos : endpointCandidates(citizen, departure, arrival)) {
                if (!safeEndpoint(citizen, pos)) continue;
                citizen.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, citizen.getYRot(), citizen.getXRot());
                citizen.getNavigation().stop();
                citizen.routeMovement().gatewayArrived(level.getGameTime());
                return true;
            }
        }
        return false;
    }

    private boolean gatewayEnabledSafe(@Nullable RouteMarker marker) { return marker != null && gatewayEnabled(marker); }

    public List<BlockPos> endpointCandidates(Citizen citizen, @Nullable RouteMarker from, RouteMarker to) {
        refresh();
        var key = new EndpointKey(from == null ? to.id() : from.id(), to.id(), citizen.isBaby(), data.version(), loadRevision);
        var cached = endpoints.get(key);
        if (from != null && cached != null && cached.expires > level.getGameTime()) return cached.positions;
        var direction = from == null ? to.center().subtract(citizen.position()) : to.center().subtract(from.center());
        double length = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        double dx = length < 0.001 ? 1 : direction.x / length;
        double dz = length < 0.001 ? 0 : direction.z / length;
        var candidates = new LinkedHashSet<BlockPos>();
        double radius = Math.max(0, to.horizontalRadius() - 0.5);
        for (double along : new double[]{-radius * 0.75, 0, radius * 0.5}) {
            for (double side : new double[]{0, -radius * 0.5, radius * 0.5}) {
                var center = to.center().add(dx * along - dz * side, 0, dz * along + dx * side);
                int tolerance = (int) Math.floor(to.verticalTolerance());
                for (int y = 0; y <= tolerance; y++) {
                    for (int offset : y == 0 ? new int[]{0} : new int[]{y, -y}) {
                        var pos = BlockPos.containing(center).offset(0, offset, 0);
                        if (to.contains(Vec3.atBottomCenterOf(pos)) && safeEndpoint(citizen, pos)) candidates.add(pos);
                    }
                }
            }
        }
        var result = List.copyOf(candidates);
        if (endpoints.size() >= RouteSettings.MAX_CACHED_ROUTES) endpoints.remove(endpoints.keySet().iterator().next());
        if (from != null) endpoints.put(key, new EndpointCache(result, level.getGameTime() + 40));
        return result;
    }

    public boolean safeEndpoint(Citizen citizen, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.isInWorldBounds(pos.above()) || !level.getWorldBorder().isWithinBounds(pos)
                || !CitizenNavigation.hasLoadedRegion(level, pos, 1) || !level.getFluidState(pos).isEmpty()
                || WalkNodeEvaluator.getBlockPathTypeStatic(level, pos.mutable()) != BlockPathTypes.WALKABLE) return false;
        var bounds = citizen.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(citizen.position()));
        return level.getWorldBorder().isWithinBounds(bounds) && level.noCollision(citizen, bounds);
    }
}
