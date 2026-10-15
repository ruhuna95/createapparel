package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.*;

public final class CitizenRouteMovement {
    public enum State { IDLE, DISCOVERING, MOVING, WAIT_CROSSING, WAIT_ROUTE, DESTINATION }
    private final Citizen citizen;
    @Nullable private UUID current;
    @Nullable private UUID previous;
    @Nullable private UUID target;
    @Nullable private UUID edge;
    @Nullable private Vec3 destination;
    @Nullable private CitizenRoutePlanner.Plan plan;
    @Nullable private RouteMarker currentDefinition;
    @Nullable private RouteMarker targetDefinition;
    @Nullable private RouteEdge edgeDefinition;
    private CitizenPathfindingScheduler.Priority priority = CitizenPathfindingScheduler.Priority.AUTONOMY;
    private List<BlockPos> endpoints = List.of();
    private int endpointIndex;
    private int planIndex;
    private boolean pathIssued;
    private boolean crossingStarted;
    private Vec3 pathStart = Vec3.ZERO;
    private long waitSince = -1;
    private long retryAfter;
    private long graphVersion = -1;
    private long gatewayCooldownUntil;
    private State state = State.IDLE;
    private final Map<UUID, Long> inaccessibleMarkers = new HashMap<>();

    public CitizenRouteMovement(Citizen citizen) { this.citizen = citizen; }
    public State state() { return state; }
    public boolean hasDestination() { return destination != null; }
    @Nullable public Vec3 currentDestination() {
        if (destination != null) return destination;
        if (target != null && targetDefinition != null) return targetDefinition.center();
        var navigation = citizen.getNavigation();
        var pos = navigation.getTargetPos();
        return navigation.isDone() || pos == null ? null : Vec3.atBottomCenterOf(pos);
    }
    public boolean preciseArrival() { return state == State.MOVING || state == State.DESTINATION; }
    @Nullable public UUID currentMarker() { return current; }
    public long crossingWaitTicks(long time) { return waitSince < 0 ? 0 : Math.max(0, time - waitSince); }

    public void requestDestination(Vec3 position, CitizenPathfindingScheduler.Priority priority) {
        if (citizen.level().isClientSide || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) return;
        reset();
        destination = position;
        this.priority = priority;
    }

    public void reset() {
        if (citizen.level() instanceof ServerLevel level) CitizenPathfindingScheduler.get(level).cancel(citizen);
        citizen.getNavigation().stop();
        current = previous = target = edge = null;
        currentDefinition = targetDefinition = null;
        edgeDefinition = null;
        destination = null;
        plan = null;
        endpoints = List.of();
        endpointIndex = planIndex = 0;
        pathIssued = crossingStarted = false;
        waitSince = -1;
        retryAfter = 0;
        graphVersion = -1;
        priority = CitizenPathfindingScheduler.Priority.AUTONOMY;
        state = State.IDLE;
        inaccessibleMarkers.clear();
    }

    public boolean gatewayReady(long time) { return time >= gatewayCooldownUntil; }
    public void gatewayArrived(long time) {
        var intent = destination;
        var intentPriority = priority;
        reset();
        destination = intent;
        priority = intentPriority;
        gatewayCooldownUntil = time + ModConfigs.COMMON.gatewayTransitCooldown.get();
    }

