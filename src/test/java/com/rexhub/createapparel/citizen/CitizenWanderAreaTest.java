package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.population.*;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CitizenWanderAreaTest {
    @BeforeAll static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }
    private static CitizenBehaviorState behavior(int radius) {
        return CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, BlockPos.ZERO, Level.OVERWORLD, radius);
    }
    private static CitizenGauge district(CitizenPopulationSavedData data, int id, int x, int z, int radius) {
        var gauge = new CitizenGauge(new UUID(1, id), new BlockPos(id, 0, 0), new UUID(0, id), new BlockPos(x, 0, z), radius, 0, 0, false);
        data.put(gauge); return gauge;
    }

    @Test void unlimitedHasNoAnchorLimitAndFiniteRadiiKeepTheirCircle() {
        var data = new CitizenPopulationSavedData();
        var free = new CitizenWanderArea(behavior(0), CitizenPopulationState.DEFAULT, data, new BlockPos(100, 70, 100));
        assertTrue(free.allowsDestination(new BlockPos(10000, -60, -10000)));
        var finite = new CitizenWanderArea(behavior(4), CitizenPopulationState.DEFAULT, data, BlockPos.ZERO);
        assertTrue(finite.allowsPath(new BlockPos(4, 500, 0)));
        assertFalse(finite.allowsPath(new BlockPos(4, 0, 1)));
    }

    @Test void assignedCitizensStayInOwnedBlocksEvenWhenDistrictSquaresOverlap() {
        var data = new CitizenPopulationSavedData(); var home = district(data, 1, 0, 0, 8); district(data, 2, 8, 0, 8);
        var member = CitizenPopulationState.DEFAULT.district(home.districtId(), 0);
        var area = new CitizenWanderArea(behavior(0), member, data, BlockPos.ZERO);
        assertTrue(area.allowsPath(new BlockPos(4, 70, 0)));
        assertFalse(area.allowsPath(new BlockPos(5, 70, 0)));
        assertFalse(area.allowsPath(new BlockPos(-9, 70, 0)));
        var random = RandomSource.create(912);
        int allowed = 0;
        for (int index = 0; index < 200; index++) {
            var point = area.candidate(random, index % 8);
            if (area.allowsDestination(point)) {
                allowed++; assertEquals(home.districtId(), data.territory().districtAt(point));
            }
        }
        assertTrue(allowed > 50);
    }

    @Test void displacedMembersApproachHomeAndDoNotKeepDriftingAway() {
        var data = new CitizenPopulationSavedData(); var home = district(data, 1, 0, 0, 4);
        var area = new CitizenWanderArea(behavior(0), CitizenPopulationState.DEFAULT.district(home.districtId(), 0), data, new BlockPos(30, 70, 0));
        var first = area.candidate(RandomSource.create(1), 0);
        assertEquals(18, first.getX()); assertTrue(area.allowsDestination(first));
        assertFalse(area.allowsDestination(new BlockPos(31, 70, 0)));
        assertFalse(area.allowsPath(new BlockPos(50, 70, 0)));
    }

    @Test void unassignedCitizensSeekNearbySquareEdgesRatherThanDistantCenters() {
        var data = new CitizenPopulationSavedData(); district(data, 1, 100, 0, 80); district(data, 2, -100, 0, 2);
        var area = new CitizenWanderArea(behavior(0), CitizenPopulationState.DEFAULT, data, BlockPos.ZERO);
        var first = area.candidate(RandomSource.create(1), 0);
        assertEquals(12, first.getX()); assertTrue(area.allowsDestination(first));
        assertFalse(area.allowsDestination(new BlockPos(-1, 0, 0)));
        var distant = new CitizenPopulationSavedData(); district(distant, 3, 35, 0, 1);
        var free = new CitizenWanderArea(behavior(0), CitizenPopulationState.DEFAULT, distant, BlockPos.ZERO);
        assertTrue(free.allowsDestination(new BlockPos(-1000, 0, 0)));
    }

    @Test void independentCitizensIgnoreNearbyDistricts() {
        var data = new CitizenPopulationSavedData(); district(data, 1, 8, 0, 3);
        var independent = new CitizenWanderArea(behavior(0), CitizenPopulationState.DEFAULT.flags(true, false), data, BlockPos.ZERO);
        var free = new CitizenWanderArea(behavior(0), CitizenPopulationState.DEFAULT, new CitizenPopulationSavedData(), BlockPos.ZERO);
        var first = RandomSource.create(712); var second = RandomSource.create(712);
        for (int index = 0; index < 30; index++) assertEquals(free.candidate(first, index), independent.candidate(second, index));
        assertTrue(independent.allowsDestination(new BlockPos(-1000, 0, 0)));
    }

    @Test void queuedAreasExpireAfterMembershipOrGeometryChangesButNotNameChanges() {
        var data = new CitizenPopulationSavedData(); var home = district(data, 1, 0, 0, 8);
        var state = behavior(0); var population = CitizenPopulationState.DEFAULT.district(home.districtId(), 0);
        var area = new CitizenWanderArea(state, population, data, BlockPos.ZERO);
        assertTrue(area.valid(state, population));
        data.put(home.settings(8, 2, "New Name")); assertTrue(area.valid(state, population));
        assertFalse(area.valid(state, CitizenPopulationState.DEFAULT));
        assertFalse(area.valid(state.withRadius(1), population));
        data.put(home.settings(7, 2)); assertFalse(area.valid(state, population));
    }
}
