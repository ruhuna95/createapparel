package com.rexhub.createapparel.client.screen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.client.apparel.ClientApparelInventories;
import com.rexhub.createapparel.client.apparel.ApparelRenderSnapshot;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.garment.GarmentAppearance;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.GarmentStyleResolver;
import java.util.Map;
import java.util.EnumMap;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.menu.WardrobeMenu;
import com.rexhub.createapparel.menu.ApparelMenu;
import com.rexhub.createapparel.menu.MannequinMenu;
import com.rexhub.createapparel.network.MannequinActionPacket;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.network.WardrobeActionPacket;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class WardrobeScreen<T extends ApparelMenu> extends AbstractContainerScreen<T> {
    private static final ResourceLocation VANILLA_CONTAINER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "minecraft",
            "textures/gui/container/generic_54.png"
    );
    private static final int BACKGROUND_COLOR = 0xFFC6C6C6;
    private static final int LIGHT_EDGE_COLOR = 0xFFFFFFFF;
    private static final int DARK_EDGE_COLOR = 0xFF555555;
    private static final int PREVIEW_BACKGROUND_COLOR = 0xFF000000;
    private static final int SELECTED_SLOT_COLOR = SelectionMenu.SELECTED_SLOT_COLOR;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int MUTED_TEXT_COLOR = 0xFF707070;
    private static final int BUTTON_HEIGHT = 18;
    private static final int SCREEN_WIDTH = 300;
    private static final int SELECTION_WIDTH = 96;
    private static final int SELECTION_X = SCREEN_WIDTH - SELECTION_WIDTH - 2;
    private static final int STYLE_BUTTON_WIDTH = SELECTION_WIDTH;
    private static final int SELECTED_GARMENT_Y = 18;
    private static final double NAME_SCROLL_PIXELS_PER_SECOND = 20.0D;
    private static final double NAME_SCROLL_PAUSE_SECONDS = 1.0D;
    private static final int STYLE_BUTTON_Y = 40;
    private static final int PREVIEW_PANEL_Y = 6;
    private static final int PREVIEW_PANEL_WIDTH = 100;
    private static final int PREVIEW_PANEL_X = (SCREEN_WIDTH - PREVIEW_PANEL_WIDTH) / 2;
    private static final int PREVIEW_CENTER_X = PREVIEW_PANEL_X + PREVIEW_PANEL_WIDTH / 2;
    private static final double PREVIEW_CENTER_OFFSET_X = -0.5D;
    private static final int PLAYER_INVENTORY_LABEL_Y = 136;
    private static final ResourceLocation[] EMPTY_APPAREL_SLOT_TEXTURES = createEmptyApparelSlotTextures();

    private final List<Button> styleButtons = new ArrayList<>();
    private final ButtonValueScroll buttonScroll = new ButtonValueScroll();
    private final List<GarmentSlot> slotSelectionOrder;
    private GarmentSlot selectedSlot = GarmentSlot.TOP;
    private ItemStack observedStack = ItemStack.EMPTY;
    private GarmentDefinition selectedDefinition;
    private GarmentAppearance previewData;
    private EquippedStyleState previewStyles = EquippedStyleState.EMPTY;
    private EquippedStyleState observedStyles = EquippedStyleState.EMPTY;
    private long observedRevision = -1;
    private long selectedNameChangedAt = Util.getMillis();
    private ItemStack previewStack = ItemStack.EMPTY;
    private ChestProfile previewChestProfile = ChestProfile.FLAT;
    private SkinTone previewSkinTone = SkinTone.NATURAL;
    private ApparelRenderSnapshot cachedPreview;
    private List<ItemStack> cachedMenuContents = List.of();
    private long previewAssetGeneration = -1;
    private Button chestProfileButton;
    private Button skinToneButton;
    private Button poseButton;

    public WardrobeScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = SCREEN_WIDTH;
        imageHeight = 228;
        slotSelectionOrder = List.of(GarmentSlot.values()).stream()
                .filter(slot -> menu.slots.get(slot.ordinal()).isActive())
                .sorted(Comparator.comparingInt((GarmentSlot slot) -> menu.slots.get(slot.ordinal()).y)
                        .thenComparingInt(slot -> menu.slots.get(slot.ordinal()).x))
                .toList();
    }

    private final SelectionMenu selectionMenu = new SelectionMenu();

    @Override
    protected void init() {
        selectionMenu.close();
        buttonScroll.clear();
        super.init();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        previewChestProfile = ClientApparelInventories.chestProfile(minecraft.player);
        previewSkinTone = ClientApparelInventories.skinTone(minecraft.player);
        selectedSlot = firstEquippedSlot().orElse(slotSelectionOrder.contains(GarmentSlot.TOP)
                ? GarmentSlot.TOP : slotSelectionOrder.get(0));

        if (menu instanceof MannequinMenu mannequinMenu) {
            poseButton = addRenderableWidget(Button.builder(Component.empty(), button -> cyclePose(1))
                    .bounds(leftPos + PREVIEW_PANEL_X + 2, topPos + 113, PREVIEW_PANEL_WIDTH - 4, BUTTON_HEIGHT).build());
            buttonScroll.bind(poseButton, this::cyclePose);
            updatePoseButton();
            refreshSelectedGarment(true);
            return;
        }

        chestProfileButton = addRenderableWidget(Button.builder(Component.empty(), button -> cycleChestProfile(1))
                .bounds(leftPos + PREVIEW_PANEL_X + 2, topPos + 91, PREVIEW_PANEL_WIDTH - 4, BUTTON_HEIGHT)
                .build());
        skinToneButton = addRenderableWidget(new ColorButton(leftPos + PREVIEW_PANEL_X + 2,
                topPos + 113, PREVIEW_PANEL_WIDTH - 4, BUTTON_HEIGHT,
                button -> openSkinToneGrid(), () -> previewSkinTone.tintRgb()));
        buttonScroll.bind(chestProfileButton, this::cycleChestProfile);
        buttonScroll.bind(skinToneButton, direction -> selectSkinTone(
                Math.floorMod(previewSkinTone.ordinal() + direction, SkinTone.values().length)));

        updateProfileButtons();
        refreshSelectedGarment(true);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshSelectedGarment(false);
        updatePoseButton();
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
            if (menu.slots.get(slot.ordinal()).isActive() && !menu.apparelStack(slot).isEmpty()) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (selectionMenu.mouseScrolled(delta)) return true;
        if (buttonScroll.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (delta == 0 || mouseX < leftPos || mouseX >= leftPos + imageWidth
                || mouseY < topPos || mouseY >= topPos + PLAYER_INVENTORY_LABEL_Y) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int direction = delta > 0 ? -1 : 1;
        int index = Math.floorMod(slotSelectionOrder.indexOf(selectedSlot) + direction, slotSelectionOrder.size());
        selectedSlot = slotSelectionOrder.get(index);
        refreshSelectedGarment(true);
        return true;
    }

    private void refreshSelectedGarment(boolean force) {
        ItemStack actual = menu.slots.get(selectedSlot.ordinal()).getItem();
        EquippedStyleState actualStyles = targetStyles().getOrDefault(selectedSlot, EquippedStyleState.EMPTY);
        long revision = targetRevision();
        if (!force && revision == observedRevision && ItemStack.matches(actual, observedStack) && actualStyles.equals(observedStyles)) {
            return;
        }
        cachedPreview = null;

        GarmentDefinition definition = definitionFor(actual).orElse(null);
        GarmentAppearance actualData = readData(actual, definition).orElse(null);
        if (force || !ItemStack.matches(actual, observedStack)) {
            selectedNameChangedAt = Util.getMillis();
        }
        observedStack = actual.copy();
        observedStyles = actualStyles;
        observedRevision = revision;
        previewStyles = definition == null ? EquippedStyleState.EMPTY : actualStyles.normalize(definition, selectedSlot);
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
        styleButtons.forEach(buttonScroll::remove);
        styleButtons.forEach(this::removeWidget);
        styleButtons.clear();
        if (selectedDefinition == null || previewData == null) {
            return;
        }

        int index = 0;
        for (String category : selectedDefinition.stylesFor(selectedSlot).keySet()) {
            Button button = Button.builder(Component.empty(), ignored -> cycleStyle(category, 1))
                    .bounds(
                            leftPos + SELECTION_X,
                            topPos + STYLE_BUTTON_Y + index * 20,
                            STYLE_BUTTON_WIDTH,
                            BUTTON_HEIGHT
                    )
                    .build();
            styleButtons.add(addRenderableWidget(button));
            buttonScroll.bind(button, direction -> cycleStyle(category, direction));
            index++;
        }
        updateStyleButtonMessages();
    }

    private void cycleStyle(String category, int direction) {
        if (selectedDefinition == null || previewData == null) {
            return;
        }
        List<String> values = GarmentStyleResolver.allowedStyles(selectedDefinition, selectedSlot,
                ClientApparelInventories.context(menu.copyApparelContents(), previewStyleSnapshot())).get(category);
        if (values == null || values.isEmpty()) {
            return;
        }
        String current = previewStyles.styles().getOrDefault(category, values.get(0));
        String next = values.get(Math.floorMod(values.indexOf(current) + direction, values.size()));
        if (next.equals(current)) return;
        previewStyles = previewStyles.with(category, next);
        cachedPreview = null;
        updateStyleButtonMessages();
        if (menu instanceof MannequinMenu) {
            ModNetwork.updateMannequin(MannequinActionPacket.style(menu.containerId, selectedSlot, category, next));
        } else {
            ModNetwork.updateWardrobe(WardrobeActionPacket.style(selectedSlot, category, next));
        }
    }

    private Map<GarmentSlot, EquippedStyleState> previewStyleSnapshot() {
        return previewStyleSnapshot(menu.copyApparelContents());
    }

    private Map<GarmentSlot, EquippedStyleState> previewStyleSnapshot(List<ItemStack> contents) {
        Map<GarmentSlot, EquippedStyleState> styles = new EnumMap<>(GarmentSlot.class);
        styles.putAll(targetStyles());
        styles.put(selectedSlot, previewStyles);
        var context = ClientApparelInventories.context(contents, styles);
        context.equipped().forEach((slot, view) -> styles.put(slot,
                GarmentStyleResolver.normalize(view.definition(), slot, view.styleState(), context)));
        return styles;
    }

    private Map<GarmentSlot, EquippedStyleState> targetStyles() {
        if (menu instanceof MannequinMenu mannequinMenu) {
            return mannequinMenu.mannequin().mannequinState().loadout().copyStyles();
        }
        return minecraft == null || minecraft.player == null ? Map.of()
                : ClientApparelInventories.styles(minecraft.player);
    }

    private long targetRevision() {
        if (menu instanceof MannequinMenu mannequinMenu) return mannequinMenu.mannequin().mannequinState().revision();
        return minecraft == null || minecraft.player == null ? 0 : ClientApparelInventories.revision(minecraft.player);
    }

    private void cyclePose(int direction) {
        if (!(menu instanceof MannequinMenu mannequinMenu)) return;
        var state = mannequinMenu.mannequin().mannequinState();
        var poses = mannequinMenu.mannequin().variant().form().poses();
        var pose = poses.get(Math.floorMod(poses.indexOf(state.pose()) + direction, poses.size()));
        ModNetwork.updateMannequin(MannequinActionPacket.pose(menu.containerId, pose));
    }

    private void updatePoseButton() {
        if (poseButton != null && menu instanceof MannequinMenu mannequinMenu) {
            poseButton.setMessage(Component.translatable("screen.createapparel.mannequin.pose",
                    Component.translatable("pose.createapparel." + mannequinMenu.mannequin().mannequinState().pose().id())));
        }
    }

    private void cycleChestProfile(int direction) {
        ChestProfile[] values = ChestProfile.values();
        previewChestProfile = values[Math.floorMod(previewChestProfile.ordinal() + direction, values.length)];
        cachedPreview = null;
        updateProfileButtons();
        ModNetwork.updateWardrobe(WardrobeActionPacket.chestProfile(previewChestProfile));
    }

    private void openSkinToneGrid() {
        List<SkinTone> tones = List.of(SkinTone.values());
        selectionMenu.open(skinToneButton, width, height, tones.stream()
                .map(tone -> (Component) Component.translatable("skin_tone.createapparel." + tone.getSerializedName()))
                .toList(), tones.stream().map(SkinTone::tintRgb).toList(), previewSkinTone.ordinal(), this::selectSkinTone);
    }

    private void selectSkinTone(int index) {
        SkinTone next = SkinTone.values()[index];
        if (next == previewSkinTone) return;
        previewSkinTone = next;
        cachedPreview = null;
        updateProfileButtons();
        ModNetwork.updateWardrobe(WardrobeActionPacket.skinTone(previewSkinTone));
    }

    private void rebuildPreviewStack() {
        previewStack = ItemStack.EMPTY;
        if (previewData == null || observedStack.isEmpty()) {
            return;
        }
        previewStack = observedStack.copy();
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
        for (String category : selectedDefinition.stylesFor(selectedSlot).keySet()) {
            if (index >= styleButtons.size()) {
                break;
            }
            String value = previewStyles.styles().get(category);
            ResourceLocation garmentId = selectedDefinition.id();
            String garmentStyleKey = "style." + garmentId.getNamespace() + "." + garmentId.getPath()
                    + "." + category + "." + value;
            String styleKey = I18n.exists(garmentStyleKey) ? garmentStyleKey
                    : "style.createapparel." + category + "." + value;
            styleButtons.get(index).setMessage(Component.translatable(
                    "screen.createapparel.wardrobe.style",
                    Component.translatable("style_category.createapparel." + category),
                    Component.translatable(styleKey)
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

    private static Optional<GarmentAppearance> readData(ItemStack stack, GarmentDefinition definition) {
        if (stack.isEmpty() || definition == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(GarmentAppearance.fromStack(stack, definition));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!selectionMenu.isOpen()) renderTooltip(graphics, mouseX, mouseY);
        selectionMenu.render(graphics, mouseX, mouseY);
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
            if (slot.isActive()) renderSlotBackground(graphics, slot, index);
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

    private boolean previewIsCurrent() {
        if (cachedPreview == null || minecraft == null || minecraft.player == null
                || cachedPreview.playerId() != minecraft.player.getId()
                || cachedPreview.revision() != ClientApparelInventories.revision(minecraft.player)
                || cachedPreview.armProfile() != ClientApparelInventories.armProfile(minecraft.player)
                || previewAssetGeneration != ClientGarmentAssets.INSTANCE.generation()) return false;
        for (int i = 0; i < cachedMenuContents.size(); i++) {
            if (!ItemStack.matches(cachedMenuContents.get(i), menu.slots.get(i).getItem())) return false;
        }
        return true;
    }

    private void renderPlayerPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu instanceof MannequinMenu mannequinMenu) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    leftPos + PREVIEW_CENTER_X, topPos + 104, 42,
                    leftPos + PREVIEW_CENTER_X - mouseX, topPos + 49 - mouseY, mannequinMenu.mannequin());
            return;
        }
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (!previewIsCurrent()) {
            cachedMenuContents = menu.copyApparelContents();
            List<ItemStack> previewContents = new ArrayList<>(cachedMenuContents);
            if (!previewStack.isEmpty()) previewContents.set(selectedSlot.ordinal(), previewStack);
            cachedPreview = ClientApparelInventories.previewSnapshot(minecraft.player, previewContents,
                    previewStyleSnapshot(cachedMenuContents), previewChestProfile, previewSkinTone);
            previewAssetGeneration = ClientGarmentAssets.INSTANCE.generation();
        }
        ClientApparelInventories.withPreview(
                cachedPreview,
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
        renderFittedSelectionText(graphics,
                Component.translatable("screen.createapparel.wardrobe.selected_garment", Component.empty()),
                SELECTED_GARMENT_Y, TEXT_COLOR);
        renderSelectedName(graphics, selectedName);
        if (selectedDefinition != null && selectedDefinition.stylesFor(selectedSlot).isEmpty()) {
            renderFittedSelectionText(
                    graphics,
                    Component.translatable("screen.createapparel.wardrobe.no_styles"),
                    STYLE_BUTTON_Y,
                    MUTED_TEXT_COLOR
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

    private void renderSelectedName(GuiGraphics graphics, Component name) {
        int y = SELECTED_GARMENT_Y + 10;
        int overflow = font.width(name) - SELECTION_WIDTH;
        if (overflow <= 0) {
            graphics.drawString(font, name, SELECTION_X, y, TEXT_COLOR, false);
            return;
        }
        double travelSeconds = overflow / NAME_SCROLL_PIXELS_PER_SECOND;
        double halfCycle = NAME_SCROLL_PAUSE_SECONDS + travelSeconds;
        double elapsed = (Util.getMillis() - selectedNameChangedAt) / 1000.0D % (2 * halfCycle);
        double offset = elapsed < halfCycle
                ? Math.max(0, elapsed - NAME_SCROLL_PAUSE_SECONDS) * NAME_SCROLL_PIXELS_PER_SECOND
                : overflow - Math.max(0, elapsed - halfCycle - NAME_SCROLL_PAUSE_SECONDS) * NAME_SCROLL_PIXELS_PER_SECOND;
        graphics.enableScissor(leftPos + SELECTION_X, topPos + y,
                leftPos + SELECTION_X + SELECTION_WIDTH, topPos + y + font.lineHeight + 1);
        try {
            graphics.drawString(font, name, SELECTION_X - (int) offset, y, TEXT_COLOR, false);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderFittedSelectionText(GuiGraphics graphics, Component text, int y, int color) {
        float scale = Math.min(1.0F, (float) SELECTION_WIDTH / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(SELECTION_X, y, 0);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, text, 0, 0, color, false);
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        for (GarmentSlot slot : GarmentSlot.values()) {
            Slot menuSlot = menu.slots.get(slot.ordinal());
            int x = leftPos + menuSlot.x;
            int y = topPos + menuSlot.y;
            if (menuSlot.isActive() && !menuSlot.hasItem()
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return selectionMenu.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (selectionMenu.mouseReleased()) {
            setDragging(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return selectionMenu.isOpen() || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return selectionMenu.keyPressed(key) || super.keyPressed(key, scanCode, modifiers);
    }
}
