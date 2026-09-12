package com.rexhub.createapparel.client.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.client.renderer.CommonGarmentRenderer;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.recipe.SewingRecipe;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class SewingPatternScreen extends Screen {
    private static final int MIN_BUTTON_WIDTH = 120;
    private static final int MAX_BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_SPACING = 24;
    private static final float PREVIEW_SCALE = 4.0F;
    private static final float PREVIEW_MODEL_SCALE = 0.82F;
    private static final float PREVIEW_TILT_DEGREES = 15.0F;
    private static final float ITEM_RENDER_Z = 150.0F;
    private static final long PREVIEW_ROTATION_PERIOD_MILLIS = 8_000L;

    private final InteractionHand hand;
    private List<SewingRecipe> availableRecipes = List.of();
    private List<GarmentSlot> categories = List.of();
    private List<SewingRecipe> recipes = List.of();
    private List<String> designs = List.of();
    private int categoryIndex;
    private int recipeIndex;
    private int designIndex;
    private int previewFabricColorIndex = FabricColor.WHITE.ordinal();
    private int secondaryColorIndex;
    private int accentColorIndex;
    private int leftPanelCenter;
    private int rightPanelCenter;
    private ItemStack previewStack = ItemStack.EMPTY;
    private Button categoryButton;
    private Button recipeButton;
    private Button designButton;
    private Button previewFabricColorButton;
    private Button secondaryColorButton;
    private Button accentColorButton;
    private Button doneButton;

    public SewingPatternScreen(InteractionHand hand) {
        super(Component.translatable("screen.createapparel.sewing_pattern"));
        this.hand = hand;
    }

    private final SelectionMenu selectionMenu = new SelectionMenu();

    @Override
    protected void init() {
        selectionMenu.close();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        ItemStack patternStack = minecraft.player.getItemInHand(hand);
        availableRecipes = minecraft.level.getRecipeManager()
                .getAllRecipesFor(SewingRecipe.recipeType())
                .stream()
                .filter(recipe -> recipe.matchesPatternItem(patternStack))
                .filter(recipe -> ClientGarmentAssets.getDefinition(recipe.getResultId()).isPresent())
                .sorted(SewingPatternOrder.RECIPES)
                .toList();
        categories = availableRecipes.stream()
                .map(recipe -> ClientGarmentAssets.getDefinition(recipe.getResultId()).orElseThrow())
                .map(GarmentDefinition::defaultSlot)
                .distinct()
                .sorted(Comparator.comparingInt(GarmentSlot::ordinal))
                .toList();

        PatternData current = PatternData.fromStack(patternStack).orElse(null);
        if (current != null) {
            selectCategory(current.recipe());
            secondaryColorIndex = current.secondaryColor().map(Enum::ordinal).orElse(FabricColor.WHITE.ordinal());
            accentColorIndex = current.accentColor().map(Enum::ordinal).orElse(FabricColor.WHITE.ordinal());
        }
        updateRecipes(current == null ? null : current.recipe(), current == null ? null : current.design());

        leftPanelCenter = width / 4;
        rightPanelCenter = width * 3 / 4;
        int buttonWidth = Mth.clamp(width / 2 - 32, MIN_BUTTON_WIDTH, MAX_BUTTON_WIDTH);
        int left = leftPanelCenter - buttonWidth / 2;
        int top = height / 2 - 74;
        categoryButton = addRenderableWidget(Button.builder(Component.empty(), button -> openCategoryMenu())
                .bounds(left, top, buttonWidth, BUTTON_HEIGHT)
                .build());
        recipeButton = addRenderableWidget(Button.builder(Component.empty(), button -> openRecipeMenu())
                .bounds(left, top + ROW_SPACING, buttonWidth, BUTTON_HEIGHT)
                .build());
        designButton = addRenderableWidget(Button.builder(Component.empty(), button -> openDesignMenu())
                .bounds(left, top + ROW_SPACING * 2, buttonWidth, BUTTON_HEIGHT)
                .build());
        secondaryColorButton = addRenderableWidget(Button.builder(Component.empty(), button -> openSecondaryColorGrid())
                .bounds(left, top + ROW_SPACING * 3, buttonWidth, BUTTON_HEIGHT)
                .build());
        accentColorButton = addRenderableWidget(Button.builder(Component.empty(), button -> openAccentColorGrid())
                .bounds(left, top + ROW_SPACING * 4, buttonWidth, BUTTON_HEIGHT)
                .build());
        previewFabricColorButton = addRenderableWidget(Button.builder(
                        Component.empty(),
                        button -> openFabricColorGrid()
                )
                .bounds(
                        rightPanelCenter - buttonWidth / 2,
                        height / 2 + 58,
                        buttonWidth,
                        BUTTON_HEIGHT
                )
                .build());
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
                .bounds(leftPanelCenter - 50, top + ROW_SPACING * 5 + 4, 100, BUTTON_HEIGHT)
                .build());

        updateButtons();
    }

    private void openCategoryMenu() {
        selectionMenu.open(categoryButton, width, height, categories.stream()
                .map(slot -> (Component) Component.translatable("garment_slot.createapparel." + slot.getSerializedName()))
                .toList(), List.of(), categoryIndex, index -> {
                    categoryIndex = index;
                    updateRecipes(null, null);
                    updateButtons();
                });
    }

    private void openRecipeMenu() {
        selectionMenu.open(recipeButton, width, height, recipes.stream()
                .map(recipe -> recipe.getResultItem(minecraft.level.registryAccess()).getHoverName())
                .toList(), List.of(), recipeIndex, index -> {
                    recipeIndex = index;
                    updateDesigns(null);
                    updateButtons();
                });
    }

    private void selectCategory(ResourceLocation recipeId) {
        availableRecipes.stream()
                .filter(recipe -> recipe.getId().equals(recipeId))
                .findFirst()
                .flatMap(recipe -> ClientGarmentAssets.getDefinition(recipe.getResultId()))
                .map(GarmentDefinition::defaultSlot)
                .map(categories::indexOf)
                .filter(index -> index >= 0)
                .ifPresent(index -> categoryIndex = index);
    }

    private void updateRecipes(ResourceLocation preferredRecipe, String preferredDesign) {
        if (categories.isEmpty()) {
            recipes = List.of();
        } else {
            GarmentSlot category = categories.get(categoryIndex);
            recipes = availableRecipes.stream()
                    .filter(recipe -> ClientGarmentAssets.getDefinition(recipe.getResultId())
                            .map(definition -> definition.defaultSlot() == category)
                            .orElse(false))
                    .toList();
        }
        recipeIndex = 0;
        if (preferredRecipe != null) {
            for (int index = 0; index < recipes.size(); index++) {
                if (recipes.get(index).getId().equals(preferredRecipe)) {
                    recipeIndex = index;
                    break;
                }
            }
        }
        updateDesigns(preferredDesign);
    }

    private void openDesignMenu() {
        selectionMenu.open(designButton, width, height, designs.stream()
                .map(design -> (Component) Component.translatable("design.createapparel." + design))
                .toList(), List.of(), designIndex, index -> {
                    designIndex = index;
                    updateButtons();
                });
    }

    private void selectColor(Button anchor, int current, java.util.function.IntConsumer onSelect) {
        List<FabricColor> colors = List.of(FabricColor.values());
        selectionMenu.open(anchor, width, height, colors.stream()
                .map(color -> (Component) Component.translatable("color.minecraft." + color.getSerializedName()))
                .toList(), colors.stream().map(FabricColor::tintRgb).toList(), current, index -> {
                    onSelect.accept(index);
                    updateButtons();
                });
    }

    private void openSecondaryColorGrid() {
        selectColor(secondaryColorButton, secondaryColorIndex, index -> secondaryColorIndex = index);
    }

    private void openFabricColorGrid() {
        selectColor(previewFabricColorButton, previewFabricColorIndex, index -> previewFabricColorIndex = index);
    }

    private void openAccentColorGrid() {
        selectColor(accentColorButton, accentColorIndex, index -> accentColorIndex = index);
    }

    private void updateDesigns(String preferredDesign) {
        designs = selectedDefinition()
                .map(definition -> new ArrayList<>(definition.designs()))
                .map(List::copyOf)
                .orElse(List.of());
        designIndex = 0;
        if (preferredDesign != null) {
            int preferredIndex = designs.indexOf(preferredDesign);
            if (preferredIndex >= 0) {
                designIndex = preferredIndex;
                return;
            }
        }
        int plainIndex = designs.indexOf("plain");
        if (plainIndex >= 0) {
            designIndex = plainIndex;
        }
    }

    private void updateButtons() {
        previewStack = ItemStack.EMPTY;
        boolean hasCategory = !categories.isEmpty();
        boolean hasRecipe = !recipes.isEmpty();
        categoryButton.active = hasCategory;
        recipeButton.active = hasRecipe;
        designButton.active = hasRecipe && !designs.isEmpty();
        previewFabricColorButton.active = designButton.active;
        doneButton.active = designButton.active;

        if (!hasCategory) {
            categoryButton.setMessage(Component.translatable(
                    "screen.createapparel.sewing_pattern.no_categories"
            ));
        } else {
            categoryButton.setMessage(Component.translatable(
                    "screen.createapparel.sewing_pattern.category",
                    Component.translatable(
                            "garment_slot.createapparel." + categories.get(categoryIndex).getSerializedName()
                    )
            ));
        }

        if (!hasRecipe) {
            recipeButton.setMessage(Component.translatable("screen.createapparel.sewing_pattern.no_recipes"));
            designButton.setMessage(Component.translatable("screen.createapparel.sewing_pattern.no_designs"));
            previewFabricColorButton.visible = false;
            secondaryColorButton.visible = false;
            accentColorButton.visible = false;
            return;
        }

        SewingRecipe recipe = recipes.get(recipeIndex);
        ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
        recipeButton.setMessage(Component.translatable(
                "screen.createapparel.sewing_pattern.recipe",
                result.getHoverName()
        ));
        String design = designs.isEmpty() ? "" : designs.get(designIndex);
        designButton.setMessage(Component.translatable(
                "screen.createapparel.sewing_pattern.design",
                Component.translatable("design.createapparel." + design)
        ));
        previewFabricColorButton.visible = true;
        previewFabricColorButton.setMessage(colorMessage(
                "screen.createapparel.sewing_pattern.fabric_color",
                FabricColor.values()[previewFabricColorIndex]
        ));

        ClientGarmentAssets.ColorChannels channels = ClientGarmentAssets
                .getRequiredColorChannels(recipe.getResultId(), design)
                .orElse(new ClientGarmentAssets.ColorChannels(false, false));
        secondaryColorButton.visible = channels.secondary();
        accentColorButton.visible = channels.accent();
        secondaryColorButton.setMessage(colorMessage(
                "screen.createapparel.sewing_pattern.secondary_color",
                FabricColor.values()[secondaryColorIndex]
        ));
        accentColorButton.setMessage(colorMessage(
                "screen.createapparel.sewing_pattern.accent_color",
                FabricColor.values()[accentColorIndex]
        ));
        updatePreview(recipe, design, channels);
    }

    private void updatePreview(
            SewingRecipe recipe,
            String design,
            ClientGarmentAssets.ColorChannels channels
    ) {
        if (minecraft == null || minecraft.level == null || design.isEmpty()) {
            return;
        }
        GarmentDefinition definition = selectedDefinition().orElse(null);
        if (definition == null) {
            return;
        }

        ItemStack result = recipe.getResultItem(minecraft.level.registryAccess()).copy();
        if (result.isEmpty()) {
            return;
        }
        GarmentData defaults = GarmentData.defaultFor(definition);
        GarmentData previewData = new GarmentData(
                defaults.material(),
                design,
                FabricColor.values()[previewFabricColorIndex],
                channels.secondary()
                        ? Optional.of(FabricColor.values()[secondaryColorIndex])
                        : Optional.empty(),
                channels.accent()
                        ? Optional.of(FabricColor.values()[accentColorIndex])
                        : Optional.empty()
        );
        previewData.write(result);
        previewStack = result;
    }

    private Optional<GarmentDefinition> selectedDefinition() {
        if (recipes.isEmpty()) {
            return Optional.empty();
        }
        return ClientGarmentAssets.getDefinition(recipes.get(recipeIndex).getResultId());
    }

    private Component colorMessage(String labelKey, FabricColor color) {
        return Component.translatable(
                labelKey,
                Component.translatable("color.minecraft." + color.getSerializedName())
        );
    }

    private void saveAndClose() {
        if (recipes.isEmpty() || designs.isEmpty()) {
            return;
        }
        SewingRecipe recipe = recipes.get(recipeIndex);
        String design = designs.get(designIndex);
        ClientGarmentAssets.ColorChannels channels = ClientGarmentAssets
                .getRequiredColorChannels(recipe.getResultId(), design)
                .orElse(new ClientGarmentAssets.ColorChannels(false, false));
        PatternData data = new PatternData(
                recipe.getId(),
                design,
                channels.secondary()
                        ? Optional.of(FabricColor.values()[secondaryColorIndex])
                        : Optional.empty(),
                channels.accent()
                        ? Optional.of(FabricColor.values()[accentColorIndex])
                        : Optional.empty()
        );
        ModNetwork.updateSewingPattern(hand, data);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(width / 2, height / 2 - 104, width / 2 + 1, height / 2 + 82, 0x55FFFFFF);
        graphics.drawCenteredString(font, title, leftPanelCenter, height / 2 - 98, 0xFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.createapparel.sewing_pattern.preview"),
                rightPanelCenter,
                height / 2 - 98,
                0xFFFFFF
        );
        renderPreview(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        selectionMenu.render(graphics, mouseX, mouseY);
    }

    private void renderPreview(GuiGraphics graphics) {
        if (previewStack.isEmpty() || minecraft == null) {
            return;
        }

        var garment = ClientGarmentAssets.INSTANCE.resolve(previewStack);
        if (garment.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(rightPanelCenter, height / 2.0F - 14.0F, ITEM_RENDER_Z);
            graphics.pose().mulPoseMatrix(new Matrix4f().scaling(1.0F, -1.0F, 1.0F));
            graphics.pose().scale(
                    16.0F * PREVIEW_SCALE * PREVIEW_MODEL_SCALE,
                    16.0F * PREVIEW_SCALE * PREVIEW_MODEL_SCALE,
                    16.0F * PREVIEW_SCALE * PREVIEW_MODEL_SCALE
            );
            graphics.pose().mulPose(Axis.XP.rotationDegrees(PREVIEW_TILT_DEGREES));
            graphics.pose().mulPose(Axis.YP.rotationDegrees(225.0F + previewRotationDegrees()));
            Lighting.setupFor3DItems();
            CommonGarmentRenderer.renderPreview(
                    garment.get(),
                    graphics.pose(),
                    graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY
            );
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
        graphics.drawCenteredString(font, previewStack.getHoverName(), rightPanelCenter, height / 2 + 42, 0xFFFFFF);
    }

    private static float previewRotationDegrees() {
        return (Util.getMillis() % PREVIEW_ROTATION_PERIOD_MILLIS)
                * 360.0F
                / PREVIEW_ROTATION_PERIOD_MILLIS;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return selectionMenu.mouseScrolled(delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }
}
