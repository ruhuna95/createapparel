package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.citizen.CitizenNavigation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.*;

public final class CitizenIntentController {
    private final Citizen citizen;
    @Nullable private CitizenIntent active;
    @Nullable private Vec3 destination;
    @Nullable private UUID departure;
    @Nullable private UUID progressMarker;
    private boolean gatewayPassed;
    private boolean gatewayFallbackAllowed;
    private boolean stucked;
    private long nextCheck, awaySince = -1, returnAfter;
    private long lastProgress;
    private double lastDistance = Double.POSITIVE_INFINITY;
    @Nullable private CitizenBehaviorState autonomyBehavior;
    @Nullable private Vec3 autonomyOrigin;
    private long autonomyProgressAt;
    private int autonomyRetries, wanderFailures, autonomyFailureBase;
    private boolean wanderRetry;
    private long walkAfter;
    public CitizenIntentController(Citizen citizen) { this.citizen = citizen; }
    @Nullable public CitizenIntent active() { return active; }
    public boolean stucked() { return stucked; }
    public void wanderFailed() { wanderFailures = Math.min(20, wanderFailures + 1); }
    public void wanderSucceeded() { wanderFailures = 0; }
    public void wanderIdle() { if (active == null) resetAutonomy(); }
    public boolean consumeWanderRetry() { boolean retry = wanderRetry; wanderRetry = false; return retry; }

