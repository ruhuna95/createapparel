package com.rexhub.createapparel.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

final class FashionLockButton extends Button {
    private final BooleanSupplier locked;

    FashionLockButton(int x, int y, OnPress onPress, BooleanSupplier locked) {
        super(x, y, 18, 18, Component.translatable("screen.createapparel.fashion.lock"), onPress, DEFAULT_NARRATION);
        this.locked = locked;
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int textColor) {
        boolean closed = locked.getAsBoolean();
        int x = getX() + 4;
        int y = getY() + 3;
        int color = active ? (closed ? 0xFFFFD36A : 0xFFE7E7E7) : 0xFF888888;
        int shackleX = x + (closed ? 2 : 5);
        graphics.fill(shackleX, y, shackleX + 5, y + 2, color);
        graphics.fill(shackleX, y + 1, shackleX + 2, y + (closed ? 6 : 3), color);
        graphics.fill(shackleX + 4, y + 1, shackleX + 6, y + 6, color);
        graphics.fill(x, y + 5, x + 10, y + 12, color);
        graphics.fill(x + 4, y + 7, x + 6, y + 10, 0xFF333333);
    }
}
