package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.route.SmartPhoneRouteEditor;
import org.junit.jupiter.api.Test;
import static com.rexhub.createapparel.citizen.route.SmartPhoneRouteEditor.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class RouteInteractionDecisionTest {
    @Test void clientAndServerUseTheSamePrecedenceForEveryCombination() {
        for (int bits = 0; bits < 64; bits++) {
            boolean marker = (bits & 1) != 0, editing = (bits & 2) != 0, sneak = (bits & 4) != 0;
            boolean linking = (bits & 8) != 0, edge = (bits & 16) != 0, placement = (bits & 32) != 0;
            var expected = marker ? MARKER : !editing ? NONE : !sneak ? linking ? CONTROL : NONE
                    : edge ? DELETE_EDGE : placement ? PLACE : NONE;
            assertEquals(expected, SmartPhoneRouteEditor.action(marker, editing, sneak, linking, edge, placement));
        }
    }

    @Test void edgeDeletionDoesNotDependOnMarkerPlacementAndNeverOverridesMarkerMenus() {
        assertEquals(DELETE_EDGE, SmartPhoneRouteEditor.action(false, true, true, true, true, false));
        assertEquals(MARKER, SmartPhoneRouteEditor.action(true, true, true, true, true, false));
        assertEquals(NONE, SmartPhoneRouteEditor.action(false, true, true, true, false, false));
        assertEquals(NONE, SmartPhoneRouteEditor.action(false, false, true, true, true, true));
    }
}
