package com.duox.storagemanager.gui.widgets;

import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ItemFilterCategory;
import com.duox.storagemanager.utils.ChestCache;
import com.duox.storagemanager.utils.ItemSerializer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* Aggregates, filters, and renders cached items. */
public class ItemGridView {
    private static final float COUNT_SCALE = 0.75f;

    /* Grid geometry rebuilt when the screen layout changes. */
    public record Layout(int gridX, int gridY, int cols, int rows, int slotSize,
                         int scrollbarX, int scrollbarWidth, float itemScale) {
        int gridWidth() {
            return cols * slotSize;
        }

        int gridHeight() {
            return rows * slotSize;
        }
    }

    private static final int MIN_THUMB_HEIGHT = 24;

    public record ItemSelection(String id, ItemStack stack, int totalCount) {}

    private final Minecraft mc = Minecraft.getInstance();
    private final StorageManager storageManager;

    private final List<ItemEntry> allItems = new ArrayList<>();
    private List<ItemEntry> filteredItems = new ArrayList<>();

    private String query = "";
    private ItemFilterCategory category = ItemFilterCategory.ALL;

    private float scrollPosition = 0.0f;
    private boolean draggingScrollbar = false;
    private int scrollbarDragStartY = 0;
    private float scrollPositionAtDragStart = 0.0f;
    private long lastCacheVersion = -1;

