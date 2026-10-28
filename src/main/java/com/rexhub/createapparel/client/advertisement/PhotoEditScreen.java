package com.rexhub.createapparel.client.advertisement;

import com.rexhub.createapparel.photo.AdvertisingPhotoData;
import com.rexhub.createapparel.photo.PhotographyMenu;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PhotoEditScreen extends PhotoSettingsScreen<PhotographyMenu> {
    public PhotoEditScreen(PhotographyMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 320; imageHeight = 180; }
    @Override protected void init() {
        super.init(); previewBounds(10, 8, 174, 160, 1, 2); addPhotoControls(196, 12, 112);
        addRenderableWidget(Button.builder(Component.translatable("screen.createapparel.photo.save"), button -> sendSetting(8))
                .bounds(leftPos + 196, topPos + 144, 112, 20).build());
    }
    @Override protected AdvertisingPhotoData photo() { return menu.preview(); }
    @Override protected void sendSetting(int action) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); }
}
