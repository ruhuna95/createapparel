package com.rexhub.createapparel.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SelectionMenuTest {
    private final SelectionMenu menu = new SelectionMenu(label -> { });
    private final AtomicInteger selected = new AtomicInteger(-1);

    @Test
    void tabClosesMenuAndFallsThroughForFocusTraversal() {
        open(20, 25, List.of(), 0);
        assertFalse(menu.keyPressed(GLFW.GLFW_KEY_TAB));
        assertFalse(menu.isOpen());
        assertEquals(-1, selected.get());
    }

    @Test
    void scrollingChangesVisibleChoicesAndConsumesTheSelectionClick() {
        open(20, 25, List.of(), 0);
        assertTrue(menu.mouseScrolled(-1));
        assertTrue(menu.mouseScrolled(-1));
        assertTrue(menu.mouseClicked(20, 50, 0));
        assertEquals(2, selected.get());
        assertFalse(menu.isOpen());
        assertTrue(menu.mouseReleased());
        assertFalse(menu.mouseReleased());
    }

    @Test
    void keyboardCanReachTheLastChoiceBeyondTheVisibleRows() {
        open(20, 25, List.of(), 0);
        assertTrue(menu.keyPressed(GLFW.GLFW_KEY_END));
        assertTrue(menu.keyPressed(GLFW.GLFW_KEY_ENTER));
        assertEquals(24, selected.get());
        assertFalse(menu.isOpen());
    }

    @Test
    void outsideClickAndEscapeCancelWithoutPassingThrough() {
        open(20, 25, List.of(), 0);
        assertTrue(menu.mouseClicked(300, 230, 0));
        assertTrue(menu.mouseReleased());
        assertEquals(-1, selected.get());
        assertFalse(menu.isOpen());
        open(20, 25, List.of(), 0);
        assertTrue(menu.keyPressed(GLFW.GLFW_KEY_ESCAPE));
        assertFalse(menu.keyPressed(GLFW.GLFW_KEY_ESCAPE));
        assertEquals(-1, selected.get());
    }

    @Test
    void fabricGridOpensAboveLowButtonWithoutMovingTheButton() {
        open(190, 16, IntStream.range(0, 16).boxed().toList(), 0);
        assertTrue(menu.mouseClicked(62, 178, 0));
        assertEquals(14, selected.get());
    }

    @Test
    void skinGridHasThreeColumnsAndOpensWithCurrentSelectionVisible() {
        open(20, 9, IntStream.range(0, 9).boxed().toList(), 8);
        assertTrue(menu.mouseClicked(62, 92, 0));
        assertEquals(8, selected.get());
    }

    @Test
    void additionalColorsFollowVanillaColorsAndTheLastPartialRowIsReachable() {
        var colors = IntStream.range(0, 33).boxed().toList();
        open(20, 33, colors, 15);
        menu.keyPressed(GLFW.GLFW_KEY_RIGHT);
        menu.keyPressed(GLFW.GLFW_KEY_ENTER);
        assertEquals(16, selected.get());
        open(20, 33, colors, 0);
        menu.mouseScrolled(-1);
        menu.mouseClicked(15, 187, 0);
        assertEquals(32, selected.get());
        selected.set(-1);
        open(20, 33, colors, 32);
        menu.mouseClicked(35, 187, 0);
        assertEquals(-1, selected.get());
    }

    private void open(int y, int count, List<Integer> colors, int current) {
        var anchor = Button.builder(Component.empty(), button -> { }).bounds(10, y, 120, 20).build();
        menu.open(anchor, 320, 240, IntStream.range(0, count)
                .mapToObj(index -> (Component) Component.literal("Option " + index)).toList(), colors, current, selected::set);
        assertEquals(10, anchor.getX());
        assertEquals(y, anchor.getY());
        assertEquals(120, anchor.getWidth());
    }
}
