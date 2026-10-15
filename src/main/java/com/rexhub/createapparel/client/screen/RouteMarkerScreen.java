package com.rexhub.createapparel.client.screen;

import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.menu.RouteMarkerMenu;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.network.RouteMarkerActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import javax.annotation.Nullable;
import java.util.*;

public final class RouteMarkerScreen extends AbstractContainerScreen<RouteMarkerMenu> {
    private enum Tab { SETTINGS, TYPE }
    private final Map<AbstractWidget, Tab> groups = new LinkedHashMap<>();
    private final EnumSet<RouteMarkerActionPacket.Action> pending = EnumSet.noneOf(RouteMarkerActionPacket.Action.class);
    private final Map<RouteMarkerActionPacket.Action, Long> draftVersions = new EnumMap<>(RouteMarkerActionPacket.Action.class);
    private final SelectionMenu selection = new SelectionMenu();
    private final ButtonValueScroll buttonScroll = new ButtonValueScroll();
    private EditBox radius, tolerance, district, threshold;
    private Button typeButton, invertButton;
    private RouteMarkerType markerType = RouteMarkerType.NORMAL;
    private CrossingCondition condition = CrossingCondition.UNSET;
    private boolean inverted, updating, initialized, closeRequested, rejectedUpdate;
    private int sequence, inFlightSequence, requestTicks, closingTicks;
    private long inFlightVersion;
    @Nullable private RouteMarkerActionPacket.Action inFlight;
    private Tab tab = Tab.SETTINGS;

    public RouteMarkerScreen(RouteMarkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 340; imageHeight = 210;
    }

    private static net.minecraft.network.chat.MutableComponent label(String key) { return Component.translatable("screen.createapparel.route." + key); }

    @Override protected void init() {
        var drafts = radius == null ? List.<String>of() : List.of(radius.getValue(), tolerance.getValue(), district.getValue(), threshold.getValue());
        boolean wasInitialized = initialized;
        super.init();
        topPos += 12;
        groups.clear(); buttonScroll.clear(); initialized = false; updating = true; selection.close();
        for (var value : Tab.values()) addRenderableWidget(new TabWidget(leftPos + 8 + value.ordinal() * 122, topPos - 23, value));
        radius = field(Tab.SETTINGS, 112, 58, 74, 16, RouteMarkerActionPacket.Action.REGION);
        tolerance = field(Tab.SETTINGS, 112, 88, 74, 16, RouteMarkerActionPacket.Action.REGION);
        typeButton = button(Tab.SETTINGS, 112, 126, 216, Component.empty(), () -> selection.open(typeButton, width, height,
                Arrays.stream(RouteMarkerType.values()).map(type -> (Component) label("type." + type.id())).toList(),
                List.of(), markerType.ordinal(), this::selectType));
        buttonScroll.bind(typeButton, direction -> selectType(Math.floorMod(markerType.ordinal() + direction, RouteMarkerType.values().length)));
        button(Tab.SETTINGS, 10, 184, 152, label("delete_marker").withStyle(ChatFormatting.RED),
                () -> changed(RouteMarkerActionPacket.Action.DELETE));
        district = field(Tab.TYPE, 10, 78, 318, 36, RouteMarkerActionPacket.Action.DISTRICT);
        threshold = field(Tab.TYPE, 122, 82, 64, 2, RouteMarkerActionPacket.Action.CONDITION);
        invertButton = button(Tab.TYPE, 10, 114, 154, Component.empty(), () -> {
            inverted = !inverted; changed(RouteMarkerActionPacket.Action.CONDITION); updateControls();
        });
        if (!drafts.isEmpty()) {
            var fields = List.of(radius, tolerance, district, threshold);
            for (int i = 0; i < fields.size(); i++) fields.get(i).setValue(drafts.get(i));
            initialized = wasInitialized;
        }
        updating = false; updateControls();
    }

    private Button button(Tab group, int x, int y, int width, Component text, Runnable action) {
        var button = addRenderableWidget(Button.builder(text, ignored -> action.run()).bounds(leftPos + x, topPos + y, width, 20).build());
        groups.put(button, group); return button;
    }

