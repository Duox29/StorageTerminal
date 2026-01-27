package com.duox.storagemanager.gui.widgets;

import com.duox.storagemanager.modules.StorageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Custom mouse scroll event handler for item slots in storage GUIs.
 * Provides fine-grained control over scroll behavior with priority-based event
 * handling.
 *
 * @param <T> The type of item entry (e.g., ItemEntry from StoragePanel or
 *            StorageScreen)
 */
public class SlotScrollHandler<T> {

    /**
     * Interface for extracting item data from entry objects.
     */
    public interface ItemEntryAccessor<T> {
        String getId(T entry);

        int getTotalCount(T entry);
    }

    private final StorageManager storageManager;
    private final ItemEntryAccessor<T> accessor;
    private final Minecraft mc;

    public SlotScrollHandler(StorageManager manager, ItemEntryAccessor<T> accessor) {
        this.storageManager = manager;
        this.accessor = accessor;
        this.mc = Minecraft.getInstance();
    }

    /**
     * Handles scroll event over a slot.
     *
     * @param mouseX         Mouse X position
     * @param mouseY         Mouse Y position
     * @param scrollX        Horizontal scroll delta
     * @param scrollY        Vertical scroll delta (positive = up, negative = down)
     * @param guiX           Grid origin X
     * @param guiY           Grid origin Y
     * @param slotSize       Slot size in pixels
     * @param gridCols       Number of columns
     * @param gridRows       Number of visible rows
     * @param scrollPosition Current scroll position
     * @param filteredItems  List of filtered items
     * @return true if event was handled (slot scroll), false if should fallback to
     *         list scroll
     */
    public boolean handleScroll(
            double mouseX, double mouseY, double scrollX, double scrollY,
            int guiX, int guiY,
            int slotSize, int gridCols, int gridRows,
            float scrollPosition,
            List<T> filteredItems) {

        System.out.println("[SlotScrollHandler] handleScroll called");
        System.out.println("  -> Mouse: (" + mouseX + ", " + mouseY + "), Delta: " + scrollY);
        System.out.println("  -> Grid origin: (" + guiX + ", " + guiY + "), SlotSize: " + slotSize);

        // Detect hovered slot
        ItemSlotHelper.SlotHoverResult<T> result = ItemSlotHelper.getHoveredSlot(
                mouseX, mouseY, guiX, guiY, slotSize, gridCols, gridRows,
                scrollPosition, filteredItems);

        System.out.println("  -> Hover result valid: " + result.isValid());

        // If not hovering over any slot, return false to allow normal list scrolling
        if (!result.isValid()) {
            System.out.println("  -> No slot hovered, returning false");
            return false;
        }

        // Adjust quantity based on scroll
        T entry = result.entry;
        String itemId = accessor.getId(entry);
        int maxCount = accessor.getTotalCount(entry);

        System.out.println("  -> Hovered item: " + itemId + " (max: " + maxCount + ")");

        int currentQueued = storageManager.getRequestQueue().getOrDefault(itemId, 0);
        // FIX: Use the Minecraft instance (mc) instead of the static Screen method
        boolean shiftHeld = mc.hasShiftDown();

        System.out.println("  -> Current queued: " + currentQueued + ", Shift: " + shiftHeld);

        int newQueued = ItemSlotHelper.calculateScrolledQuantity(
                currentQueued, maxCount, scrollY, shiftHeld);

        System.out.println("  -> New queued: " + newQueued);

        // Update request queue
        if (newQueued == 0) {
            storageManager.getRequestQueue().remove(itemId);
            System.out.println("  -> Removed from queue");
        } else {
            storageManager.getRequestQueue().put(itemId, newQueued);
            System.out.println("  -> Updated queue: " + itemId + " = " + newQueued);
        }

        // Play feedback sound
        mc.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        SoundEvents.UI_BUTTON_CLICK, 1.0F));

        System.out.println("  -> Event handled, returning true");
        return true; // Event handled, prevent list scrolling
    }
}
