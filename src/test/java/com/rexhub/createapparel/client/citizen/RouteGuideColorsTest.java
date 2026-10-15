package com.rexhub.createapparel.client.citizen;

import com.rexhub.createapparel.citizen.route.RouteMarkerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouteGuideColorsTest {
    @Test void inactiveCrossingsHaveGrayFramesAndAllOtherNodesHaveWhiteFrames() {
        for (var type : RouteMarkerType.values()) {
            assertEquals(type == RouteMarkerType.CROSSING ? 0xD9444444 : 0xD9FFFFFF, RouteGuideColors.nodeFrame(type, false));
            assertEquals(0xD9FFFFFF, RouteGuideColors.nodeFrame(type, true));
        }
    }

    @Test void fillsKeepExistingTypeColorsAndSelectionHighlight() {
        assertEquals(0x6B33CCFF, RouteGuideColors.nodeFill(RouteMarkerType.NORMAL, false));
        assertEquals(0xB8CC79A7, RouteGuideColors.nodeFill(RouteMarkerType.GATEWAY, false));
        assertEquals(0x6BFFCC1A, RouteGuideColors.nodeFill(RouteMarkerType.CROSSING, false));
        for (var type : RouteMarkerType.values()) assertEquals(0x6BFFFFFF, RouteGuideColors.nodeFill(type, true));
    }

    @Test void openCrossingsKeepTheirYellowAndClosedCrossingsUseMutedYellow() {
        assertEquals(0x6BFFCC1A, RouteGuideColors.routeEdge(true, true));
        assertEquals(0x6BA5A07A, RouteGuideColors.routeEdge(true, false));
        assertEquals(RouteGuideColors.routeEdge(false, true), RouteGuideColors.routeEdge(false, false));
    }

    @Test void poweredControlWiresTurnRedWithoutChangingTheirTransparency() {
        assertEquals(0x6BFFFFFF, RouteGuideColors.controlEdge(false));
        assertEquals(0x6BFF3333, RouteGuideColors.controlEdge(true));
    }
}
