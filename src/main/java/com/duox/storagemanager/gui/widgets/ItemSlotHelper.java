package com.duox.storagemanager.gui.widgets;

import java.util.List;

/**
 * Shared utility for item slot detection and coordinate calculations.
 * Used by both StoragePanel and StorageScreen to avoid code duplication.
 */
public class ItemSlotHelper {

    /**
     * Represents the result of a slot hover detection.
     */
    public static class SlotHoverResult<T> {
        public final T entry;
        public final int slotX;
        public final int slotY;
        public final int index;

        public SlotHoverResult(T entry, int slotX, int slotY, int index) {
            this.entry = entry;
            this.slotX = slotX;
            this.slotY = slotY;
            this.index = index;
        }

        public boolean isValid() {
            return entry != null;
        }
    }

    /**
     * Detects which slot (if any) the mouse is hovering over.
     *
     * @param mouseX         Mouse X position
     * @param mouseY         Mouse Y position
     * @param guiX           Grid origin X
     * @param guiY           Grid origin Y
     * @param slotSize       Size of each slot (typically 18)
     * @param gridCols       Number of columns in the grid
     * @param gridRows       Number of rows visible
     * @param scrollPosition Current scroll position (0.0 to 1.0)
     * @param filteredItems  List of items to search through
     * @return SlotHoverResult containing the hovered entry, or null entry if none
     */
    public static <T> SlotHoverResult<T> getHoveredSlot(
            double mouseX, double mouseY,
            int guiX, int guiY,
            int slotSize, int gridCols, int gridRows,
            float scrollPosition,
            List<T> filteredItems) {

        System.out.println("[ItemSlotHelper] getHoveredSlot called");

        // Check if mouse is within grid bounds
        int gridWidth = gridCols * slotSize;
        int gridHeight = gridRows * slotSize;

        int gridRight = guiX + gridWidth;
        int gridBottom = guiY + gridHeight;

        System.out.println("  -> Grid bounds: (" + guiX + ", " + guiY + ") to (" + gridRight + ", " + gridBottom + ")");
        System.out.println("  -> Mouse: (" + mouseX + ", " + mouseY + ")");

        if (mouseX < guiX || mouseX > guiX + gridWidth ||
                mouseY < guiY || mouseY > guiY + gridHeight) {
            System.out.println("  -> Mouse outside grid bounds");
            return new SlotHoverResult<>(null, 0, 0, -1);
        }

        // Calculate which slot is being hovered
        int col = (int) ((mouseX - guiX) / slotSize);
        int row = (int) ((mouseY - guiY) / slotSize);

        System.out.println("  -> Calculated slot: col=" + col + ", row=" + row);

        // Ensure within bounds
        if (col < 0 || col >= gridCols || row < 0 || row >= gridRows) {
            System.out.println("  -> Slot out of bounds");
            return new SlotHoverResult<>(null, 0, 0, -1);
        }

        // Calculate the actual item index considering scroll
        int totalRows = (int) Math.ceil((double) filteredItems.size() / gridCols);
        int startRow = (int) (scrollPosition * Math.max(0, totalRows - gridRows));
        int index = (startRow + row) * gridCols + col;

        System.out
                .println("  -> Total items: " + filteredItems.size() + ", startRow: " + startRow + ", index: " + index);

        // Check if index is valid
        if (index < 0 || index >= filteredItems.size()) {
            System.out.println("  -> Invalid index (out of items list)");
            return new SlotHoverResult<>(null, 0, 0, -1);
        }

        // Calculate slot screen coordinates
        int slotX = guiX + col * slotSize;
        int slotY = guiY + row * slotSize;

        System.out.println("  -> Valid slot found at index " + index);

        return new SlotHoverResult<>(filteredItems.get(index), slotX, slotY, index);
    }

    /**
     * Calculates new quantity based on scroll delta and modifier keys.
     *
     * @param current     Current quantity
     * @param max         Maximum allowed quantity
     * @param scrollDelta Scroll direction (positive = up, negative = down)
     * @param useShift    Whether Shift key is held
     * @return New clamped quantity
     */
    public static int calculateScrolledQuantity(int current, int max, double scrollDelta, boolean useShift) {
        int increment = useShift ? 64 : 1;
        int change = scrollDelta > 0 ? increment : -increment;
        int newValue = current + change;
        return Math.max(0, Math.min(max, newValue));
    }
}
