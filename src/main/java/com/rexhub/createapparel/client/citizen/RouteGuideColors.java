package com.rexhub.createapparel.client.citizen;

import com.rexhub.createapparel.citizen.route.RouteMarkerType;

final class RouteGuideColors {
    private RouteGuideColors() { }

    static int nodeFill(RouteMarkerType type, boolean selected) {
        if (selected) return 0x6BFFFFFF;
        return switch (type) {
            case NORMAL -> 0x6B33CCFF;
            case GATEWAY -> 0xB8CC79A7;
            case CROSSING -> 0x6BFFCC1A;
        };
    }

    static int nodeFrame(RouteMarkerType type, boolean on) {
        return type == RouteMarkerType.CROSSING && !on ? 0xD9444444 : 0xD9FFFFFF;
    }

    static int routeEdge(boolean conditional, boolean open) {
        return conditional ? open ? 0x6BFFCC1A : 0x6BA5A07A : 0x6B33CCFF;
    }

    static int controlEdge(boolean powered) { return powered ? 0x6BFF3333 : 0x6BFFFFFF; }
}
