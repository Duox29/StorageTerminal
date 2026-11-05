package com.duox.storagemanager.gui.widgets;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/* Vanilla Minecraft button with a subtle marker for selected toggles. */
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
        this.extractDefaultSprite(graphics);
        this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));

        if (isActiveSupplier.getAsBoolean()) {
            graphics.fill(getX() + 4, getY() + height - 4,
                    getX() + width - 4, getY() + height - 2, StorageTheme.BTN_ACTIVE_BORDER);
        }
    }
}
