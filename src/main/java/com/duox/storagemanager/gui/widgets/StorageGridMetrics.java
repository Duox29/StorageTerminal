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
        int slotSize = Math.max(1, (int) (18 * scale));
        int gridXOffset = Math.max(0, (int) (gridXBase * scale));
        int gridYOffset = Math.max(0, (int) (gridYBase * scale));
        // Keep room for the scrollbar and the panel border at small GUI scales.
        int rightSpace = Math.max(19, (int) (extraWidthBase * scale));
        int width = gridXOffset + (cols * slotSize) + rightSpace;
        int height = gridYOffset + (rows * slotSize) + Math.max(0, (int) (extraHeightBase * scale));
        return new StorageGridMetrics(width, height, cols, rows, slotSize,
                gridXOffset, gridYOffset, (float) scale);
    }

    public ItemGridView.Layout layoutAt(int originX, int originY,
                                        int scrollbarRightInset, int scrollbarWidth) {

        int scrollbarRight = Math.min(originX + width - 1, originX + width - scrollbarRightInset);
        int scrollbarLeft = Math.max(originX + gridXOffset, scrollbarRight - Math.max(1, scrollbarWidth));
        return new ItemGridView.Layout(
                originX + gridXOffset, originY + gridYOffset,
                cols, rows, slotSize,
                scrollbarLeft, Math.max(1, scrollbarRight - scrollbarLeft),
                itemScale);
    }
}