    public void resumeWandering() {
        var behavior = citizen.citizenState().behavior();
        if (active != null || citizen.routeMovement().hasDestination() || behavior.mode() != CitizenBehaviorMode.WALK) return;
        citizen.citizenState().setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, citizen.blockPosition(),
                citizen.level().dimension(), behavior.wanderRadius()));
        walkAfter = citizen.level().getGameTime() + 1200;
        resetAutonomy();
    }

    public boolean request(CitizenIntent intent) {
        if (!(citizen.level() instanceof ServerLevel level) || citizen.isRemoved() || citizen.isNoAi()) return false;
        var manager = CitizenPopulationManager.get(level);
        if (intent.source() != CitizenIntent.Source.PLAYER && manager.reserved(citizen.getUUID())
                && intent.source() != CitizenIntent.Source.GAUGE_TRANSFER) return false;
        if (active != null && intent.source().ordinal() >= active.source().ordinal() && intent.source() != CitizenIntent.Source.PLAYER) return false;
        var mode = citizen.citizenState().behavior().mode();
        if (intent.source() != CitizenIntent.Source.PLAYER && (mode == CitizenBehaviorMode.FOLLOW || mode == CitizenBehaviorMode.POSE)) return false;
        if (intent.source() == CitizenIntent.Source.GAUGE_TRANSFER
                && (citizen.citizenState().population().force() || citizen.citizenState().population().independent())) return false;
        if (citizen.citizenState().population().independent() && (intent.source() == CitizenIntent.Source.IMPULSE_VISIT || intent.source() == CitizenIntent.Source.RETURN_HOME)) return false;
        if (intent.source() == CitizenIntent.Source.GAUGE_TRANSFER
                && !manager.linkedDistricts(citizen.citizenState().population().districtId()).contains(intent.district())) return false;
        if (intent.destinationType() == CitizenIntent.DestinationType.DISTRICT && !manager.enabled(intent.district())) return false;
        Vec3 point = intent.position();
        if (intent.destinationType() == CitizenIntent.DestinationType.DISTRICT) point = DistrictDestinations.choose(level, citizen, manager.data().district(intent.district()));
        if (point == null) return false;
        if (intent.source() == CitizenIntent.Source.PLAYER) manager.interrupt(citizen.getUUID());
        clear(false);
        active = intent; destination = point; gatewayPassed = false; lastProgress = level.getGameTime(); lastDistance = Double.POSITIVE_INFINITY;
        manager.intentChanged(citizen, null, intent);
        selectGateway(level);
        move(); return true;
    }

    public void playerOverride() {
        if (citizen.level() instanceof ServerLevel level) CitizenPopulationManager.get(level).interrupt(citizen.getUUID());
        clear(false);
    }
    public void cancel() { clear(true); }
    private void clear(boolean notify) {
        var previous = active;
        if (previous != null && citizen.level() instanceof ServerLevel server) CitizenPopulationManager.get(server).intentChanged(citizen, previous, null);
        active = null; destination = null; departure = null; progressMarker = null; stucked = false; gatewayFallbackAllowed = false;
        resetAutonomy();
        citizen.routeMovement().reset();
        if (notify && previous != null && previous.source() == CitizenIntent.Source.GAUGE_TRANSFER && citizen.level() instanceof ServerLevel level)
            CitizenPopulationManager.get(level).interrupt(citizen.getUUID());
    }
    private CitizenPathfindingScheduler.Priority priority() {
        return active != null && active.source() == CitizenIntent.Source.PLAYER ? CitizenPathfindingScheduler.Priority.PLAYER
                : active != null && active.source() == CitizenIntent.Source.RETURN_HOME ? CitizenPathfindingScheduler.Priority.SAFETY
                : active != null && active.source() == CitizenIntent.Source.FASHION ? CitizenPathfindingScheduler.Priority.SAFETY
                : active != null && active.source() == CitizenIntent.Source.GAUGE_TRANSFER ? CitizenPathfindingScheduler.Priority.TRANSFER
                : active != null && active.source() == CitizenIntent.Source.IMPULSE_VISIT ? CitizenPathfindingScheduler.Priority.AUTONOMY : CitizenPathfindingScheduler.Priority.ROUTE;
    }
    private void selectGateway(ServerLevel level) {
        departure = null;
        if (gatewayPassed || active == null || active.district() == null) return;
        if (active.source() == CitizenIntent.Source.GAUGE_TRANSFER && citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK
                && !gatewayFallbackAllowed) return;
        var population = CitizenPopulationManager.get(level);
        var source = active.source() == CitizenIntent.Source.GAUGE_TRANSFER ? population.data().district(citizen.citizenState().population().districtId())
                : population.physicalDistrict(citizen.position());
        var routes = CitizenRouteManager.get(level);
        if (source == null || source.districtId().equals(active.district()) || !population.linkedDistricts(source.districtId()).contains(active.district())) return;
        boolean arrival = routes.data().gateways(active.district()).stream().anyMatch(marker -> routes.gatewayEnabled(marker)
                && !routes.endpointCandidates(citizen, null, marker).isEmpty());
        if (!arrival) return;
        departure = routes.data().gateways(source.districtId()).stream().filter(routes::gatewayEnabled)
                .min(Comparator.comparingDouble(marker -> marker.center().distanceToSqr(citizen.position()))).map(RouteMarker::id).orElse(null);
    }
    public boolean useGatewayFallback() {
        if (!(citizen.level() instanceof ServerLevel level) || active == null || active.source() != CitizenIntent.Source.GAUGE_TRANSFER
                || citizen.citizenState().behavior().mode() != CitizenBehaviorMode.WALK || gatewayPassed || gatewayFallbackAllowed) return false;
        gatewayFallbackAllowed = true;
        selectGateway(level);
        if (departure == null) return false;
        move(); return true;
    }
    private void move() {
        if (active == null || destination == null || !(citizen.level() instanceof ServerLevel level)) return;
        var marker = departure == null ? null : CitizenRouteManager.get(level).data().marker(departure);
        citizen.routeMovement().requestIntentDestination(marker == null ? destination : marker.center(), priority(), marker == null ? arrivalRadius() : Math.min(0.25, marker.horizontalRadius() * 0.5));
    }
    private double arrivalRadius() { return active != null && active.destinationType() == CitizenIntent.DestinationType.DISTRICT ? 0.25 : 1; }
    public void tick() {
        if (!(citizen.level() instanceof ServerLevel level) || !citizen.isAlive()) return;
        long now = level.getGameTime();
        if (now < nextCheck) return;
        nextCheck = now + 20;
        var manager = CitizenPopulationManager.get(level);
        manager.update(citizen);
        trackAway(manager, now);
        if (citizen.isNoAi()) { if (active != null) cancel(); resetAutonomy(); return; }
        if (active == null) {
            if (CitizenActivityScheduler.get(level).state(citizen) == CitizenActivityScheduler.State.THROTTLED) { resetAutonomy(); return; }
            tickAutonomyRecovery(level, manager, now); autonomy(level, now); return;
        }
        if (now >= active.expiresAt() || active.district() != null && !manager.enabled(active.district())) { cancel(); return; }
        var reason = active.source() == CitizenIntent.Source.GAUGE_TRANSFER ? CitizenActivityScheduler.Reason.GAUGE_TRANSFER
                : active.source() == CitizenIntent.Source.RETURN_HOME ? CitizenActivityScheduler.Reason.RETURN_HOME : CitizenActivityScheduler.Reason.PLAYER;
        if (active.source() != CitizenIntent.Source.IMPULSE_VISIT && active.source() != CitizenIntent.Source.FASHION)
            CitizenActivityScheduler.get(level).forceActive(citizen, reason, 40);
        var routes = CitizenRouteManager.get(level);
        if (departure != null) {
            var marker = routes.data().marker(departure);
            if (marker == null || !routes.gatewayEnabled(marker) || !manager.linkedDistricts(marker.districtId()).contains(active.district())) { departure = null; move(); }
            else if (marker.contains(citizen.position())) {
                if (routes.transitGateway(citizen, departure, active.district())) { gatewayPassed = true; departure = null; lastProgress = now; move(); }
                else if (citizen.routeMovement().gatewayReady(now)) { departure = null; move(); }
            }
        }
        if (destination == null) { cancel(); return; }
        if (departure == null && citizen.position().distanceToSqr(destination) <= arrivalRadius() * arrivalRadius()) { complete(manager); return; }
        if (citizen.routeMovement().state() == CitizenRouteMovement.State.WAIT_CROSSING) { lastProgress = now; return; }
        var reachedMarker = citizen.routeMovement().currentMarker();
        if (reachedMarker != null && !reachedMarker.equals(progressMarker)) {
            progressMarker = reachedMarker; lastProgress = now; lastDistance = Double.POSITIVE_INFINITY;
        }
        double distance = citizen.position().distanceTo(citizen.routeMovement().currentDestination() == null ? destination : citizen.routeMovement().currentDestination());
        if (distance < lastDistance - 0.25) { lastDistance = distance; lastProgress = now; }
        if (now - lastProgress >= ModConfigs.COMMON.populationStuckTicks.get()
                || citizen.routeMovement().consecutiveFailures() >= ModConfigs.COMMON.populationStuckRetries.get()) {
            if (active.retryCount() < ModConfigs.COMMON.populationStuckRetries.get()) {
                active = active.retried(); lastProgress = now; lastDistance = Double.POSITIVE_INFINITY;
                if (active.district() != null) {
                    var point = DistrictDestinations.choose(level, citizen, manager.data().district(active.district()));
                    if (point != null) destination = point;
                }
                departure = null; move();
            } else {
                stucked = true;
                var state = citizen.citizenState().population();
                var district = manager.data().district(active.district() == null ? state.districtId() : active.district());
                if (!state.force() && !state.independent() && district != null && manager.loaded(district)) {
                    var point = DistrictDestinations.choose(level, citizen, district);
                    if (point != null) {
                        citizen.moveTo(point.x, point.y, point.z, citizen.getYRot(), citizen.getXRot());
                        complete(manager);
                    }
                }
            }
        } else if (!citizen.routeMovement().hasDestination()) move();
    }
    private void complete(CitizenPopulationManager manager) {
        var previous = active;
        clear(false);
        if (previous != null && previous.source() == CitizenIntent.Source.RETURN_HOME) awaySince = -1;
        if (previous != null && previous.source() == CitizenIntent.Source.GAUGE_TRANSFER) manager.arrived(citizen, previous.district());
    }
    private void resetAutonomy() {
        autonomyBehavior = null; autonomyOrigin = null;
        autonomyRetries = wanderFailures = autonomyFailureBase = 0; wanderRetry = false; stucked = false;
    }
    private void tickAutonomyRecovery(ServerLevel level, CitizenPopulationManager manager, long now) {
        var behavior = citizen.citizenState().behavior();
        var movement = citizen.routeMovement();
        if (!behavior.equals(autonomyBehavior)) { resetAutonomy(); autonomyBehavior = behavior; }
        if (behavior.mode() != CitizenBehaviorMode.WALK && behavior.mode() != CitizenBehaviorMode.WANDER
                || movement.hasDestination() || citizen.isPassenger() || citizen.isLeashed() || movement.state() == CitizenRouteMovement.State.WAIT_CROSSING) {
            resetAutonomy(); return;
        }
        if (autonomyOrigin == null || autonomyOrigin.distanceToSqr(citizen.position()) >= 0.25) {
            autonomyOrigin = citizen.position(); autonomyProgressAt = now;
            autonomyRetries = wanderFailures = autonomyFailureBase = 0; stucked = false;
        }
        boolean demand = behavior.mode() == CitizenBehaviorMode.WALK
                ? movement.currentDestination() != null || movement.consecutiveFailures() > 0
                : !citizen.getNavigation().isDone() || wanderFailures > 0;
        if (!demand || CitizenPathfindingScheduler.get(level).pending(citizen)
                || CitizenActivityScheduler.get(level).state(citizen) == CitizenActivityScheduler.State.THROTTLED && citizen.getNavigation().isDone()
                || !CitizenNavigation.hasLoadedRegion(level, citizen.blockPosition(), (int) Math.ceil(citizen.getAttributeValue(Attributes.FOLLOW_RANGE)) + 8)) {
            autonomyProgressAt = now; return;
        }
        int retries = ModConfigs.COMMON.populationStuckRetries.get();
        int failures = behavior.mode() == CitizenBehaviorMode.WALK ? movement.consecutiveFailures() : wanderFailures;
        if (failures < autonomyFailureBase) autonomyFailureBase = 0;
        if (now - autonomyProgressAt < ModConfigs.COMMON.populationStuckTicks.get() && failures - autonomyFailureBase < retries) return;
        autonomyProgressAt = now; wanderFailures = 0;
        CitizenActivityScheduler.get(level).forceActive(citizen, CitizenActivityScheduler.Reason.STUCKED, 40);
        if (autonomyRetries < retries) {
            autonomyRetries++;
            if (behavior.mode() == CitizenBehaviorMode.WALK) {
                autonomyFailureBase = failures; movement.retryAutonomy();
            } else { movement.reset(); wanderRetry = true; }
            return;
        }
        stucked = true;
        if (behavior.mode() == CitizenBehaviorMode.WALK && !movement.hasRecoveryMarker()) { resumeWandering(); return; }
        var state = citizen.citizenState().population();
        var home = manager.data().district(state.districtId());
        if (state.force() || state.independent() || !manager.loaded(home)) return;
        var point = DistrictDestinations.choose(level, citizen, home);
        if (point == null) return;
        movement.reset();
        citizen.moveTo(point.x, point.y, point.z, citizen.getYRot(), citizen.getXRot());
        manager.update(citizen); resetAutonomy(); wanderRetry = true;
    }
    private void autonomy(ServerLevel level, long now) {
        var state = citizen.citizenState().population();
        var manager = CitizenPopulationManager.get(level);
        var home = manager.data().district(state.districtId());
        if (state.independent() || home == null) { awaySince = -1; return; }
        if (awaySince >= 0 && now - awaySince >= returnAfter) {
            request(CitizenIntent.district(CitizenIntent.Source.RETURN_HOME, home.districtId(), now, ModConfigs.COMMON.populationIntentTimeout.get())); return;
        }
        if (Math.floorMod(now / 20 + citizen.getUUID().hashCode(), 5) == 0
                && CitizenActivityScheduler.get(level).state(citizen) != CitizenActivityScheduler.State.THROTTLED
                && (manager.tryWandering(citizen) || now >= walkAfter && manager.tryWalking(citizen))) return;
        if (Math.floorMod(now / 20 + citizen.getUUID().hashCode(), Math.max(1, ModConfigs.COMMON.populationAutonomyTicks.get() / 20)) != 0
                || citizen.citizenState().behavior().mode() != CitizenBehaviorMode.WANDER
                || CitizenActivityScheduler.get(level).state(citizen) == CitizenActivityScheduler.State.THROTTLED) return;
        double random = citizen.getRandom().nextDouble();
        double chance = ModConfigs.COMMON.populationVisitChance.get();
        if (random >= chance) return;
        var linked = manager.linkedDistricts(home.districtId()).stream().filter(manager::enabled).toList();
        if (linked.isEmpty()) return;
        var target = linked.get(citizen.getRandom().nextInt(linked.size()));
        var from = manager.data().owns(home, citizen.position()) ? home.districtId() : null;
        if (random < chance / RouteEdgeCost.homeDepartureMultiplier(home.districtId(), from, target))
            request(CitizenIntent.district(CitizenIntent.Source.IMPULSE_VISIT, target, now, ModConfigs.COMMON.populationIntentTimeout.get()));
    }
    private void trackAway(CitizenPopulationManager manager, long now) {
        var state = citizen.citizenState().population();
        var home = manager.data().district(state.districtId());
        if (state.independent() || home == null || manager.data().owns(home, citizen.position())) awaySince = -1;
        else if (awaySince < 0) {
            awaySince = now;
            returnAfter = (long) (ModConfigs.COMMON.populationReturnHomeTicks.get() * (0.75 + citizen.getRandom().nextDouble() * 0.5));
        }
    }
}
