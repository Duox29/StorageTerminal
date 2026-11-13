package com.duox.storagemanager.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Minecraft's chest texture, sliced so storage panels can have any size. */
public final class VanillaStorageGui {
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int CHEST_WIDTH = 176;
    private static final int CHEST_HEIGHT = 222;
    private static final int CORNER = 4;

    private VanillaStorageGui() {}

    /*
     * GUI scale 3+ leaves the window narrower than some GUI widths. A blit whose
     * width shrinks to zero makes the renderer push a 0-wide scissor, which
     * vanilla rejects with "Scissor size must be >0". Skip empty slices instead.
     */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;

        int innerWidth = Math.max(0, width - CORNER * 2);
        int innerHeight = Math.max(0, height - CORNER * 2);
        int edgeWidth = Math.min(CORNER, width / 2);
        int edgeHeight = Math.min(CORNER, height / 2);

        // The blank header of the vanilla chest is the center/edge sample, so
        // no inventory slots get stretched into the panel's background.
        slice(graphics, x + edgeWidth, y + edgeHeight, innerWidth, innerHeight, 9, 9, 1, 1);
        slice(graphics, x + edgeWidth, y, innerWidth, edgeHeight, 9, 0, 1, CORNER);
        slice(graphics, x + edgeWidth, y + height - edgeHeight, innerWidth, edgeHeight,
                9, CHEST_HEIGHT - CORNER, 1, CORNER);
        slice(graphics, x, y + edgeHeight, edgeWidth, innerHeight, 0, 9, CORNER, 1);
        slice(graphics, x + width - edgeWidth, y + edgeHeight, edgeWidth, innerHeight,
                CHEST_WIDTH - CORNER, 9, CORNER, 1);

        slice(graphics, x, y, edgeWidth, edgeHeight, 0, 0, CORNER, CORNER);
        slice(graphics, x + width - edgeWidth, y, edgeWidth, edgeHeight,
                CHEST_WIDTH - CORNER, 0, CORNER, CORNER);
        slice(graphics, x, y + height - edgeHeight, edgeWidth, edgeHeight,
                0, CHEST_HEIGHT - CORNER, CORNER, CORNER);
        slice(graphics, x + width - edgeWidth, y + height - edgeHeight, edgeWidth, edgeHeight,
                CHEST_WIDTH - CORNER, CHEST_HEIGHT - CORNER, CORNER, CORNER);
    }

    /** The exact 18x18 recessed slot found in vanilla chest screens, scaled to size. */
    public static void slot(GuiGraphics graphics, int x, int y, int size) {
        if (size <= 0) return;
        slice(graphics, x, y, size, size, 7, 17, 18, 18);
    }

    /** A recessed inset for scroll tracks and other inventory controls. */
    public static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 2 || height <= 2) return;
        graphics.fill(x, y, x + width, y + height, 0xFF8B8B8B);
        graphics.fill(x, y, x + width - 1, y + 1, 0xFF373737);
        graphics.fill(x, y, x + 1, y + height - 1, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF555555);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, 0xFFFFFFFF);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, 0xFFFFFFFF);
    }

    private static void slice(GuiGraphics graphics, int x, int y, int width, int height,
                              int u, int v, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0) return;

        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        int left = Math.max(0, x);
        int top = Math.max(0, y);
        int right = Math.min(guiWidth, x + width);
        int bottom = Math.min(guiHeight, y + height);
        if (right <= left || bottom <= top) return;

        int sourceWidthAt = Math.max(1, width);
        int sourceHeightAt = Math.max(1, height);
        int uAt = u + (left - x) * sourceWidth / sourceWidthAt;
        int vAt = v + (top - y) * sourceHeight / sourceHeightAt;
        graphics.blit(CHEST, left, top, right - left, bottom - top, (float) uAt, (float) vAt,
                sourceWidth, sourceHeight, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
