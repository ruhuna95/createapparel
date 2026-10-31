package com.rexhub.createapparel.client.advertisement;

import com.rexhub.createapparel.client.screen.ColorButton;
import com.rexhub.createapparel.client.screen.SelectionMenu;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.photo.PhotoRegistries;
import com.rexhub.createapparel.photo.PhotographyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.rexhub.createapparel.photo.PhotographyMenuLayout.*;

public final class PhotographyScreen extends AbstractContainerScreen<PhotographyMenu> {
    private static final ResourceLocation CONTAINER = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation CRAFTING = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private final Map<Integer, Button> controls = new LinkedHashMap<>();
    private final SelectionMenu selectionMenu = new SelectionMenu();
    private final Map<String, Button> styleButtons = new LinkedHashMap<>();
    private java.util.List<String> styleCategories = java.util.List.of();
    private java.util.List<GarmentSlot> slotSelectionOrder = java.util.List.of();
    private int observedSlot = -1;

    public PhotographyScreen(PhotographyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = menu.mode() == PhotographyMenu.Mode.CONSENT ? CONSENT_HEIGHT : HEIGHT;
        inventoryLabelX = INVENTORY_X;
        inventoryLabelY = INVENTORY_Y - 12;
    }

    @Override protected void init() {
        super.init();
        controls.clear();
        styleButtons.clear();
        styleCategories = java.util.List.of();
        observedSlot = -1;
        slotSelectionOrder = menu.slots.stream().filter(slot -> slot.index < PhotographyMenu.GARMENT_SLOTS)
                .sorted(java.util.Comparator.comparingInt((net.minecraft.world.inventory.Slot slot) -> slot.y).thenComparingInt(slot -> slot.x))
                .map(slot -> GarmentSlot.values()[slot.index]).toList();
        selectionMenu.close();
        if (menu.mode() == PhotographyMenu.Mode.CONSENT) {
            addRenderableWidget(Button.builder(Component.translatable("screen.createapparel.photo.approve"), button -> send(8))
                    .bounds(leftPos + SETTINGS_X, topPos + 130, SETTINGS_WIDTH, 20).build());
            return;
        }
        for (int index = 0; index < 8; index++) {
            if (menu.mode() == PhotographyMenu.Mode.EDIT && index >= 6) continue;
            int action = index;
            Button button = index == 0 ? new ColorButton(leftPos + SETTINGS_X, topPos + SETTINGS_Y, SETTINGS_WIDTH, BUTTON_HEIGHT,
                    this::openColors, () -> menu.preview().layout().background().tintRgb())
                    : Button.builder(Component.empty(), pressed -> send(action))
                    .bounds(leftPos + SETTINGS_X, topPos + SETTINGS_Y + index * BUTTON_STEP, SETTINGS_WIDTH, BUTTON_HEIGHT).build();
            controls.put(index, addRenderableWidget(button));
        }
        if (menu.mode() == PhotographyMenu.Mode.EDIT) {
            addRenderableWidget(Button.builder(Component.translatable("screen.createapparel.photo.save"), button -> send(8))
                    .bounds(leftPos + SETTINGS_X, topPos + TRADE_Y, SETTINGS_WIDTH, 20).build());
        }
        updateControls();
        updateStyles();
    }

    private void openColors(Button anchor) {
        var colors = Arrays.asList(FabricColor.values());
        selectionMenu.open(anchor, width, height,
                colors.stream().map(color -> (Component) Component.translatable(color.translationKey())).toList(),
                colors.stream().map(FabricColor::tintRgb).toList(), menu.setting(0), index -> send(PhotographyMenu.COLOR_ACTION + index));
    }

    private void send(int action) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    private void updateControls() {
        String[] fields = {"background", "framing", "aspect", "horizontal", "vertical", "mirror", "pose", "body_yaw"};
        var photo = menu.preview();
        Component[] values = {
                Component.translatable(photo.layout().background().translationKey()),
                value(photo.layout().framing()), value(photo.layout().aspect()), value(photo.layout().horizontal()), value(photo.layout().vertical()),
                Component.translatable("screen.createapparel.gauge." + (photo.layout().mirror() ? "on" : "off")),
                Component.translatable("pose.createapparel." + photo.pose().id()), Component.literal(photo.layout().bodyYaw() + "°")
        };
        controls.forEach((index, button) -> button.setMessage(Component.translatable("screen.createapparel.photo." + fields[index], values[index])));
    }

    private static Component value(Enum<?> value) {
        return Component.translatable("screen.createapparel.photo.value." + value.name().toLowerCase(Locale.ROOT));
    }