    public void tick() {
        if (!(citizen.level() instanceof ServerLevel level)) return;
        var manager = CitizenRouteManager.get(level);
        long time = level.getGameTime();
        inaccessibleMarkers.values().removeIf(until -> until <= time);
        var scheduler = CitizenPathfindingScheduler.get(level);
        var activity = CitizenActivityScheduler.get(level);
        if (citizen.isPassenger() || citizen.isLeashed()) { scheduler.cancel(citizen); citizen.getNavigation().stop(); pathIssued = false; return; }
        if (destination != null && priority != CitizenPathfindingScheduler.Priority.AUTONOMY) {
            activity.forceActive(citizen, CitizenActivityScheduler.Reason.PLAYER, 40);
        }
        if (graphVersion != manager.data().version()) {
            graphVersion = manager.data().version();
            inaccessibleMarkers.clear();
            plan = null;
            var connection = edge == null ? null : manager.data().edge(edge);
            boolean committedCrossing = crossingStarted && connection != null && connection.conditional()
                    && manager.loaded(manager.data().marker(connection.a())) && manager.loaded(manager.data().marker(connection.b()));
            boolean unchanged = (current == null || manager.loaded(manager.data().marker(current)) && Objects.equals(currentDefinition, manager.data().marker(current)))
                    && (target == null || manager.loaded(manager.data().marker(target)) && Objects.equals(targetDefinition, manager.data().marker(target)))
                    && (edge == null || Objects.equals(edgeDefinition, connection));
            if (!committedCrossing && !unchanged) {
                scheduler.cancel(citizen);
                citizen.getNavigation().stop();
                target = edge = null;
                current = previous = null;
                currentDefinition = targetDefinition = null;
                edgeDefinition = null;
                pathIssued = crossingStarted = false;
                waitSince = -1;
            }
        }
        if (target != null) {
            var marker = manager.data().marker(target);
            var connection = edge == null ? null : manager.data().edge(edge);
            if (!manager.loaded(marker) || edge != null && (connection == null || !manager.loaded(manager.data().marker(current)))) {
                scheduler.cancel(citizen);
                citizen.getNavigation().stop();
                current = target = edge = null;
                plan = null;
                pathIssued = crossingStarted = false;
                retryAfter = time + RouteSettings.RETRY_INTERVAL;
                state = State.WAIT_ROUTE;
                return;
            }
            if (marker.contains(citizen.position())) {
                citizen.getNavigation().stop();
                scheduler.cancel(citizen);
                if (edge != null) manager.runtime().succeeded(citizen.getUUID(), edge);
                previous = current;
                current = target;
                currentDefinition = targetDefinition;
                targetDefinition = null;
                edgeDefinition = null;
                target = edge = null;
                pathIssued = crossingStarted = false;
                waitSince = -1;
                if (plan != null) planIndex++;
            } else {
                if (connection != null && connection.conditional() && !crossingStarted && !manager.crossingOpen(connection)) {
                    state = State.WAIT_CROSSING;
                    faceCrossing(marker.center());
                    if (waitSince < 0) waitSince = time;
                    if (time - waitSince >= ModConfigs.COMMON.crossingWaitTimeout.get()) {
                        failEdge(manager, time, false);
                    }
                    return;
                }
                if (pathIssued && !citizen.getNavigation().isDone()) return;
                if (pathIssued) {
                    var candidate = endpoints.get(endpointIndex);
                    if (citizen.position().distanceToSqr(pathStart) < 0.25 || citizen.position().distanceToSqr(Vec3.atBottomCenterOf(candidate)) > 4
                            && citizen.position().distanceToSqr(Vec3.atBottomCenterOf(candidate)) <= 144) endpointIndex++;
                    pathIssued = false;
                }
                if (activity.state(citizen) == CitizenActivityScheduler.State.THROTTLED || scheduler.pending(citizen)) return;
                if (endpointIndex >= endpoints.size()) { failEdge(manager, time, true); return; }
                requestPath(manager, endpoints.get(endpointIndex), time);
                return;
            }
        }
        if (time < retryAfter || activity.state(citizen) == CitizenActivityScheduler.State.THROTTLED || scheduler.pending(citizen)) return;
        if (destination != null) { destination(manager, time); return; }
        if (citizen.citizenState().behavior().mode() != CitizenBehaviorMode.WALK) return;
        if (current == null || !manager.loaded(manager.data().marker(current)) || !manager.data().marker(current).contains(citizen.position())) {
            state = State.DISCOVERING;
            var nearby = manager.nearby(citizen.position(), RouteSettings.DISCOVERY_RADIUS).stream()
                    .filter(marker -> !inaccessibleMarkers.containsKey(marker.id())).toList();
            if (nearby.isEmpty()) { retryAfter = time + RouteSettings.RETRY_INTERVAL + Math.floorMod(citizen.getId(), 20); state = State.WAIT_ROUTE; return; }
            start(manager, nearby.get(0).id(), null);
            return;
        }
        var candidates = new ArrayList<>(manager.data().connected(current).stream()
                .filter(connection -> manager.loaded(manager.data().marker(connection.other(current))))
                .filter(connection -> !manager.runtime().blocked(citizen.getUUID(), connection.id(), time)).toList());
        if (candidates.size() > 1) candidates.removeIf(connection -> connection.other(current).equals(previous));
        if (candidates.isEmpty()) { retryAfter = time + RouteSettings.RETRY_INTERVAL; state = State.WAIT_ROUTE; return; }
        var next = candidates.get(citizen.getRandom().nextInt(candidates.size()));
        start(manager, next.other(current), next.id());
    }

