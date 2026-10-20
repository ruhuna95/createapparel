package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.population.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CitizenOptimizationTest {
    @Test void reservationDimensionsAreImmutableAndPartialObservationsNeverChangeReservedQuantities() {
        var data = new CitizenPopulationSavedData(); var center = new BlockPos(1, 70, 2);
        var id = data.reserveCenter(center, null, 8, "minecraft:overworld", 50);
        data.verifyCenter(id, "minecraft:overworld", 2, 100);
        assertEquals(8, data.centerClaim(id).reservedItemCount()); assertEquals(2, data.centerClaim(id).lastObservedItems());
        data.verifyCenter(id, "minecraft:the_nether", 1, 150);
        var claim = data.centerClaim(id); assertEquals("minecraft:overworld", claim.dimension());
        assertEquals("minecraft:the_nether", claim.lastObservedDimension()); assertEquals(1, claim.lastObservedItems());
        assertEquals(GaugeCenterReservation.State.DIMENSION_MISMATCH, claim.state());
        data.releaseCenter(id, 8); assertNotNull(data.centerClaim(id));
        data.verifyCenter(id, "minecraft:overworld", 0, Long.MAX_VALUE - 1);
        assertEquals(GaugeCenterReservation.State.DIMENSION_MISMATCH, data.centerClaim(id).state());
        var restored = CitizenPopulationSavedData.load(data.save(new CompoundTag()));
        assertEquals(data.centerClaim(id), restored.centerClaim(id)); assertFalse(restored.centerAvailable(center, null));
        assertTrue(restored.releaseOrphanCenter(id)); assertTrue(restored.centerAvailable(center, null));
    }
    @Test void observationsNeverUpgradeUnknownDimensionsOrUnknownLegacyItemCounts() {
        var id = UUID.randomUUID(); var legacy = new GaugeCenterReservation(id, "", BlockPos.ZERO, -1, 0, 0, GaugeCenterReservation.State.UNVERIFIED);
        var observed = legacy.verified("minecraft:the_end", 3, 100);
        assertEquals("", observed.dimension()); assertEquals(-1, observed.reservedItemCount()); assertEquals(3, observed.lastObservedItems());
        assertEquals(observed, GaugeCenterReservation.load(observed.save()));
        var oldTag = legacy.save(); oldTag.remove("LastObservedItems"); oldTag.remove("LastObservedDimension");
        assertEquals(-1, GaugeCenterReservation.load(oldTag).lastObservedItems());
        assertThrows(IllegalArgumentException.class, () -> legacy.verified("minecraft:overworld", -1, 1));
    }
    @Test void exactOrZeroLocalObservationsAreNotGlobalOwnershipProofAndNeverExpireOtherStorage() {
        var data = new CitizenPopulationSavedData(); var center = BlockPos.ZERO;
        var id = data.reserveCenter(center, null, 3, "minecraft:overworld", 0);
        data.verifyCenter(id, "minecraft:overworld", 3, 10); data.verifyCenter(id, "minecraft:overworld", 0, Long.MAX_VALUE - 1);
        assertEquals(3, data.centerClaim(id).reservedItemCount()); assertEquals(0, data.centerClaim(id).lastObservedItems()); assertFalse(data.centerAvailable(center, null));
        data.releaseCenter(id, 1); assertEquals(2, data.centerClaim(id).reservedItemCount());
        assertEquals(0, data.centerClaim(id).lastObservedItems());
        data.verifyCenter(id, "minecraft:overworld", 4, 100); assertEquals(GaugeCenterReservation.State.CONFLICT, data.centerClaim(id).state());
        data.verifyCenter(id, "minecraft:overworld", 1, 200); assertTrue(data.centerClaim(id).conflicted());
        assertTrue(data.releaseOrphanCenter(id));
    }
    @BeforeAll static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }
    private static CitizenGauge district(int x) { return CitizenGauge.create(new BlockPos(x, 70, 0), new BlockPos(x, 64, 0)); }

    @Test void independentNetworksSurvivePresentationPopulationAndUnrelatedTopologyEdits() {
        var data = new CitizenPopulationSavedData(); var a = district(0); var b = district(80); var c = district(160); var d = district(240);
        for (var gauge : List.of(a, b, c, d)) data.put(gauge);
        data.link(a.id(), b.id()); data.link(c.id(), d.id());
        data.networks().request(a.id()); data.networks().request(c.id()); data.networks().tick(64);
        var first = data.networks().cached(a.id()); var separate = data.networks().cached(c.id());
        long topology = data.topologyRevision(), geometry = data.districtGeometryRevision(), population = data.populationRevision(), presentation = data.presentationRevision();
        data.put(a.settings(a.range(), a.target(), "Renamed"));
        assertSame(first, data.networks().cached(a.id())); assertEquals(topology, data.topologyRevision());
        assertEquals(geometry, data.districtGeometryRevision()); assertEquals(population, data.populationRevision()); assertTrue(data.presentationRevision() > presentation);
        data.put(data.gauge(a.id()).settings(a.range(), 0));
        assertSame(first, data.networks().cached(a.id())); assertTrue(data.populationRevision() > population);
        data.unlink(a.id(), b.id()); assertNull(data.networks().cached(a.id())); assertSame(separate, data.networks().cached(c.id()));
        data.networks().request(a.id()); data.networks().request(b.id()); data.networks().tick(1);
        assertTrue(data.networks().lastVisited() <= 1); assertSame(separate, data.networks().cached(d.id()));
        data.networks().tick(1); assertNotSame(first, data.networks().cached(b.id()));
    }

    @Test void allNetworkRebuildsShareOneBudgetAndLoopEdgesDoNotDuplicateWork() {
        var data = new CitizenPopulationSavedData(); var nodes = new ArrayList<CitizenGauge>();
        for (int i = 0; i < 200; i++) {
            var gauge = district(i * 80); data.put(gauge); nodes.add(gauge);
            if (i % 10 != 0) data.link(nodes.get(i - 1).id(), gauge.id());
            if (i % 10 == 9) data.link(nodes.get(i - 9).id(), gauge.id());
        }
        nodes.forEach(gauge -> data.networks().request(gauge.id())); int total = 0;
        for (int i = 0; i < 4; i++) { data.networks().tick(64); assertTrue(data.networks().lastVisited() <= 64); total += data.networks().lastVisited(); }
        assertEquals(200, total); assertEquals(20, data.networks().cacheCount()); nodes.forEach(gauge -> assertNotNull(data.networks().cached(gauge.id())));
    }

    @Test void chainReverseIndexTracksAdvancesCompletionAndCancellationWithoutOtherChains() {
        var index = new PopulationChainIndex(); var a = UUID.randomUUID(); var b = UUID.randomUUID(); var c = UUID.randomUUID();
        var first = new PopulationTransferChain(UUID.randomUUID(), List.of(a, b, c), 0);
        var second = new PopulationTransferChain(UUID.randomUUID(), List.of(b, c), 0);
        index.put(first); index.put(second); assertEquals(Set.of(first.id(), second.id()), index.chains(b));
        first.advance(1); index.put(first); assertEquals(Set.of(first.id()), index.chains(a));
        first.advance(2); index.put(first); assertTrue(index.chains(a).isEmpty()); assertEquals(Set.of(second.id()), index.chains(b));
        index.remove(first.id()); index.remove(second.id()); index.remove(second.id()); assertTrue(index.chains(c).isEmpty()); assertFalse(index.busy(c));
    }

    @Test void reservationMetadataPersistsWithoutExpiringOfflineStorageAndConflictsNeedExplicitRelease() {
        var data = new CitizenPopulationSavedData(); var center = new BlockPos(1, 64, 1);
        var id = data.reserveCenter(center, null, 2, "minecraft:overworld", 50); data.verifyCenter(id, "minecraft:overworld", 1, 1000000);
        var restored = CitizenPopulationSavedData.load(data.save(new CompoundTag())); var claim = restored.centerClaim(id);
        assertEquals(50, claim.createdAt()); assertEquals(1000000, claim.lastVerifiedAt()); assertEquals(2, claim.reservedItemCount());
        assertEquals("minecraft:overworld", claim.dimension()); assertFalse(restored.centerAvailable(center, null));
        restored.verifyCenter(id, "minecraft:overworld", 3, 1000001); restored.releaseCenter(id, 3);
        assertEquals(GaugeCenterReservation.State.CONFLICT, restored.centerClaim(id).state()); assertFalse(restored.centerAvailable(center, null));
        assertTrue(restored.releaseOrphanCenter(id)); assertTrue(restored.centerAvailable(center, null));
        var district = district(80); var consumed = restored.reserveCenter(district.center(), null); restored.put(district);
        assertFalse(restored.releaseOrphanCenter(consumed)); assertTrue(restored.centerUsed(district.center()));
    }

    @Test void snapshotsAndDeltasAddChangeAndRemoveWithoutLosingUnchangedEntries() {
        var a = district(0); var b = district(80); var first = snapshot(a, b);
        var received = GaugeViewDelta.apply(null, -1, GaugeViewDelta.between(null, first, 0, 1)); assertNotNull(received);
        var c = district(160); var second = snapshot(b.settings(32, 10, "B"), c);
        var delta = GaugeViewDelta.between(first, second, 1, 2);
        assertEquals(1, delta.getList("RemovedGauges", 10).size());
        var merged = GaugeViewDelta.apply(received, 1, delta); assertNotNull(merged);
        assertEquals(second.get("Gauges"), merged.get("Gauges"));
        assertNull(GaugeViewDelta.apply(received, 0, delta)); assertNull(GaugeViewDelta.apply(null, 1, delta));
        assertNotNull(GaugeViewDelta.apply(received, 0, GaugeViewDelta.between(null, second, 0, 10)));
        var unchanged = GaugeViewDelta.between(second, second, 2, 3); assertTrue(unchanged.getList("Gauges", 10).isEmpty());
    }
    private static CompoundTag snapshot(CitizenGauge... gauges) {
        var tag = new CompoundTag(); tag.putString("Dimension", "minecraft:overworld"); var entries = new ListTag();
        for (var gauge : gauges) entries.add(gauge.save()); tag.put("Gauges", entries); tag.put("TerritoryGauges", entries.copy()); return tag;
    }
    @Test void menuDeltasDoNotResendLinksOrGeometryForPopulationChanges() {
        var before = snapshot(district(0), district(80)); before.putInt("Population", 1); before.putLong("Now", 100);
        var after = before.copy(); after.putInt("Population", 2); after.putLong("Now", 120);
        var delta = GaugeViewDelta.fieldsBetween(before, after, 1, 2);
        assertFalse(delta.contains("Gauges")); assertFalse(delta.contains("TerritoryGauges")); assertEquals(2, delta.getInt("Population"));
        var merged = GaugeViewDelta.applyFields(before, 1, delta); assertNotNull(merged); assertEquals(2, merged.getInt("Population"));
        assertEquals(before.get("Gauges"), merged.get("Gauges")); assertNull(GaugeViewDelta.applyFields(before, 0, delta));
        after.putLong("Now", 140); assertTrue(GaugeViewDelta.fieldsBetween(after, after, 2, 3).isEmpty());
    }
    @Test void movingOwnershipAndReusedPhysicalAddressesRemainConsistentAcrossSaveLoad() {
        var data = new CitizenPopulationSavedData(); var original = district(0); data.put(original); data.moving(original.id(), true);
        var owner = UUID.randomUUID(); assertTrue(data.bindMoving(original.id(), owner)); assertFalse(data.bindMoving(original.id(), UUID.randomUUID()));
        var replacement = CitizenGauge.create(original.pos(), null); data.put(replacement);
        var restored = CitizenPopulationSavedData.load(data.save(new CompoundTag()));
        assertTrue(restored.ownedByContraption(original.id(), owner)); assertEquals(replacement.id(), restored.at(original.pos()).id());
        var moved = original.relocate(original.pos().east(8), 0); restored.put(moved); restored.moving(original.id(), false);
        assertEquals(moved.id(), restored.at(moved.pos()).id()); assertEquals(replacement.id(), restored.at(original.pos()).id());
        restored.remove(original.id()); assertEquals(replacement.id(), restored.at(original.pos()).id());
    }
    @Test void movingOwnerLocationsPersistOncePerEntityAndInverseOwnershipShrinksAfterRestorationAndRemoval() {
        var data = new CitizenPopulationSavedData(); var first = district(0); var second = district(80); var third = district(160);
        for (var gauge : List.of(first, second, third)) { data.put(gauge); data.moving(gauge.id(), true); }
        var owner = UUID.randomUUID(); var other = UUID.randomUUID();
        assertTrue(data.bindMoving(first.id(), owner)); assertTrue(data.bindMoving(second.id(), owner)); assertTrue(data.bindMoving(third.id(), other));
        assertFalse(data.bindMoving(first.id(), other)); data.movingLocation(owner, 1234L); data.movingLocation(other, 5678L);
        data.movingLocation(UUID.randomUUID(), 9999L);
        var saved = data.save(new CompoundTag()); assertEquals(2, saved.getList("MovingOwners", 10).size());
        var restored = CitizenPopulationSavedData.load(saved);
        assertEquals(Set.of(first.id(), second.id()), new HashSet<>(restored.ownedMovingGauges(owner)));
        assertEquals(1234L, restored.movingChunk(first.id())); assertEquals(1234L, restored.movingChunk(second.id())); assertEquals(5678L, restored.movingChunk(third.id()));
        assertTrue(restored.bindMoving(first.id(), owner)); restored.movingLocation(owner, 42L); assertEquals(42L, restored.movingChunk(second.id()));
        restored.moving(first.id(), false); assertNull(restored.movingOwner(first.id())); assertNull(restored.movingChunk(first.id()));
        assertEquals(List.of(second.id()), restored.ownedMovingGauges(owner)); restored.remove(second.id());
        assertTrue(restored.ownedMovingGauges(owner).isEmpty()); assertEquals(1, restored.save(new CompoundTag()).getList("MovingOwners", 10).size());
        var legacy = data.save(new CompoundTag()); legacy.remove("MovingOwners"); legacy.putInt("SchemaVersion", 6);
        var migrated = CitizenPopulationSavedData.load(legacy); assertTrue(migrated.ownedByContraption(first.id(), owner));
        assertNull(migrated.movingChunk(first.id())); assertEquals(2, migrated.ownedMovingGauges(owner).size());
    }
    @Test void missingGaugeMetadataKeepsLinksAndMembershipUntilTheBlockEntityCanRecoverIt() {
        var data = new CitizenPopulationSavedData(); var home = district(0).settings(32, 1); var peer = district(80).settings(32, 0);
        data.put(home); data.put(peer); data.link(home.id(), peer.id()); var resident = UUID.randomUUID();
        data.register(resident, new CitizenPopulationState(home.districtId(), false, false, 0));
        var tag = data.save(new CompoundTag()); tag.getList("Gauges", 10).remove(0);
        var restored = CitizenPopulationSavedData.load(tag);
        assertEquals(Set.of(home.id()), restored.linked(peer.id())); assertEquals(home.districtId(), restored.citizen(resident).districtId());
        assertEquals(1, restored.unresolvedGaugeReferences()); assertEquals(1, restored.unresolvedDistricts());
        restored.networks().request(peer.id()); restored.networks().tick(64); assertFalse(restored.networks().cached(peer.id()).saturated());
        restored = CitizenPopulationSavedData.load(restored.save(new CompoundTag())); restored.put(home);
        assertEquals(Set.of(peer.id()), restored.linked(home.id())); assertEquals(1, restored.population(home.districtId()));
        assertEquals(0, restored.unresolvedGaugeReferences()); assertEquals(0, restored.unresolvedDistricts());
        restored.networks().request(home.id()); restored.networks().tick(64); assertTrue(restored.networks().cached(home.id()).saturated());
        restored.remove(home.id()); assertNull(restored.citizen(resident).districtId());
    }
    @Test void conflictingDuplicateGaugeIdsCannotRelocateOrOverwriteTheFirstSavedRecord() {
        var data = new CitizenPopulationSavedData(); var home = district(0); data.put(home);
        var tag = data.save(new CompoundTag()); tag.getList("Gauges", 10).add(home.relocate(home.pos().east(8), 1).save());
        var restored = CitizenPopulationSavedData.load(tag);
        assertEquals(home, restored.gauge(home.id())); assertNotNull(restored.at(home.pos())); assertNull(restored.at(home.pos().east(8), 1));
    }
}