    private EditBox field(Tab group, int x, int y, int width, int length, RouteMarkerActionPacket.Action action) {
        var field = new EditBox(font, leftPos + x, topPos + y, width, 18, Component.empty());
        field.setMaxLength(length); field.setResponder(value -> changed(action));
        addRenderableWidget(field); groups.put(field, group); return field;
    }

    private void changed(RouteMarkerActionPacket.Action action) {
        if (!updating && initialized && pending.add(action)) draftVersions.put(action, menu.info().getLong("Version"));
    }

    private void selectType(int index) {
        var next = RouteMarkerType.values()[index];
        if (markerType == next) return;
        markerType = next; pending.remove(RouteMarkerActionPacket.Action.DISTRICT); pending.remove(RouteMarkerActionPacket.Action.CONDITION);
        condition = CrossingCondition.UNSET; inverted = false;
        boolean wasUpdating = updating; updating = true; threshold.setValue(Integer.toString(RouteSettings.DEFAULT_SIGNAL_THRESHOLD)); updating = wasUpdating;
        changed(RouteMarkerActionPacket.Action.TYPE); updateControls();
    }

    @Override protected void containerTick() {
        super.containerTick();
        var marker = menu.info().getCompound("Marker");
        if (marker.isEmpty()) return;
        if (inFlight != null && menu.info().getInt("EditSequence") == inFlightSequence) {
            rejectedUpdate = !menu.info().getBoolean("EditAccepted");
            long acknowledgedVersion = menu.info().getLong("Version");
            if (!rejectedUpdate && acknowledgedVersion >= inFlightVersion && acknowledgedVersion <= inFlightVersion + 1) {
                draftVersions.replaceAll((action, version) -> version == inFlightVersion ? acknowledgedVersion : version);
            }
            inFlight = null;
        }
        updating = true;
        var node = RouteMarker.load(marker);
        if (!pending.contains(RouteMarkerActionPacket.Action.REGION) && inFlight != RouteMarkerActionPacket.Action.REGION) {
            set(radius, Double.toString(node.horizontalRadius())); set(tolerance, Double.toString(node.verticalTolerance()));
        }
        if (!pending.contains(RouteMarkerActionPacket.Action.TYPE) && inFlight != RouteMarkerActionPacket.Action.TYPE) markerType = node.type();
        if (!pending.contains(RouteMarkerActionPacket.Action.DISTRICT) && inFlight != RouteMarkerActionPacket.Action.DISTRICT) set(district, node.districtId() == null ? "" : node.districtId().toString());
        if (!pending.contains(RouteMarkerActionPacket.Action.CONDITION) && inFlight != RouteMarkerActionPacket.Action.CONDITION) {
            condition = node.condition(); inverted = condition.inverted();
            set(threshold, Integer.toString(condition.threshold()));
        }
        initialized = true; updating = false; rejectedUpdate = false;
        if (inFlight != null && ++requestTicks >= 60) {
            inFlight = null; pending.clear(); draftVersions.clear();
            if (minecraft != null && minecraft.player != null) minecraft.player.displayClientMessage(label("update_timeout"), true);
        }
        sendNext(); updateControls();
        if (closeRequested && (inFlight == null && !hasValidPending() || ++closingTicks >= 60)) super.onClose();
    }

    private void set(EditBox field, String value) {
        if (!field.getValue().equals(value) && (!initialized || rejectedUpdate || !field.isFocused() || !equivalentNumber(field.getValue(), value))) field.setValue(value);
    }

    private static boolean equivalentNumber(String first, String second) {
        try { return Double.parseDouble(first) == Double.parseDouble(second); }
        catch (NumberFormatException exception) { return false; }
    }

    private boolean hasValidPending() { return pending.stream().anyMatch(action -> packet(action) != null); }

