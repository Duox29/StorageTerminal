package com.duox.storagemanager.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;

/* Vanilla Minecraft button with green text for selected toggles. */
public class ModernButton extends Button {
    private static final WidgetSprites BUTTON_SPRITES = new WidgetSprites(ResourceLocation.withDefaultNamespace("widget/button"), ResourceLocation.withDefaultNamespace("widget/button_disabled"), ResourceLocation.withDefaultNamespace("widget/button_highlighted"));
    private BooleanSupplier isActiveSupplier;
    private ResourceLocation icon;
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
        icon = ResourceLocation.fromNamespaceAndPath("buttons", name + ".png");
        iconTextureSize = textureSize;
        setTooltip(Tooltip.create(getMessage()));
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blitSprite(BUTTON_SPRITES.get(active, isHoveredOrFocused()), getX(), getY(), getWidth(), getHeight());
        if (icon == null) {
            int color = isActiveSupplier == null ? (active ? 0xFFFFFFFF : 0xFFA0A0A0)
                    : isActiveSupplier.getAsBoolean() ? StorageTheme.BTN_ACTIVE_TEXT : StorageTheme.BTN_INACTIVE_TEXT;
            renderString(graphics, Minecraft.getInstance().font, color);
            return;
        }
        int size = Math.min(16, Math.min(getWidth() - 4, getHeight() - 4));
        if (size <= 0) return;
        int iconX = getX() + (getWidth() - size) / 2;
        int iconY = getY() + (getHeight() - size) / 2;
        graphics.blit(icon, iconX, iconY, size, size, 0.0f, 0.0f,
                iconTextureSize, iconTextureSize, iconTextureSize, iconTextureSize);
        if (isActiveSupplier != null && isActiveSupplier.getAsBoolean()) {
            graphics.fill(getX() + 3, getY() + getHeight() - 3, getX() + getWidth() - 3,
                    getY() + getHeight() - 2, 0xFF55FF55);
        }
    }
}
