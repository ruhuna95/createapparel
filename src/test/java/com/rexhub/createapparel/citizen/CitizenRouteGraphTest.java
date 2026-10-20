package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.route.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CitizenRouteGraphTest {
    @BeforeAll static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }
    private static RouteMarker marker(int x, RouteMarkerType type) { return RouteMarker.create(UUID.randomUUID(), new BlockPos(x, 70, -17), type); }

    @Test void distantEditsDoNotInvalidateViewsButConnectedRemoteEndpointsDo() {
        var graph = new CitizenRouteSavedData(); var near = marker(0, RouteMarkerType.NORMAL); var far = marker(1000, RouteMarkerType.CROSSING);
        graph.put(near); graph.put(far); long revision = graph.viewRevision(near.pos(), 64);
        graph.put(far.withRegion(4, 2)); assertEquals(revision, graph.viewRevision(near.pos(), 64));
        graph.link(near.id(), far.id(), false); assertTrue(graph.viewRevision(near.pos(), 64) > revision);
        revision = graph.viewRevision(near.pos(), 64);
        graph.put(graph.marker(far.id()).withRegion(5, 2)); assertTrue(graph.viewRevision(near.pos(), 64) > revision);
        revision = graph.viewRevision(near.pos(), 64);
        graph.remove(far.id()); assertTrue(graph.viewRevision(near.pos(), 64) > revision); assertTrue(graph.connected(near.id()).isEmpty());
        assertEquals(1, graph.save(new CompoundTag()).getInt("SchemaVersion"));
    }

    @Test void removedMarkerDisconnectsIndexedControlReferencesAtomically() {
        var graph = new CitizenRouteSavedData(); var source = marker(0, RouteMarkerType.CROSSING); var removed = marker(8, RouteMarkerType.NORMAL);
        source = source.withCondition(new CrossingCondition(CrossingCondition.Mode.REDSTONE, removed.pos(), "", "", "", true, 7));
        graph.put(source); graph.put(removed); graph.link(source.id(), removed.id(), false);
        long version = graph.version(); assertTrue(graph.remove(removed.id()));
        assertEquals(version + 1, graph.version()); assertTrue(graph.connected(source.id()).isEmpty());
        var condition = graph.marker(source.id()).condition();
        assertFalse(condition.configured()); assertTrue(condition.inverted()); assertEquals(7, condition.threshold());
        var restored = CitizenRouteSavedData.load(graph.save(new CompoundTag()));
        assertEquals(condition, restored.marker(source.id()).condition());
        assertEquals(1, graph.save(new CompoundTag()).getInt("SchemaVersion"));
    }

    @Test void idsEdgesIndexesVersionAndSaveRoundTripStayConsistent() {
        var graph = new CitizenRouteSavedData(); var a = marker(-17, RouteMarkerType.NORMAL); var b = marker(32, RouteMarkerType.NORMAL);
        assertTrue(graph.put(a)); assertTrue(graph.put(b)); assertTrue(graph.link(a.id(), b.id(), false));
        long version = graph.version();
        assertFalse(graph.link(b.id(), a.id(), false)); assertFalse(graph.link(a.id(), a.id(), false)); assertEquals(version, graph.version());
        assertEquals(1, graph.connected(a.id()).size()); assertEquals(1, graph.connected(b.id()).size());
        assertEquals(a, graph.nearby(new BlockPos(-16, 70, -17), 2).get(0));
        var restored = CitizenRouteSavedData.load(graph.save(new CompoundTag()));
        assertEquals(version, restored.version()); assertEquals(a, restored.at(a.pos())); assertEquals(java.util.List.copyOf(graph.edges()), java.util.List.copyOf(restored.edges()));
        assertTrue(restored.remove(a.id())); assertTrue(restored.version() > version); assertTrue(restored.connected(b.id()).isEmpty());
        assertTrue(restored.nearby(a.pos(), 4).isEmpty()); assertNull(restored.at(a.pos()));
        var replaced = RouteMarker.create(UUID.randomUUID(), a.pos(), RouteMarkerType.NORMAL);
        assertTrue(restored.put(replaced)); assertNotEquals(a.id(), restored.at(a.pos()).id());
    }

    @Test void typeChangesAutomaticallyReclassifyEveryEdgeWithoutChangingIdentity() {
        var graph = new CitizenRouteSavedData(); var a = marker(0, RouteMarkerType.CROSSING); var b = marker(8, RouteMarkerType.CROSSING); var c = marker(16, RouteMarkerType.CROSSING);
        graph.put(a); graph.put(b); graph.put(c); assertTrue(graph.link(a.id(), b.id(), false)); assertFalse(graph.link(a.id(), b.id(), true));
        assertTrue(graph.link(a.id(), c.id(), false)); assertTrue(graph.link(b.id(), c.id(), true));
        var original = java.util.List.copyOf(graph.edges());
        assertTrue(original.stream().allMatch(RouteEdge::conditional));
        long version = graph.version(); assertTrue(graph.put(a.withType(RouteMarkerType.GATEWAY).withDistrict(UUID.randomUUID())));
        assertEquals(version + 1, graph.version()); assertEquals(2, graph.connected(b.id()).size());
        assertTrue(graph.connected(a.id()).stream().noneMatch(RouteEdge::conditional));
        assertTrue(graph.connected(b.id()).stream().filter(edge -> edge.other(b.id()).equals(c.id())).allMatch(RouteEdge::conditional));
        assertEquals(a.id(), graph.at(a.pos()).id());
        var district = graph.marker(a.id()).districtId();
        assertTrue(graph.put(graph.marker(a.id()).withType(RouteMarkerType.CROSSING)));
        assertEquals(district, graph.marker(a.id()).districtId()); assertFalse(graph.marker(a.id()).condition().configured());
        assertEquals(original, java.util.List.copyOf(graph.edges()));
        assertFalse(graph.link(a.id(), b.id(), true)); assertTrue(graph.removePair(a.id())); assertTrue(graph.connected(a.id()).isEmpty());
        assertTrue(graph.remove(a.id())); assertEquals(1, graph.connected(b.id()).size());
    }

    @Test void everyMarkerTypeKeepsDistrictAcrossTypeChangesAndReload() {
        var district = UUID.randomUUID();
        for (var type : RouteMarkerType.values()) {
            var node = marker(0, type).withDistrict(district);
            assertEquals(district, RouteMarker.load(node.save()).districtId());
            for (var next : RouteMarkerType.values()) {
                var changed = node.withType(next).withRegion(4, 2);
                assertEquals(district, changed.districtId());
                assertEquals(changed, RouteMarker.load(changed.save()));
            }
        }
    }

    @Test void gatewayCandidateCacheTracksTypeDistrictRegionDeletionAndReload() {
        var graph = new CitizenRouteSavedData(); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        var node = marker(0, RouteMarkerType.NORMAL).withDistrict(a); graph.put(node);
        assertTrue(graph.gateways(a).isEmpty());
        node = node.withType(RouteMarkerType.GATEWAY); graph.put(node);
        var cached = graph.gateways(a); assertSame(cached, graph.gateways(a)); assertEquals(java.util.List.of(node), cached);
        node = node.withRegion(8, 3); graph.put(node);
        assertNotSame(cached, graph.gateways(a)); assertEquals(node, graph.gateways(a).get(0));
        graph.put(node.withDistrict(b)); assertTrue(graph.gateways(a).isEmpty()); assertEquals(1, graph.gateways(b).size());
        graph = CitizenRouteSavedData.load(graph.save(new CompoundTag())); assertEquals(1, graph.gateways(b).size());
        graph.put(graph.marker(node.id()).withType(RouteMarkerType.CROSSING)); assertTrue(graph.gateways(b).isEmpty());
        graph.put(graph.marker(node.id()).withType(RouteMarkerType.GATEWAY)); assertEquals(1, graph.gateways(b).size());
        graph.remove(node.id()); assertTrue(graph.gateways(b).isEmpty());
    }

    @Test void crossingIsDerivedForEveryEndpointTypeCombinationAndEitherLinkOrder() {
        for (var first : RouteMarkerType.values()) for (var second : RouteMarkerType.values()) {
            var graph = new CitizenRouteSavedData(); var a = marker(0, first); var b = marker(8, second);
            graph.put(a); graph.put(b);
            assertTrue(graph.link(b.id(), a.id(), false));
            assertEquals(first == RouteMarkerType.CROSSING && second == RouteMarkerType.CROSSING, graph.connected(a.id()).get(0).conditional());
            long version = graph.version();
            assertFalse(graph.link(a.id(), b.id(), false)); assertFalse(graph.link(a.id(), b.id(), true));
            assertEquals(version, graph.version());
        }
    }

    @Test void legacyNormalAndPairDuplicatesMigrateToOneAutomaticallyClassifiedEdge() {
        var graph = new CitizenRouteSavedData(); var a = marker(0, RouteMarkerType.CROSSING); var b = marker(8, RouteMarkerType.CROSSING);
        graph.put(a); graph.put(b); graph.link(a.id(), b.id(), false);
        var id = graph.edges().iterator().next().id(); var saved = graph.save(new CompoundTag());
        var entries = saved.getList("Edges", 10);
        entries.getCompound(0).putBoolean("Conditional", false);
        entries.add(new RouteEdge(UUID.randomUUID(), b.id(), a.id(), true).save());
        var restored = CitizenRouteSavedData.load(saved);
        assertEquals(1, restored.edges().size()); assertEquals(id, restored.connected(a.id()).get(0).id());
        assertTrue(restored.connected(b.id()).get(0).conditional());
        assertEquals(graph.version() + 1, restored.version()); assertTrue(restored.isDirty());
        var secondLoad = CitizenRouteSavedData.load(restored.save(new CompoundTag()));
        assertEquals(restored.version(), secondLoad.version()); assertFalse(secondLoad.isDirty());
    }

    @Test void regionUnitsAndAllNumericBoundariesAreStrict() {
        var marker = marker(0, RouteMarkerType.NORMAL).withRegion(2, 1);
        assertTrue(marker.contains(marker.center().add(2, 1, 0))); assertFalse(marker.contains(marker.center().add(2.001, 0, 0)));
        assertFalse(marker.contains(marker.center().add(0, 1.001, 0))); assertTrue(marker.contains(marker.center().add(0, -1, 0)));
        assertTrue(RouteSettings.validRegion(0.5, 0)); assertTrue(RouteSettings.validRegion(16, 8));
        for (double radius : new double[]{0.49, 16.01, Double.NaN, Double.POSITIVE_INFINITY}) assertFalse(RouteSettings.validRegion(radius, 1));
        for (double tolerance : new double[]{-0.01, 8.01, Double.NaN, Double.NEGATIVE_INFINITY}) assertFalse(RouteSettings.validRegion(2, tolerance));
        assertEquals(marker, RouteMarker.load(marker.save()));
    }

    @Test void malformedSavedEdgesAreRepairedWithoutOrphanReferences() {
        var graph = new CitizenRouteSavedData(); var a = marker(0, RouteMarkerType.NORMAL); var b = marker(8, RouteMarkerType.NORMAL);
        graph.put(a); graph.put(b); graph.link(a.id(), b.id(), false); var saved = graph.save(new CompoundTag());
        var edges = saved.getList("Edges", 10); edges.add(new RouteEdge(UUID.randomUUID(), a.id(), UUID.randomUUID(), false).save());
        edges.add(new RouteEdge(UUID.randomUUID(), b.id(), a.id(), false).save());
        var restored = CitizenRouteSavedData.load(saved);
        assertEquals(1, restored.edges().size()); assertEquals(graph.version() + 1, restored.version()); assertTrue(restored.isDirty());
    }

    @Test void temporaryBlocksNeedMultipleCitizensExpireAndInvalidateByVersion() {
        var runtime = new EdgeRuntimeState(); UUID edge = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        runtime.failed(a, edge, 10, 1); assertTrue(runtime.blocked(a, edge, 10)); assertFalse(runtime.blocked(d, edge, 10));
        runtime.failed(a, edge, 20, 1); runtime.failed(b, edge, 20, 1); assertFalse(runtime.blocked(d, edge, 20));
        runtime.failed(c, edge, 30, 1); assertTrue(runtime.blocked(d, edge, 30));
        runtime.update(1, 231); assertFalse(runtime.blocked(a, edge, 231)); assertFalse(runtime.blocked(d, edge, 231));
        runtime.failed(a, edge, 250, 1); runtime.update(2, 251); assertFalse(runtime.blocked(a, edge, 251));
        runtime.failed(a, edge, 300, 2); runtime.succeeded(a, edge); assertFalse(runtime.blocked(a, edge, 300));
    }

    @Test void localRevisionsIgnoreOtherCitizensButSharedFailuresAndExpiryInvalidateThem() {
        var runtime = new EdgeRuntimeState(); var a = UUID.randomUUID(); var b = UUID.randomUUID(); var c = UUID.randomUUID(); var edge = UUID.randomUUID();
        runtime.update(1, 0); long original = runtime.revision(a), view = runtime.viewRevision();
        runtime.blockLocal(b, edge, 1); assertEquals(original, runtime.revision(a)); assertEquals(view, runtime.viewRevision());
        runtime.blockLocal(a, edge, 1); assertNotEquals(original, runtime.revision(a)); long blocked = runtime.revision(a);
        runtime.update(1, 202); assertNotEquals(blocked, runtime.revision(a)); assertFalse(runtime.blocked(a, edge, 202));
        runtime.failed(b, edge, 210, 1); runtime.failed(c, edge, 210, 1); assertEquals(original, runtime.revision(a));
        runtime.failed(UUID.randomUUID(), edge, 210, 1); assertNotEquals(original, runtime.revision(a)); assertTrue(runtime.blocked(a, edge, 210));
        long shared = runtime.revision(a); runtime.succeeded(b, edge); assertNotEquals(shared, runtime.revision(a)); assertFalse(runtime.blocked(a, edge, 211));
        runtime.clear(); long cleanView = runtime.viewRevision(); runtime.update(2, 212); assertEquals(cleanView, runtime.viewRevision());
    }

    @Test void legacyLowGraphVersionsStillInvalidateRegionalViewsAfterLoading() {
        var graph = new CitizenRouteSavedData(); var a = marker(0, RouteMarkerType.NORMAL); var b = marker(8, RouteMarkerType.NORMAL);
        graph.put(a); graph.put(b); var saved = graph.save(new CompoundTag()); saved.putLong("RouteGraphVersion", 0);
        var restored = CitizenRouteSavedData.load(saved); long previous = restored.viewRevision(a.pos(), 64);
        restored.put(a.withRegion(4, 2)); assertTrue(restored.viewRevision(a.pos(), 64) > previous);
        assertEquals(1, restored.save(new CompoundTag()).getInt("SchemaVersion"));
    }
}
