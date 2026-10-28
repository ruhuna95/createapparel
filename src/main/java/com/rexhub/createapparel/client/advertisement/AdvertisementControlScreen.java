package com.rexhub.createapparel.client.advertisement;

import com.rexhub.createapparel.advertisement.AdvertisementControlMenu;
import com.rexhub.createapparel.advertisement.AdvertisementNetwork;
import com.rexhub.createapparel.photo.AdvertisingPhotoData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class AdvertisementControlScreen extends PhotoSettingsScreen<AdvertisementControlMenu> {
    private EditBox channel;
    private Button scene;
    private int selectedScene;

    public AdvertisementControlScreen(AdvertisementControlMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.kind() == AdvertisementControlMenu.Kind.BROADCASTER ? 232 : 320;
        imageHeight = menu.kind() == AdvertisementControlMenu.Kind.BROADCASTER ? 216 : menu.kind() == AdvertisementControlMenu.Kind.SIGNAGE ? 198 : 178;
    }
    @Override protected void init() {
        super.init();
        if (menu.kind() != AdvertisementControlMenu.Kind.BILLBOARD) {
            String pendingChannel = channel == null ? menu.channel() : channel.getValue();
            channel = new EditBox(font, leftPos + 10, topPos + 9, menu.kind() == AdvertisementControlMenu.Kind.BROADCASTER ? 212 : 184, 18,
                    Component.translatable("screen.createapparel.advertisement.channel"));
            channel.setMaxLength(48); channel.setFilter(value -> value.matches("[a-z0-9_]{0,48}")); channel.setValue(pendingChannel);
            channel.setHint(Component.translatable("screen.createapparel.advertisement.channel"));
            channel.setResponder(value -> AdvertisementNetwork.control(menu.containerId, value, selectedScene, -1));
            addRenderableWidget(channel);
        }
        if (menu.kind() == AdvertisementControlMenu.Kind.BROADCASTER) return;
        boolean signage = menu.kind() == AdvertisementControlMenu.Kind.SIGNAGE;
        previewBounds(10, signage ? 36 : 8, 178, signage ? 152 : 160, menu.panelWidth(), menu.panelHeight());
        addPhotoControls(200, signage ? 42 : 12, 110);
        if (signage) scene = addRenderableWidget(Button.builder(Component.empty(), button -> selectScene(1))
                .bounds(leftPos + 202, topPos + 8, 108, 20).build());
        updateScene();
    }
    private void selectScene(int direction) { selectedScene = Math.floorMod(selectedScene + direction, menu.scenes().size()); updateScene(); }
    private void updateScene() {
        selectedScene = Math.floorMod(selectedScene, menu.scenes().size());
        if (scene != null) scene.setMessage(Component.translatable("screen.createapparel.advertisement.scene", selectedScene + 1, menu.scenes().size()));
    }
    @Override protected AdvertisingPhotoData photo() { return menu.preview(selectedScene); }
    @Override protected void sendSetting(int action) { AdvertisementNetwork.control(menu.containerId, channel == null ? menu.channel() : channel.getValue(), selectedScene, action); }
    @Override protected void containerTick() { super.containerTick(); if (channel != null) channel.tick(); updateScene(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (menu.kind() != AdvertisementControlMenu.Kind.BROADCASTER) return;
        var texture = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
        for (var slot : menu.slots) graphics.blit(texture, leftPos + slot.x - 1, topPos + slot.y - 1, 7, 17, 18, 18);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.kind() == AdvertisementControlMenu.Kind.BROADCASTER) graphics.drawString(font, playerInventoryTitle, 35, 118, 0x404040, false);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (!colorMenuOpen() && scene != null && delta != 0 && scene.isMouseOver(x, y)) { selectScene(delta > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(x, y, delta);
    }
    @Override public boolean keyPressed(int key, int code, int modifiers) {
        if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && channel != null
                && (channel.keyPressed(key, code, modifiers) || channel.canConsumeInput())) return true;
        return super.keyPressed(key, code, modifiers);
    }
}
