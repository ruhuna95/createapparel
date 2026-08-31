package com.rexhub.createapparel.client.screen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.client.apparel.ClientApparelInventories;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.menu.WardrobeMenu;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.network.WardrobeActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public final class WardrobeScreen extends AbstractContainerScreen<WardrobeMenu> {
    private static final ResourceLocation VANILLA_CONTAINER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "minecraft",
            "textures/gui/container/generic_54.png"
    );
    private static final int BACKGROUND_COLOR = 0xFFC6C6C6;
    private static final int LIGHT_EDGE_COLOR = 0xFFFFFFFF;
    private static final int DARK_EDGE_COLOR = 0xFF555555;
    private static final int PREVIEW_BACKGROUND_COLOR = 0xFF000000;
    private static final int SELECTED_SLOT_COLOR = 0xFFFFFF80;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int MUTED_TEXT_COLOR = 0xFF707070;
    private static final int BUTTON_HEIGHT = 18;
    private static final int SCREEN_WIDTH = 300;
    private static final int SELECTION_WIDTH = 96;
    private static final int SELECTION_X = SCREEN_WIDTH - SELECTION_WIDTH - 2;
    private static final int STYLE_BUTTON_WIDTH = SELECTION_WIDTH;
    private static final int SELECTED_GARMENT_Y = 18;
    private static final int STYLE_BUTTON_Y = 40;
    private static final int PREVIEW_PANEL_Y = 6;
    private static final int PREVIEW_PANEL_WIDTH = 100;
    private static final int PREVIEW_PANEL_X = (SCREEN_WIDTH - PREVIEW_PANEL_WIDTH) / 2;
    private static final int PREVIEW_CENTER_X = PREVIEW_PANEL_X + PREVIEW_PANEL_WIDTH / 2;
    private static final double PREVIEW_CENTER_OFFSET_X = -0.5D;
    private static final int PLAYER_INVENTORY_LABEL_Y = 136;
    private static final ResourceLocation[] EMPTY_APPAREL_SLOT_TEXTURES = createEmptyApparelSlotTextures();

    private final List<Button> styleButtons = new ArrayList<>();
    private GarmentSlot selectedSlot = GarmentSlot.TOP;
    private ItemStack observedStack = ItemStack.EMPTY;
    private GarmentDefinition selectedDefinition;
    private GarmentData previewData;
    private ItemStack previewStack = ItemStack.EMPTY;
    private ChestProfile previewChestProfile = ChestProfile.FLAT;
    private SkinTone previewSkinTone = SkinTone.NATURAL;
    private Button chestProfileButton;
    private Button skinToneButton;

    public WardrobeScreen(WardrobeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = SCREEN_WIDTH;
        imageHeight = 228;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        previewChestProfile = ClientApparelInventories.chestProfile(minecraft.player);
        previewSkinTone = ClientApparelInventories.skinTone(minecraft.player);
        selectedSlot = firstEquippedSlot().orElse(GarmentSlot.TOP);

        chestProfileButton = addRenderableWidget(Button.builder(Component.empty(), button -> cycleChestProfile())
                .bounds(leftPos + PREVIEW_PANEL_X + 2, topPos + 91, PREVIEW_PANEL_WIDTH - 4, BUTTON_HEIGHT)
                .build());
        skinToneButton = addRenderableWidget(Button.builder(Component.empty(), button -> cycleSkinTone())
                .bounds(leftPos + PREVIEW_PANEL_X + 2, topPos + 113, PREVIEW_PANEL_WIDTH - 4, BUTTON_HEIGHT)
                .build());

        updateProfileButtons();
        refreshSelectedGarment(true);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshSelectedGarment(false);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slotId >= 0 && slotId < WardrobeMenu.APPAREL_SLOT_COUNT) {
            selectedSlot = GarmentSlot.values()[slotId];
        }
        super.slotClicked(slot, slotId, mouseButton, type);
        refreshSelectedGarment(true);
    }

    private Optional<GarmentSlot> firstEquippedSlot() {
        for (GarmentSlot slot : GarmentSlot.values()) {
            if (!menu.apparelStack(slot).isEmpty()) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    private void refreshSelectedGarment(boolean force) {
        ItemStack actual = menu.apparelStack(selectedSlot);
        if (!force && ItemStack.matches(actual, observedStack)) {
            return;
        }

        GarmentDefinition definition = definitionFor(actual).orElse(null);
        GarmentData actualData = readData(actual, definition).orElse(null);
        observedStack = actual.copy();
        selectedDefinition = definition;
        if (definition == null || actualData == null) {
            previewData = null;
            previewStack = ItemStack.EMPTY;
        } else {
            previewData = actualData;
            rebuildPreviewStack();
        }
        rebuildStyleButtons();
    }

    private void rebuildStyleButtons() {
        styleButtons.forEach(this::removeWidget);
        styleButtons.clear();
        if (selectedDefinition == null || previewData == null) {
            return;
        }

        int index = 0;
        for (String category : selectedDefinition.styles().keySet()) {
            Button button = Button.builder(Component.empty(), ignored -> cycleStyle(category))
                    .bounds(
                            leftPos + SELECTION_X,
                            topPos + STYLE_BUTTON_Y + index * 20,
                            STYLE_BUTTON_WIDTH,
                            BUTTON_HEIGHT
                    )
                    .build();
            styleButtons.add(addRenderableWidget(button));
            index++;
        }
        updateStyleButtonMessages();
    }

    private void cycleStyle(String category) {
        if (selectedDefinition == null || previewData == null) {
            return;
        }
        List<String> values = selectedDefinition.styles().get(category);
        if (values == null || values.isEmpty()) {
            return;
        }
        String current = previewData.styles().getOrDefault(category, values.get(0));
        String next = values.get((values.indexOf(current) + 1) % values.size());
        LinkedHashMap<String, String> styles = new LinkedHashMap<>(previewData.styles());
        styles.put(category, next);
        previewData = new GarmentData(
                previewData.material(),
                previewData.design(),
                previewData.primaryColor(),
                previewData.secondaryColor(),
                previewData.accentColor(),
                styles
        );
        rebuildPreviewStack();
        updateStyleButtonMessages();
        ModNetwork.updateWardrobe(WardrobeActionPacket.style(selectedSlot, category, next));
    }

    private void cycleChestProfile() {
        ChestProfile[] values = ChestProfile.values();
        previewChestProfile = values[(previewChestProfile.ordinal() + 1) % values.length];
        updateProfileButtons();
        ModNetwork.updateWardrobe(WardrobeActionPacket.chestProfile(previewChestProfile));
    }

    private void cycleSkinTone() {
        SkinTone[] values = SkinTone.values();
        previewSkinTone = values[(previewSkinTone.ordinal() + 1) % values.length];
        updateProfileButtons();
        ModNetwork.updateWardrobe(WardrobeActionPacket.skinTone(previewSkinTone));
    }

    private void rebuildPreviewStack() {
        previewStack = ItemStack.EMPTY;
        if (previewData == null || observedStack.isEmpty()) {
            return;
        }
        previewStack = observedStack.copy();
        previewData.write(previewStack);
    }

    private void updateProfileButtons() {
        if (chestProfileButton == null || skinToneButton == null) {
            return;
        }
        chestProfileButton.setMessage(Component.translatable(
                "screen.createapparel.wardrobe.chest_profile",
                Component.translatable("chest_profile.createapparel." + previewChestProfile.getSerializedName())
        ));
        skinToneButton.setMessage(Component.translatable(
                "screen.createapparel.wardrobe.skin_tone",
                Component.translatable("skin_tone.createapparel." + previewSkinTone.getSerializedName())
        ));
    }

    private void updateStyleButtonMessages() {
        if (selectedDefinition == null || previewData == null) {
            return;
        }
        int index = 0;
        for (String category : selectedDefinition.styles().keySet()) {
            if (index >= styleButtons.size()) {
                break;
            }
            String value = previewData.styles().get(category);
            styleButtons.get(index).setMessage(Component.translatable(
                    "screen.createapparel.wardrobe.style",
                    Component.translatable("style_category.createapparel." + category),
                    Component.translatable("style.createapparel." + category + "." + value)
            ));
            index++;
        }
    }

    private static Optional<GarmentDefinition> definitionFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId == null ? Optional.empty() : ClientGarmentAssets.getDefinition(itemId);
    }

    private static Optional<GarmentData> readData(ItemStack stack, GarmentDefinition definition) {
        if (stack.isEmpty() || definition == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(GarmentData.fromStack(stack, definition));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderRaisedPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        renderInsetPanel(
                graphics,
                leftPos + PREVIEW_PANEL_X,
                topPos + PREVIEW_PANEL_Y,
                PREVIEW_PANEL_WIDTH,
                128
        );

        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            renderSlotBackground(graphics, slot, index);
        }
        renderPlayerPreview(graphics, mouseX, mouseY);
    }

    private void renderSlotBackground(GuiGraphics graphics, Slot slot, int index) {
        int x = leftPos + slot.x;
        int y = topPos + slot.y;
        if (index == selectedSlot.ordinal()) {
            graphics.fill(x - 2, y - 2, x + 18, y + 18, SELECTED_SLOT_COLOR);
        }
        graphics.blit(VANILLA_CONTAINER_TEXTURE, x - 1, y - 1, 7, 17, 18, 18);
        if (index < WardrobeMenu.APPAREL_SLOT_COUNT && !slot.hasItem()) {
            graphics.blit(EMPTY_APPAREL_SLOT_TEXTURES[index], x, y, 0, 0, 16, 16, 16, 16);
        }
    }

    private static void renderRaisedPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BACKGROUND_COLOR);
        graphics.fill(x, y, x + width, y + 1, LIGHT_EDGE_COLOR);
        graphics.fill(x, y, x + 1, y + height, LIGHT_EDGE_COLOR);
        graphics.fill(x, y + height - 1, x + width, y + height, DARK_EDGE_COLOR);
        graphics.fill(x + width - 1, y, x + width, y + height, DARK_EDGE_COLOR);
    }

    private static void renderInsetPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PREVIEW_BACKGROUND_COLOR);
        graphics.fill(x, y, x + width, y + 1, DARK_EDGE_COLOR);
        graphics.fill(x, y, x + 1, y + height, DARK_EDGE_COLOR);
        graphics.fill(x, y + height - 1, x + width, y + height, LIGHT_EDGE_COLOR);
        graphics.fill(x + width - 1, y, x + width, y + height, LIGHT_EDGE_COLOR);
    }

    private static ResourceLocation[] createEmptyApparelSlotTextures() {
        GarmentSlot[] slots = GarmentSlot.values();
        ResourceLocation[] textures = new ResourceLocation[slots.length];
        for (GarmentSlot slot : slots) {
            textures[slot.ordinal()] = ResourceLocation.fromNamespaceAndPath(
                    CreateApparel.MOD_ID,
                    "textures/item/empty_apparel_slot_" + slot.getSerializedName() + ".png"
            );
        }
        return textures;
    }

    private void renderPlayerPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        List<ItemStack> previewContents = new ArrayList<>(menu.copyApparelContents());
        if (!previewStack.isEmpty()) {
            previewContents.set(selectedSlot.ordinal(), previewStack.copy());
        }
        ClientApparelInventories.withPreview(
                minecraft.player,
                previewContents,
                previewChestProfile,
                previewSkinTone,
                () -> {
                    graphics.pose().pushPose();
                    graphics.pose().translate(PREVIEW_CENTER_OFFSET_X, 0.0D, 0.0D);
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            graphics,
                            leftPos + PREVIEW_CENTER_X,
                            topPos + 88,
                            35,
                            (float) (leftPos + PREVIEW_CENTER_X + PREVIEW_CENTER_OFFSET_X - mouseX),
                            topPos + 49 - mouseY,
                            minecraft.player
                    );
                    graphics.pose().popPose();
                }
        );
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, TEXT_COLOR, false);
        Component selectedName = observedStack.isEmpty()
                ? Component.translatable("screen.createapparel.wardrobe.empty")
                : observedStack.getHoverName();
        List<FormattedCharSequence> selectedLines = font.split(
                Component.translatable("screen.createapparel.wardrobe.selected_garment", selectedName),
                SELECTION_WIDTH
        );
        for (int line = 0; line < Math.min(selectedLines.size(), 2); line++) {
            graphics.drawString(
                    font,
                    selectedLines.get(line),
                    SELECTION_X,
                    SELECTED_GARMENT_Y + line * 10,
                    TEXT_COLOR,
                    false
            );
        }
        if (selectedDefinition != null && selectedDefinition.styles().isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable("screen.createapparel.wardrobe.no_styles"),
                    SELECTION_X,
                    STYLE_BUTTON_Y,
                    MUTED_TEXT_COLOR,
                    false
            );
        }
        graphics.drawString(
                font,
                playerInventoryTitle,
                WardrobeMenu.PLAYER_INVENTORY_X,
                PLAYER_INVENTORY_LABEL_Y,
                TEXT_COLOR,
                false
        );
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        for (GarmentSlot slot : GarmentSlot.values()) {
            Slot menuSlot = menu.slots.get(slot.ordinal());
            int x = leftPos + menuSlot.x;
            int y = topPos + menuSlot.y;
            if (!menuSlot.hasItem()
                    && mouseX >= x && mouseX < x + 16
                    && mouseY >= y && mouseY < y + 16) {
                graphics.renderTooltip(
                        font,
                        Component.translatable("garment_slot.createapparel." + slot.getSerializedName()),
                        mouseX,
                        mouseY
                );
                return;
            }
        }
    }
}
