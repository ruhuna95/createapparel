package com.rexhub.createapparel.client.mirror;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MirrorRenderSchedulingTest {
    @Test void centeredPanelWinsFocusOverALargerCloserSideMirror() throws ReflectiveOperationException {
        var panel = candidate("panel", 0.03, 6, 1, false, true);
        var side = candidate("side", 0.7, 1, 100, true, false);
        var select = MirrorRenderManager.class.getDeclaredMethod("focusedCandidate", List.class);
        select.setAccessible(true);
        assertSame(panel, select.invoke(null, List.of(side, panel)));
    }

    @Test void absentCenterMirrorKeepsTheAreaDistanceFocusPriority() throws ReflectiveOperationException {
        var small = candidate("small", 0.05, 5, 1, false, false);
        var large = candidate("large", 0.1, 8, 1, false, false);
        var select = MirrorRenderManager.class.getDeclaredMethod("focusedCandidate", List.class);
        select.setAccessible(true);
        assertSame(large, select.invoke(null, List.of(small, large)));
        assertNull(select.invoke(null, List.of()));
    }

    @Test void coveredFocusAndReservedBackgroundCannotBeStarvedByUncoveredSideMirrors() throws ReflectiveOperationException {
        var constructor = MirrorRenderManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var manager = constructor.newInstance();
        var focus = candidate("focus", 0.03, 4.5, 1, false, true);
        var side = candidate("side", 0.7, 1, 100, true, false);
        var background = candidate("background", 0.01, 10, 0.1, false, false);
        var order = MirrorRenderManager.class.getDeclaredMethod("prioritizeCandidates", List.class, nestedType("Candidate"));
        order.setAccessible(true);
        var candidates = new ArrayList<>(List.of(side, background, focus));
        order.invoke(manager, candidates, focus);
        assertEquals(List.of(focus, side, background), candidates);
        assertTrue(MirrorRenderPolicy.reserveBackgroundTurn(1_200_000_000L, 200_000_000L));
        order.invoke(manager, candidates, background);
        assertEquals(List.of(background, side, focus), candidates);
    }

    @Test void recursiveSelectionStillPrefersExistingChildImages() throws ReflectiveOperationException {
        var constructor = MirrorRenderManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var manager = constructor.newInstance();
        var viewConstructor = nestedType("RenderView").getDeclaredConstructor(int.class);
        viewConstructor.setAccessible(true);
        var view = viewConstructor.newInstance(1);
        var activeView = MirrorRenderManager.class.getDeclaredField("activeView");
        activeView.setAccessible(true);
        activeView.set(manager, view);
        var cached = candidate("cached", 0.01, 10, 0.1, false, false);
        var uncached = candidate("uncached", 0.7, 1, 100, true, true);
        var texturesField = view.getClass().getDeclaredField("textures");
        texturesField.setAccessible(true);
        @SuppressWarnings("unchecked") var textures = (Map<Set<?>, Object>) texturesField.get(view);
        textures.put(Set.of("cached"), new Object());
        var order = MirrorRenderManager.class.getDeclaredMethod("prioritizeCandidates", List.class, nestedType("Candidate"));
        order.setAccessible(true);
        var candidates = new ArrayList<>(List.of(uncached, cached));
        order.invoke(manager, candidates, uncached);
        assertEquals(List.of(cached, uncached), candidates);
    }

    private static Object candidate(String key, double area, double distance, double priority,
                                    boolean uncovered, boolean centered) throws ReflectiveOperationException {
        var constructor = nestedType("Candidate").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(Set.of(key), List.of(), new AABB(0, 0, 0, 1, 1, 1),
                area, distance, priority, false, uncovered, centered);
    }

    private static Class<?> nestedType(String name) {
        return Arrays.stream(MirrorRenderManager.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals(name)).findFirst().orElseThrow();
    }
}
