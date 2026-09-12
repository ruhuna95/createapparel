package com.rexhub.createapparel.client.screen;

import net.minecraft.client.gui.components.Button;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

final class ButtonValueScroll {
    private final Map<Button, IntConsumer> actions = new LinkedHashMap<>();

    void bind(Button button, IntConsumer action) { actions.put(button, action); }

    void remove(Button button) { actions.remove(button); }

    void clear() { actions.clear(); }

    boolean mouseScrolled(double x, double y, double delta) {
        if (delta == 0) return false;
        for (var entry : actions.entrySet()) {
            Button button = entry.getKey();
            if (button.visible && button.active && button.isMouseOver(x, y)) {
                entry.getValue().accept(delta > 0 ? -1 : 1);
                return true;
            }
        }
        return false;
    }
}
