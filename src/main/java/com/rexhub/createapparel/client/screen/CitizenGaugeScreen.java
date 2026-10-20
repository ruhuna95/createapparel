package com.rexhub.createapparel.client.screen;

import com.rexhub.createapparel.citizen.population.CitizenGauge;
import com.rexhub.createapparel.client.citizen.DistrictGaugeIcon;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.menu.CitizenGaugeMenu;
import com.rexhub.createapparel.network.*;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public final class CitizenGaugeScreen extends AbstractContainerScreen<CitizenGaugeMenu> {
    public static final int DISTRICT_WIDTH = 312;
    public static final int DISTRICT_HEIGHT = 192;
    public static final int SOURCE_HEIGHT = 104;
    private EditBox range, target;
    private GaugeEditBox name;
    private IconButton copy, confirm;
    private Button flowToggle;
    private CitizenGauge.Flow flow = CitizenGauge.Flow.INFLUX;
    private boolean initialized, districtLayout;

    public CitizenGaugeScreen(CitizenGaugeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        districtLayout = menu.info().hasUUID("District");
        imageWidth = districtLayout ? DISTRICT_WIDTH : 200;
        imageHeight = districtLayout ? DISTRICT_HEIGHT : SOURCE_HEIGHT;
    }
    private static Component text(String key, Object... args) { return Component.translatable("screen.createapparel.gauge." + key, args); }
    private IconButton icon(int x, int y, ScreenElement icon, Component tooltip, Runnable action) {
        var button = addRenderableWidget(new GaugeIconButton(leftPos + x, topPos + y, icon));
        button.withCallback(action); button.setToolTip(tooltip); return button;
    }
    @Override protected void init() {
        super.init(); initialized = false;
        if (districtLayout) leftPos -= 32;
        range = addRenderableWidget(districtLayout ? new GaugeEditBox(font, leftPos + 230, topPos + 50, 58, 16, text("range"))
                : new EditBox(font, leftPos + 38, topPos + 110, 40, 10, text("range")));
        target = addRenderableWidget(districtLayout ? new GaugeEditBox(font, leftPos + 230, topPos + 82, 58, 16, text("target"))
                : new EditBox(font, leftPos + 100, topPos + 110, 40, 10, text("target")));
        range.setBordered(false); target.setBordered(false); range.setTextColor(0x555555); target.setTextColor(0x555555);
        range.setMaxLength(3); target.setMaxLength(4);
        range.setFilter(value -> value.matches("[0-9]*")); target.setFilter(value -> value.matches("[0-9]*"));
        name = addRenderableWidget(new GaugeEditBox(font, leftPos + 12, topPos + imageHeight - 26, imageWidth - 82, 16, text("name")));
        name.setBordered(false); name.setTextColor(0x555555); name.setMaxLength(CitizenGauge.MAX_NAME_LENGTH);
        icon(districtLayout ? 18 : 31, districtLayout ? 47 : 25, AllIcons.I_ADD, text("connect"), () -> { send(CitizenGaugeActionPacket.Action.CONNECT, 0, 0, null); onClose(); });
        icon(districtLayout ? 18 : 31, districtLayout ? 67 : 45, AllIcons.I_MOVE_GAUGE, text("relocate"), () -> { send(CitizenGaugeActionPacket.Action.RELOCATE, 0, 0, null); onClose(); });
        int footerButtonY = imageHeight - (districtLayout ? 33 : 25);
        copy = icon(districtLayout ? imageWidth - 55 : 16, footerButtonY, CitizenGaugeScreen::renderCopyIcon, text("copy"), () -> {
            var info = menu.info(); if (info.hasUUID("District")) minecraft.keyboardHandler.setClipboard(info.getUUID("District").toString());
        });
        confirm = icon(imageWidth - 33, footerButtonY, AllIcons.I_CONFIRM, text("save_close"), this::saveAndClose);
        flowToggle = addRenderableWidget(Button.builder(text("switch_outflow"), button -> {
            flow = flow == CitizenGauge.Flow.INFLUX ? CitizenGauge.Flow.OUTFLOW : CitizenGauge.Flow.INFLUX;
            updateFlowButton();
        }).bounds(leftPos + 12, topPos + footerButtonY, 140, 18).build());
        flowToggle.visible = !districtLayout;
        range.visible = target.visible = name.visible = copy.visible = districtLayout;
        confirm.active = false;
        initializeValues();
    }
    private static void renderCopyIcon(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 2, y + 1, x + 11, y + 12, 0xFF555555);
        graphics.fill(x + 3, y + 2, x + 10, y + 11, 0xFFE0E0E0);
        graphics.fill(x + 5, y + 4, x + 14, y + 15, 0xFF555555);
        graphics.fill(x + 6, y + 5, x + 13, y + 14, 0xFFF2F2F2);
        graphics.fill(x + 8, y + 8, x + 12, y + 9, 0xFF999999);
        graphics.fill(x + 8, y + 11, x + 12, y + 12, 0xFF999999);
    }
    private void saveAndClose() {
        if (!validSettings()) return;
        if (districtLayout) ModNetwork.updateGauge(new CitizenGaugeActionPacket(menu.containerId, menu.gaugeId(), menu.info().getLong("Version"),
                CitizenGaugeActionPacket.Action.SETTINGS, Integer.parseInt(range.getValue()), Integer.parseInt(target.getValue()), null, name.getValue()));
        else send(CitizenGaugeActionPacket.Action.FLOW, 0, flow.ordinal(), null);
        onClose();
    }
    private void updateFlowButton() { flowToggle.setMessage(text(flow == CitizenGauge.Flow.INFLUX ? "switch_outflow" : "switch_influx")); }
    private boolean validSettings() {
        if (!initialized) return false;
        if (!districtLayout) return true;
        try {
            int radius = Integer.parseInt(range.getValue()), population = Integer.parseInt(target.getValue());
            CitizenGauge.normalizedName(name.getValue(), menu.info().getUUID("District"));
            return CitizenGauge.validSettings(radius, population);
        } catch (IllegalArgumentException ignored) { return false; }
    }
    private void send(CitizenGaugeActionPacket.Action action, int radius, int population, @javax.annotation.Nullable java.util.UUID other) {
        ModNetwork.updateGauge(new CitizenGaugeActionPacket(menu.containerId, menu.gaugeId(), menu.info().getLong("Version"), action, radius, population, other));
    }
    @Override protected void containerTick() {
        super.containerTick(); var info = menu.info();
        if (!info.hasUUID("Id")) return;
        if (districtLayout != info.hasUUID("District")) {
            districtLayout = info.hasUUID("District"); imageWidth = districtLayout ? DISTRICT_WIDTH : 200; imageHeight = districtLayout ? DISTRICT_HEIGHT : SOURCE_HEIGHT;
            init(minecraft, width, height);
        }
        initializeValues();
        confirm.active = validSettings();
    }
    private void initializeValues() {
        var info = menu.info();
        if (!initialized && info.hasUUID("Id")) {
            range.setValue(Integer.toString(info.getInt("Range"))); target.setValue(Integer.toString(info.getInt("Target")));
            String fallback = districtLayout ? info.getUUID("District").toString() : "";
            name.setValue(info.getString("Name").equals(fallback) ? "" : info.getString("Name")); name.placeholder = fallback;
            flow = info.getString("Flow").equals(CitizenGauge.Flow.OUTFLOW.name()) ? CitizenGauge.Flow.OUTFLOW : CitizenGauge.Flow.INFLUX;
            updateFlowButton(); initialized = true;
        }
    }
    private int inputValue(EditBox field) {
        try { return Integer.parseInt(field.getValue()); }
        catch (NumberFormatException ignored) { return -1; }
    }
    private boolean invalidRange() { int value = inputValue(range); return value < 1 || value > 256; }
    private boolean invalidTarget() { int value = inputValue(target); return value < 0 || value > inputValue(range); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
        var info = menu.info();
        if (districtLayout && isHovering(18, 94, 18, 18, mouseX, mouseY) || !districtLayout && isHovering(60, 32, imageWidth - 72, 24, mouseX, mouseY)) {
            long last = info.getLong("LastSuccessfulAction");
            var lines = new java.util.ArrayList<Component>(); lines.add(text("diagnostics"));
            if (districtLayout) {
                lines.add(text("population_summary", info.getInt("Population"), info.getInt("Target"))); lines.add(text("effective", info.getInt("Effective")));
                lines.add(text("reserved", info.getInt("Incoming"), info.getInt("Outgoing")));
            }
            lines.add(text("population_status", text("status." + info.getString("PopulationStatus"))));
            lines.add(text("request_status", text("request." + info.getString("RequestStatus")), info.getLong("RequestCreatedAt") < 0 || minecraft.level == null ? 0 : Math.max(0, minecraft.level.getGameTime() - info.getLong("RequestCreatedAt")) / 20));
            lines.add(text("chains", info.getInt("ActiveChains")));
            lines.add(last < 0 ? text("last_action_never") : text("last_action", Math.max(0, (info.getLong("Now") - last) / 20)));
            lines.add(text("blocked", text("blocked." + info.getString("BlockedReason"))));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        if (districtLayout) {
            if (isHovering(12, imageHeight - 38, imageWidth - 82, 28, mouseX, mouseY))
                graphics.renderComponentTooltip(font, List.of(text("name"), Component.literal(name.getValue().isEmpty() ? name.placeholder : name.getValue())), mouseX, mouseY);
            if (isHovering(54, 22, imageWidth - 78, 24, mouseX, mouseY))
                graphics.renderTooltip(font, text("center", BlockPos.of(info.getLong("Center")).toShortString()), mouseX, mouseY);
            if (isHovering(210, 50, 16, 16, mouseX, mouseY)) {
                var warnings = new java.util.ArrayList<Component>();
                if (invalidRange()) warnings.add(text("range_invalid"));
                if (info.getInt("Overlaps") > 0) warnings.add(text(info.getBoolean("StrongOverlap") ? "overlap_strong" : "overlap", info.getInt("Overlaps")));
                if (!warnings.isEmpty()) graphics.renderComponentTooltip(font, warnings, mouseX, mouseY);
            }
            if (invalidTarget() && isHovering(210, 82, 16, 16, mouseX, mouseY))
                graphics.renderTooltip(font, text("target_invalid"), mouseX, mouseY);
        }
        for (var child : children()) {
            if (child instanceof GaugeIconButton button && button.visible && button.isMouseOver(mouseX, mouseY)) {
                graphics.pose().pushPose(); graphics.pose().translate(0, 0, 400);
                graphics.renderComponentTooltip(font, button.getToolTip(), mouseX, mouseY);
                graphics.pose().popPose();
            }
        }
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && key != org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && getFocused() instanceof EditBox field) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) { saveAndClose(); return true; }
            return field.keyPressed(key, scanCode, modifiers);
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    private void bodyStrip(GuiGraphics graphics, AllGuiTextures texture, int y, int sourceY, int height) {
        graphics.blit(texture.location, leftPos + 4, topPos + y, imageWidth - 16, height,
                texture.getStartX() + 4, texture.getStartY() + sourceY, 1, height, 256, 256);
    }
    private int flowContentX(Component label) { return (60 + imageWidth - 12 - (16 + 4 + font.width(label))) / 2; }
    private void factoryBand(GuiGraphics graphics, AllGuiTextures texture, int y, int sourceY, int height) {
        int right = texture == AllGuiTextures.FACTORY_GAUGE_BOTTOM ? 64 : 4;
        int original = texture.getWidth(), rendered = original + imageWidth - 200;
        graphics.blit(texture.location, leftPos, topPos + y, 4, height, texture.getStartX(), texture.getStartY() + sourceY, 4, height, 256, 256);
        graphics.blit(texture.location, leftPos + 4, topPos + y, rendered - 4 - right, height,
                texture.getStartX() + 4, texture.getStartY() + sourceY, original - 4 - right, height, 256, 256);
        graphics.blit(texture.location, leftPos + rendered - right, topPos + y, right, height,
                texture.getStartX() + original - right, texture.getStartY() + sourceY, right, height, 256, 256);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (districtLayout) {
            factoryBand(graphics, AllGuiTextures.FACTORY_GAUGE_RECIPE, 0, 0, 96);
            factoryBand(graphics, AllGuiTextures.FACTORY_GAUGE_BOTTOM, imageHeight - 80, 0, 34);
            bodyStrip(graphics, AllGuiTextures.FACTORY_GAUGE_RECIPE, 16, 16, 80);
            for (int y = 96; y < imageHeight - 48; y += 16) {
                factoryBand(graphics, AllGuiTextures.FACTORY_GAUGE_RECIPE, y, 80, 16);
                bodyStrip(graphics, AllGuiTextures.FACTORY_GAUGE_RECIPE, y, 80, 16);
            }
            for (int row = 0; row < 8; row++) factoryBand(graphics, AllGuiTextures.FACTORY_GAUGE_BOTTOM, imageHeight - 46 + row, 34, 1);
            factoryBand(graphics, AllGuiTextures.FACTORY_GAUGE_BOTTOM, imageHeight - 38, 34, 28);
            for (int row = 0; row < 8; row++) factoryBand(graphics, AllGuiTextures.FACTORY_GAUGE_BOTTOM, imageHeight - 10 + row, 61, 1);
            factoryBand(graphics, AllGuiTextures.FACTORY_GAUGE_BOTTOM, imageHeight - 2, 62, 2);
            graphics.fill(leftPos + 4, topPos + imageHeight - 46, leftPos + imageWidth - 63, topPos + imageHeight - 2, 0xFFC6C6C6);
        } else {
            AllGuiTextures.FACTORY_GAUGE_RECIPE.render(graphics, leftPos, topPos);
            AllGuiTextures.FACTORY_GAUGE_BOTTOM.render(graphics, leftPos, topPos + imageHeight - 64);
            bodyStrip(graphics, AllGuiTextures.FACTORY_GAUGE_RECIPE, 16, 16, imageHeight - 48);
            bodyStrip(graphics, AllGuiTextures.FACTORY_GAUGE_BOTTOM, imageHeight - 32, 32, 32);
        }
        if (districtLayout && menu.info().hasUUID("District"))
            DistrictGaugeIcon.render(graphics, menu.info().getUUID("District"), leftPos + 19, topPos + 23);
        if (!districtLayout) DistrictGaugeIcon.render(graphics, flow == CitizenGauge.Flow.OUTFLOW ? DistrictGaugeIcon.OUTFLOW : DistrictGaugeIcon.INFLUX,
                0xFFFFFF, leftPos + flowContentX(text(flow == CitizenGauge.Flow.INFLUX ? "source" : "outflow")), topPos + 36);
        var preview = new ItemStack(ModItems.CITIZEN_GAUGE.get());
        if (districtLayout) preview.getOrCreateTag().putLong("GaugeCenter", menu.info().getLong("Center"));
        else if (flow == CitizenGauge.Flow.OUTFLOW) preview.getOrCreateTag().putBoolean("GaugeOutflow", true);
        GuiGameElement.of(preview).scale(4).at(0, 0, -200)
                .render(graphics, leftPos + imageWidth - 5, topPos + imageHeight - (districtLayout ? 53 : 45));
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var info = menu.info(); graphics.drawCenteredString(font, districtLayout ? text("district") : title, (imageWidth - 8) / 2, 4, 0xFFFFFF);
        if (!districtLayout) {
            var label = text(flow == CitizenGauge.Flow.INFLUX ? "source" : "outflow");
            graphics.drawString(font, label, flowContentX(label) + 20, 40, 0xE4D5BE, false); return;
        }
        graphics.drawString(font, text("center_label"), 54, 23, 0xE4D5BE, false);
        graphics.drawString(font, font.plainSubstrByWidth(BlockPos.of(info.getLong("Center")).toShortString(), imageWidth - 78), 54, 36, 0xFFFFFF, false);
        graphics.drawString(font, text("range"), 54, 54, 0xE4D5BE, false);
        graphics.drawString(font, text("population_label"), 54, 70, 0xE4D5BE, false);
        graphics.drawString(font, Integer.toString(info.getInt("Population")), 234, 70, info.getInt("Population") == info.getInt("Target") ? 0x8DDB74 : 0xE87070, false);
        graphics.drawString(font, text("target"), 54, 86, 0xE4D5BE, false);
        graphics.drawString(font, text("reserved", info.getInt("Incoming"), info.getInt("Outgoing")), 54, 106, 0xE4D5BE, false);
        long elapsed = minecraft.level == null ? 0 : Math.max(0, minecraft.level.getGameTime() - info.getLong("Now"));
        graphics.drawString(font, text("next_action", Math.max(0, (info.getLong("CooldownUntil") - info.getLong("Now") - elapsed + 19) / 20)), 54, 122, 0xE4D5BE, false);
        graphics.drawString(font, "?", 23, 98, 0xE4D5BE, false);
        graphics.drawString(font, text("name"), 12, imageHeight - 38, 0x555555, false);
        if (invalidRange() || info.getInt("Overlaps") > 0) graphics.drawString(font, "!", 216, 54, invalidRange() ? 0xFF7777 : 0xFFBB55, false);
        if (invalidTarget()) graphics.drawString(font, "!", 216, 86, 0xFF7777, false);
    }

    private static final class GaugeIconButton extends IconButton {
        private GaugeIconButton(int x, int y, ScreenElement icon) { super(x, y, icon); }
        @Override protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
    }

    private static final class GaugeEditBox extends EditBox {
        private final Font inputFont;
        private int viewStart, selectionEnd;
        private String placeholder = "";

        private GaugeEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message); inputFont = font; setBordered(false);
        }
        @Override public void setHighlightPos(int position) {
            super.setHighlightPos(position); selectionEnd = net.minecraft.util.Mth.clamp(position, 0, getValue().length());
        }
        @Override public void onClick(double x, double y) {
            String visible = inputFont.plainSubstrByWidth(getValue().substring(Math.min(viewStart, getValue().length())), width - 8);
            moveCursorTo(Math.min(viewStart, getValue().length()) + inputFont.plainSubstrByWidth(visible, Math.max(0, (int) x - getX() - 4)).length());
        }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!visible) return;
            int x = getX(), y = getY(), cursor = getCursorPosition(), available = width - 8;
            String value = getValue(); viewStart = Math.min(viewStart, cursor);
            while (viewStart < cursor && inputFont.width(value.substring(viewStart, cursor)) > available - 1) viewStart++;
            while (viewStart > 0 && inputFont.width(value.substring(viewStart - 1, cursor)) <= available - 1) viewStart--;
            String shown = inputFont.plainSubstrByWidth(value.substring(viewStart), available);
            int end = viewStart + shown.length(), textY = y + (height - 8) / 2;
            graphics.fill(x, y, x + width, y + height, isFocused() ? 0xFF526E7E : 0xFF888377);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFF5F0E5);
            int from = net.minecraft.util.Mth.clamp(Math.min(cursor, selectionEnd), viewStart, end);
            int to = net.minecraft.util.Mth.clamp(Math.max(cursor, selectionEnd), viewStart, end);
            if (isFocused() && from != to) graphics.fill(x + 4 + inputFont.width(value.substring(viewStart, from)), textY - 1,
                    x + 4 + inputFont.width(value.substring(viewStart, to)), textY + 9, 0xFF96B9D0);
            if (value.isEmpty() && !isFocused() && !placeholder.isEmpty()) {
                float scale = Math.min(1, (float) available / inputFont.width(placeholder));
                graphics.pose().pushPose(); graphics.pose().translate(x + 4, textY, 0); graphics.pose().scale(scale, scale, 1);
                graphics.drawString(inputFont, placeholder, 0, 0, 0xFF777777, false); graphics.pose().popPose();
            } else graphics.drawString(inputFont, shown, x + 4, textY, 0xFF333333, false);
            if (isFocused() && net.minecraft.Util.getMillis() / 300 % 2 == 0) {
                int caret = x + 4 + inputFont.width(value.substring(viewStart, Math.min(cursor, end)));
                graphics.fill(caret, textY - 1, caret + 1, textY + 9, 0xFF333333);
            }
        }
    }
}