    public ItemGridView(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    /* Rebuilds the list when the cache version changes. */
    public void refreshIfDirty() {
        if (ChestCache.getVersion() != lastCacheVersion) {
            refreshFromCache();
        }
    }

    /* Re-aggregates the chests inside the active scan range. */
    public void refreshFromCache() {
        lastCacheVersion = ChestCache.getVersion();
        Map<String, Integer> totals = new HashMap<>();
        for (Map<String, Integer> contents : ChestCache.getActiveCache().values()) {
            for (Map.Entry<String, Integer> entry : contents.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
            }
        }

        allItems.clear();
        totals.forEach((id, count) -> allItems.add(new ItemEntry(id, count)));
        allItems.sort((a, b) -> Integer.compare(b.totalCount, a.totalCount));
        applyFilter();
    }

    public void setQuery(String query) {
        this.query = query.toLowerCase();
        this.scrollPosition = 0.0f;
        applyFilter();
    }

    public void setCategory(ItemFilterCategory category) {
        this.category = category;
        applyFilter();
    }

    public ItemFilterCategory getCategory() {
        return category;
    }

    private void applyFilter() {
        filteredItems = allItems.stream()
                .filter(entry -> category.matches(entry.stack))
                .filter(entry -> query.isEmpty()
                        || entry.stack.getHoverName().getString().toLowerCase().contains(query)
                        || entry.id.contains(query))
                .toList();
        scrollPosition = Mth.clamp(scrollPosition, 0.0f, 1.0f);
    }

    public void render(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        renderSlotBackgrounds(graphics, layout);
        renderItems(graphics, layout, mouseX, mouseY);
        renderScrollbar(graphics, layout);
    }

    private void renderSlotBackgrounds(GuiGraphicsExtractor graphics, Layout layout) {
        // Slots clipped by a window edge would push an empty clip rectangle.
        int right = Math.min(graphics.guiWidth(), layout.gridX() + layout.gridWidth());
        int bottom = Math.min(graphics.guiHeight(), layout.gridY() + layout.gridHeight());
        for (int row = 0; row < layout.rows(); row++) {
            for (int col = 0; col < layout.cols(); col++) {
                int x = layout.gridX() + col * layout.slotSize();
                int y = layout.gridY() + row * layout.slotSize();
                if (y >= bottom) return;
                if (x >= right) break;
                VanillaStorageGui.slot(graphics, x, y, layout.slotSize());
            }
        }
    }

    private void renderItems(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        int totalRows = totalRows(layout);
        int startIndex = (int) (scrollPosition * Math.max(0, totalRows - layout.rows())) * layout.cols();
        int endIndex = Math.min(startIndex + layout.rows() * layout.cols(), filteredItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            ItemEntry entry = filteredItems.get(i);
            int relIndex = i - startIndex;
            int x = layout.gridX() + (relIndex % layout.cols()) * layout.slotSize();
            int y = layout.gridY() + (relIndex / layout.cols()) * layout.slotSize();
            int slotSize = layout.slotSize();
            if (x + slotSize <= 0 || y + slotSize <= 0) continue;

            boolean hovered = mouseX >= x && mouseX < x + slotSize && mouseY >= y && mouseY < y + slotSize;
            if (hovered && slotSize > 2) {
                graphics.fill(x + 1, y + 1, x + slotSize - 1, y + slotSize - 1,
                        StorageTheme.SLOT_HIGHLIGHT);
            }

            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 1, y + 1);
            graphics.pose().scale(layout.itemScale(), layout.itemScale());
            graphics.item(entry.stack, 0, 0);
            graphics.itemDecorations(mc.font, entry.stack, 0, 0, "");
            String count = shortenedCount(entry.totalCount);
            graphics.pose().pushMatrix();
            graphics.pose().translate(16 - mc.font.width(count) * COUNT_SCALE,
                    16 - mc.font.lineHeight * COUNT_SCALE);
            graphics.pose().scale(COUNT_SCALE, COUNT_SCALE);
            graphics.text(mc.font, count, 0, 0, 0xFFFFFFFF, true);
            graphics.pose().popMatrix();
            graphics.pose().popMatrix();

            int queued = storageManager.getQueued(entry.id);
            if (queued > 0 && slotSize > 2) {
                graphics.outline(x, y, slotSize , slotSize ,
                        StorageTheme.BTN_ACTIVE_BORDER);
            }

            if (hovered) {
                List<Component> tooltip = new ArrayList<>(entry.stack.getTooltipLines(
                        Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL));
                tooltip.add(Component.literal("§7Stored: §f" + entry.totalCount));
                if (queued > 0) {
                    tooltip.add(Component.literal("§eRequesting: " + queued));
                }
                tooltip.add(Component.literal("§8[L-Click: +64 | R-Click: +1 | Shift: Remove]"));
                tooltip.add(Component.literal("§8[Ctrl-Click: request 1 stack]"));
                tooltip.add(Component.literal("§8[Drag to inventory: retrieve]"));
                graphics.setTooltipForNextFrame(mc.font, tooltip, entry.stack.getTooltipImage(), mouseX, mouseY);
            }
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, Layout layout) {
        int x = layout.scrollbarX();
        int y = layout.gridY();
        int width = layout.scrollbarWidth();
        int height = layout.gridHeight();
        if (width <= 0 || height <= 0) return;

        VanillaStorageGui.inset(graphics, x, y, width, height);

        int totalRows = totalRows(layout);
        if (totalRows <= layout.rows()) {
            return;
        }

        int thumbHeight = thumbHeight(height, totalRows, layout.rows());
        int thumbY = y + (int) ((height - thumbHeight) * scrollPosition);
        if (width > 4 && thumbHeight > 2) {
            graphics.fill(x + 2, thumbY + 1, x + width - 2, thumbY + thumbHeight - 1,
                    StorageTheme.SCROLLBAR_THUMB);
        }
        if (width > 2) {
            graphics.outline(x + 1, thumbY, width - 2, thumbHeight,
                    StorageTheme.SCROLLBAR_BORDER);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, Layout layout) {
        if (handleSlotScroll(mouseX, mouseY, scrollY, layout)) {
            return true;
        }

        int totalRows = totalRows(layout);
        if (totalRows <= layout.rows()) {
            return false;
        }

        float scrollStep = 1.0f / (totalRows - layout.rows());
        if (scrollY > 0) {
            scrollPosition -= scrollStep;
        } else if (scrollY < 0) {
            scrollPosition += scrollStep;
        }
        scrollPosition = Mth.clamp(scrollPosition, 0.0f, 1.0f);
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, Layout layout) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && isOverScrollbar(mouseX, mouseY, layout)
                && totalRows(layout) > layout.rows()) {
            draggingScrollbar = true;
            scrollbarDragStartY = (int) mouseY;
            scrollPositionAtDragStart = scrollPosition;
            return true;
        }

        int index = indexAt(mouseX, mouseY, layout);
        if (index < 0 || index >= filteredItems.size()) {
            return false;
        }

        applyClick(selectionOf(filteredItems.get(index)), button, mc.hasShiftDown());
        return true;
    }

    public ItemSelection itemAt(double mouseX, double mouseY, Layout layout) {
        refreshIfDirty();
        int index = indexAt(mouseX, mouseY, layout);
        return index >= 0 && index < filteredItems.size() ? selectionOf(filteredItems.get(index)) : null;
    }

    private ItemSelection selectionOf(ItemEntry entry) {
        return new ItemSelection(entry.id, entry.stack.copy(), entry.totalCount);
    }