    private void updateStyles() {
        if (menu.mode() == PhotographyMenu.Mode.CONSENT) return;
        var slot = GarmentSlot.values()[menu.setting(7)];
        var definition = menu.loadout().definition(slot).orElse(null);
        var categories = definition == null ? java.util.List.<String>of() : java.util.List.copyOf(definition.stylesFor(slot).keySet());
        if (observedSlot != slot.ordinal() || !styleCategories.equals(categories)) {
            styleButtons.values().forEach(this::removeWidget);
            styleButtons.clear();
            styleCategories = categories;
            observedSlot = slot.ordinal();
            for (int index = 0; index < Math.min(16, categories.size()); index++) {
                int categoryIndex = index;
                var button = Button.builder(Component.empty(), pressed -> send(PhotographyMenu.STYLE_ACTION + categoryIndex))
                        .bounds(leftPos + STYLE_X, topPos + STYLE_Y + index * 20, STYLE_WIDTH, BUTTON_HEIGHT).build();
                styleButtons.put(categories.get(index), addRenderableWidget(button));
            }
        }
        if (definition == null) return;
        var allowed = menu.loadout().allowedStyles(slot);
        styleButtons.forEach((category, button) -> {
            var value = menu.loadout().styleState(slot).styles().get(category);
            var id = definition.id();
            String key = "style." + id.getNamespace() + "." + id.getPath() + "." + category + "." + value;
            button.setMessage(Component.translatable("screen.createapparel.wardrobe.style",
                    Component.translatable("style_category.createapparel." + category),
                    Component.translatable(I18n.exists(key) ? key : "style.createapparel." + category + "." + value)));
            button.active = menu.mode() == PhotographyMenu.Mode.CAPTURE && allowed.getOrDefault(category, java.util.List.of()).size() > 1;
        });
    }

    @Override protected void containerTick() { super.containerTick(); updateControls(); updateStyles(); }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.fill(leftPos, topPos, leftPos + imageWidth - 1, topPos + 2, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight - 1, 0xFFFFFFFF);
        graphics.fill(leftPos + 1, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
        graphics.fill(leftPos + imageWidth - 2, topPos + 1, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
        graphics.fill(leftPos + PREVIEW_X - 1, topPos + PREVIEW_Y - 1,
                leftPos + PREVIEW_X + PREVIEW_WIDTH + 1, topPos + PREVIEW_Y + PREVIEW_HEIGHT + 1, 0xFF555555);
        graphics.flush();
        var image = PhotoRenderCache.image(menu.preview(), PREVIEW_WIDTH, PREVIEW_HEIGHT);
        if (image != null) graphics.blit(image.texture(), leftPos + PREVIEW_X, topPos + PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT,
                0, image.height(), image.width(), -image.height(), image.width(), image.height());
        for (var slot : menu.slots) {
            if (slot.index < PhotographyMenu.GARMENT_SLOTS && slot.index == menu.setting(7))
                graphics.fill(leftPos + slot.x - 2, topPos + slot.y - 2, leftPos + slot.x + 18, topPos + slot.y + 18, 0xFFFFFF80);
            if (slot.index == PhotographyMenu.RESULT_SLOT)
                graphics.blit(CRAFTING, leftPos + slot.x - 5, topPos + slot.y - 5, 119, 30, 26, 26);
            else graphics.blit(CONTAINER, leftPos + slot.x - 1, topPos + slot.y - 1, 7, 17, 18, 18);
            if (slot.index < PhotographyMenu.GARMENT_SLOTS && !slot.hasItem()) {
                var texture = ResourceLocation.fromNamespaceAndPath("createapparel", "textures/item/empty_apparel_slot_" + GarmentSlot.values()[slot.index].getSerializedName() + ".png");
                graphics.blit(texture, leftPos + slot.x, topPos + slot.y, 0, 0, 16, 16, 16, 16);
            }
        }
        if (menu.mode() == PhotographyMenu.Mode.CAPTURE) {
            var emerald = new ItemStack(Items.EMERALD);
            graphics.renderFakeItem(emerald, leftPos + PAYMENT_X, topPos + OFFER_Y);
            graphics.renderItemDecorations(font, emerald, leftPos + PAYMENT_X, topPos + OFFER_Y,
                    menu.remainingCost() == 1 ? null : Integer.toString(menu.remainingCost()));
            graphics.renderFakeItem(new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()), leftPos + RESULT_X, topPos + OFFER_Y);
            renderTradeArrow(graphics, OFFER_Y);
            renderTradeArrow(graphics, TRADE_Y);
        }
    }

