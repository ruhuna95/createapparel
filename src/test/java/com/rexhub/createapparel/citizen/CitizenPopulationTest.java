package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.population.*;
import com.rexhub.createapparel.entity.CitizenState;
import com.rexhub.createapparel.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CitizenPopulationTest {
    @BeforeAll static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }
    private static CitizenGauge district(int x, int target) { return CitizenGauge.create(new BlockPos(x, 70, 0), new BlockPos(x, 64, 0)).settings(10, target); }

    @Test void unplacedCenterReservationsPersistWithoutCreatingDistrictsOrInvalidatingCaches() {
        var data = new CitizenPopulationSavedData(); var center = new BlockPos(28000000, 64, 28000000);
        long version = data.version(); var owner = data.reserveCenter(center, null); assertNotNull(owner);
        assertFalse(data.centerUsed(center)); assertTrue(data.gauges().isEmpty());
        assertNull(data.physicalDistrict(Vec3.atCenterOf(center)));
        assertFalse(data.centerAvailable(center, null)); assertFalse(data.centerAvailable(center, UUID.randomUUID()));
        assertNull(data.reserveCenter(center, null)); assertNull(data.reserveCenter(center, UUID.randomUUID()));
        assertEquals(owner, data.reserveCenter(center, owner)); assertTrue(data.centerAvailable(center, owner));
        assertEquals(version, data.version());
        assertNotNull(new CitizenPopulationSavedData().reserveCenter(center, null));
        data = CitizenPopulationSavedData.load(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()));
        assertNull(data.reserveCenter(center, null)); assertTrue(data.centerAvailable(center, owner));
        var next = data.reserveCenter(center.east(), owner); assertNotNull(next); assertNotEquals(owner, next);
        data.releaseCenter(UUID.randomUUID()); assertFalse(data.centerAvailable(center, null));
        data.releaseCenter(owner); assertTrue(data.centerAvailable(center, null));
        assertFalse(data.centerAvailable(center.east(), null));
        data.releaseCenter(next); assertTrue(data.centerAvailable(center.east(), null));
    }

    @Test void wallRegistrationConsumesPendingCenterAndOldSavesAndInvalidClaimsRemainReadable() {
        var data = new CitizenPopulationSavedData(); var home = district(0, 0); var owner = data.reserveCenter(home.center(), null);
        data.put(home); assertTrue(data.centerUsed(home.center())); assertFalse(data.centerAvailable(home.center(), owner));
        assertTrue(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()).getList("CenterReservations", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty());
        data.remove(home.id()); assertTrue(data.centerAvailable(home.center(), null));
        var legacy = data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()); legacy.remove("CenterReservations"); legacy.putInt("SchemaVersion", 3);
        assertNotNull(CitizenPopulationSavedData.load(legacy).reserveCenter(home.center(), null));
        data.put(home); var saved = data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()); var claims = new net.minecraft.nbt.ListTag();
        var claim = new CompoundTag(); claim.putLong("Center", home.center().asLong()); claim.putUUID("Id", owner); claims.add(claim);
        claim = claim.copy(); claim.putLong("Center", home.center().east().asLong()); claims.add(claim);
        var duplicateId = claim.copy(); duplicateId.putLong("Center", home.center().west().asLong()); claims.add(duplicateId);
        var duplicateCenter = claim.copy(); duplicateCenter.putUUID("Id", UUID.randomUUID()); claims.add(duplicateCenter);
        claims.add(new CompoundTag()); saved.put("CenterReservations", claims);
        var restored = CitizenPopulationSavedData.load(saved);
        assertEquals(1, restored.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()).getList("CenterReservations", net.minecraft.nbt.Tag.TAG_COMPOUND).size());
        assertFalse(restored.centerAvailable(home.center(), owner)); assertTrue(restored.centerAvailable(home.center().east(), owner));
        assertTrue(restored.centerAvailable(home.center().west(), null));
        restored.releaseCenter(owner); assertTrue(restored.centerAvailable(home.center().east(), null));
    }

    @Test void partialLossAndRerecordingKeepOtherItemsClaimsAcrossReload() {
        var data = new CitizenPopulationSavedData(); var center = new BlockPos(0, 64, 0);
        assertThrows(IllegalArgumentException.class, () -> new CitizenPopulationSavedData().reserveCenter(center, null, 0));
        var id = data.reserveCenter(center, null, 4); long version = data.version();
        assertEquals(id, data.reserveCenter(center, id, 4));
        data.releaseCenter(id, 0); data.releaseCenter(id, -1); data.releaseCenter(UUID.randomUUID(), 1);
        data.releaseCenter(id, 1); assertFalse(data.centerAvailable(center, null));
        data = CitizenPopulationSavedData.load(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()));
        data.releaseCenter(id, 2); assertFalse(data.centerAvailable(center, null));
        data.releaseCenter(id, 1); assertTrue(data.centerAvailable(center, null)); assertEquals(version, data.version());
        id = data.reserveCenter(center, null, 3);
        var next = data.reserveCenter(center.east(), null, 1); data.releaseCenter(id, 1);
        assertFalse(data.centerAvailable(center, null)); assertFalse(data.centerAvailable(center.east(), null));
        data.releaseCenter(id, 2); assertTrue(data.centerAvailable(center, null));
        data.put(CitizenGauge.create(center.above(), center.east()));
        data.remove(data.at(center.above()).id()); assertTrue(data.centerAvailable(center.east(), next));
        assertTrue(data.centerAvailable(center.east(), null));
    }

    @Test void oldReservationsWithUnknownItemCountsRequireOwnerRefreshRatherThanFreeingOtherCopies() {
        var data = new CitizenPopulationSavedData(); var center = new BlockPos(0, 64, 0); var id = data.reserveCenter(center, null, 3);
        var tag = data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()); tag.getList("CenterReservations", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("Items");
        data = CitizenPopulationSavedData.load(tag); data.releaseCenter(id, 1);
        assertFalse(data.centerAvailable(center, null));
        data = CitizenPopulationSavedData.load(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup())); data.releaseCenter(id, 2);
        assertFalse(data.centerAvailable(center, null)); assertNull(data.reserveCenter(center, null, 1));
        var refreshed = data.reserveCenter(center, id, 2); assertNotNull(refreshed); assertNotEquals(id, refreshed);
        assertFalse(data.centerAvailable(center, id)); assertTrue(data.centerAvailable(center, refreshed));
        data.releaseCenter(id, 100); assertFalse(data.centerAvailable(center, null));
        data.releaseCenter(refreshed, 1); assertFalse(data.centerAvailable(center, null));
        data.releaseCenter(refreshed, 1); assertTrue(data.centerAvailable(center, null));
        var moved = CitizenPopulationSavedData.load(tag); moved.releaseCenterForRerecord(id, 1);
        assertTrue(moved.centerAvailable(center, null));
    }

    @Test void creativeDiscardPacketRoundTripsAndRejectsInvalidCounts() {
        var dimension = net.minecraft.world.level.Level.OVERWORLD.location(); var id = UUID.randomUUID();
        var packet = new GaugeReservationDiscardPacket(dimension, id, 64); var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.encode(buffer); assertEquals(packet, GaugeReservationDiscardPacket.decode(buffer)); }
        finally { buffer.release(); }
        for (int count : new int[]{-1, 0, 65, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new GaugeReservationDiscardPacket(dimension, id, count));
        }
        assertNull(GaugeReservationDiscardPacket.fromStack(net.minecraft.world.item.ItemStack.EMPTY));
    }

    @Test void centerIndexTracksSettingsRelocationReloadAndLegacyDuplicatesWithoutLoadingChunks() {
        var data = new CitizenPopulationSavedData(); var home = district(0, 1); var center = home.center();
        assertFalse(data.centerUsed(center)); data.put(home); assertTrue(data.centerUsed(center));
        assertFalse(data.centerUsed(center.east())); assertFalse(data.centerUsed(center.above()));
        assertFalse(new CitizenPopulationSavedData().centerUsed(center));
        data.put(home.settings(20, 2)); assertTrue(data.centerUsed(center));
        var moved = data.gauge(home.id()).relocate(new BlockPos(28000000, 70, 28000000), 0); data.put(moved);
        assertTrue(data.centerUsed(center));
        var duplicate = CitizenGauge.create(new BlockPos(1, 70, 0), center); data.put(duplicate);
        data = CitizenPopulationSavedData.load(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup())); assertTrue(data.centerUsed(center));
        data.remove(home.id()); assertTrue(data.centerUsed(center));
        data.remove(duplicate.id()); assertFalse(data.centerUsed(center));
        var source = CitizenGauge.create(new BlockPos(3, 70, 0), null); data.put(source);
        assertFalse(data.centerUsed(source.pos()));
        data.put(new CitizenGauge(source.id(), source.pos(), UUID.randomUUID(), center, 10, 1, 0, false));
        assertTrue(data.centerUsed(center)); data.put(source); assertFalse(data.centerUsed(center));
    }

    @Test void editableTargetsCannotExceedRadiusButLegacySettingsRemainReadable() {
        assertTrue(CitizenGauge.validSettings(1, 0)); assertTrue(CitizenGauge.validSettings(256, 256));
        assertFalse(CitizenGauge.validSettings(0, 0)); assertFalse(CitizenGauge.validSettings(257, 1));
        assertFalse(CitizenGauge.validSettings(8, 9)); assertFalse(CitizenGauge.validSettings(8, -1));
        var legacy = district(0, 100);
        assertEquals(legacy, CitizenGauge.load(legacy.save()));
    }

    @Test void networkCacheBuildsIncrementallyAndTracksPopulationWithoutRebuilding() {
        var data = new CitizenPopulationSavedData(); var nodes = new ArrayList<CitizenGauge>();
        for (int index = 0; index < 300; index++) {
            var node = district(index * 32, 1); data.put(node); nodes.add(node);
            if (index > 0) data.link(nodes.get(index - 1).id(), node.id());
        }
        var sink = CitizenGauge.create(new BlockPos(-1, 70, 0), null).withFlow(CitizenGauge.Flow.OUTFLOW);
        data.put(sink); data.link(sink.id(), nodes.get(0).id());
        var index = data.networks(); assertNull(index.request(sink.id())); index.tick(64);
        assertNull(index.cached(sink.id()));
        var resident = UUID.randomUUID(); data.register(resident, new CitizenPopulationState(nodes.get(0).districtId(), false, false, 0));
        index.tick(300); var network = index.cached(sink.id()); assertNotNull(network);
        assertTrue(network.hasOutflow()); assertFalse(network.saturated()); assertEquals(299, network.shortageCount());
        for (int i = 1; i < nodes.size(); i++) data.register(UUID.randomUUID(), new CitizenPopulationState(nodes.get(i).districtId(), false, false, 0));
        assertSame(network, index.cached(sink.id())); assertTrue(network.saturated()); assertFalse(network.hasSurplus());
        var extra = UUID.randomUUID(); data.register(extra, new CitizenPopulationState(nodes.get(0).districtId(), false, false, 0));
        assertTrue(network.hasSurplus()); assertEquals(nodes.get(0).id(), network.nextSurplus());
        data.removeCitizen(extra); assertFalse(network.hasSurplus());
        data.removeCitizen(resident); assertFalse(network.saturated());
        data.put(sink.cooldown(50)); assertSame(network, index.cached(sink.id()));
        data.unlink(sink.id(), nodes.get(0).id()); assertNull(index.cached(sink.id()));
        index.request(sink.id()); index.tick(1); assertFalse(index.cached(sink.id()).saturated());
    }

    @Test void existingCitizenSupplyCanBePreferredOverANearerInfluxWithinOneQueryBudget() {
        var data = new CitizenPopulationSavedData(); var requester = district(0, 1); var middle = district(32, 1); var donor = district(64, 1);
        var influx = CitizenGauge.create(new BlockPos(-1, 70, 0), null);
        for (var gauge : List.of(requester, middle, donor, influx)) data.put(gauge);
        data.link(requester.id(), influx.id()); data.link(requester.id(), middle.id()); data.link(middle.id(), donor.id());
        var request = new PopulationRequest(requester.id(), 0);
        assertEquals(List.of(donor.id(), middle.id(), requester.id()), request.findSupply(data, 16, 256, gauge -> gauge.influx() || gauge.id().equals(donor.id()), true));
        assertEquals(List.of(influx.id(), requester.id()), request.findSupply(data, 16, 2, gauge -> gauge.influx() || gauge.id().equals(donor.id()), true));
        assertEquals(2, request.visited().size());
    }

    @Test void emptyGaugesDefaultToInfluxAndPersistOutflowAcrossCopiesAndSaving() {
        var influx = CitizenGauge.create(new BlockPos(0, 70, 0), null);
        assertTrue(influx.influx()); assertFalse(influx.outflow());
        var outflow = influx.withFlow(CitizenGauge.Flow.OUTFLOW);
        assertTrue(outflow.source()); assertTrue(outflow.outflow()); assertFalse(outflow.influx());
        assertEquals(outflow, CitizenGauge.load(outflow.save()));
        assertTrue(outflow.relocate(new BlockPos(1, 70, 0), 2).cooldown(100).resolve(false).outflow());
        var old = influx.save(); old.remove("Flow"); assertTrue(CitizenGauge.load(old).influx());
        assertThrows(IllegalArgumentException.class, () -> district(0, 1).withFlow(CitizenGauge.Flow.OUTFLOW));
        assertFalse(PopulationRequest.improves(outflow, 100, district(10, 1), 0));
    }

    @Test void outflowModeIsAConfigurationChangeButCooldownIsNot() {
        var data = new CitizenPopulationSavedData(); var gauge = CitizenGauge.create(new BlockPos(0, 70, 0), null); data.put(gauge);
        long before = data.version(); data.put(gauge.cooldown(50)); assertEquals(before, data.version());
        data.put(gauge.withFlow(CitizenGauge.Flow.OUTFLOW)); assertTrue(data.version() > before);
    }

    @Test void unsatisfiableOldBacklogCannotStarveYoungerRequestsAtEitherRetryLimit() {
        for (int retry : new int[]{20, 200}) {
            var waiting = new ArrayList<PopulationRequest>();
            for (int index = 0; index < 96; index++) waiting.add(new PopulationRequest(new UUID(0, index + 1), index));
            var younger = new PopulationRequest(new UUID(1, retry), 100);
            waiting.add(younger);
            var attempted = new HashSet<UUID>();
            for (long now = 200; now <= 1200; now += 20) {
                waiting.sort(PopulationRequest.schedulingOrder(request -> 0));
                int budget = 4;
                for (var request : waiting) {
                    if (request.retryAt() > now) continue;
                    if (budget-- == 0) break;
                    request.attempted(now, retry); attempted.add(request.requester());
                }
            }
            assertEquals(waiting.size(), attempted.size(), "Every request must get a turn with retry=" + retry);
            assertTrue(attempted.contains(younger.requester()), "Older impossible requests cannot consume every round");
        }
    }

    @Test void unattemptedRequestsPreferAgeThenPopulationRatioAndStableTies() {
        var old = new PopulationRequest(new UUID(0, 1), 1);
        var full = new PopulationRequest(new UUID(0, 2), 2);
        var empty = new PopulationRequest(new UUID(0, 3), 2);
        var tie = new PopulationRequest(new UUID(0, 4), 2);
        var waiting = new ArrayList<>(List.of(full, old, empty, tie));
        waiting.sort(PopulationRequest.schedulingOrder(request -> request == full ? .8 : 0));
        assertEquals(List.of(old, empty, tie, full), waiting);
    }

    @Test void spatialDistrictIndexMatchesLinearSearchIncludingNegativeCellsAndRangeEdges() {
        var data = new CitizenPopulationSavedData(); var random = new Random(5831);
        for (int index = 0; index < 160; index++) {
            var center = new BlockPos(random.nextInt(1800) - 900, 64, random.nextInt(1800) - 900);
            data.put(CitizenGauge.create(new BlockPos(index, 70, 1000), center).settings(1 + random.nextInt(256), 0));
        }
        for (int index = 0; index < 500; index++) {
            var point = new Vec3(random.nextDouble() * 2200 - 1100, index - 250, random.nextDouble() * 2200 - 1100);
            assertEquals(linearDistrict(data, point), data.physicalDistrict(point));
        }
        var first = new CitizenGauge(new UUID(0, 1), new BlockPos(-1000, 70, 0), new UUID(0, 10), new BlockPos(-65, 64, -65), 64, 0, 0, false);
        var second = new CitizenGauge(new UUID(0, 2), new BlockPos(-1001, 70, 0), new UUID(0, 11), first.center(), 64, 0, 0, false);
        data = new CitizenPopulationSavedData(); data.put(second); data.put(first);
        assertEquals(first, data.physicalDistrict(Vec3.atCenterOf(first.center())));
        assertEquals(first, data.physicalDistrict(new Vec3(-.5, -100, -64.5)));
        assertNull(data.physicalDistrict(new Vec3(0, 64, -64.5)));
        data.remove(first.id()); assertEquals(second, data.physicalDistrict(Vec3.atCenterOf(second.center())));
        var restored = CitizenPopulationSavedData.load(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()));
        assertEquals(second, restored.physicalDistrict(Vec3.atCenterOf(second.center())));
        assertEquals(List.of(second), restored.inChunk(new net.minecraft.world.level.ChunkPos(second.pos()).toLong()));
    }

    private static CitizenGauge linearDistrict(CitizenPopulationSavedData data, Vec3 point) {
        return data.gauges().stream().filter(gauge -> gauge.contains(point)).min(Comparator
                .comparingDouble((CitizenGauge gauge) -> {
                    double x = Math.floor(point.x) - gauge.center().getX(), z = Math.floor(point.z) - gauge.center().getZ();
                    return x * x + z * z;
                }).thenComparing(CitizenGauge::districtId)).orElse(null);
    }

    @Test void overlapCacheInvalidatesOnlyWhenDistrictGeometryChanges() {
        var data = new CitizenPopulationSavedData(); var a = district(-65, 0); var b = district(-45, 0);
        data.put(a); data.put(b);
        assertEquals(1, data.overlap(a.id()).count());
        var cached = data.overlap(a.id()); assertSame(cached, data.overlap(a.id()));
        a = a.cooldown(40).settings(10, 5, "Renamed"); data.put(a); assertSame(cached, data.overlap(a.id()));
        b = b.settings(11, 0); data.put(b); assertEquals(1, data.overlap(a.id()).count()); assertFalse(data.overlap(a.id()).strong());
        b = new CitizenGauge(b.id(), b.pos(), b.districtId(), a.center().east(2), 11, 0, 0, false); data.put(b);
        assertFalse(data.overlap(a.id()).strong());
        var moved = a.relocate(a.pos().east(200), 2); data.put(moved);
        assertTrue(data.inChunk(new net.minecraft.world.level.ChunkPos(a.pos()).toLong()).isEmpty());
        assertEquals(List.of(moved), data.inChunk(new net.minecraft.world.level.ChunkPos(moved.pos()).toLong()));
        data.remove(b.id()); assertEquals(0, data.overlap(a.id()).count()); assertFalse(data.overlap(a.id()).strong());
    }

    @Test void linkedDistrictCacheTracksLinkSourceConversionRemovalAndRename() {
        var data = new CitizenPopulationSavedData(); var a = district(0, 0); var b = district(20, 0); var source = CitizenGauge.create(new BlockPos(30, 70, 0), null);
        data.put(a); data.put(b); data.put(source); data.link(a.id(), b.id()); data.link(a.id(), source.id());
        var cached = data.linkedDistricts(a.districtId()); assertEquals(List.of(b.districtId()), cached); assertSame(cached, data.linkedDistricts(a.districtId()));
        data.put(b.settings(12, 1, "Other name")); assertSame(cached, data.linkedDistricts(a.districtId()));
        data.unlink(a.id(), b.id()); assertTrue(data.linkedDistricts(a.districtId()).isEmpty());
        var converted = new CitizenGauge(source.id(), source.pos(), UUID.randomUUID(), source.pos(), 10, 0, 0, false); data.put(converted);
        assertEquals(List.of(converted.districtId()), data.linkedDistricts(a.districtId()));
        data.remove(converted.id()); assertTrue(data.linkedDistricts(a.districtId()).isEmpty());
    }

    @Test void reservationIndexMatchesFullScanAfterEveryHopCompletionAndCancellation() {
        var a = UUID.randomUUID(); var b = UUID.randomUUID(); var c = UUID.randomUUID(); var d = UUID.randomUUID();
        var gauges = List.of(a, b, c, d); var index = new PopulationChainIndex(); var chains = new ArrayList<PopulationTransferChain>();
        var first = new PopulationTransferChain(UUID.randomUUID(), List.of(a, b, c), 0);
        var second = new PopulationTransferChain(UUID.randomUUID(), List.of(d, b, c), 0);
        chains.add(first); chains.add(second); index.put(first); index.put(second); index.put(first);
        assertReservations(gauges, chains, index);
        first.advance(10); index.put(first); assertReservations(gauges, chains, index);
        second.advance(11); index.put(second); assertReservations(gauges, chains, index);
        first.advance(20); index.put(first); chains.remove(first); assertReservations(gauges, chains, index);
        index.remove(second.id()); chains.remove(second); assertReservations(gauges, chains, index);
        index.remove(second.id()); assertReservations(gauges, chains, index);
    }

    private static void assertReservations(List<UUID> gauges, List<PopulationTransferChain> chains, PopulationChainIndex index) {
        for (var gauge : gauges) {
            assertEquals(chains.stream().mapToInt(chain -> chain.incoming(gauge)).sum(), index.incoming(gauge));
            assertEquals(chains.stream().mapToInt(chain -> chain.outgoing(gauge)).sum(), index.outgoing(gauge));
            assertEquals(chains.stream().anyMatch(chain -> chain.path().contains(gauge)), index.busy(gauge));
        }
    }
    @Test void fourPanelsHaveIndependentIdentitySettingsLinksAndPersistence() {
        var data = new CitizenPopulationSavedData(); var pos = new BlockPos(1, 70, 2);
        for (int slot = 0; slot < 4; slot++) data.put(CitizenGauge.create(pos, new BlockPos(slot, 60, 0), slot).settings(10 + slot, slot));
        assertEquals(4, data.atAll(pos).size());
        var a = data.at(pos, 1); var b = data.at(pos, 3); data.link(a.id(), b.id());
        var restored = CitizenPopulationSavedData.load(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()));
        assertEquals(a, restored.at(pos, 1)); assertEquals(b, restored.at(pos, 3)); assertTrue(restored.linked(a.id()).contains(b.id()));
        assertThrows(IllegalArgumentException.class, () -> data.put(CitizenGauge.create(pos, null, 1)));
        assertThrows(IllegalArgumentException.class, () -> CitizenGauge.create(pos, null, 4));
        restored.remove(a.id()); assertEquals(3, restored.atAll(pos).size()); assertEquals(b, restored.at(pos, 3));
        assertTrue(restored.linked(b.id()).isEmpty());
        assertEquals(3, b.cooldown(40).resolve(true).settings(12, 5).slot());
    }
    @Test void legacyGaugesMigrateToFirstPanelWithoutChangingIds() {
        var gauge = district(1, 8); var legacy = gauge.save(); legacy.remove("Slot");
        assertEquals(gauge, CitizenGauge.load(legacy));
    }
    @Test void relocationPreservesDistrictLinksResidentsAndSettings() {
        var a = district(0, 12).cooldown(120); var b = district(4, 6); var data = new CitizenPopulationSavedData();
        data.put(a); data.put(b); data.link(a.id(), b.id()); var resident = UUID.randomUUID();
        data.register(resident, new CitizenPopulationState(a.districtId(), false, false, 40));
        var moved = a.relocate(b.pos(), 3); data.put(moved);
        assertNull(data.at(a.pos())); assertEquals(moved, data.at(b.pos(), 3)); assertEquals(b, data.at(b.pos()));
        assertEquals(a.id(), moved.id()); assertEquals(a.districtId(), moved.districtId()); assertEquals(a.center(), moved.center());
        assertEquals(a.range(), moved.range()); assertEquals(a.target(), moved.target()); assertEquals(120, moved.cooldownUntil());
        assertTrue(data.linked(a.id()).contains(b.id())); assertEquals(1, data.population(a.districtId()));
        assertEquals(moved, CitizenPopulationSavedData.load(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup())).district(a.districtId()));
    }
    @Test void gaugeIdentityRangeAndZeroTargetSurviveSaving() {
        var gauge = district(-17, 0); var data = new CitizenPopulationSavedData(); data.put(gauge);
        assertFalse(gauge.source()); assertEquals(0, gauge.ratio(0)); assertEquals(Double.POSITIVE_INFINITY, gauge.ratio(3));
        assertEquals(0, gauge.settings(10, 1).buffer()); assertEquals(2, gauge.settings(10, 20).buffer());
        assertFalse(CitizenGauge.create(BlockPos.ZERO, null).save().contains("Target"));
        assertTrue(gauge.contains(new Vec3(-16.5, -60, 0.5))); assertTrue(gauge.contains(new Vec3(-6.5, 300, 0.5)));
        assertFalse(gauge.contains(new Vec3(-6, 64, 0.5)));
        var restored = CitizenPopulationSavedData.load(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()));
        assertEquals(gauge, restored.district(gauge.districtId())); assertEquals(gauge, restored.at(gauge.pos()));
        restored.remove(gauge.id()); var replacement = CitizenGauge.create(gauge.pos(), gauge.center()); restored.put(replacement);
        assertNotEquals(gauge.id(), replacement.id()); assertNotEquals(gauge.districtId(), replacement.districtId());
    }
    @Test void ratiosMustStrictlyImproveAndCannotOscillate() {
        var a = district(0, 5); var b = district(20, 5);
        assertFalse(PopulationRequest.improves(a, 3, b, 2)); assertFalse(PopulationRequest.improves(a, 2, b, 2));
        assertTrue(PopulationRequest.improves(a, 3, b, 1));
        assertTrue(PopulationRequest.improves(district(0, 20), 15, district(30, 10), 5));
        assertFalse(PopulationRequest.improves(a, 0, b, 0));
        assertTrue(PopulationRequest.improves(a.settings(10, 0), 1, b, 0));
        assertFalse(PopulationRequest.improves(a.settings(10, 0), 0, b, 0));
        assertFalse(PopulationRequest.improves(a, 10, b.settings(10, 0), 0));
        assertTrue(PopulationRequest.improves(CitizenGauge.create(BlockPos.ZERO, null), 0, b, 0));
    }
    @Test void loopQueriesFindMultiHopSupplyWithoutSkippingEdges() {
        var data = new CitizenPopulationSavedData(); var a = district(0, 5); var b = district(20, 5); var c = district(40, 5); var d = district(60, 5);
        for (var gauge : List.of(a, b, c, d)) data.put(gauge);
        data.link(a.id(), b.id()); data.link(b.id(), c.id()); data.link(c.id(), d.id()); data.link(d.id(), b.id());
        var request = new PopulationRequest(c.id(), 10);
        var path = request.findSupply(data, 8, 32, candidate -> candidate.id().equals(a.id()));
        assertEquals(List.of(a.id(), b.id(), c.id()), path); assertEquals(new HashSet<>(path).size(), path.size());
        assertTrue(request.findSupply(data, 1, 32, candidate -> candidate.id().equals(a.id())).isEmpty());
        assertTrue(request.findSupply(data, 8, 1, candidate -> candidate.id().equals(a.id())).isEmpty());
        assertTrue(request.visited().size() <= 1);
    }
    @Test void topologyAndUnloadedPopulationPersistWithoutEntityQueries() {
        var data = new CitizenPopulationSavedData(); var source = CitizenGauge.create(BlockPos.ZERO, null); var a = district(30, 10); var b = district(60, 10);
        for (var gauge : List.of(source, a, b)) data.put(gauge);
        assertTrue(data.link(source.id(), a.id())); assertTrue(data.link(a.id(), b.id())); assertFalse(data.link(a.id(), a.id())); assertFalse(data.link(a.id(), source.id()));
        var id = UUID.randomUUID(); data.register(id, new CitizenPopulationState(a.districtId(), false, true, 2400));
        data.register(id, new CitizenPopulationState(a.districtId(), false, true, 2400)); assertEquals(1, data.population(a.districtId()));
        var restored = CitizenPopulationSavedData.load(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()));
        assertEquals(1, restored.population(a.districtId())); assertTrue(restored.citizen(id).force());
        assertEquals(Set.of(a.id()), restored.linked(source.id())); assertEquals(Set.of(source.id(), b.id()), restored.linked(a.id()));
        restored.remove(a.id()); assertTrue(restored.linked(source.id()).isEmpty()); assertTrue(restored.linked(b.id()).isEmpty());
        assertNull(restored.citizen(id).districtId()); assertFalse(restored.citizen(id).independent());
    }
    @Test void independenceAndMembershipChangesNeverDoubleCount() {
        var data = new CitizenPopulationSavedData(); var a = district(0, 5); var b = district(20, 5); data.put(a); data.put(b);
        var id = UUID.randomUUID(); var state = new CitizenPopulationState(a.districtId(), false, false, 0); data.register(id, state);
        data.register(id, state.district(b.districtId(), 10)); assertEquals(0, data.population(a.districtId())); assertEquals(1, data.population(b.districtId()));
        data.register(id, state.flags(true, true)); assertEquals(0, data.population(b.districtId())); assertNull(data.citizen(id).districtId());
        data.removeCitizen(id); assertNull(data.citizen(id));
        var entity = new CitizenState(); entity.setPopulation(state); var restored = new CitizenState(); restored.deserializeNBT(com.rexhub.createapparel.TestRegistries.lookup(), entity.serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()));
        assertEquals(state, restored.population()); restored.deserializeNBT(com.rexhub.createapparel.TestRegistries.lookup(), new CompoundTag()); assertEquals(CitizenPopulationState.DEFAULT, restored.population());
    }
    @Test void eachHopOwnsItsReservationsAndProgressTimeout() {
        var a = UUID.randomUUID(); var b = UUID.randomUUID(); var c = UUID.randomUUID();
        var chain = new PopulationTransferChain(UUID.randomUUID(), List.of(a, b, c), 0);
        assertEquals(1, chain.outgoing(a)); assertEquals(1, chain.incoming(b)); assertEquals(1, chain.outgoing(b)); assertEquals(1, chain.incoming(c));
        chain.reserve(UUID.randomUUID(), 100); chain.started(200); assertEquals(200, chain.lastProgressAt());
        chain.advance(300); assertEquals(b, chain.from()); assertEquals(c, chain.to()); assertNull(chain.citizen());
        assertEquals(0, chain.outgoing(a)); assertEquals(0, chain.incoming(b)); assertEquals(1, chain.outgoing(b));
        chain.advance(900); assertTrue(chain.complete()); assertEquals(900, chain.lastProgressAt()); assertEquals(0, chain.incoming(c));
    }
    @Test void gaugePacketsRoundTripAndRejectInvalidValuesAtTheDataBoundary() {
        var packet = new CitizenGaugeActionPacket(7, UUID.randomUUID(), 123, CitizenGaugeActionPacket.Action.UNLINK, 256, 0, UUID.randomUUID());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.encode(buffer); assertEquals(packet, CitizenGaugeActionPacket.decode(buffer)); } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> district(0, -1));
        assertThrows(IllegalArgumentException.class, () -> district(0, 1).settings(257, 1));
        assertThrows(IllegalArgumentException.class, () -> new PopulationTransferChain(UUID.randomUUID(), List.of(packet.gaugeId(), packet.gaugeId()), 0));
        assertThrows(IllegalArgumentException.class, () -> new CitizenIntent(CitizenIntent.Source.PLAYER, CitizenIntent.DestinationType.POSITION, new Vec3(Double.NaN, 0, 0), null, 0, 100, 0));
    }
    @Test void districtNamesDefaultToUuidAndSurviveSettingsRelocationAndSaving() {
        var gauge = district(0, 5);
        assertEquals(gauge.districtId().toString(), gauge.name());
        var legacy = gauge.save(); legacy.remove("Name");
        assertEquals(gauge, CitizenGauge.load(legacy));
        var named = gauge.settings(12, 8, "  中央地区  ").cooldown(50).resolve(true).relocate(new BlockPos(8, 70, 2), 3);
        assertEquals("中央地区", named.name());
        assertEquals(named, CitizenGauge.load(named.save()));
        assertEquals("中央地区", named.settings(24, 9).name());
        assertEquals(gauge.districtId().toString(), named.settings(12, 8, " ").name());
        assertFalse(CitizenGauge.create(BlockPos.ZERO, null).save().contains("Name"));
        assertThrows(IllegalArgumentException.class, () -> gauge.settings(12, 8, "x".repeat(CitizenGauge.MAX_NAME_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class, () -> gauge.settings(12, 8, "bad\nname"));
        assertThrows(IllegalArgumentException.class, () -> gauge.settings(12, 8, "§cbad"));
    }
    @Test void nameChangesInvalidateStaleEditsButPopulationActivityPreservesNames() {
        var data = new CitizenPopulationSavedData(); var gauge = district(0, 5); data.put(gauge); long version = data.version();
        var named = gauge.settings(gauge.range(), gauge.target(), "中央地区"); data.put(named);
        assertEquals(version + 1, data.version());
        data.put(named.resolve(true).cooldown(100));
        assertEquals(version + 1, data.version());
        assertEquals("中央地区", CitizenPopulationSavedData.load(data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup())).gauge(gauge.id()).name());
    }
    @Test void namePacketRoundTripsWithinTheBoundedNameLength() {
        var packet = new CitizenGaugeActionPacket(7, UUID.randomUUID(), 123, CitizenGaugeActionPacket.Action.SETTINGS, 12, 7, null, "中央地区");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.encode(buffer); assertEquals(packet, CitizenGaugeActionPacket.decode(buffer)); } finally { buffer.release(); }
    }
    @Test void districtLampMatchesActualPopulationAndHandlesZeroTarget() {
        var districtId = java.util.UUID.randomUUID();
        assertTrue(new com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity.DistrictStatus(districtId, "A", 0, 0).satisfied());
        assertTrue(new com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity.DistrictStatus(districtId, "A", 5, 5).satisfied());
        assertFalse(new com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity.DistrictStatus(districtId, "A", 0, 5).satisfied());
        assertFalse(new com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity.DistrictStatus(districtId, "A", 6, 5).satisfied());
    }
    @Test void restartReleasesPersistedReservationsBeforeReevaluatingPopulation() {
        var data = new CitizenPopulationSavedData(); var gauge = district(0, 10); data.put(gauge);
        var id = UUID.randomUUID(); var chain = UUID.randomUUID(); data.register(id, new CitizenPopulationState(gauge.districtId(), false, false, 0));
        data.reserve(id, chain); assertEquals(chain, data.reservation(id));
        var tag = data.save(new CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup());
        assertFalse(tag.getList("Citizens", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).contains("TransferReservation"));
        tag.getList("Citizens", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putUUID("TransferReservation", chain);
        var restored = CitizenPopulationSavedData.load(tag);
        assertNull(restored.reservation(id)); assertEquals(1, restored.population(gauge.districtId()));
        data.release(id); assertNull(data.reservation(id));
    }
    @Test void populationActivityDoesNotInvalidateConcurrentGaugeSettingsEdits() {
        var data = new CitizenPopulationSavedData(); var gauge = district(0, 10); data.put(gauge); long version = data.version();
        data.put(gauge.cooldown(1000).resolve(true)); assertEquals(version, data.version());
        data.put(data.gauge(gauge.id()).settings(12, 10)); assertEquals(version + 1, data.version());
        assertEquals(1000, data.gauge(gauge.id()).cooldownUntil());
        data.register(UUID.randomUUID(), new CitizenPopulationState(gauge.districtId(), false, false, 0)); assertEquals(version + 1, data.version());
    }
}
