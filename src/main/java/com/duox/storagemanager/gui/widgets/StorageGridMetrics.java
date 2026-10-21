package com.duox.storagemanager.gui.widgets;

/* Shared geometry for both storage GUIs. */
public record StorageGridMetrics(
        int width,
        int height,
        int cols,
        int rows,
        int slotSize,
        int gridXOffset,
        int gridYOffset,
        float itemScale
) {
    public static StorageGridMetrics create(double scale, int cols, int rows,
                                             int gridXBase, int gridYBase,
                                             int extraWidthBase, int extraHeightBase) {
        int slotSize = (int) (18 * scale);
        int gridXOffset = (int) (gridXBase * scale);
        int gridYOffset = (int) (gridYBase * scale);
        int width = gridXOffset + (cols * slotSize) + (int) (extraWidthBase * scale);
        int height = gridYOffset + (rows * slotSize) + (int) (extraHeightBase * scale);
        return new StorageGridMetrics(width, height, cols, rows, slotSize,
                gridXOffset, gridYOffset, (float) scale);
    }

    public ItemGridView.Layout layoutAt(int originX, int originY,
                                        int scrollbarRightInset, int scrollbarWidth) {
        return new ItemGridView.Layout(
                originX + gridXOffset, originY + gridYOffset,
                cols, rows, slotSize,
                originX + width - scrollbarRightInset, scrollbarWidth,
                itemScale);
    }
}
