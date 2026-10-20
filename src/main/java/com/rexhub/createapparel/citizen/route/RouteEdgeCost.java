package com.rexhub.createapparel.citizen.route;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class RouteEdgeCost {
    private RouteEdgeCost() { }

    public static double districtMultiplier(RouteMarker a, RouteMarker b) {
        return a.districtId() != null && b.districtId() != null && !a.districtId().equals(b.districtId())
                ? RouteSettings.INTER_DISTRICT_COST_MULTIPLIER : 1;
    }

    public static double distance(RouteMarker a, RouteMarker b) {
        return Math.max(0.5, a.center().distanceTo(b.center())) * districtMultiplier(a, b);
    }

    public static double homeDepartureMultiplier(@Nullable UUID home, @Nullable UUID from, @Nullable UUID to) {
        return home != null && home.equals(from) && to != null && !home.equals(to)
                ? RouteSettings.HOME_DEPARTURE_COST_MULTIPLIER : 1;
    }

    public static double randomDestinationMultiplier(@Nullable UUID home, RouteMarker current, RouteMarker target) {
        if (target.type() == RouteMarkerType.GATEWAY) return RouteSettings.GATEWAY_DESTINATION_COST_MULTIPLIER;
        return districtMultiplier(current, target) * homeDepartureMultiplier(home, current.districtId(), target.districtId());
    }

    public static RouteEdge choose(List<RouteEdge> edges, @Nullable UUID home, RouteMarker current, Function<UUID, RouteMarker> markers, double random) {
        double total = 0;
        for (var edge : edges) total += 1 / randomDestinationMultiplier(home, current, markers.apply(edge.other(current.id())));
        double remaining = random * total;
        for (var edge : edges) {
            remaining -= 1 / randomDestinationMultiplier(home, current, markers.apply(edge.other(current.id())));
            if (remaining < 0) return edge;
        }
        return edges.get(edges.size() - 1);
    }
}