    private void sendNext() {
        if (inFlight != null || !initialized) return;
        for (var action : List.of(RouteMarkerActionPacket.Action.TYPE, RouteMarkerActionPacket.Action.REGION,
                RouteMarkerActionPacket.Action.DISTRICT, RouteMarkerActionPacket.Action.CONDITION, RouteMarkerActionPacket.Action.DELETE)) {
            if (!pending.contains(action)) continue;
            var packet = packet(action);
            if (packet == null) continue;
            pending.remove(action); draftVersions.remove(action); inFlight = action; inFlightSequence = ++sequence; inFlightVersion = packet.version(); requestTicks = 0;
            ModNetwork.updateRouteMarker(packet); break;
        }
    }

    @Nullable private RouteMarkerActionPacket packet(RouteMarkerActionPacket.Action action) {
        try {
            double r = 0, t = 0;
            String value = "";
            var pos = BlockPos.ZERO;
            int signalThreshold = RouteSettings.DEFAULT_SIGNAL_THRESHOLD;
            if (action == RouteMarkerActionPacket.Action.REGION) {
                r = Double.parseDouble(radius.getValue()); t = Double.parseDouble(tolerance.getValue());
                if (!RouteSettings.validRegion(r, t)) return null;
            } else if (action == RouteMarkerActionPacket.Action.TYPE) value = markerType.name();
            else if (action == RouteMarkerActionPacket.Action.DISTRICT) {
                if (markerType != RouteMarkerType.GATEWAY) return null;
                value = district.getValue().trim();
                if (!value.isEmpty()) UUID.fromString(value);
            } else if (action == RouteMarkerActionPacket.Action.CONDITION) {
                if (markerType != RouteMarkerType.CROSSING) return null;
                pos = condition.pos(); value = condition.mode().name();
                signalThreshold = Integer.parseInt(threshold.getValue());
                if (!RouteSettings.validSignalThreshold(signalThreshold)) return null;
            }
            return new RouteMarkerActionPacket(menu.containerId, menu.markerId(), draftVersions.getOrDefault(action, menu.info().getLong("Version")), action,
                    r, t, value, pos, "", "", "", inverted, 0, sequence + 1, signalThreshold);
        } catch (IllegalArgumentException exception) { return null; }
    }

    private void updateControls() {
        typeButton.setMessage(label("type." + markerType.id()));
        invertButton.setMessage(label("inverted." + inverted));
        groups.forEach((widget, group) -> {
            boolean show = group == tab;
            if (group == Tab.TYPE) show &= widget == district ? markerType == RouteMarkerType.GATEWAY : markerType == RouteMarkerType.CROSSING;
            widget.visible = show; widget.active = show && initialized && !closeRequested;
        });
        int regionColor = pending.contains(RouteMarkerActionPacket.Action.REGION) && packet(RouteMarkerActionPacket.Action.REGION) == null ? 0xFF5555 : 0xE0E0E0;
        radius.setTextColor(regionColor); tolerance.setTextColor(regionColor);
        district.setTextColor(pending.contains(RouteMarkerActionPacket.Action.DISTRICT) && packet(RouteMarkerActionPacket.Action.DISTRICT) == null ? 0xFF5555 : 0xE0E0E0);
        threshold.setTextColor(pending.contains(RouteMarkerActionPacket.Action.CONDITION) && packet(RouteMarkerActionPacket.Action.CONDITION) == null ? 0xFF5555 : 0xE0E0E0);
    }

