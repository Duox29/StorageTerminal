package com.duox.storagemanager.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/* Flat button with hover and active states. */
public class ModernButton extends Button {
    private BooleanSupplier isActiveSupplier = () -> false;

    public ModernButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public ModernButton setActiveSupplier(BooleanSupplier supplier) {
        this.isActiveSupplier = supplier;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        boolean active = isActiveSupplier.getAsBoolean();

        int bgColor = hovered ? StorageTheme.BTN_HOVER_BG : StorageTheme.BTN_NORMAL_BG;
        int borderColor = active ? StorageTheme.BTN_ACTIVE_BORDER
                : (hovered ? StorageTheme.BTN_HOVER_BORDER : StorageTheme.BTN_NORMAL_BORDER);
        int textColor = hovered || active ? 0xFFFFFFFF : 0xFFAAAAAA;

        graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
        graphics.outline(getX(), getY(), width, height, borderColor);
        graphics.centeredText(Minecraft.getInstance().font, getMessage(),
                getX() + width / 2, getY() + (height - 8) / 2, textColor);
    }
}
