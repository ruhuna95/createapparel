package com.rexhub.createapparel.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ButtonValueScrollTest {
    @Test
    void hoveredButtonScrollsInBothDirectionsWithoutClickingIt() {
        var changes = new AtomicInteger();
        var scroll = new ButtonValueScroll();
        var button = button();
        scroll.bind(button, changes::addAndGet);
        assertTrue(scroll.mouseScrolled(20, 25, 1));
        assertEquals(-1, changes.get());
        assertTrue(scroll.mouseScrolled(20, 25, -1));
        assertEquals(0, changes.get());
        assertFalse(scroll.mouseScrolled(20, 25, 0));
        assertFalse(scroll.mouseScrolled(200, 25, 1));
    }

    @Test
    void rapidWheelInputEmitsUnitDeltasWithoutWaitingForSynchronizedValues() {
        List<Integer> deltas = new ArrayList<>();
        var scroll = new ButtonValueScroll();
        scroll.bind(button(), deltas::add);
        for (int event = 0; event < 3; event++) assertTrue(scroll.mouseScrolled(20, 25, -12));
        for (int event = 0; event < 2; event++) assertTrue(scroll.mouseScrolled(20, 25, 7));
        assertEquals(List.of(1, 1, 1, -1, -1), deltas);
    }

    @Test
    void inactiveHiddenAndRemovedButtonsFallThrough() {
        var scroll = new ButtonValueScroll();
        var button = button();
        scroll.bind(button, direction -> fail("Unavailable button was scrolled"));
        button.active = false;
        assertFalse(scroll.mouseScrolled(20, 25, 1));
        button.active = true;
        button.visible = false;
        assertFalse(scroll.mouseScrolled(20, 25, 1));
        button.visible = true;
        scroll.remove(button);
        assertFalse(scroll.mouseScrolled(20, 25, 1));
    }

    @Test
    void openMenuConsumesWheelBeforeHoveredButton() {
        var selection = new SelectionMenu(label -> { });
        var scroll = new ButtonValueScroll();
        var button = button();
        scroll.bind(button, direction -> fail("Menu must have priority"));
        selection.open(button, 320, 240, List.of(Component.literal("A")), List.of(), 0, index -> { });
        assertTrue(selection.mouseScrolled(1) || scroll.mouseScrolled(20, 25, 1));
    }

    private static Button button() {
        return Button.builder(Component.empty(), ignored -> fail("Wheel must not invoke click action"))
                .bounds(10, 20, 100, 20).build();
    }
}
