package com.rexhub.createapparel.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

public final class ColorButton extends Button {
    private final IntSupplier color;

    public ColorButton(int x, int y, int width, int height, OnPress onPress, IntSupplier color) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.color = color;
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int textColor) {
        int x = getX() + 4;
        int y = getY() + (getHeight() - 12) / 2;
        graphics.fill(x, y, x + 12, y + 12, 0xFF111111);
        graphics.fill(x + 1, y + 1, x + 11, y + 11, 0xFF000000 | color.getAsInt());
        int textWidth = font.width(getMessage());
        if (textWidth <= getWidth() - 24) {
            graphics.drawString(font, getMessage(), getX() + textLeft(getWidth(), textWidth),
                    getY() + (getHeight() - 9) / 2 + 1, textColor);
        } else {
            renderScrollingString(graphics, font, getMessage(), getX() + 20, getY(),
                    getX() + getWidth() - 4, getY() + getHeight(), textColor);
        }
    }

    static int textLeft(int buttonWidth, int textWidth) {
        return Math.max(20, (buttonWidth - textWidth) / 2);
    }
}
