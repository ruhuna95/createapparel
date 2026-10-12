package com.rexhub.createapparel.client.screen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.citizen.CitizenHairStyles;
import com.rexhub.createapparel.client.citizen.ClientCitizenAssets;
import com.rexhub.createapparel.entity.CitizenAppearance;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.menu.CitizenMenu;
import com.rexhub.createapparel.network.CitizenActionPacket;
import com.rexhub.createapparel.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class CitizenScreen extends WardrobeScreen<CitizenMenu> {
    private final Map<CitizenActionPacket.Action, Button> appearanceButtons = new EnumMap<>(CitizenActionPacket.Action.class);
    private final Map<CitizenMenu.Tab, Button> tabButtons = new EnumMap<>(CitizenMenu.Tab.class);
    private float previewRotation;
    private Button rotationButton;
    private CitizenMenu.Tab observedTab;

    public CitizenScreen(CitizenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private CitizenAppearance appearance() {
        return menu.citizen() == null ? CitizenAppearance.DEFAULT : menu.citizen().citizenState().appearance();
    }

    @Override
    protected boolean initCustomControls() {
        appearanceButtons.clear();
        tabButtons.clear();
        for (var tab : CitizenMenu.Tab.values()) {
            tabButtons.put(tab, addRenderableWidget(Button.builder(Component.translatable(tabKey(tab)), button -> {
                selectionMenu.close();
                ModNetwork.updateCitizen(CitizenActionPacket.tab(menu, tab));
                menu.setTab(tab);
                updateControls();
            }).bounds(leftPos + 4 + tab.ordinal() * 98, topPos + 6, 96, 18).build()));
        }
        addChoice(CitizenActionPacket.Action.ARM_PROFILE, 4, 48,
                () -> List.of(ArmProfile.values()).stream().map(ArmProfile::getSerializedName).toList(), List.of(),
                () -> appearance().armProfile().getSerializedName(), "arm_profile.createapparel.", null);
        addChoice(CitizenActionPacket.Action.CHEST_PROFILE, 4, 82,
                () -> List.of(ChestProfile.values()).stream().map(ChestProfile::getSerializedName).toList(), List.of(),
                () -> appearance().chestProfile().getSerializedName(), "chest_profile.createapparel.", null);
        addChoice(CitizenActionPacket.Action.SKIN_TONE, 4, 116,
                () -> List.of(SkinTone.values()).stream().map(SkinTone::getSerializedName).toList(),
                List.of(SkinTone.values()).stream().map(SkinTone::tintRgb).toList(),
                () -> appearance().skinTone().getSerializedName(), "skin_tone.createapparel.", () -> appearance().skinTone().tintRgb());
        addChoice(CitizenActionPacket.Action.HAIR_STYLE, 204, 48,
                () -> CitizenHairStyles.BUILT_IN.stream().map(name -> CreateApparel.MOD_ID + ":" + name)
                        .filter(id -> ClientCitizenAssets.INSTANCE.hasStyle(ResourceLocation.parse(id))).toList(),
                List.of(), () -> appearance().hairStyleId().toString(), "hair_style.createapparel.", null);
        List<Integer> palette = List.of(FabricColor.values()).stream().map(FabricColor::tintRgb).toList();
        addChoice(CitizenActionPacket.Action.HAIR_COLOR, 204, 82,
                () -> List.of(FabricColor.values()).stream().map(FabricColor::getSerializedName).toList(), palette,
                () -> colorName(appearance().hairColorRgb()), "", () -> appearance().hairColorRgb());
        addChoice(CitizenActionPacket.Action.EYE_COLOR, 204, 116,
                () -> List.of(FabricColor.values()).stream().map(FabricColor::getSerializedName).toList(), palette,
                () -> colorName(appearance().eyeColorRgb()), "", () -> appearance().eyeColorRgb());
        rotationButton = addRenderableWidget(Button.builder(Component.empty(), button -> rotate(1))
                .bounds(leftPos + 102, topPos + 113, 96, 18).build());
        buttonScroll.bind(rotationButton, this::rotate);
        updateControls();
        return true;
    }

    private void addChoice(CitizenActionPacket.Action action, int x, int y, Supplier<List<String>> values,
                           List<Integer> colors, Supplier<String> current, String prefix,
                           @javax.annotation.Nullable IntSupplier swatch) {
        Button.OnPress onPress = button -> {
            List<String> options = values.get();
            selectionMenu.open(button, width, height, options.stream().map(value -> label(prefix, value)).toList(),
                    colors, Math.max(0, options.indexOf(current.get())), index ->
                            ModNetwork.updateCitizen(CitizenActionPacket.select(menu, action, options.get(index))));
        };
        Button button = swatch == null ? Button.builder(Component.empty(), onPress)
                .bounds(leftPos + x, topPos + y, 92, 18).build()
                : new ColorButton(leftPos + x, topPos + y, 92, 18, onPress, swatch);
        appearanceButtons.put(action, addRenderableWidget(button));
    }

    private static Component label(String prefix, String value) {
        if (prefix.isEmpty()) return FabricColor.fromSerializedName(value)
                .map(color -> (Component) Component.translatable(color.translationKey()))
                .orElse(Component.literal(value));
        return Component.translatable(prefix + (value.startsWith(CreateApparel.MOD_ID + ":")
                ? value.substring(CreateApparel.MOD_ID.length() + 1) : value));
    }

    private static String colorName(int rgb) {
        return List.of(FabricColor.values()).stream().filter(color -> color.tintRgb() == rgb)
                .map(FabricColor::getSerializedName).findFirst().orElse(String.format("#%06X", rgb));
    }

    private static String tabKey(CitizenMenu.Tab tab) {
        return "screen.createapparel.citizen.tab." + tab.name().toLowerCase(java.util.Locale.ROOT);
    }

    private void rotate(int direction) {
        previewRotation = (previewRotation + direction * 22.5F) % 360;
        updateControls();
    }

    private void updateControls() {
        if (observedTab != menu.tab()) selectionMenu.close();
        observedTab = menu.tab();
        appearanceButtons.values().forEach(button -> {
            button.visible = menu.tab() == CitizenMenu.Tab.APPEARANCE;
            button.active = button.visible;
        });
        tabButtons.forEach((tab, button) -> button.active = tab != menu.tab());
        var appearance = appearance();
        appearanceButtons.get(CitizenActionPacket.Action.ARM_PROFILE).setMessage(label("arm_profile.createapparel.", appearance.armProfile().getSerializedName()));
        appearanceButtons.get(CitizenActionPacket.Action.CHEST_PROFILE).setMessage(label("chest_profile.createapparel.", appearance.chestProfile().getSerializedName()));
        appearanceButtons.get(CitizenActionPacket.Action.SKIN_TONE).setMessage(label("skin_tone.createapparel.", appearance.skinTone().getSerializedName()));
        appearanceButtons.get(CitizenActionPacket.Action.HAIR_STYLE).setMessage(label("hair_style.createapparel.", appearance.hairStyleId().toString()));
        appearanceButtons.get(CitizenActionPacket.Action.HAIR_COLOR).setMessage(label("", colorName(appearance.hairColorRgb())));
        appearanceButtons.get(CitizenActionPacket.Action.EYE_COLOR).setMessage(label("", colorName(appearance.eyeColorRgb())));
        rotationButton.setMessage(Component.translatable("screen.createapparel.citizen.preview_rotation", Math.round(previewRotation)));
        updateGarmentControls();
    }

    @Override
    protected boolean showGarmentControls() { return menu.tab() == CitizenMenu.Tab.GARMENT; }

    @Override
    protected int garmentControlsOffset() { return 20; }

    @Override
    protected void renderTitle(GuiGraphics graphics) { }

    @Override
    protected void containerTick() {
        if (minecraft != null && minecraft.player != null && !menu.stillValid(minecraft.player)) {
            minecraft.player.closeContainer();
            return;
        }
        super.containerTick();
        updateControls();
    }

    @Override
    protected void renderPlayerPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        var citizen = menu.citizen();
        if (citizen == null) return;
        float previousBody = citizen.yBodyRotO;
        try {
            float yaw = (float) Math.atan((leftPos + 150 - mouseX) / 40.0F) + previewRotation / 20.0F;
            citizen.yBodyRotO = 180 + yaw * 20;
            InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, leftPos + 150, topPos + 108, 35,
                    yaw, (float) Math.atan((topPos + 49 - mouseY) / 40.0F), citizen);
        } finally {
            citizen.yBodyRotO = previousBody;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (showGarmentControls()) {
            super.renderLabels(graphics, mouseX, mouseY);
            return;
        }
        if (menu.tab() == CitizenMenu.Tab.BEHAVIOR) {
            graphics.drawString(font, Component.translatable("screen.createapparel.citizen.stand"), 8, 38, 0x404040, false);
            return;
        }
        for (var action : appearanceButtons.keySet()) {
            var button = appearanceButtons.get(action);
            var text = Component.translatable("screen.createapparel.citizen." + action.name().toLowerCase(java.util.Locale.ROOT));
            graphics.drawString(font, text, button.getX() - leftPos, button.getY() - topPos - 11, 0x404040, false);
        }
    }
}
