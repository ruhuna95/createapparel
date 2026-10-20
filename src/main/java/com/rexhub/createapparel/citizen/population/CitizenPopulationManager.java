package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.citizen.CitizenSpawnInitialization;
import com.rexhub.createapparel.citizen.CitizenNavigation;
import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.*;

public final class CitizenPopulationManager implements CitizenGatewayAccess {
    private static final Map<ServerLevel, CitizenPopulationManager> LEVELS = new IdentityHashMap<>();
    private final ServerLevel level;
    private final CitizenPopulationSavedData data;
    private final MarkerDistrictSynchronizer markerDistricts;
    private final DistrictRouteCapacity routeCapacity;
    private final Map<UUID, Citizen> loadedCitizens = new LinkedHashMap<>();
    private final Map<UUID, PopulationRequest> requests = new LinkedHashMap<>();
    private final Map<UUID, PopulationTransferChain> chains = new LinkedHashMap<>();
    private final PopulationChainIndex chainIndex = new PopulationChainIndex();
    private final ArrayDeque<UUID> chainQueue = new ArrayDeque<>();
    private final Map<UUID, UUID> reservations = new HashMap<>();
    private final Map<UUID, Long> citizenCells = new HashMap<>();
    private final Map<Long, Integer> densities = new HashMap<>();
    private final ArrayDeque<UUID> gauges = new ArrayDeque<>();
    private final Set<UUID> queuedGauges = new HashSet<>();
    private final Map<UUID, Long> nextEvaluation = new HashMap<>();
    private int spawnsThisTick;
    private int removalsThisTick;
    private long lastTick = Long.MIN_VALUE;