    @Override public void onClose() {
        selection.close(); closeRequested = true; closingTicks = 0; sendNext();
        if (inFlight == null && !hasValidPending()) super.onClose();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return selection.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return selection.mouseReleased() || super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return selection.mouseScrolled(delta) || buttonScroll.mouseScrolled(mouseX, mouseY, delta)
                || scrollNumber(radius, mouseX, mouseY, delta, RouteSettings.MIN_RADIUS, RouteSettings.MAX_RADIUS)
                || scrollNumber(tolerance, mouseX, mouseY, delta, RouteSettings.MIN_VERTICAL_TOLERANCE, RouteSettings.MAX_VERTICAL_TOLERANCE)
                || scrollThreshold(mouseX, mouseY, delta)
                || super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean scrollThreshold(double mouseX, double mouseY, double delta) {
        if (delta == 0 || !threshold.visible || !threshold.active || !threshold.isMouseOver(mouseX, mouseY)) return false;
        try {
            int current = Integer.parseInt(threshold.getValue());
            int next = Math.max(RouteSettings.MIN_SIGNAL_THRESHOLD, Math.min(RouteSettings.MAX_SIGNAL_THRESHOLD, current + (delta > 0 ? 1 : -1)));
            if (next != current) threshold.setValue(Integer.toString(next));
        } catch (NumberFormatException ignored) { }
        return true;
    }
    private static boolean scrollNumber(EditBox field, double mouseX, double mouseY, double delta, double min, double max) {
        if (delta == 0 || !field.visible || !field.active || !field.isMouseOver(mouseX, mouseY)) return false;
        try {
            double current = Double.parseDouble(field.getValue());
            double next = Math.max(min, Math.min(max, current + Math.copySign(0.5, delta)));
            if (Double.isFinite(current) && next != current) field.setValue(Double.toString(next));
        } catch (NumberFormatException ignored) { }
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        return selection.keyPressed(key) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY); selection.render(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        panel(graphics, leftPos, topPos, imageWidth, imageHeight, 0xFFC6C6C6);
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        graphics.fill(x, y, x + width, y + 1, 0xFFFFFFFF); graphics.fill(x, y, x + 1, y + height, 0xFFFFFFFF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF555555); graphics.fill(x + width - 1, y, x + width, y + height, 0xFF555555);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 8, 0x404040, false);
        if (tab == Tab.SETTINGS) {
            draw(graphics, "radius", 10, 63); draw(graphics, "tolerance", 10, 93); draw(graphics, "marker_type", 10, 132);
            graphics.drawString(font, Component.translatable("screen.createapparel.route.allowed_values", "0.5–16"), 202, 63, 0x606060, false);
            graphics.drawString(font, Component.translatable("screen.createapparel.route.allowed_values", "0–8"), 202, 93, 0x606060, false);
        } else if (markerType == RouteMarkerType.GATEWAY) {
            draw(graphics, "district", 10, 56); draw(graphics, menu.info().getBoolean("GatewayEnabled") ? "enabled" : "gateway_disabled", 10, 110);
        } else if (markerType == RouteMarkerType.CROSSING) {
            boolean on = menu.info().getBoolean("NodeOn");
            graphics.drawString(font, label("node_state").append(" ").append(label(on ? "state.on" : "state.off")), 10, 56, on ? 0xCC2222 : 0x404040, false);
            draw(graphics, "threshold", 10, 87);
            graphics.drawString(font, Component.translatable("screen.createapparel.route.allowed_values", "1–15"), 202, 87, 0x606060, false);
            graphics.drawWordWrap(font, label("control_hint"), 10, 148, 318, 0x404040);
        } else draw(graphics, "no_type_settings", 10, 56);
    }

    private void draw(GuiGraphics graphics, String key, int x, int y) { graphics.drawString(font, label(key), x, y, 0x404040, false); }

    private final class TabWidget extends AbstractWidget {
        private final Tab value;
        private TabWidget(int x, int y, Tab value) {
            super(x, y, 120, 24, label(value.name().toLowerCase(Locale.ROOT))); this.value = value;
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = tab == value;
            panel(graphics, getX(), getY() + (selected ? 0 : 3), getWidth(), getHeight() - (selected ? 0 : 3), selected ? 0xFFC6C6C6 : 0xFF999999);
            if (selected) graphics.fill(getX() + 1, getY() + getHeight() - 1, getX() + getWidth() - 1, topPos + 2, 0xFFC6C6C6);
            graphics.drawString(font, getMessage(), getX() + getWidth() / 2 - font.width(getMessage()) / 2, getY() + 8,
                    selected || isHoveredOrFocused() ? 0xFFFFFF : 0x404040, selected);
        }
        @Override public void onClick(double mouseX, double mouseY) { tab = value; selection.close(); updateControls(); }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_SPACE) return false;
            onClick(0, 0); return true;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { output.add(NarratedElementType.TITLE, getMessage()); }
    }
}