    private void destination(CitizenRouteManager manager, long time) {
        if (destination == null) return;
        if (citizen.position().distanceToSqr(destination) <= 1) { reset(); return; }
        if (plan == null) {
            var resolved = manager.planner().plan(citizen, destination, inaccessibleMarkers.keySet());
            if (resolved.deferred()) { retryAfter = time + 5; return; }
            plan = resolved;
            planIndex = 0;
            if (!plan.fallback()) { start(manager, plan.nodes().get(0), null); return; }
        }
        if (!plan.fallback() && planIndex < plan.nodes().size()) {
            start(manager, plan.nodes().get(planIndex), plan.edges().get(planIndex - 1));
            return;
        }
        state = State.DESTINATION;
        if (!citizen.getNavigation().isDone()) return;
        var goal = BlockPos.containing(destination);
        if (!com.rexhub.createapparel.citizen.CitizenNavigation.hasLoadedRegion(manager.level(), goal, 0)) { retryAfter = time + RouteSettings.RETRY_INTERVAL; return; }
        var expected = destination;
        var waypoint = intermediate(Vec3.atBottomCenterOf(goal));
        CitizenPathfindingScheduler.get(manager.level()).submit(citizen, priority, () -> expected.equals(destination), () -> {
            var path = citizen.getNavigation().createPath(waypoint, 0);
            if (path != null && path.canReach() && citizen.getNavigation().moveTo(path, 1)) return;
            plan = null;
            retryAfter = manager.level().getGameTime() + RouteSettings.RETRY_INTERVAL;
        });
    }

    private void faceCrossing(Vec3 destination) {
        var delta = destination.subtract(citizen.position());
        if (delta.horizontalDistanceSqr() < 1.0E-6) return;
        float targetYaw = (float) (net.minecraft.util.Mth.atan2(delta.z, delta.x) * 180 / Math.PI) - 90;
        float yaw = net.minecraft.util.Mth.approachDegrees(citizen.getYRot(), targetYaw, 10);
        citizen.setYRot(yaw);
        citizen.setYBodyRot(yaw);
        citizen.setYHeadRot(yaw);
        citizen.getLookControl().setLookAt(destination.x, citizen.getEyeY(), destination.z);
    }

    private void start(CitizenRouteManager manager, UUID to, @Nullable UUID connection) {
        var marker = manager.data().marker(to);
        if (!manager.loaded(marker)) { plan = null; retryAfter = manager.level().getGameTime() + RouteSettings.RETRY_INTERVAL; return; }
        target = to;
        edge = connection;
        targetDefinition = marker;
        edgeDefinition = connection == null ? null : manager.data().edge(connection);
        endpoints = manager.endpointCandidates(citizen, current == null ? null : manager.data().marker(current), marker);
        endpointIndex = 0;
        pathIssued = crossingStarted = false;
        waitSince = -1;
        state = State.MOVING;
    }

    private void requestPath(CitizenRouteManager manager, BlockPos endpoint, long time) {
        var selectedTarget = target;
        var selectedEdge = edge;
        var waypoint = intermediate(Vec3.atBottomCenterOf(endpoint));
        CitizenPathfindingScheduler.get(manager.level()).submit(citizen, priority,
                () -> Objects.equals(selectedTarget, target) && Objects.equals(selectedEdge, edge) && graphVersion == manager.data().version()
                        && manager.loaded(manager.data().marker(selectedTarget)) && (selectedEdge == null
                        || manager.data().edge(selectedEdge) != null && manager.loaded(manager.data().marker(current))), () -> {
                    if (!manager.safeEndpoint(citizen, endpoint)) { endpointIndex++; return; }
                    var connection = selectedEdge == null ? null : manager.data().edge(selectedEdge);
                    if (connection != null && connection.conditional() && !crossingStarted && !manager.crossingOpen(connection)) return;
                    var path = citizen.getNavigation().createPath(waypoint, 0);
                    if (path == null || !path.canReach() || !citizen.getNavigation().moveTo(path, 1)) { endpointIndex++; return; }
                    pathIssued = true;
                    pathStart = citizen.position();
                    crossingStarted = connection != null && connection.conditional();
                    state = State.MOVING;
                });
    }

    private BlockPos intermediate(Vec3 endpoint) {
        var delta = endpoint.subtract(citizen.position());
        return BlockPos.containing(delta.lengthSqr() > 144 ? citizen.position().add(delta.normalize().scale(12)) : endpoint);
    }

    private void failEdge(CitizenRouteManager manager, long time, boolean navigationFailure) {
        CitizenPathfindingScheduler.get(manager.level()).cancel(citizen);
        citizen.getNavigation().stop();
        if (edge != null) {
            if (navigationFailure) manager.runtime().failed(citizen.getUUID(), edge, time, manager.data().version());
            else manager.runtime().blockLocal(citizen.getUUID(), edge, time);
        } else if (target != null) inaccessibleMarkers.put(target, time + RouteSettings.TEMPORARY_BLOCK_TICKS);
        target = edge = null;
        pathIssued = crossingStarted = false;
        waitSince = -1;
        plan = null;
        retryAfter = time + 5;
        state = State.WAIT_ROUTE;
    }
}
