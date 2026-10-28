package com.rexhub.createapparel.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class SelectionMenu {
    static final int SELECTED_SLOT_COLOR = 0xFFFFFF80;
    private static final int CELL = 20;
    private final Consumer<Component> narrator;
    private List<Component> labels = List.of();
    private List<Integer> colors = List.of();
    private IntConsumer selection = index -> { };
    private int x;
    private int y;
    private int width;
    private int rows;
    private int columns;
    private int firstRow;
    private int highlighted;
    private int selected;
    private boolean open;
    private boolean consumedClick;

    public SelectionMenu() {
        this(label -> Minecraft.getInstance().getNarrator().sayNow(label));
    }

    SelectionMenu(Consumer<Component> narrator) {
        this.narrator = narrator;
    }

    public void open(Button anchor, int screenWidth, int screenHeight, List<Component> options,
              List<Integer> swatches, int current, IntConsumer onSelect) {
        if (options.isEmpty()) return;
        labels = List.copyOf(options);
        colors = List.copyOf(swatches);
        selection = onSelect;
        columns = colors.isEmpty() ? 1 : colors.size() == 9 ? 3 : 4;
        width = Math.min(screenWidth - 4, colors.isEmpty() ? anchor.getWidth() : columns * CELL + 4);
        int below = screenHeight - anchor.getY() - anchor.getHeight() - 2;
        int above = anchor.getY() - 2;
        rows = Math.max(1, Math.min(Math.min(8, (labels.size() + columns - 1) / columns),
                (Math.max(above, below) - 4) / CELL));
        int height = rows * CELL + 4;
        x = Mth.clamp(anchor.getX(), 2, Math.max(2, screenWidth - width - 2));
        y = below >= height ? anchor.getY() + anchor.getHeight()
                : Math.max(2, anchor.getY() - height);
        highlighted = selected = Mth.clamp(current, 0, labels.size() - 1);
        firstRow = Mth.clamp(highlighted / columns - rows + 1, 0, maxFirstRow());
        open = true;
        announce();
    }

    public boolean isOpen() { return open; }

    public void close() { open = false; }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        consumedClick = true;
        int index = indexAt(mouseX, mouseY);
        if (button == 0 && index >= 0) {
            open = false;
            selection.accept(index);
        } else if (index < 0) {
            close();
        }
        return true;
    }

    public boolean mouseReleased() {
        boolean consumed = consumedClick || open;
        consumedClick = false;
        return consumed;
    }

    public boolean mouseScrolled(double delta) {
        if (!open) return false;
        if (delta != 0) firstRow = Mth.clamp(firstRow + (delta > 0 ? -1 : 1), 0, maxFirstRow());
        return true;
    }

    public boolean keyPressed(int key) {
        if (!open) return false;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> close();
            case GLFW.GLFW_KEY_TAB -> {
                close();
                return false;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                close();
                selection.accept(highlighted);
            }
            case GLFW.GLFW_KEY_UP -> move(-columns);
            case GLFW.GLFW_KEY_DOWN -> move(columns);
            case GLFW.GLFW_KEY_LEFT -> move(-1);
            case GLFW.GLFW_KEY_RIGHT -> move(1);
            case GLFW.GLFW_KEY_HOME -> move(-labels.size());
            case GLFW.GLFW_KEY_END -> move(labels.size());
            case GLFW.GLFW_KEY_PAGE_UP -> move(-rows * columns);
            case GLFW.GLFW_KEY_PAGE_DOWN -> move(rows * columns);
            default -> { }
        }
        return true;
    }

    private void move(int offset) {
        highlighted = Mth.clamp(highlighted + offset, 0, labels.size() - 1);
        firstRow = Mth.clamp(firstRow, Math.max(0, highlighted / columns - rows + 1), highlighted / columns);
        announce();
    }

    private void announce() {
        narrator.accept(labels.get(highlighted));
    }

    private int maxFirstRow() { return Math.max(0, (labels.size() + columns - 1) / columns - rows); }

    private int indexAt(double mouseX, double mouseY) {
        if (mouseX < x + 2 || mouseX >= x + width - 2 || mouseY < y + 2 || mouseY >= y + 2 + rows * CELL) return -1;
        int column = colors.isEmpty() ? 0 : (int) (mouseX - x - 2) / CELL;
        int index = (firstRow + (int) (mouseY - y - 2) / CELL) * columns + column;
        return index < labels.size() ? index : -1;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!open) return;
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        graphics.fill(x, y, x + width, y + rows * CELL + 4, 0xFFF0F0F0);
        graphics.fill(x + 1, y + 1, x + width - 1, y + rows * CELL + 3, 0xFF202020);
        int hovered = indexAt(mouseX, mouseY);
        for (int cell = 0; cell < rows * columns; cell++) {
            int index = firstRow * columns + cell;
            if (index >= labels.size()) break;
            int cx = x + 2 + cell % columns * CELL;
            int cy = y + 2 + cell / columns * CELL;
            int cellWidth = colors.isEmpty() ? width - 4 : CELL;
            boolean focus = index == hovered || hovered < 0 && index == highlighted;
            graphics.fill(cx, cy, cx + cellWidth, cy + CELL, focus ? 0xFF606060 : 0xFF303030);
            if (colors.isEmpty()) {
                int available = cellWidth - 16;
                Component label = labels.get(index);
                String text = font.width(label) > available
                        ? font.plainSubstrByWidth(label.getString(), Math.max(0, available - font.width("…"))) + "…"
                        : label.getString();
                graphics.drawString(font, text, cx + 4, cy + 6, 0xFFFFFF, false);
                if (index == selected) graphics.drawString(font, "*", cx + cellWidth - 10, cy + 6, 0xFFFFFF, false);
            } else {
                graphics.fill(cx + 2, cy + 2, cx + CELL - 2, cy + CELL - 2, index == selected ? SELECTED_SLOT_COLOR : 0xFF111111);
                graphics.fill(cx + 4, cy + 4, cx + CELL - 4, cy + CELL - 4, 0xFF000000 | colors.get(index));
            }
        }
        if (maxFirstRow() > 0) {
            int track = rows * CELL;
            int thumb = Math.max(4, track * rows / ((labels.size() + columns - 1) / columns));
            int top = y + 2 + (track - thumb) * firstRow / maxFirstRow();
            graphics.fill(x + width - 2, top, x + width, top + thumb, 0xFFAAAAAA);
        }
        if (hovered >= 0) graphics.renderTooltip(font, labels.get(hovered), mouseX, mouseY);
        graphics.pose().popPose();
    }
}
