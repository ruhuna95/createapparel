package com.rexhub.createapparel.client.advertisement;

import com.rexhub.createapparel.client.screen.ColorButton;
import com.rexhub.createapparel.client.screen.SelectionMenu;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.photo.AdvertisingPhotoData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public abstract class PhotoSettingsScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private final Map<Integer, Button> controls = new LinkedHashMap<>();
    private final SelectionMenu colors = new SelectionMenu();
    private int previewX, previewY, previewWidth, previewHeight;

    protected PhotoSettingsScreen(T menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    protected abstract AdvertisingPhotoData photo();
    protected abstract void sendSetting(int action);
    protected final boolean colorMenuOpen() { return colors.isOpen(); }
    @Override protected void init() { super.init(); controls.clear(); colors.close(); previewWidth = previewHeight = 0; }
    protected final void previewBounds(int x, int y, int width, int height, int surfaceWidth, int surfaceHeight) {
        double scale = Math.min((double) width / surfaceWidth, (double) height / surfaceHeight);
        previewWidth = Math.max(1, (int) (surfaceWidth * scale)); previewHeight = Math.max(1, (int) (surfaceHeight * scale));
        previewX = x + (width - previewWidth) / 2; previewY = y + (height - previewHeight) / 2;
    }
    protected final void addPhotoControls(int x, int y, int width) {
        for (int index = 0; index < 6; index++) {
            int action = index;
            var button = index == 0 ? new ColorButton(leftPos + x, topPos + y, width, 18,
                    this::openColors, () -> photo().layout().background().tintRgb())
                    : Button.builder(Component.empty(), ignored -> sendSetting(action)).bounds(leftPos + x, topPos + y + index * 20, width, 18).build();
            controls.put(index, addRenderableWidget(button));
        }
        updateControls();
    }
    private void openColors(Button button) {
        var values = Arrays.asList(FabricColor.values());
        colors.open(button, width, height, values.stream().map(color -> (Component) Component.translatable(color.translationKey())).toList(),
                values.stream().map(FabricColor::tintRgb).toList(), photo().layout().background().ordinal(), index -> sendSetting(16 + index));
    }
    private void updateControls() {
        if (controls.isEmpty()) return;
        var layout = photo().layout();
        String[] fields = {"background", "framing", "aspect", "horizontal", "vertical", "mirror"};
        Component[] values = {Component.translatable(layout.background().translationKey()), value(layout.framing()), value(layout.aspect()),
                value(layout.horizontal()), value(layout.vertical()), Component.translatable("screen.createapparel.gauge." + (layout.mirror() ? "on" : "off"))};
        controls.forEach((index, button) -> button.setMessage(Component.translatable("screen.createapparel.photo." + fields[index], values[index])));
    }
    private static Component value(Enum<?> value) { return Component.translatable("screen.createapparel.photo.value." + value.name().toLowerCase(Locale.ROOT)); }
    @Override protected void containerTick() { super.containerTick(); updateControls(); }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) { }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.fill(leftPos, topPos, leftPos + imageWidth - 1, topPos + 2, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight - 1, 0xFFFFFFFF);
        graphics.fill(leftPos + 1, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
        graphics.fill(leftPos + imageWidth - 2, topPos + 1, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
        if (previewWidth == 0) return;
        graphics.fill(leftPos + previewX - 1, topPos + previewY - 1, leftPos + previewX + previewWidth + 1, topPos + previewY + previewHeight + 1, 0xFF555555);
        graphics.flush();
        var image = PhotoRenderCache.image(photo(), previewWidth, previewHeight);
        if (image != null) graphics.blit(image.texture(), leftPos + previewX, topPos + previewY, previewWidth, previewHeight,
                0, image.height(), image.width(), -image.height(), image.width(), image.height());
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick); super.render(graphics, mouseX, mouseY, partialTick);
        if (!colors.isOpen()) renderTooltip(graphics, mouseX, mouseY);
        colors.render(graphics, mouseX, mouseY);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontalScroll, double delta) {
        if (colors.mouseScrolled(delta)) return true;
        if (delta != 0) for (var entry : controls.entrySet()) if (entry.getValue().isMouseOver(x, y)) {
            sendSetting(entry.getKey() + (delta > 0 ? 64 : 0)); return true;
        }
        return super.mouseScrolled(x, y, horizontalScroll, delta);
    }
    @Override public boolean mouseClicked(double x, double y, int button) { return colors.mouseClicked(x, y, button) || super.mouseClicked(x, y, button); }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (colors.mouseReleased()) { setDragging(false); return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return colors.isOpen() || super.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean keyPressed(int key, int code, int modifiers) { return colors.keyPressed(key) || super.keyPressed(key, code, modifiers); }
}