    private void renderTradeArrow(GuiGraphics graphics, int y) {
        graphics.blit(CRAFTING, leftPos + PAYMENT_X + 23, topPos + y - 1, 89, 34, 24, 17);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        fittedText(graphics, title, 8, 5, PREVIEW_X - 16);
        if (menu.mode() != PhotographyMenu.Mode.CONSENT) {
            var stack = menu.loadout().getStack(GarmentSlot.values()[menu.setting(7)]);
            fittedText(graphics, Component.translatable("screen.createapparel.wardrobe.selected_garment", Component.empty()), STYLE_X, 18, STYLE_WIDTH);
            fittedText(graphics, stack.isEmpty() ? Component.translatable("screen.createapparel.wardrobe.empty") : stack.getHoverName(), STYLE_X, 28, STYLE_WIDTH);
            if (!stack.isEmpty() && styleButtons.isEmpty()) fittedText(graphics,
                    Component.translatable("screen.createapparel.wardrobe.no_styles"), STYLE_X, STYLE_Y, STYLE_WIDTH);
        }
        if (menu.mode() == PhotographyMenu.Mode.CAPTURE) {
            float scale = 0.65F;
            var lines = font.split(Component.translatable("screen.createapparel.photo.return_clothes"), (int) ((PREVIEW_X - 16) / scale));
            while (scale > 0.45F && lines.size() * font.lineHeight * scale > inventoryLabelY - RETURN_NOTE_Y - 2) {
                scale -= 0.05F;
                lines = font.split(Component.translatable("screen.createapparel.photo.return_clothes"), (int) ((PREVIEW_X - 16) / scale));
            }
            graphics.pose().pushPose();
            graphics.pose().translate(PREVIEW_X / 2.0F, RETURN_NOTE_Y, 0);
            graphics.pose().scale(scale, scale, 1);
            for (int index = 0; index < lines.size(); index++)
                graphics.drawString(font, lines.get(index), -font.width(lines.get(index)) / 2, index * font.lineHeight, 0x404040, false);
            graphics.pose().popPose();
        }
        if (menu.mode() != PhotographyMenu.Mode.CONSENT)
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        else graphics.drawWordWrap(font, Component.translatable("screen.createapparel.photo.consent_hint"), SETTINGS_X, 24, SETTINGS_WIDTH, 0x404040);
    }

    private void fittedText(GuiGraphics graphics, Component text, int x, int y, int availableWidth) {
        float scale = Math.min(1.0F, (float) availableWidth / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, 0x404040, false);
        graphics.pose().popPose();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!selectionMenu.isOpen()) {
            renderTooltip(graphics, mouseX, mouseY);
            if (hoveredSlot != null && hoveredSlot.index < PhotographyMenu.GARMENT_SLOTS && !hoveredSlot.hasItem())
                graphics.renderTooltip(font, Component.translatable("garment_slot.createapparel." +
                        GarmentSlot.values()[hoveredSlot.index].getSerializedName()), mouseX, mouseY);
        }
        selectionMenu.render(graphics, mouseX, mouseY);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double delta) {
        if (selectionMenu.mouseScrolled(delta)) return true;
        if (delta != 0) {
            int index = 0;
            for (var button : styleButtons.values()) {
                if (button.active && button.isMouseOver(mouseX, mouseY)) {
                    send((delta > 0 ? PhotographyMenu.REVERSE_STYLE_ACTION : PhotographyMenu.STYLE_ACTION) + index); return true;
                }
                index++;
            }
        }
        if (delta != 0) for (var entry : controls.entrySet()) {
            if (entry.getValue().isMouseOver(mouseX, mouseY)) {
                send(entry.getKey() + (delta > 0 ? PhotographyMenu.REVERSE_ACTION : 0)); return true;
            }
        }
        if (delta != 0 && menu.mode() != PhotographyMenu.Mode.CONSENT && mouseX >= leftPos && mouseX < leftPos + SETTINGS_X
                && mouseY >= topPos && mouseY < topPos + INVENTORY_Y - 12) {
            var current = GarmentSlot.values()[menu.setting(7)];
            var next = slotSelectionOrder.get(Math.floorMod(slotSelectionOrder.indexOf(current) + (delta > 0 ? -1 : 1), slotSelectionOrder.size()));
            menu.setData(7, next.ordinal());
            updateStyles();
            send(PhotographyMenu.SELECT_SLOT_ACTION + next.ordinal()); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalScroll, delta);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return selectionMenu.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (selectionMenu.mouseReleased()) { setDragging(false); return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return selectionMenu.isOpen() || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        return selectionMenu.keyPressed(key) || super.keyPressed(key, scanCode, modifiers);
    }
}