    private CitizenPopulationManager(ServerLevel level) {
        this.level = level;
        data = level.getDataStorage().computeIfAbsent(CitizenPopulationSavedData::load, CitizenPopulationSavedData::new, CitizenPopulationSavedData.NAME);
        data.gauges().forEach(gauge -> queue(gauge.id()));
        var routes = CitizenRouteManager.get(level);
        routes.setGatewayAccess(this);
        markerDistricts = new MarkerDistrictSynchronizer(data.territory(), routes.data());
        routeCapacity = new DistrictRouteCapacity(routes.data(), data.territory(), routes::loaded, routes::loadRevision);
    }
    public static CitizenPopulationManager get(ServerLevel level) { return LEVELS.computeIfAbsent(level, CitizenPopulationManager::new); }
    public static void unload(ServerLevel level) { LEVELS.remove(level); }
    public static void clear() { LEVELS.clear(); }
    public CitizenPopulationSavedData data() { return data; }
    public DistrictRouteCapacity routeCapacity() { return routeCapacity; }
    public void modeChanged(Citizen citizen) { data.walking(citizen.getUUID(), citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK); }
    public void queueMarkerChunk(long chunk) { markerDistricts.queue(chunk); }
    public RouteMarker refreshMarkerDistrict(RouteMarker marker) { return markerDistricts.refresh(marker); }
    public int loadedCount() { return loadedCitizens.size(); }
    public int chainCount() { return chains.size(); }
    public int requestCount() { return requests.size(); }
    public boolean reserved(UUID citizen) { return reservations.containsKey(citizen); }
    public int incoming(UUID gauge) { return chainIndex.incoming(gauge); }
    public int outgoing(UUID gauge) { return chainIndex.outgoing(gauge); }
    public int effective(CitizenGauge gauge) { return gauge.source() ? 0 : data.population(gauge.districtId()) + incoming(gauge.id()) - outgoing(gauge.id()); }
    public boolean loaded(@Nullable CitizenGauge gauge) {
        return gauge != null && CitizenNavigation.hasLoadedRegion(level, gauge.pos(), 0)
                && level.getBlockEntity(gauge.pos()) instanceof CitizenGaugeBlockEntity entity && gauge.id().equals(entity.gaugeId(gauge.slot()));
    }
    @Override public boolean enabled(UUID district) { return loaded(data.district(district)); }
    @Override public List<UUID> linkedDistricts(UUID district) {
        return data.linkedDistricts(district);
    }
    @Nullable public CitizenGauge physicalDistrict(Vec3 point) {
        return data.physicalDistrict(point);
    }
    public CitizenGauge register(CitizenGaugeBlockEntity entity) {
        return register(entity, 0);
    }
    public CitizenGauge register(CitizenGaugeBlockEntity entity, int slot) {
        var gauge = data.at(entity.getBlockPos(), slot);
        if (gauge == null) {
            gauge = CitizenGauge.create(entity.getBlockPos(), entity.initialCenter(slot), slot);
            if (gauge.source() && entity.outflow(slot)) gauge = gauge.withFlow(CitizenGauge.Flow.OUTFLOW);
            data.put(gauge);
        }
        else if (gauge.source() && entity.initialCenter(slot) != null) {
            gauge = new CitizenGauge(gauge.id(), gauge.pos(), UUID.randomUUID(), entity.initialCenter(slot), gauge.range(), gauge.target(), gauge.cooldownUntil(), false, slot);
            data.put(gauge);
        }
        entity.setGaugeId(slot, gauge.id()); queue(gauge.id());
        entity.refreshFlow(slot, gauge.outflow());
        CitizenRouteManager.get(level).setGatewayAccess(this);
        return gauge;
    }
    private void queue(UUID id) { if (queuedGauges.add(id)) gauges.addLast(id); }
    public void reconcile(LevelChunk chunk) {
        for (var gauge : data.inChunk(chunk.getPos().toLong()))
            if (!(chunk.getBlockEntity(gauge.pos()) instanceof CitizenGaugeBlockEntity entity) || !entity.hasPanel(gauge.slot())) remove(gauge.id());
        for (var entity : List.copyOf(chunk.getBlockEntities().values())) if (entity instanceof CitizenGaugeBlockEntity gauge)
            for (int slot = 0; slot < 4; slot++) if (gauge.hasPanel(slot)) register(gauge, slot);
    }
    public void removeAt(BlockPos pos) { for (var gauge : data.atAll(pos)) remove(gauge.id()); }
    public boolean relocate(UUID id, GaugeSurface.Target target) {
        var gauge = data.gauge(id);
        if (!loaded(gauge) || !GaugeSurface.loaded(level, target.pos()) || data.at(target.pos(), target.slot()) != null) return false;
        var old = (CitizenGaugeBlockEntity) level.getBlockEntity(gauge.pos());
        var state = old.getBlockState();
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atLowerCornerOf(target.pos()).add(
                com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(state, target.slot())), state.getValue(com.rexhub.createapparel.block.CitizenGaugeBlock.FACING), target.pos(), false);
        var backingHit = level.getBlockState(target.pos()).isAir() ? new net.minecraft.world.phys.BlockHitResult(hit.getLocation(), hit.getDirection(), target.pos().relative(hit.getDirection().getOpposite()), false) : hit;
        if (!target.equals(GaugeSurface.relocationTarget(level, gauge.pos(), state, backingHit))) return false;
        if (level.getBlockState(target.pos()).isAir() && !level.setBlock(target.pos(), state, 3)) return false;
        if (!(level.getBlockEntity(target.pos()) instanceof CitizenGaugeBlockEntity destination)) return false;
        var moved = gauge.relocate(target.pos(), target.slot()); data.put(moved);
        old.detachPanel(gauge.slot()); destination.acceptMovedPanel(moved);
        if (old.panelCount() == 0) level.removeBlock(gauge.pos(), false);
        data.linked(id).forEach(this::syncGauge);
        CitizenRouteManager.get(level).loadChanged(); return true;
    }
    public void remove(UUID id) {
        var gauge = data.gauge(id);
        if (gauge == null) return;
        var neighbors = Set.copyOf(data.linked(id));
        for (var chain : List.copyOf(chains.values())) if (chain.path().contains(id)) cancel(chain);
        requests.remove(id); data.remove(id); nextEvaluation.remove(id);
        neighbors.forEach(this::syncGauge);
        for (var citizen : loadedCitizens.values()) {
            if (Objects.equals(citizen.citizenState().population().districtId(), gauge.districtId()) && !gauge.source()) {
                citizen.citizenState().setPopulation(data.citizen(citizen.getUUID()));
                var intent = citizen.intents().active();
                if (intent != null && (intent.source() == CitizenIntent.Source.IMPULSE_VISIT || intent.source() == CitizenIntent.Source.RETURN_HOME)) citizen.intents().cancel();
            }
        }
        CitizenRouteManager.get(level).loadChanged();
    }
    public boolean link(UUID a, UUID b) {
        if (!data.link(a, b)) return false;
        syncGauge(a); syncGauge(b);
        CitizenRouteManager.get(level).loadChanged(); return true;
    }
    public boolean unlink(UUID a, UUID b) {
        if (!data.unlink(a, b)) return false;
        syncGauge(a); syncGauge(b);
        for (var chain : List.copyOf(chains.values())) if (!valid(chain)) cancel(chain);
        CitizenRouteManager.get(level).loadChanged(); return true;
    }
    private void syncGauge(UUID id) {
        var gauge = data.gauge(id);
        if (gauge != null && loaded(gauge) && level.getBlockEntity(gauge.pos()) instanceof CitizenGaugeBlockEntity entity) entity.sync();
    }
    public void settings(UUID id, int radius, int target) {
        var gauge = data.gauge(id);
        if (gauge != null) settings(id, radius, target, gauge.name());
    }
    public void flow(UUID id, CitizenGauge.Flow flow) {
        var gauge = data.gauge(id);
        if (!loaded(gauge) || !gauge.source() || gauge.flow() == flow) return;
        for (var chain : List.copyOf(chains.values())) if (chain.path().contains(id)) cancel(chain);
        data.put(gauge.withFlow(flow));
        requests.remove(id); nextEvaluation.remove(id);
        ((CitizenGaugeBlockEntity) level.getBlockEntity(gauge.pos())).refreshFlow(gauge.slot(), flow == CitizenGauge.Flow.OUTFLOW);
        syncGauge(id);
    }
    public void settings(UUID id, int radius, int target, String name) {
        var gauge = data.gauge(id);
        if (gauge == null || gauge.source()) return;
        data.put(gauge.settings(radius, target, name)); nextEvaluation.remove(id); syncGauge(id);
        for (var chain : List.copyOf(chains.values())) if (chain.path().get(chain.path().size() - 1).equals(id)
                && data.population(gauge.districtId()) >= target) cancel(chain);
    }
    public void join(Citizen citizen) {
        if (level.getEntity(citizen.getUUID()) instanceof Citizen existing && existing != citizen && !existing.isRemoved()) return;
        loadedCitizens.put(citizen.getUUID(), citizen);
        var saved = data.citizen(citizen.getUUID());
        if (saved != null) citizen.citizenState().setPopulation(saved);
        update(citizen);
    }
    public void update(Citizen citizen) {
        if (citizen.level() != level || !citizen.isAlive() || citizen.isRemoved()) return;
        loadedCitizens.put(citizen.getUUID(), citizen);
        var state = citizen.citizenState().population();
        if (state.districtId() != null && data.district(state.districtId()) == null) state = state.district(null, state.transferCooldownUntil());
        if (!state.independent() && state.districtId() == null) {
            var district = physicalDistrict(citizen.position());
            if (district != null) state = state.district(district.districtId(), state.transferCooldownUntil());
        }
        citizen.citizenState().setPopulation(state); data.register(citizen.getUUID(), state, citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK);
        long cell = cell(citizen.position());
        var previous = citizenCells.put(citizen.getUUID(), cell);
        if (previous == null || previous != cell) {
            if (previous != null) decrement(previous);
            densities.merge(cell, 1, Integer::sum);
        }
        if ((state.force() || state.independent()) && reserved(citizen.getUUID())) interrupt(citizen.getUUID());
    }
    public void flags(Citizen citizen, boolean independent, boolean force) {
        interrupt(citizen.getUUID());
        var intent = citizen.intents().active();
        if (independent && intent != null && (intent.source() == CitizenIntent.Source.IMPULSE_VISIT || intent.source() == CitizenIntent.Source.RETURN_HOME)) citizen.intents().cancel();
        citizen.citizenState().setPopulation(citizen.citizenState().population().flags(independent, force)); update(citizen);
    }
    public void leave(Citizen citizen, boolean permanent) {
        interrupt(citizen.getUUID());
        if (permanent) data.removeCitizen(citizen.getUUID());
        else if (citizen.isAlive()) data.register(citizen.getUUID(), citizen.citizenState().population(), citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK);
        loadedCitizens.remove(citizen.getUUID());
        var previous = citizenCells.remove(citizen.getUUID()); if (previous != null) decrement(previous);
    }
    private void decrement(long cell) { densities.computeIfPresent(cell, (key, count) -> count <= 1 ? null : count - 1); }
    private static long cell(Vec3 point) { return net.minecraft.world.level.ChunkPos.asLong(net.minecraft.util.Mth.floor(point.x) >> 4, net.minecraft.util.Mth.floor(point.z) >> 4); }
    public int density(Vec3 point) { return densities.getOrDefault(cell(point), 0); }
    public void interrupt(UUID citizen) {
        var id = reservations.get(citizen); var chain = id == null ? null : chains.get(id);
        if (chain != null) cancel(chain);
    }
    private boolean eligible(Citizen citizen, UUID district) {
        var state = citizen.citizenState().population();
        var active = citizen.intents().active();
        var mode = citizen.citizenState().behavior().mode();
        return citizen.level() == level && citizen.isAlive() && !citizen.isRemoved() && !citizen.isNoAi() && !citizen.isPassenger() && !citizen.isLeashed()
                && !state.independent() && !state.force() && district.equals(state.districtId()) && state.transferCooldownUntil() <= level.getGameTime()
                && !reserved(citizen.getUUID()) && (active == null || active.source() == CitizenIntent.Source.IMPULSE_VISIT)
                && mode != CitizenBehaviorMode.FOLLOW && mode != CitizenBehaviorMode.POSE
                && (!citizen.routeMovement().hasDestination() || active != null);
    }
    @Nullable private Citizen donor(CitizenGauge gauge) {
        for (var id : data.residents(gauge.districtId())) {
            var citizen = loadedCitizens.get(id);
            if (citizen != null && eligible(citizen, gauge.districtId())) return citizen;
        }
        return null;
    }
    public void tick() {
        long now = level.getGameTime();
        if (now == lastTick) return;
        lastTick = now; spawnsThisTick = 0; removalsThisTick = 0;
        markerDistricts.tick();
        routeCapacity.tick();
        data.networks().tick(64);
        for (int index = 0, size = Math.min(4, gauges.size()); index < size; index++) {
            var id = gauges.removeFirst(); var gauge = data.gauge(id);
            if (gauge == null) { queuedGauges.remove(id); continue; }
            boolean gaugeLoaded = loaded(gauge);
            if (CitizenNavigation.hasLoadedRegion(level, gauge.pos(), 0) && !gaugeLoaded) {
                remove(id); queuedGauges.remove(id); continue;
            }
            gauges.addLast(id);
            if (gaugeLoaded && !gauge.source() && level.getBlockEntity(gauge.pos()) instanceof CitizenGaugeBlockEntity entity)
                entity.refreshDistrictStatus(gauge.slot(), gauge.districtId(), gauge.name(), data.population(gauge.districtId()), gauge.target());
            if (!gaugeLoaded || gauge.influx() || now < nextEvaluation.getOrDefault(id, 0L)) continue;
            nextEvaluation.put(id, now + 100 + Math.floorMod(id.hashCode(), 40));
            if (gauge.outflow()) {
                data.networks().request(id);
                requests.computeIfAbsent(id, key -> new PopulationRequest(id, now));
                continue;
            }
            int population = effective(gauge);
            boolean resolving = gauge.target() > 0 && (gauge.resolving() ? data.population(gauge.districtId()) < gauge.target() : population < gauge.target() - gauge.buffer());
            data.put(gauge.resolve(resolving));
            if (resolving && population < gauge.target()) requests.computeIfAbsent(id, key -> new PopulationRequest(id, now));
            else requests.remove(id);
        }
        for (int index = 0, size = Math.min(16, chainQueue.size()); index < size; index++) {
            var id = chainQueue.removeFirst(); var chain = chains.get(id);
            if (chain == null) continue;
            chainQueue.addLast(id);
            if (!valid(chain) || now - chain.lastProgressAt() > ModConfigs.COMMON.populationHopTimeout.get()) { cancel(chain); continue; }
            advance(chain, now);
        }
        if (now % 20 != 0) return;
        var waiting = new ArrayList<>(requests.values());
        waiting.sort(PopulationRequest.schedulingOrder(request -> {
            var gauge = data.gauge(request.requester()); return gauge == null ? 0 : gauge.ratio(effective(gauge));
        }));
        int budget = 4;
        for (var request : waiting) {
            var requester = data.gauge(request.requester());
            if (requester != null && requester.outflow()) {
                if (!loaded(requester) || request.retryAt() > now || requester.cooldownUntil() > now) continue;
                if (data.networks().request(requester.id()) == null) continue;
                if (budget-- <= 0) break;
                request.attempted(now, ModConfigs.COMMON.populationRetryTicks.get());
                processOutflow(requester, now);
                continue;
            }
            if (requester == null || requester.source() || !requester.resolving() || requester.target() <= effective(requester)) { requests.remove(request.requester()); continue; }
            if (!loaded(requester) || request.retryAt() > now || busy(requester.id()) || requester.cooldownUntil() > now) continue;
            if (budget-- <= 0) break;
            request.attempted(now, ModConfigs.COMMON.populationRetryTicks.get());
            var network = data.networks().cached(requester.id());
            var path = request.findSupply(data, ModConfigs.COMMON.populationMaxHops.get(), ModConfigs.COMMON.populationQueryBudget.get(),
                    gauge -> !busy(gauge.id()) && loaded(gauge) && PopulationRequest.improves(gauge, effective(gauge), requester, effective(requester))
                            && (gauge.influx() || !gauge.source() && donor(gauge) != null),
                    network != null && network.hasOutflow() && network.hasSurplus());
            if (path.isEmpty() || path.stream().anyMatch(this::busy) || chains.size() >= ModConfigs.COMMON.populationMaxChains.get()) continue;
            var chain = new PopulationTransferChain(request.id(), path, now);
            chains.put(chain.id(), chain);
            chainIndex.put(chain);
            chainQueue.addLast(chain.id());
            var first = data.gauge(chain.from());
            if (!first.source()) {
                var citizen = donor(first);
                if (citizen == null) { cancel(chain); continue; }
                reserve(chain, citizen, now);
            }
            requests.remove(request.requester());
        }
    }
    private void processOutflow(CitizenGauge outflow, long now) {
        var network = data.networks().request(outflow.id());
        if (network == null) return;
        if (!network.saturated()) {
            if (!network.hasSurplus()) return;
            for (int index = 0, size = Math.min(4, network.shortageCount()); index < size; index++) {
                var district = data.gauge(network.nextShortage());
                if (district == null || !loaded(district) || busy(district.id()) || effective(district) >= district.target()) continue;
                data.put(district.resolve(true));
                requests.computeIfAbsent(district.id(), key -> new PopulationRequest(district.id(), now));
            }
            return;
        }
        if (removalsThisTick >= 1 || !network.hasSurplus()) return;
        for (var chain : chains.values()) for (var id : chain.path()) if (network.contains(id)) return;
        for (var citizen : loadedCitizens.values()) {
            var district = data.district(citizen.citizenState().population().districtId());
            if (district == null || !network.contains(district.id()) || district.cooldownUntil() > now || effective(district) <= district.target()
                    || !loaded(district) || !eligible(citizen, district.districtId()) || !data.owns(district, citizen.position())) continue;
            cooldown(outflow.id(), now); cooldown(district.id(), now);
            citizen.discard();
            leave(citizen, true);
            removalsThisTick++;
            return;
        }
    }
    private boolean busy(UUID gauge) { return chainIndex.busy(gauge); }
    private boolean valid(PopulationTransferChain chain) {
        var path = chain.path();
        for (int index = 0; index < path.size(); index++) {
            if (data.gauge(path.get(index)) == null || index + 1 < path.size() && !data.linked(path.get(index)).contains(path.get(index + 1))) return false;
        }
        return true;
    }
    private void reserve(PopulationTransferChain chain, Citizen citizen, long now) {
        reservations.put(citizen.getUUID(), chain.id()); chain.reserve(citizen.getUUID(), now);
        data.reserve(citizen.getUUID(), chain.id());
        CitizenActivityScheduler.get(level).forceActive(citizen, CitizenActivityScheduler.Reason.GAUGE_TRANSFER, 40);
    }
    private void advance(PopulationTransferChain chain, long now) {
        if (chain.complete()) { chains.remove(chain.id()); chainIndex.remove(chain.id()); return; }
        var from = data.gauge(chain.from()); var to = data.gauge(chain.to());
        if (!loaded(from) || !loaded(to)) return;
        if (chain.state() == PopulationTransferChain.State.MOVING) {
            var citizen = chain.citizen() == null ? null : loadedCitizens.get(chain.citizen());
            if (citizen == null || citizen.intents().active() == null || citizen.intents().active().source() != CitizenIntent.Source.GAUGE_TRANSFER) cancel(chain);
            return;
        }
        if (from.cooldownUntil() > now || to.cooldownUntil() > now) return;
        if (from.influx()) {
            if (!routeCapacity.ready() || loadedCount() >= ModConfigs.COMMON.maxLoadedCitizensPerDimension.get()
                    || spawnsThisTick >= ModConfigs.COMMON.populationSpawnBudget.get()) return;
            var citizen = ModEntities.CITIZEN.get().create(level);
            if (citizen == null) return;
            var point = DistrictDestinations.chooseSpawn(level, citizen, to);
            if (point == null) return;
            CitizenSpawnInitialization.initialize(citizen, CitizenSpawnInitialization.selectAge(level.random), level.random);
            citizen.citizenState().setPopulation(new CitizenPopulationState(to.districtId(), false, false, now + citizenCooldown()));
            citizen.setBehavior(routeCapacity.hasSpace(to.districtId(), data.walkingPopulation(to.districtId())) ? CitizenBehaviorState.walking()
                    : CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, BlockPos.containing(point), level.dimension(), CitizenBehaviorState.DEFAULT_RADIUS));
            citizen.moveTo(point.x, point.y, point.z, level.random.nextFloat() * 360, 0);
            if (!level.addFreshEntity(citizen)) return;
            join(citizen); spawnsThisTick++;
            cooldown(from.id(), now); cooldown(to.id(), now); chain.advance(now);
            chainIndex.put(chain);
            if (chain.complete()) chains.remove(chain.id());
            return;
        }
        var citizen = chain.citizen() == null ? donor(from) : loadedCitizens.get(chain.citizen());
        if (citizen == null) return;
        if (chain.citizen() == null) reserve(chain, citizen, now);
        CitizenActivityScheduler.get(level).forceActive(citizen, CitizenActivityScheduler.Reason.GAUGE_TRANSFER, 40);
        if (citizen.intents().request(CitizenIntent.district(CitizenIntent.Source.GAUGE_TRANSFER, to.districtId(), now, ModConfigs.COMMON.populationIntentTimeout.get()))) {
            chain.started(now); cooldown(from.id(), now); cooldown(to.id(), now);
        }
    }
    public void arrived(Citizen citizen, @Nullable UUID district) {
        var id = reservations.get(citizen.getUUID()); var chain = id == null ? null : chains.get(id);
        if (chain == null || !valid(chain) || !Objects.equals(data.gauge(chain.to()).districtId(), district)) { interrupt(citizen.getUUID()); return; }
        var target = data.gauge(chain.to());
        if (!data.owns(target, citizen.position())) { cancel(chain); return; }
        long now = level.getGameTime();
        if (citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK
                && routeCapacity.ready() && !routeCapacity.hasSpace(district, data.walkingPopulation(district))) citizen.intents().resumeWandering();
        citizen.citizenState().setPopulation(citizen.citizenState().population().district(district, now + citizenCooldown()));
        var behavior = citizen.citizenState().behavior();
        if (behavior.mode() == CitizenBehaviorMode.WANDER || behavior.mode() == CitizenBehaviorMode.STAND)
            citizen.citizenState().setBehavior(CitizenBehaviorState.anchored(behavior.mode(), citizen.blockPosition(), level.dimension(), behavior.wanderRadius()).withLookMode(behavior.lookMode()));
        update(citizen); reservations.remove(citizen.getUUID()); data.release(citizen.getUUID());
        cooldown(chain.to(), now); chain.advance(now);
        chainIndex.put(chain);
        if (chain.complete()) chains.remove(chain.id());
    }
    public boolean tryWalking(Citizen citizen) {
        var state = citizen.citizenState().population();
        if (!canAdjustRouteMode(citizen)
                || citizen.citizenState().behavior().mode() != CitizenBehaviorMode.WANDER
                || !routeCapacity.hasSpace(state.districtId(), data.walkingPopulation(state.districtId()))) return false;
        var routes = CitizenRouteManager.get(level);
        boolean nearby = routes.nearby(citizen.position(), RouteSettings.DISCOVERY_RADIUS).stream()
                .filter(marker -> state.districtId().equals(data.territory().districtAt(marker.pos())))
                .anyMatch(marker -> routes.data().connected(marker.id()).stream().anyMatch(edge -> routes.loaded(routes.data().marker(edge.other(marker.id())))));
        if (!nearby) return false;
        citizen.citizenState().setBehavior(CitizenBehaviorState.walking().withRadius(citizen.citizenState().behavior().wanderRadius()));
        return true;
    }
    public boolean tryWandering(Citizen citizen) {
        var state = citizen.citizenState().population();
        if (!canAdjustRouteMode(citizen) || citizen.citizenState().behavior().mode() != CitizenBehaviorMode.WALK
                || citizen.routeMovement().waitingForCrossing()
                || !routeCapacity.overCapacity(state.districtId(), data.walkingPopulation(state.districtId()))) return false;
        var routes = CitizenRouteManager.get(level);
        boolean nearby = routes.nearby(citizen.position(), RouteSettings.DISCOVERY_RADIUS).stream()
                .anyMatch(marker -> routes.data().connected(marker.id()).stream().anyMatch(edge -> routes.loaded(routes.data().marker(edge.other(marker.id())))));
        if (!nearby) return false;
        citizen.intents().resumeWandering(); return true;
    }
    private boolean canAdjustRouteMode(Citizen citizen) {
        var state = citizen.citizenState().population();
        return state.districtId() != null && !state.independent() && !state.force() && !citizen.isNoAi() && !citizen.isPassenger() && !citizen.isLeashed()
                && citizen.intents().active() == null && !reserved(citizen.getUUID()) && !citizen.routeMovement().hasDestination();
    }
    private int citizenCooldown() { return jitter(Math.max(ModConfigs.COMMON.populationCitizenCooldown.get(), ModConfigs.COMMON.populationGaugeCooldown.get() * 2)); }
    private int jitter(int base) { return base * 3 / 4 + level.random.nextInt(Math.max(1, base / 2 + 1)); }
    private void cooldown(UUID id, long now) { var gauge = data.gauge(id); if (gauge != null) data.put(gauge.cooldown(now + jitter(ModConfigs.COMMON.populationGaugeCooldown.get()))); }
    private void cancel(PopulationTransferChain chain) {
        chains.remove(chain.id());
        chainIndex.remove(chain.id());
        if (chain.citizen() != null) {
            reservations.remove(chain.citizen());
            data.release(chain.citizen());
            var citizen = loadedCitizens.get(chain.citizen());
            if (citizen != null && citizen.intents().active() != null && citizen.intents().active().source() == CitizenIntent.Source.GAUGE_TRANSFER) citizen.intents().cancel();
        }
        var requester = chain.path().get(chain.path().size() - 1);
        var gauge = data.gauge(requester);
        if (gauge != null && gauge.target() > effective(gauge)) {
            var request = new PopulationRequest(requester, chain.createdAt());
            request.attempted(level.getGameTime(), ModConfigs.COMMON.populationRetryTicks.get()); requests.putIfAbsent(requester, request);
        }
    }
}
