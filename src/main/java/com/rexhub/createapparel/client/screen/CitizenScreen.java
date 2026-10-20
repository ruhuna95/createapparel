package com.rexhub.createapparel.client.screen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.citizen.CitizenHairStyles;
import com.rexhub.createapparel.client.citizen.ClientCitizenAssets;
import com.rexhub.createapparel.entity.CitizenAppearance;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.entity.CitizenLookMode;
import com.rexhub.createapparel.entity.MannequinForm;
import com.rexhub.createapparel.entity.PosePreset;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
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
    private final Map<CitizenActionPacket.Action, Button> behaviorButtons = new EnumMap<>(CitizenActionPacket.Action.class);
    private Button radiusButton;
    private Button worldRotationButton;
    private Button populationForceButton;
    private Button populationIndependentButton;
    private CitizenBehaviorMode observedMode;

    private CitizenBehaviorState behavior() {
        return menu.citizen() == null ? CitizenBehaviorState.DEFAULT : menu.citizen().citizenState().behavior();
    }

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
        behaviorButtons.clear();
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
        addBehaviorChoice(CitizenActionPacket.Action.BEHAVIOR_MODE, 4, 52,
                List.of(CitizenBehaviorMode.STAND, CitizenBehaviorMode.WANDER, CitizenBehaviorMode.FOLLOW, CitizenBehaviorMode.WALK).stream()
                        .map(CitizenBehaviorMode::id).toList(),
                () -> behavior().mode().id(), "behavior.createapparel.");
        addBehaviorChoice(CitizenActionPacket.Action.STAND_LOOK_MODE, 4, 86,
                List.of(CitizenLookMode.values()).stream().map(CitizenLookMode::id).toList(),
                () -> behavior().lookMode().id(), "look_mode.createapparel.");
        addBehaviorChoice(CitizenActionPacket.Action.POSE_PRESET, 204, 52,
                MannequinForm.FULL.poses().stream().map(PosePreset::id).toList(),
                () -> behavior().pose().id(), "pose.createapparel.");
        radiusButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            var radii = java.util.stream.IntStream.rangeClosed(CitizenBehaviorState.MIN_RADIUS, CitizenBehaviorState.MAX_RADIUS)
                    .mapToObj(radius -> radius == CitizenBehaviorState.UNLIMITED_RADIUS ? "-" : Integer.toString(radius)).toList();
            selectionMenu.open(button, width, height, radii.stream().map(value -> (Component) Component.literal(value)).toList(),
                    List.of(), behavior().wanderRadius(), index -> ModNetwork.updateCitizen(
                            CitizenActionPacket.select(menu, CitizenActionPacket.Action.WANDER_RADIUS, radii.get(index))));
        }).bounds(leftPos + 204, topPos + 52, 92, 18).build());
        buttonScroll.bind(radiusButton, direction -> ModNetwork.updateCitizen(CitizenActionPacket.step(menu, CitizenActionPacket.Action.WANDER_RADIUS, direction)));
        worldRotationButton = addRenderableWidget(Button.builder(Component.empty(), button -> rotateWorld(1))
                .bounds(leftPos + 204, topPos + 86, 92, 18).build());
        buttonScroll.bind(worldRotationButton, this::rotateWorld);
        populationForceButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (menu.citizen() != null) ModNetwork.updateCitizen(CitizenActionPacket.select(menu, CitizenActionPacket.Action.POPULATION_FORCE,
                    Boolean.toString(!menu.citizen().citizenState().population().force())));
        }).bounds(leftPos + 8, topPos + 207, 139, 16).build());
        populationIndependentButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (menu.citizen() != null) ModNetwork.updateCitizen(CitizenActionPacket.select(menu, CitizenActionPacket.Action.POPULATION_INDEPENDENT,
                    Boolean.toString(!menu.citizen().citizenState().population().independent())));
        }).bounds(leftPos + 151, topPos + 207, 141, 16).build());
        updateControls();
        return true;
    }

    private void addBehaviorChoice(CitizenActionPacket.Action action, int x, int y, List<String> options,
                                   Supplier<String> current, String prefix) {
        var button = addRenderableWidget(Button.builder(Component.empty(), pressed ->
                selectionMenu.open(pressed, width, height, options.stream().map(value -> label(prefix, value)).toList(),
                        List.of(), Math.max(0, options.indexOf(current.get())), index ->
                                ModNetwork.updateCitizen(CitizenActionPacket.select(menu, action, options.get(index)))))
                .bounds(leftPos + x, topPos + y, 92, 18).build());
        behaviorButtons.put(action, button);
        buttonScroll.bind(button, direction -> ModNetwork.updateCitizen(CitizenActionPacket.step(menu, action, direction)));
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
        buttonScroll.bind(button, direction -> ModNetwork.updateCitizen(CitizenActionPacket.step(menu, action, direction)));
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

    private void rotateWorld(int direction) {
        ModNetwork.updateCitizen(new CitizenActionPacket(menu.containerId, menu.citizen() == null ? -1 : menu.citizen().getId(),
                CitizenActionPacket.Action.POSE_ROTATION_STEP, direction, "", "step"));
    }

    private void updateControls() {
        if (observedTab != menu.tab() || observedMode != behavior().mode()) selectionMenu.close();
        observedTab = menu.tab();
        observedMode = behavior().mode();
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
        boolean behaviorTab = menu.tab() == CitizenMenu.Tab.BEHAVIOR;
        populationForceButton.visible = populationForceButton.active = behaviorTab;
        populationIndependentButton.visible = populationIndependentButton.active = behaviorTab;
        var population = menu.citizen() == null ? com.rexhub.createapparel.citizen.population.CitizenPopulationState.DEFAULT : menu.citizen().citizenState().population();
        populationForceButton.setMessage(Component.translatable("screen.createapparel.gauge.force", Component.translatable("screen.createapparel.gauge." + (population.force() ? "on" : "off"))));
        populationIndependentButton.setMessage(Component.translatable("screen.createapparel.gauge.independent", Component.translatable("screen.createapparel.gauge." + (population.independent() ? "on" : "off"))));
        behaviorButtons.forEach((action, button) -> {
            button.visible = behaviorTab && (action == CitizenActionPacket.Action.BEHAVIOR_MODE
                    || action == CitizenActionPacket.Action.STAND_LOOK_MODE && behavior().mode() == CitizenBehaviorMode.STAND
                    || action == CitizenActionPacket.Action.POSE_PRESET && behavior().mode() == CitizenBehaviorMode.POSE);
            button.active = button.visible;
        });
        behaviorButtons.get(CitizenActionPacket.Action.BEHAVIOR_MODE).setMessage(label("behavior.createapparel.", behavior().mode().id()));
        behaviorButtons.get(CitizenActionPacket.Action.STAND_LOOK_MODE).setMessage(label("look_mode.createapparel.", behavior().lookMode().id()));
        behaviorButtons.get(CitizenActionPacket.Action.POSE_PRESET).setMessage(label("pose.createapparel.", behavior().pose().id()));
        radiusButton.visible = radiusButton.active = behaviorTab && behavior().mode() == CitizenBehaviorMode.WANDER;
        radiusButton.setMessage(Component.literal(behavior().unlimitedWander() ? "-" : Integer.toString(behavior().wanderRadius())));
        worldRotationButton.visible = worldRotationButton.active = behaviorTab && behavior().mode() == CitizenBehaviorMode.POSE;
        worldRotationButton.setMessage(Component.translatable("screen.createapparel.citizen.world_rotation", menu.poseRotationStep() * 22.5F));
        updateGarmentControls();
    }

    @Override
    protected boolean showGarmentControls() { return menu.tab() == CitizenMenu.Tab.GARMENT; }

    @Override
    protected int garmentControlsOffset() { return 20; }

    @Override
    protected void renderTitle(GuiGraphics graphics) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!selectionMenu.isOpen() && menu.tab() == CitizenMenu.Tab.BEHAVIOR
                && mouseX >= leftPos + 8 && mouseX < leftPos + 292
                && mouseY >= topPos + 177 && mouseY < topPos + 177 + font.lineHeight) {
            graphics.renderTooltip(font, font.split(destinationText(), Math.min(280, width - 24)), mouseX, mouseY);
        }
        if (!selectionMenu.isOpen() && menu.tab() == CitizenMenu.Tab.BEHAVIOR && !menu.districtName().isEmpty()
                && mouseX >= leftPos + 8 && mouseX < leftPos + 292
                && mouseY >= topPos + 192 && mouseY < topPos + 192 + font.lineHeight) {
            graphics.renderTooltip(font, font.split(Component.translatable("screen.createapparel.gauge.membership", menu.districtName()),
                    Math.min(280, width - 24)), mouseX, mouseY);
        }
    }

    private Component destinationText() {
        var destination = menu.destination();
        return Component.translatable("screen.createapparel.citizen.destination",
                destination == null ? Component.translatable("screen.createapparel.citizen.destination.none")
                        : Component.literal(destination.toShortString()));
    }

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
        float previousBodyNow = citizen.yBodyRot;
        float previousBody = citizen.yBodyRotO;
        float previousHead = citizen.yHeadRot;
        float previousHeadOld = citizen.yHeadRotO;
        try {
            float poseRotation = behavior().mode() == CitizenBehaviorMode.POSE ? menu.poseRotationStep() * 22.5F : 0;
            float yaw = (float) Math.atan((leftPos + 150 - mouseX) / 40.0F) * 20 + previewRotation + poseRotation;
            float pitch = (float) Math.atan((topPos + 49 - mouseY) / 40.0F) * 20 * Mth.DEG_TO_RAD;
            float relativeHead = Mth.wrapDegrees(previousHead - previousBodyNow);
            citizen.yBodyRot = citizen.yBodyRotO = 180 + yaw;
            citizen.yHeadRot = citizen.yHeadRotO = citizen.yBodyRot + relativeHead;
            var camera = new Quaternionf().rotateX(pitch);
            var pose = new Quaternionf().rotateZ((float) Math.PI).mul(camera);
            InventoryScreen.renderEntityInInventory(graphics, leftPos + 150, topPos + 108, 35, pose, camera, citizen);
        } finally {
            citizen.yBodyRotO = previousBody;
            citizen.yBodyRot = previousBodyNow;
            citizen.yHeadRot = previousHead;
            citizen.yHeadRotO = previousHeadOld;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (showGarmentControls()) {
            super.renderLabels(graphics, mouseX, mouseY);
            return;
        }
        if (menu.tab() == CitizenMenu.Tab.BEHAVIOR) {
            var name = menu.districtName().isEmpty() ? Component.translatable("screen.createapparel.route.unassigned")
                    : Component.literal(menu.districtName());
            String districtText = Component.translatable("screen.createapparel.gauge.membership", name).getString();
            graphics.drawString(font, font.width(districtText) > 284 ? font.plainSubstrByWidth(districtText, 284 - font.width("…")) + "…" : districtText,
                    8, 192, 0x404040, false);
            var state = behavior();
            String destination = destinationText().getString();
            graphics.drawString(font, font.width(destination) > 284 ? font.plainSubstrByWidth(destination, 284 - font.width("…")) + "…" : destination,
                    8, 177, 0x404040, false);
            graphics.drawString(font, Component.translatable("screen.createapparel.citizen.behavior"), 4, 38, 0x404040, false);
            if (state.mode() == CitizenBehaviorMode.STAND) {
                graphics.drawString(font, Component.translatable("screen.createapparel.citizen.look_at"), 4, 72, 0x404040, false);
            }
            if (state.mode() == CitizenBehaviorMode.WANDER) {
                graphics.drawString(font, Component.translatable("screen.createapparel.citizen.radius"), 204, 38, 0x404040, false);
                graphics.drawString(font, Component.translatable("screen.createapparel.citizen.anchor"), 8, 140, 0x404040, false);
                var anchor = state.anchor();
                graphics.drawString(font, Component.literal(anchor == null ? "—" : anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ()),
                        8, 154, 0x404040, false);
                if (state.anchorDimension() != null) graphics.drawString(font, Component.literal(state.anchorDimension().location().toString()),
                        8, 166, 0x404040, false);
            } else if (state.mode() == CitizenBehaviorMode.POSE) {
                graphics.drawString(font, Component.translatable("screen.createapparel.citizen.pose"), 204, 38, 0x404040, false);
            } else if (state.mode() == CitizenBehaviorMode.FOLLOW) {
                graphics.drawString(font, Component.translatable("screen.createapparel.citizen.target"), 8, 140, 0x404040, false);
                int y = 154;
                for (var line : font.split(menu.followDisplay(), 284)) {
                    graphics.drawString(font, line, 8, y, 0x404040, false);
                    y += 10;
                    if (y > 174) break;
                }
            }
            return;
        }
        for (var action : appearanceButtons.keySet()) {
            var button = appearanceButtons.get(action);
            var text = Component.translatable("screen.createapparel.citizen." + action.name().toLowerCase(java.util.Locale.ROOT));
            graphics.drawString(font, text, button.getX() - leftPos, button.getY() - topPos - 11, 0x404040, false);
        }
    }
}
