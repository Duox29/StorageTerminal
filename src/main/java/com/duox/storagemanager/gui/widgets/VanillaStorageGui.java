package com.duox.storagemanager.gui.widgets;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Minecraft's chest texture, sliced so storage panels can have any size. */
public final class VanillaStorageGui {
    private static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;
    private static final int TEXTURE_SIZE = 256;
    private static final int CHEST_WIDTH = 176;
    private static final int CHEST_HEIGHT = 222;
    private static final int CORNER = 4;

    private VanillaStorageGui() {}

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (width < CORNER * 2 || height < CORNER * 2) return;

        int innerWidth = width - CORNER * 2;
        int innerHeight = height - CORNER * 2;

        // The blank header of the vanilla chest is the center/edge sample, so
        // no inventory slots get stretched into the panel's background.
        slice(graphics, x + CORNER, y + CORNER, innerWidth, innerHeight, 9, 9, 1, 1);
        slice(graphics, x + CORNER, y, innerWidth, CORNER, 9, 0, 1, CORNER);
        slice(graphics, x + CORNER, y + height - CORNER, innerWidth, CORNER,
                9, CHEST_HEIGHT - CORNER, 1, CORNER);
        slice(graphics, x, y + CORNER, CORNER, innerHeight, 0, 9, CORNER, 1);
        slice(graphics, x + width - CORNER, y + CORNER, CORNER, innerHeight,
                CHEST_WIDTH - CORNER, 9, CORNER, 1);

        slice(graphics, x, y, CORNER, CORNER, 0, 0, CORNER, CORNER);
        slice(graphics, x + width - CORNER, y, CORNER, CORNER,
                CHEST_WIDTH - CORNER, 0, CORNER, CORNER);
        slice(graphics, x, y + height - CORNER, CORNER, CORNER,
                0, CHEST_HEIGHT - CORNER, CORNER, CORNER);
        slice(graphics, x + width - CORNER, y + height - CORNER, CORNER, CORNER,
                CHEST_WIDTH - CORNER, CHEST_HEIGHT - CORNER, CORNER, CORNER);
    }

    /** Render the exact 18x18 recessed slot found in vanilla chest screens. */
    public static void slot(GuiGraphicsExtractor graphics, int x, int y, int size) {
        slice(graphics, x, y, size, size, 7, 17, 18, 18);
    }

    /** A recessed inset for scroll tracks and other inventory controls. */
    public static void inset(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF8B8B8B);
        graphics.fill(x, y, x + width - 1, y + 1, 0xFF373737);
        graphics.fill(x, y, x + 1, y + height - 1, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF555555);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, 0xFFFFFFFF);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, 0xFFFFFFFF);
    }

    private static void slice(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(PIPELINE, CHEST, x, y, u, v, width, height,
                sourceWidth, sourceHeight, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
