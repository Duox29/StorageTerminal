package com.duox.storagemanager.gui.widgets;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

/* Vanilla Minecraft button with green text for selected toggles. */
public class ModernButton extends Button {
    private BooleanSupplier isActiveSupplier;
    private Identifier icon;
    private int iconTextureSize = 16;

    public ModernButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public ModernButton setActiveSupplier(BooleanSupplier supplier) {
        this.isActiveSupplier = supplier;
        return this;
    }

    public ModernButton setIcon(String name) {
        return setIcon(name, 16);
    }

    public ModernButton setIcon(String name, int textureSize) {
        icon = Identifier.fromNamespaceAndPath("buttons", name + ".png");
        iconTextureSize = textureSize;
        setTooltip(Tooltip.create(getMessage()));
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (isActiveSupplier != null) {
            this.setMessage(getMessage().copy().withColor(isActiveSupplier.getAsBoolean()
                    ? StorageTheme.BTN_ACTIVE_TEXT : StorageTheme.BTN_INACTIVE_TEXT));
        }
        this.extractDefaultSprite(graphics);
        if (icon == null) {
            this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
            return;
        }
        int size = Math.min(16, Math.min(getWidth() - 4, getHeight() - 4));
        if (size <= 0) return;
        int iconX = getX() + (getWidth() - size) / 2;
        int iconY = getY() + (getHeight() - size) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0, 0, size, size,
                iconTextureSize, iconTextureSize, iconTextureSize, iconTextureSize);
        if (isActiveSupplier != null && isActiveSupplier.getAsBoolean()) {
            graphics.fill(getX() + 3, getY() + getHeight() - 3, getX() + getWidth() - 3,
                    getY() + getHeight() - 2, 0xFF55FF55);
        }
    }
}
