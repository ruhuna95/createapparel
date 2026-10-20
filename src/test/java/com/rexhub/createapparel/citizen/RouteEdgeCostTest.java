package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.route.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RouteEdgeCostTest {
    private static RouteMarker marker(int x) { return RouteMarker.create(UUID.randomUUID(), new BlockPos(x, 1, 0), RouteMarkerType.NORMAL); }
    @Test void onlyEdgesBetweenTwoDifferentDistrictsHaveTheSurcharge() {
        var home = UUID.randomUUID(); var a = marker(0).withDistrict(home); var b = marker(6).withDistrict(home);
        assertEquals(6, RouteEdgeCost.distance(a, b));
        assertEquals(9, RouteEdgeCost.distance(a, b.withDistrict(UUID.randomUUID())));
        assertEquals(6, RouteEdgeCost.distance(a, b.withDistrict(null)));
        assertEquals(6, RouteEdgeCost.distance(a.withDistrict(null), b.withDistrict(null)));
        assertEquals(9, RouteEdgeCost.distance(b.withDistrict(UUID.randomUUID()), a));
    }
    @Test void randomWalkingUsesTheInverseDistrictCostWithoutBlockingTravel() {
        var home = UUID.randomUUID(); var current = marker(0).withDistrict(home);
        var local = marker(4).withDistrict(home); var other = marker(-4).withDistrict(UUID.randomUUID());
        var localEdge = new RouteEdge(UUID.randomUUID(), current.id(), local.id(), false);
        var otherEdge = new RouteEdge(UUID.randomUUID(), current.id(), other.id(), false);
        var markers = Map.of(local.id(), local, other.id(), other); int localCount = 0;
        for (int index = 0; index < 1000; index++) if (RouteEdgeCost.choose(List.of(localEdge, otherEdge), null, current, markers::get, (index + .5) / 1000) == localEdge) localCount++;
        assertEquals(600, localCount);
        assertSame(otherEdge, RouteEdgeCost.choose(List.of(otherEdge), null, current, markers::get, .5));
    }

    @Test void leavingHomeAddsSelectionCostButDoesNotChangePlannedDistance() {
        var home = UUID.randomUUID(); var current = marker(0).withDistrict(home);
        var local = marker(4).withDistrict(home); var other = marker(-4).withDistrict(UUID.randomUUID());
        assertEquals(1, RouteEdgeCost.randomDestinationMultiplier(home, current, local));
        assertEquals(2.25, RouteEdgeCost.randomDestinationMultiplier(home, current, other));
        assertEquals(6, RouteEdgeCost.distance(current, other));
        var localEdge = new RouteEdge(UUID.randomUUID(), current.id(), local.id(), false);
        var otherEdge = new RouteEdge(UUID.randomUUID(), current.id(), other.id(), false);
        var markers = Map.of(local.id(), local, other.id(), other); int localCount = 0;
        for (int index = 0; index < 1300; index++)
            if (RouteEdgeCost.choose(List.of(localEdge, otherEdge), home, current, markers::get, (index + .5) / 1300) == localEdge) localCount++;
        assertEquals(900, localCount);
        assertSame(otherEdge, RouteEdgeCost.choose(List.of(otherEdge), home, current, markers::get, .999));
    }

    @Test void returningHomeAndTravelOutsideHomeHaveNoAdditionalSelectionCost() {
        var home = UUID.randomUUID(); var foreign = UUID.randomUUID(); var another = UUID.randomUUID();
        assertEquals(1, RouteEdgeCost.homeDepartureMultiplier(home, home, home));
        assertEquals(1.5, RouteEdgeCost.homeDepartureMultiplier(home, home, foreign));
        assertEquals(1, RouteEdgeCost.homeDepartureMultiplier(home, foreign, home));
        assertEquals(1, RouteEdgeCost.homeDepartureMultiplier(home, foreign, another));
        assertEquals(1, RouteEdgeCost.homeDepartureMultiplier(null, home, foreign));
        assertEquals(1, RouteEdgeCost.homeDepartureMultiplier(home, null, foreign));
        assertEquals(1, RouteEdgeCost.homeDepartureMultiplier(home, home, null));
        assertEquals(1.5, RouteEdgeCost.randomDestinationMultiplier(home, marker(0).withDistrict(foreign), marker(4).withDistrict(home)));
        assertEquals(1.5, RouteEdgeCost.randomDestinationMultiplier(null, marker(0).withDistrict(home), marker(4).withDistrict(foreign)));
    }

    @Test void gatewayDestinationCostIsExactlyThreeRegardlessOfDistrictSurcharges() {
        var home = UUID.randomUUID(); var foreign = UUID.randomUUID();
        for (var current : List.of(marker(0), marker(0).withDistrict(home), marker(0).withDistrict(foreign)))
            for (var target : List.of(marker(4), marker(4).withDistrict(home), marker(4).withDistrict(foreign))) {
                var gateway = target.withType(RouteMarkerType.GATEWAY);
                assertEquals(3, RouteEdgeCost.randomDestinationMultiplier(home, current, gateway));
                assertEquals(3, RouteEdgeCost.randomDestinationMultiplier(null, current, gateway));
                assertEquals(RouteEdgeCost.distance(current, target), RouteEdgeCost.distance(current, gateway));
            }
        assertEquals(1, RouteEdgeCost.randomDestinationMultiplier(home, marker(0).withDistrict(home).withType(RouteMarkerType.GATEWAY), marker(4).withDistrict(home)));
    }

    @Test void gatewayDestinationsHaveOneThirdTheWeightAndRemainReachableAlone() {
        var home = UUID.randomUUID(); var current = marker(0).withDistrict(home);
        var normal = marker(4).withDistrict(home); var gateway = marker(-4).withDistrict(UUID.randomUUID()).withType(RouteMarkerType.GATEWAY);
        var normalEdge = new RouteEdge(UUID.randomUUID(), current.id(), normal.id(), false);
        var gatewayEdge = new RouteEdge(UUID.randomUUID(), current.id(), gateway.id(), false);
        var markers = Map.of(normal.id(), normal, gateway.id(), gateway);
        for (var edges : List.of(List.of(normalEdge, gatewayEdge), List.of(gatewayEdge, normalEdge))) {
            int normalCount = 0;
            for (int index = 0; index < 1200; index++)
                if (RouteEdgeCost.choose(edges, home, current, markers::get, (index + .5) / 1200) == normalEdge) normalCount++;
            assertEquals(900, normalCount);
        }
        assertSame(gatewayEdge, RouteEdgeCost.choose(List.of(gatewayEdge), home, current, markers::get, 0));
        assertSame(gatewayEdge, RouteEdgeCost.choose(List.of(gatewayEdge), home, current, markers::get, .999));
    }

    @Test void gatewayCandidatesAreUniformEvenWhenTheirDistrictsDiffer() {
        var home = UUID.randomUUID(); var current = marker(0).withDistrict(home);
        var local = marker(4).withDistrict(home).withType(RouteMarkerType.GATEWAY);
        var foreign = marker(-4).withDistrict(UUID.randomUUID()).withType(RouteMarkerType.GATEWAY);
        var localEdge = new RouteEdge(UUID.randomUUID(), current.id(), local.id(), false);
        var foreignEdge = new RouteEdge(UUID.randomUUID(), current.id(), foreign.id(), false);
        var markers = Map.of(local.id(), local, foreign.id(), foreign); int localCount = 0;
        for (int index = 0; index < 1000; index++)
            if (RouteEdgeCost.choose(List.of(localEdge, foreignEdge), home, current, markers::get, (index + .5) / 1000) == localEdge) localCount++;
        assertEquals(500, localCount);
    }
}