    public boolean mouseDragged(double mouseY, int button, Layout layout) {
        if (!draggingScrollbar || button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }

        int trackHeight = layout.gridHeight();
        int totalRows = totalRows(layout);
        if (totalRows > layout.rows()) {
            int thumbHeight = thumbHeight(trackHeight, totalRows, layout.rows());
            int maxThumbTravel = trackHeight - thumbHeight;
            if (maxThumbTravel > 0) {
                float deltaScroll = (float) (mouseY - scrollbarDragStartY) / maxThumbTravel;
                scrollPosition = Mth.clamp(scrollPositionAtDragStart + deltaScroll, 0.0f, 1.0f);
            }
        }
        return true;
    }

    public boolean mouseReleased(int button) {
        if (draggingScrollbar && button == InputConstants.MOUSE_BUTTON_LEFT) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    public boolean isDraggingScrollbar() {
        return draggingScrollbar;
    }

    public void applyClick(ItemSelection entry, int button, boolean shiftDown) {
        int change = switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 64;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            default -> 0;
        };
        if (change == 0) {
            return;
        }
        if (shiftDown) {
            change = -change;
        }

        storageManager.adjustQueued(entry.id(), change, entry.totalCount());
        StorageUi.playClick();
    }

    private boolean handleSlotScroll(double mouseX, double mouseY, double scrollY, Layout layout) {
        int index = indexAt(mouseX, mouseY, layout);
        if (index < 0 || index >= filteredItems.size()) {
            return false;
        }

        ItemEntry entry = filteredItems.get(index);
        int increment = mc.hasShiftDown() ? 64 : 1;
        storageManager.adjustQueued(entry.id, scrollY > 0 ? increment : -increment, entry.totalCount);
        StorageUi.playClick();
        return true;
    }

    private int totalRows(Layout layout) {
        return (int) Math.ceil((double) filteredItems.size() / layout.cols());
    }

    /* Slot index under the cursor, or -1 when outside the grid. */
    private int indexAt(double mouseX, double mouseY, Layout layout) {
        if (!isOverGrid(mouseX, mouseY, layout)) {
            return -1;
        }
        int col = (int) ((mouseX - layout.gridX()) / layout.slotSize());
        int row = (int) ((mouseY - layout.gridY()) / layout.slotSize());
        // isOverGrid is inclusive at the borders; reject rounding spillover.
        if (col >= layout.cols() || row >= layout.rows()) {
            return -1;
        }
        int startRow = (int) (scrollPosition * Math.max(0, totalRows(layout) - layout.rows()));
        return (startRow + row) * layout.cols() + col;
    }

    private boolean isOverGrid(double mouseX, double mouseY, Layout layout) {
        return mouseX >= layout.gridX() && mouseX <= layout.gridX() + layout.gridWidth()
                && mouseY >= layout.gridY() && mouseY <= layout.gridY() + layout.gridHeight();
    }

    private boolean isOverScrollbar(double mouseX, double mouseY, Layout layout) {
        return mouseX >= layout.scrollbarX() && mouseX <= layout.scrollbarX() + layout.scrollbarWidth()
                && mouseY >= layout.gridY() && mouseY <= layout.gridY() + layout.gridHeight();
    }

    private static int thumbHeight(int trackHeight, int totalRows, int visibleRows) {
        int height = (int) ((float) trackHeight * visibleRows / totalRows);
        return Math.max(MIN_THUMB_HEIGHT, Math.min(trackHeight, height));
    }

    private static String shortenedCount(int count) {
        if (count < 1_000) return Integer.toString(count);
        if (count < 10_000) return decimalCount(count, 1_000, "k");
        if (count < 100_000) return decimalCount(count, 10_000, "w");
        if (count < 1_000_000) return count / 10_000 + "w";
        if (count < 10_000_000) return decimalCount(count, 1_000_000, "m");
        if (count < 1_000_000_000) return count / 1_000_000 + "m";
        return decimalCount(count, 1_000_000_000, "b");
    }

    private static String decimalCount(int count, int unit, String suffix) {
        return count / unit + "." + (count % unit) / (unit / 10) + suffix;
    }

    private static final class ItemEntry {
        final ItemStack stack;
        final String id;
        final int totalCount;

        ItemEntry(String id, int count) {
            this.id = id;
            this.totalCount = count;
            this.stack = ItemSerializer.deserialize(id);
        }
    }
}
