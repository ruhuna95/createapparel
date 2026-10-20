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
    private final Map<UUID, Set<UUID>> loadedCitizensByDistrict = new HashMap<>();
    private final Map<UUID, UUID> loadedDistricts = new HashMap<>();
    private final Map<UUID, PopulationRequest> requests = new LinkedHashMap<>();
    private final PopulationRequestQueue requestQueue = new PopulationRequestQueue();
    private final Map<UUID, PopulationTransferChain> chains = new LinkedHashMap<>();
    private final PopulationChainIndex chainIndex = new PopulationChainIndex();
    private final ArrayDeque<UUID> chainQueue = new ArrayDeque<>();
    private final Map<UUID, Set<UUID>> intentCitizensByDistrict = new HashMap<>();
    private final Map<UUID, Long> citizenCells = new HashMap<>();
    private final Map<Long, Integer> densities = new HashMap<>();
    private final ArrayDeque<UUID> gauges = new ArrayDeque<>();
    private final Set<UUID> queuedGauges = new HashSet<>();
    private final Map<UUID, Long> nextEvaluation = new HashMap<>();
    private final LinkedHashMap<Long, UUID> pendingChunks = new LinkedHashMap<>();
    private int spawnsThisTick;
    private int removalsThisTick;
    private long lastTick = Long.MIN_VALUE;
    private final CitizenSystemMetrics metrics = new CitizenSystemMetrics();
    public enum BlockedReason { NONE, NO_LINK, NO_DONOR, SOURCE_UNAVAILABLE, DESTINATION_UNLOADED, POPULATION_LIMIT, TRANSFER_IN_PROGRESS, COOLDOWN, NO_VALID_SPAWN_POSITION, ROUTE_UNAVAILABLE }
    private final Map<UUID, BlockedReason> blocked = new HashMap<>();
    private final Map<UUID, Long> lastSuccessfulAction = new HashMap<>();
    public CitizenSystemMetrics metrics() { return metrics; }
    public static void beginServerTick() { LEVELS.values().forEach(manager -> manager.metrics.beginTick()); }
    public static void endServerTick() { LEVELS.values().forEach(manager -> manager.metrics.endTick()); }

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
    public static void unload(ServerLevel level) { LEVELS.remove(level); GaugeViewSynchronization.clear(level); }
    public static void clear() { LEVELS.clear(); }
    public CitizenPopulationSavedData data() { return data; }
    public DistrictRouteCapacity routeCapacity() { return routeCapacity; }
    public void modeChanged(Citizen citizen) { data.walking(citizen.getUUID(), citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK); }
    public void queueMarkerChunk(long chunk) { markerDistricts.queue(chunk); }
    public RouteMarker refreshMarkerDistrict(RouteMarker marker) { return markerDistricts.refresh(marker); }
    public int loadedCount() { return loadedCitizens.size(); }
    public int chainCount() { return chains.size(); }
    public int requestCount() { return requests.size(); }
    public net.minecraft.nbt.CompoundTag diagnostics(CitizenGauge gauge) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("Effective", effective(gauge));
        tag.putString("PopulationStatus", gauge.source() ? "FLOW" : effective(gauge) < gauge.target() ? "SHORTAGE" : effective(gauge) > gauge.target() ? "SURPLUS" : "BALANCED");
        var request = requests.get(gauge.id());
        tag.putString("RequestStatus", request == null ? "NONE" : request.retryAt() > level.getGameTime() ? "RETRY" : "WAITING");
        tag.putLong("RequestCreatedAt", request == null ? -1 : request.createdAt());
        tag.putInt("ActiveChains", chainIndex.chains(gauge.id()).size());
        tag.putLong("LastSuccessfulAction", lastSuccessfulAction.getOrDefault(gauge.id(), -1L));
        var reason = !loaded(gauge) ? BlockedReason.DESTINATION_UNLOADED : busy(gauge.id()) ? BlockedReason.TRANSFER_IN_PROGRESS
                : gauge.cooldownUntil() > level.getGameTime() ? BlockedReason.COOLDOWN : data.linked(gauge.id()).isEmpty() ? BlockedReason.NO_LINK
                : blocked.getOrDefault(gauge.id(), BlockedReason.NONE);
        tag.putString("BlockedReason", reason.name()); return tag;
    }
    public boolean reserved(UUID citizen) { return data.reservation(citizen) != null; }
    public void intentChanged(Citizen citizen, @Nullable CitizenIntent previous, @Nullable CitizenIntent next) {
        if (previous != null && previous.district() != null) {
            var members = intentCitizensByDistrict.get(previous.district());
            if (members != null) { members.remove(citizen.getUUID()); if (members.isEmpty()) intentCitizensByDistrict.remove(previous.district()); }
        }
        if (next != null && next.district() != null) intentCitizensByDistrict.computeIfAbsent(next.district(), ignored -> new HashSet<>()).add(citizen.getUUID());
    }
    public int incoming(UUID gauge) { return chainIndex.incoming(gauge); }
    public int outgoing(UUID gauge) { return chainIndex.outgoing(gauge); }
    public int effective(CitizenGauge gauge) { return gauge.source() ? 0 : data.population(gauge.districtId()) + incoming(gauge.id()) - outgoing(gauge.id()); }
    public boolean loaded(@Nullable CitizenGauge gauge) {
        return gauge != null && !data.moving(gauge.id()) && CitizenNavigation.hasLoadedRegion(level, gauge.pos(), 0)
                && level.getBlockEntity(gauge.pos()) instanceof CitizenGaugeBlockEntity entity && entity.registrationIssue(gauge.slot()) == null && gauge.id().equals(entity.gaugeId(gauge.slot()));
    }
    @Override public boolean enabled(UUID district) { return loaded(data.district(district)); }
    @Override public List<UUID> linkedDistricts(UUID district) {
        return data.linkedDistricts(district);
    }
    @Nullable public CitizenGauge physicalDistrict(Vec3 point) {
        return data.physicalDistrict(point);
    }
    @Nullable public CitizenGauge register(CitizenGaugeBlockEntity entity) {
        return register(entity, 0);
    }
    @Nullable public CitizenGauge register(CitizenGaugeBlockEntity entity, int slot) {
        var gauge = data.at(entity.getBlockPos(), slot);
        var entityId = entity.gaugeId(slot);
        boolean recovering = gauge == null && entityId != null;
        var identified = entityId == null ? null : data.gauge(entityId);
        boolean moved = false;
        if (identified != null && (gauge == null || !identified.id().equals(gauge.id()))) {
            if (!data.moving(identified.id()) || gauge != null) { entity.registrationIssue(slot, "DUPLICATE_GAUGE_ID"); return null; }
            var owner = data.movingOwner(identified.id());
            if (owner != null && (!(level.getEntity(owner) instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity carrier)
                    || carrier.getContraption() == null || !carrier.getContraption().disassembled)) {
                entity.registrationIssue(slot, "MOVING_OWNER_NOT_REASSEMBLING"); return null;
            }
            gauge = identified.relocate(entity.getBlockPos(), slot); data.put(gauge);
            moved = true;
        }
        if (gauge == null) {
            if (entity.initialCenter(slot) != null && data.centerUsed(entity.initialCenter(slot))) { entity.registrationIssue(slot, "DUPLICATE_CENTER"); return null; }
            var backup = entity.definition(slot);
            gauge = backup != null ? backup.relocate(entity.getBlockPos(), slot)
                    : entityId == null ? CitizenGauge.create(entity.getBlockPos(), entity.initialCenter(slot), slot)
                    : new CitizenGauge(entityId, entity.getBlockPos(), entity.initialCenter(slot) == null ? null
                        : UUID.nameUUIDFromBytes(("createapparel:district:" + entityId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        entity.initialCenter(slot), CitizenGauge.DEFAULT_RANGE, CitizenGauge.DEFAULT_TARGET, 0, false, slot);
            if (!gauge.source() && (data.centerUsed(gauge.center()) || data.district(gauge.districtId()) != null)) {
                entity.registrationIssue(slot, "DUPLICATE_DISTRICT_DEFINITION"); return null;
            }
            if (gauge.source() && entity.outflow(slot)) gauge = gauge.withFlow(CitizenGauge.Flow.OUTFLOW);
            data.put(gauge);
        }
        data.moving(gauge.id(), false); entity.registered(gauge); queue(gauge.id());
        if (moved || recovering) { data.linked(gauge.id()).forEach(this::syncGauge); CitizenRouteManager.get(level).loadChanged(); }
        CitizenRouteManager.get(level).setGatewayAccess(this);
        return gauge;
    }
    private void queue(UUID id) { if (queuedGauges.add(id)) gauges.addLast(id); }
    public void reconcile(LevelChunk chunk) {
        for (var gauge : data.inChunk(chunk.getPos().toLong()))
            if (!data.moving(gauge.id()) && (!(chunk.getBlockEntity(gauge.pos()) instanceof CitizenGaugeBlockEntity entity) || !entity.hasPanel(gauge.slot()))) remove(gauge.id());
        for (var entity : List.copyOf(chunk.getBlockEntities().values())) if (entity instanceof CitizenGaugeBlockEntity gauge)
            for (int slot = 0; slot < 4; slot++) if (gauge.hasPanel(slot)) register(gauge, slot);
    }
    public void queueReconciliation(long chunk) { if (!pendingChunks.containsKey(chunk)) pendingChunks.put(chunk, null); }
    private void reconcilePending() {
        int budget = 16;
        for (int chunks = 0; chunks < 4 && budget > 0 && !pendingChunks.isEmpty(); chunks++) {
            long chunk = pendingChunks.keySet().iterator().next();
            int x = net.minecraft.world.level.ChunkPos.getX(chunk), z = net.minecraft.world.level.ChunkPos.getZ(chunk);
            if (!level.getChunkSource().hasChunk(x, z)) { pendingChunks.remove(chunk); continue; }
            var entries = data.inChunkAfter(chunk, pendingChunks.get(chunk), budget);
            for (var gauge : entries) {
                if (data.moving(gauge.id())) continue;
                if (level.getBlockEntity(gauge.pos()) instanceof CitizenGaugeBlockEntity entity && entity.hasPanel(gauge.slot())) register(entity, gauge.slot());
                else remove(gauge.id());
            }
            int previousBudget = budget; budget -= entries.size();
            if (entries.size() < previousBudget) pendingChunks.remove(chunk);
            else pendingChunks.put(chunk, entries.get(entries.size() - 1).id());
        }
    }
    public void removeAt(BlockPos pos) { for (var gauge : data.atAll(pos)) remove(gauge.id()); }
    public void suspendAt(BlockPos pos) {
        for (var gauge : data.atAll(pos)) {
            data.moving(gauge.id(), true);
            for (var chainId : chainIndex.chains(gauge.id())) { var chain = chains.get(chainId); if (chain != null) cancel(chain); }
        }
    }
    public void contraptionJoined(com.simibubi.create.content.contraptions.AbstractContraptionEntity entity) {
        var existing = level.getEntity(entity.getUUID());
        if (existing != null && existing != entity) {
            com.mojang.logging.LogUtils.getLogger().warn("Ignoring duplicate Citizen Gauge contraption entity UUID {}", entity.getUUID()); return;
        }
        if (existing == null && data.hasMovingOwner(entity.getUUID())) {
            level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount(), () -> {
                if (level.getEntity(entity.getUUID()) == entity) contraptionJoined(entity);
            })); return;
        }
        for (var id : contraptionGauges(entity)) if (data.moving(id) && !data.bindMoving(id, entity.getUUID()))
            com.mojang.logging.LogUtils.getLogger().warn("Ignoring duplicate moving Citizen Gauge {} in contraption {}", id, entity.getUUID());
        data.movingLocation(entity.getUUID(), entity.chunkPosition().toLong());
    }
    public void contraptionRemoved(com.simibubi.create.content.contraptions.AbstractContraptionEntity entity) {
        var reason = entity.getRemovalReason(); if (reason == null || !reason.shouldDestroy()) return;
        for (var id : data.ownedMovingGauges(entity.getUUID())) remove(id);
    }
    private boolean reconcileMoving(UUID id) {
        var owner = data.movingOwner(id); var chunk = data.movingChunk(id);
        if (owner == null || chunk == null || level.getEntity(owner) != null || !level.areEntitiesLoaded(chunk)) return false;
        var position = new BlockPos(net.minecraft.world.level.ChunkPos.getX(chunk) << 4, level.getMinBuildHeight(), net.minecraft.world.level.ChunkPos.getZ(chunk) << 4);
        if (!level.isPositionEntityTicking(position)) return false;
        com.mojang.logging.LogUtils.getLogger().warn("Removing orphaned moving Citizen Gauge {}: owner {} missing from loaded entity chunk {}", id, owner, new net.minecraft.world.level.ChunkPos(chunk));
        remove(id); return true;
    }
    public boolean releaseMoving(UUID id) {
        if (!data.moving(id)) return false;
        var owner = data.movingOwner(id); if (owner != null && level.getEntity(owner) != null) return false;
        remove(id); return true;
    }
    private static Set<UUID> contraptionGauges(com.simibubi.create.content.contraptions.AbstractContraptionEntity entity) {
        var contraption = entity.getContraption(); if (contraption == null) return Set.of();
        var ids = new HashSet<UUID>();
        for (var block : contraption.getBlocks().values()) {
            if (!(block.state().getBlock() instanceof com.rexhub.createapparel.block.CitizenGaugeBlock) || block.nbt() == null) continue;
            var tag = block.nbt(); if (tag.hasUUID("GaugeId")) ids.add(tag.getUUID("GaugeId"));
            for (var raw : tag.getList("Panels", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                var panel = (net.minecraft.nbt.CompoundTag) raw; if (panel.hasUUID("GaugeId")) ids.add(panel.getUUID("GaugeId"));
            }
        }
        return ids;
    }
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
        for (var chainId : chainIndex.chains(id)) { var chain = chains.get(chainId); if (chain != null) cancel(chain); }
        removeRequest(id); data.remove(id); nextEvaluation.remove(id);
        blocked.remove(id); lastSuccessfulAction.remove(id);
        for (var citizenId : Set.copyOf(intentCitizensByDistrict.getOrDefault(gauge.districtId(), Set.of()))) {
            var citizen = loadedCitizens.get(citizenId); if (citizen != null) citizen.intents().cancel();
        }
        neighbors.forEach(this::syncGauge);
        for (var citizenId : Set.copyOf(loadedCitizensByDistrict.getOrDefault(gauge.districtId(), Set.of()))) {
            var citizen = loadedCitizens.get(citizenId);
            if (citizen == null) continue;
            if (Objects.equals(citizen.citizenState().population().districtId(), gauge.districtId()) && !gauge.source()) {
                citizen.citizenState().setPopulation(data.citizen(citizen.getUUID()));
                var intent = citizen.intents().active();
                if (intent != null && (intent.source() == CitizenIntent.Source.IMPULSE_VISIT || intent.source() == CitizenIntent.Source.RETURN_HOME)) citizen.intents().cancel();
                indexLoaded(citizen);
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
        for (var chainId : chainIndex.chains(a)) { var chain = chains.get(chainId); if (chain != null && !valid(chain)) cancel(chain); }
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
        for (var chainId : chainIndex.chains(id)) { var chain = chains.get(chainId); if (chain != null) cancel(chain); }
        data.put(gauge.withFlow(flow));
        removeRequest(id); nextEvaluation.remove(id);
        ((CitizenGaugeBlockEntity) level.getBlockEntity(gauge.pos())).refreshFlow(gauge.slot(), flow == CitizenGauge.Flow.OUTFLOW);
        syncGauge(id);
    }
    public void settings(UUID id, int radius, int target, String name) {
        var gauge = data.gauge(id);
        if (gauge == null || gauge.source()) return;
        data.put(gauge.settings(radius, target, name)); nextEvaluation.remove(id); syncGauge(id);
        refreshRequest(id);
        for (var chainId : chainIndex.chains(id)) {
            var chain = chains.get(chainId);
            if (chain != null && chain.path().get(chain.path().size() - 1).equals(id) && data.population(gauge.districtId()) >= target) cancel(chain);
        }
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
        var previousPopulation = data.citizen(citizen.getUUID());
        var state = citizen.citizenState().population();
        if (!state.independent() && state.districtId() == null) {
            var district = physicalDistrict(citizen.position());
            if (district != null) state = state.district(district.districtId(), state.transferCooldownUntil());
        }
        citizen.citizenState().setPopulation(state); data.register(citizen.getUUID(), state, citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK);
        indexLoaded(citizen);
        if (previousPopulation == null || !Objects.equals(previousPopulation.districtId(), state.districtId())) {
            var districtGauge = data.district(state.districtId()); if (districtGauge != null) refreshRequest(districtGauge.id());
            var previousGauge = previousPopulation == null ? null : data.district(previousPopulation.districtId());
            if (previousGauge != null) refreshRequest(previousGauge.id());
        }
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
        intentChanged(citizen, citizen.intents().active(), null);
        if (permanent) data.removeCitizen(citizen.getUUID());
        else if (citizen.isAlive()) data.register(citizen.getUUID(), citizen.citizenState().population(), citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK);
        loadedCitizens.remove(citizen.getUUID());
        unindexLoaded(citizen.getUUID());
        var district = data.district(citizen.citizenState().population().districtId()); if (district != null) refreshRequest(district.id());
        var previous = citizenCells.remove(citizen.getUUID()); if (previous != null) decrement(previous);
    }
    private void decrement(long cell) { densities.computeIfPresent(cell, (key, count) -> count <= 1 ? null : count - 1); }
    private void unindexLoaded(UUID citizen) {
        var district = loadedDistricts.remove(citizen);
        if (district == null) return;
        var members = loadedCitizensByDistrict.get(district);
        if (members != null) { members.remove(citizen); if (members.isEmpty()) loadedCitizensByDistrict.remove(district); }
    }
    private void indexLoaded(Citizen citizen) {
        var district = citizen.citizenState().population().districtId();
        if (Objects.equals(district, loadedDistricts.get(citizen.getUUID()))) return;
        unindexLoaded(citizen.getUUID());
        if (district != null) {
            loadedDistricts.put(citizen.getUUID(), district);
            loadedCitizensByDistrict.computeIfAbsent(district, ignored -> new LinkedHashSet<>()).add(citizen.getUUID());
        }
    }
    private void removeRequest(UUID gauge) { requests.remove(gauge); requestQueue.remove(gauge); }
    private void refreshRequest(UUID id) {
        var request = requests.get(id); var gauge = data.gauge(id);
        if (request != null && gauge != null) requestQueue.put(request, gauge.ratio(effective(gauge)));
    }
    private void ensureRequest(UUID id, long now) {
        if (!requests.containsKey(id)) { requests.put(id, new PopulationRequest(id, now)); refreshRequest(id); }
    }
    private void retry(PopulationRequest request, long now) {
        request.defer(now, Math.max(request.retryAt(), now + 20)); refreshRequest(request.requester());
    }
    private static long cell(Vec3 point) { return net.minecraft.world.level.ChunkPos.asLong(net.minecraft.util.Mth.floor(point.x) >> 4, net.minecraft.util.Mth.floor(point.z) >> 4); }
    public int density(Vec3 point) { return densities.getOrDefault(cell(point), 0); }
    public void interrupt(UUID citizen) {
        var id = data.reservation(citizen); var chain = id == null ? null : chains.get(id);
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
        for (var id : loadedCitizensByDistrict.getOrDefault(gauge.districtId(), Set.of())) {
            var citizen = loadedCitizens.get(id);
            if (citizen != null && eligible(citizen, gauge.districtId())) return citizen;
        }
        return null;
    }
    public void tick() {
        long started = metrics.enabled() ? System.nanoTime() : 0;
        try { tickPopulation(); } finally { if (started != 0) metrics.population(System.nanoTime() - started); }
    }
    private void tickPopulation() {
        long now = level.getGameTime();
        if (now == lastTick) return;
        lastTick = now; spawnsThisTick = 0; removalsThisTick = 0;
        reconcilePending();
        markerDistricts.tick();
        routeCapacity.tick();
        data.networks().tick(64);
        requestQueue.tick(now);
        for (int index = 0, size = Math.min(4, gauges.size()); index < size; index++) {
            var id = gauges.removeFirst(); var gauge = data.gauge(id);
            if (gauge == null) { queuedGauges.remove(id); continue; }
            if (data.moving(id) && reconcileMoving(id)) { queuedGauges.remove(id); continue; }
            boolean gaugeLoaded = loaded(gauge);
            if (!data.moving(id) && CitizenNavigation.hasLoadedRegion(level, gauge.pos(), 0) && !gaugeLoaded) {
                if (level.getBlockEntity(gauge.pos()) instanceof CitizenGaugeBlockEntity entity && entity.hasPanel(gauge.slot())) { register(entity, gauge.slot()); gauges.addLast(id); }
                else { remove(id); queuedGauges.remove(id); }
                continue;
            }
            gauges.addLast(id);
            if (gaugeLoaded && !gauge.source() && level.getBlockEntity(gauge.pos()) instanceof CitizenGaugeBlockEntity entity)
                entity.refreshDistrictStatus(gauge.slot(), gauge.districtId(), gauge.name(), data.population(gauge.districtId()), gauge.target());
            if (!gaugeLoaded || gauge.influx() || now < nextEvaluation.getOrDefault(id, 0L)) continue;
            nextEvaluation.put(id, now + 100 + Math.floorMod(id.hashCode(), 40));
            if (gauge.outflow()) {
                data.networks().request(id);
                ensureRequest(id, now);
                continue;
            }
            int population = effective(gauge);
            boolean resolving = gauge.target() > 0 && (gauge.resolving() ? data.population(gauge.districtId()) < gauge.target() : population < gauge.target() - gauge.buffer());
            data.put(gauge.resolve(resolving));
            if (resolving && population < gauge.target()) ensureRequest(id, now);
            else removeRequest(id);
        }
        for (int index = 0, size = Math.min(16, chainQueue.size()); index < size; index++) {
            var id = chainQueue.removeFirst(); var chain = chains.get(id);
            if (chain == null) continue;
            chainQueue.addLast(id);
            if (!valid(chain) || now - chain.lastProgressAt() > ModConfigs.COMMON.populationHopTimeout.get()) { cancel(chain); continue; }
            advance(chain, now);
        }
        if (now % 20 != 0) return;
        int budget = 4;
        for (int checked = 0; checked < 64 && budget > 0; checked++) {
            var request = requestQueue.poll(); if (request == null) break;
            if (requests.get(request.requester()) != request) continue;
            var requester = data.gauge(request.requester());
            if (requester != null && requester.outflow()) {
                if (!loaded(requester) || request.retryAt() > now || requester.cooldownUntil() > now) { retry(request, now); continue; }
                if (data.networks().request(requester.id()) == null) { retry(request, now); continue; }
                budget--;
                request.attempted(now, ModConfigs.COMMON.populationRetryTicks.get());
                processOutflow(requester, now);
                retry(request, now);
                continue;
            }
            if (requester == null || requester.source() || !requester.resolving() || requester.target() <= effective(requester)) { removeRequest(request.requester()); continue; }
            if (!loaded(requester) || request.retryAt() > now || busy(requester.id()) || requester.cooldownUntil() > now) { retry(request, now); continue; }
            budget--;
            request.attempted(now, ModConfigs.COMMON.populationRetryTicks.get());
            var network = data.networks().cached(requester.id());
            var path = request.findSupply(data, ModConfigs.COMMON.populationMaxHops.get(), ModConfigs.COMMON.populationQueryBudget.get(),
                    gauge -> !busy(gauge.id()) && loaded(gauge) && PopulationRequest.improves(gauge, effective(gauge), requester, effective(requester))
                            && (gauge.influx() || !gauge.source() && donor(gauge) != null),
                    network != null && network.hasOutflow() && network.hasSurplus());
            if (path.isEmpty() || path.stream().anyMatch(this::busy) || chains.size() >= ModConfigs.COMMON.populationMaxChains.get()) {
                blocked.put(requester.id(), path.isEmpty() ? BlockedReason.NO_DONOR : BlockedReason.TRANSFER_IN_PROGRESS); retry(request, now); continue;
            }
            blocked.remove(requester.id());
            var chain = new PopulationTransferChain(request.id(), path, now);
            chains.put(chain.id(), chain);
            updateChain(chain);
            chainQueue.addLast(chain.id());
            var first = data.gauge(chain.from());
            if (!first.source()) {
                var citizen = donor(first);
                if (citizen == null) { cancel(chain); continue; }
                reserve(chain, citizen, now);
            }
            removeRequest(request.requester());
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
                ensureRequest(district.id(), now);
            }
            return;
        }
        if (removalsThisTick >= 1 || !network.hasSurplus()) return;
        for (var chain : chains.values()) if (network.contains(chain.from())) return;
        for (int index = 0, size = Math.min(4, network.surplusCount()); index < size; index++) {
            var district = data.gauge(network.nextSurplus());
            if (district == null || district.cooldownUntil() > now || effective(district) <= district.target() || !loaded(district)) continue;
            for (var citizenId : loadedCitizensByDistrict.getOrDefault(district.districtId(), Set.of())) {
                var citizen = loadedCitizens.get(citizenId);
                if (citizen == null || !eligible(citizen, district.districtId()) || !data.owns(district, citizen.position())) continue;
                cooldown(outflow.id(), now); cooldown(district.id(), now);
                citizen.discard(); leave(citizen, true); succeeded(outflow.id(), now); succeeded(district.id(), now); removalsThisTick++; return;
            }
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
        data.reserve(citizen.getUUID(), chain.id());
        chain.reserve(citizen.getUUID(), now);
        CitizenActivityScheduler.get(level).forceActive(citizen, CitizenActivityScheduler.Reason.GAUGE_TRANSFER, 40);
    }
    private void advance(PopulationTransferChain chain, long now) {
        if (chain.complete()) { chains.remove(chain.id()); chainIndex.remove(chain.id()); return; }
        var from = data.gauge(chain.from()); var to = data.gauge(chain.to());
        if (!loaded(from) || !loaded(to)) { blocked.put(chain.to(), !loaded(to) ? BlockedReason.DESTINATION_UNLOADED : BlockedReason.SOURCE_UNAVAILABLE); return; }
        if (chain.hop() == chain.path().size() - 2 && effective(to) > to.target()) { cancel(chain); return; }
        if (chain.state() == PopulationTransferChain.State.MOVING) {
            var citizen = chain.citizen() == null ? null : loadedCitizens.get(chain.citizen());
            if (citizen == null || citizen.intents().active() == null || citizen.intents().active().source() != CitizenIntent.Source.GAUGE_TRANSFER) cancel(chain);
            return;
        }
        var recipient = data.gauge(chain.path().get(chain.path().size() - 1));
        if (recipient == null || !PopulationRequest.improves(from, effective(from) + chain.outgoing(from.id()), recipient,
                effective(recipient) - chain.incoming(recipient.id()))) { cancel(chain); return; }
        if (from.cooldownUntil() > now || to.cooldownUntil() > now) return;
        if (from.influx()) {
            if (!routeCapacity.ready()) { blocked.put(to.id(), BlockedReason.ROUTE_UNAVAILABLE); return; }
            if (loadedCount() >= ModConfigs.COMMON.maxLoadedCitizensPerDimension.get()
                    || spawnsThisTick >= ModConfigs.COMMON.populationSpawnBudget.get()) { blocked.put(to.id(), BlockedReason.POPULATION_LIMIT); return; }
            var citizen = ModEntities.CITIZEN.get().create(level);
            if (citizen == null) return;
            var point = DistrictDestinations.chooseSpawn(level, citizen, to);
            if (point == null) { blocked.put(to.id(), BlockedReason.NO_VALID_SPAWN_POSITION); return; }
            CitizenSpawnInitialization.initialize(citizen, CitizenSpawnInitialization.selectAge(level.random), level.random);
            citizen.citizenState().setPopulation(new CitizenPopulationState(to.districtId(), false, false, now + citizenCooldown()));
            citizen.setBehavior(routeCapacity.hasSpace(to.districtId(), data.walkingPopulation(to.districtId())) ? CitizenBehaviorState.walking()
                    : CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, BlockPos.containing(point), level.dimension(), CitizenBehaviorState.DEFAULT_RADIUS));
            citizen.moveTo(point.x, point.y, point.z, level.random.nextFloat() * 360, 0);
            if (!level.addFreshEntity(citizen)) return;
            join(citizen); spawnsThisTick++;
            succeeded(from.id(), now); succeeded(to.id(), now);
            cooldown(from.id(), now); cooldown(to.id(), now); chain.advance(now);
            updateChain(chain);
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
        var id = data.reservation(citizen.getUUID()); var chain = id == null ? null : chains.get(id);
        if (chain == null || !valid(chain) || !Objects.equals(data.gauge(chain.to()).districtId(), district)) { interrupt(citizen.getUUID()); return; }
        var target = data.gauge(chain.to());
        if (!data.owns(target, citizen.position()) || chain.hop() == chain.path().size() - 2 && effective(target) > target.target()) { cancel(chain); return; }
        long now = level.getGameTime();
        if (citizen.citizenState().behavior().mode() == CitizenBehaviorMode.WALK
                && routeCapacity.ready() && !routeCapacity.hasSpace(district, data.walkingPopulation(district))) citizen.intents().resumeWandering();
        citizen.citizenState().setPopulation(citizen.citizenState().population().district(district, now + citizenCooldown()));
        var behavior = citizen.citizenState().behavior();
        if (behavior.mode() == CitizenBehaviorMode.WANDER || behavior.mode() == CitizenBehaviorMode.STAND)
            citizen.citizenState().setBehavior(CitizenBehaviorState.anchored(behavior.mode(), citizen.blockPosition(), level.dimension(), behavior.wanderRadius()).withLookMode(behavior.lookMode()));
        update(citizen); data.release(citizen.getUUID());
        succeeded(chain.from(), now); succeeded(chain.to(), now);
        cooldown(chain.to(), now); chain.advance(now);
        updateChain(chain);
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
        return data.district(state.districtId()) != null && !state.independent() && !state.force() && !citizen.isNoAi() && !citizen.isPassenger() && !citizen.isLeashed()
                && citizen.intents().active() == null && !reserved(citizen.getUUID()) && !citizen.routeMovement().hasDestination();
    }
    private int citizenCooldown() { return jitter(Math.max(ModConfigs.COMMON.populationCitizenCooldown.get(), ModConfigs.COMMON.populationGaugeCooldown.get() * 2)); }
    private void updateChain(PopulationTransferChain chain) {
        chainIndex.put(chain); data.populationChanged();
        chain.path().forEach(this::refreshRequest);
    }
    private int jitter(int base) { return base * 3 / 4 + level.random.nextInt(Math.max(1, base / 2 + 1)); }
    private void cooldown(UUID id, long now) { var gauge = data.gauge(id); if (gauge != null) data.put(gauge.cooldown(now + jitter(ModConfigs.COMMON.populationGaugeCooldown.get()))); }
    private void succeeded(UUID id, long now) { lastSuccessfulAction.put(id, now); blocked.remove(id); }
    private void cancel(PopulationTransferChain chain) {
        chains.remove(chain.id());
        chainIndex.remove(chain.id());
        data.populationChanged(); chain.path().forEach(this::refreshRequest);
        if (chain.citizen() != null) {
            data.release(chain.citizen());
            var citizen = loadedCitizens.get(chain.citizen());
            if (citizen != null && citizen.intents().active() != null && citizen.intents().active().source() == CitizenIntent.Source.GAUGE_TRANSFER) citizen.intents().cancel();
        }
        var requester = chain.path().get(chain.path().size() - 1);
        var gauge = data.gauge(requester);
        if (gauge != null && gauge.target() > effective(gauge)) {
            var request = new PopulationRequest(requester, chain.createdAt());
            request.attempted(level.getGameTime(), ModConfigs.COMMON.populationRetryTicks.get()); requests.putIfAbsent(requester, request); refreshRequest(requester);
        }
    }
}
