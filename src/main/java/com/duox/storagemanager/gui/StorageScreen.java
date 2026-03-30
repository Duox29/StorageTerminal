package com.duox.storagemanager.gui;

import com.duox.storagemanager.gui.widgets.SlotScrollHandler;
import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.system.settings.BooleanSetting;
import com.duox.storagemanager.utils.CacheDatabase;
import com.duox.storagemanager.utils.ToastUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public class StorageScreen extends Screen {
    // --- COLORS (AE2/Tech Style) ---
    private static final int COLOR_BG_MAIN = 0xFF212121; // Dark Grey Base
    private static final int COLOR_BG_BORDER = 0xFF585858; // Lighter Grey Border
    private static final int COLOR_SLOT_BG = 0xFF353535; // Darker slot
    private static final int COLOR_SLOT_HIGHLIGHT = 0x80FFFFFF;
    private static final int COLOR_TEXT_TITLE = 0xFFE0E0E0;

    // Button Colors
    private static final int COLOR_BTN_NORMAL_BG = 0xFF2A2A2A;
    private static final int COLOR_BTN_NORMAL_BORDER = 0xFF4A4A4A;
    private static final int COLOR_BTN_HOVER_BG = 0xFF3A3A3A;
    private static final int COLOR_BTN_HOVER_BORDER = 0xFF0099FF; // AE2 Blue
    private static final int COLOR_BTN_ACTIVE_BORDER = 0xFF00FF00; // Tech Green

    // --- DIMENSIONS ---
    private int guiLeft;
    private int guiTop;

    // Dynamic sizing based on settings
    private int GUI_WIDTH = 196;
    private int GUI_HEIGHT = 250;

    private int SLOT_SIZE = 18;
    private int GRID_COLS = 9;
    private int GRID_ROWS = 9;
    private int GRID_X_OFFSET = 9;
    private int GRID_Y_OFFSET = 50;

    // --- LOGIC ---
    private final StorageManager storageManager;
    private EditBox searchBox;
    private ModernButton requestButton;
    private ModernButton autoStashButton;
    private ModernButton clearCacheButton;
    private ModernButton buildCacheButton;

    // Custom scroll handler
    private SlotScrollHandler<ItemEntry> scrollHandler;

    private boolean keepModuleOn = false;
    private List<ItemEntry> allItems = new ArrayList<>();
    private List<ItemEntry> filteredItems = new ArrayList<>();

    // Scrolling
    private float scrollPosition = 0.0f;

    // Scrollbar drag support
    private boolean isDraggingScrollbar = false;
    private int scrollbarDragStartY = 0;
    private float scrollPositionAtDragStart = 0.0f;

    private static class ItemEntry {
        ItemStack stack;
        String id;
        int totalCount;

        ItemEntry(String id, int count) {
            this.id = id;
            this.totalCount = count;
            Item item = BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse(id))
                    .map(net.minecraft.core.Holder::value) // Extract Item from Holder
                    .orElse(net.minecraft.world.item.Items.AIR);
            this.stack = new ItemStack(item);
        }
    }

    // --- CUSTOM MODERN BUTTON CLASS ---
    private class ModernButton extends Button {
        private BooleanSupplier isActiveSupplier = () -> false;

        public ModernButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        public ModernButton setActiveSupplier(BooleanSupplier supplier) {
            this.isActiveSupplier = supplier;
            return this;
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            boolean active = isActiveSupplier.getAsBoolean();

            int bgColor = hovered ? COLOR_BTN_HOVER_BG : COLOR_BTN_NORMAL_BG;
            int borderColor = active ? COLOR_BTN_ACTIVE_BORDER
                    : (hovered ? COLOR_BTN_HOVER_BORDER : COLOR_BTN_NORMAL_BORDER);
            int textColor = hovered || active ? 0xFFFFFFFF : 0xFFAAAAAA;

            // Fill Background
            graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            // Draw Border
            graphics.renderOutline(getX(), getY(), width, height, borderColor);

            // Draw Text centered
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
        }
    }

    public StorageScreen(StorageManager manager) {
        super(Component.literal("Storage Terminal"));
        this.storageManager = manager;

        // Load cache from file if not already loaded (critical for module-enabled-by-default case)
        this.storageManager.ensureCacheLoaded();

        // Apply scale and grid settings
        applyScaleSettings();

        // Initialize custom scroll handler with accessor for ItemEntry
        this.scrollHandler = new SlotScrollHandler<>(manager, new SlotScrollHandler.ItemEntryAccessor<ItemEntry>() {
            @Override
            public String getId(ItemEntry entry) {
                return entry.id;
            }

            @Override
            public int getTotalCount(ItemEntry entry) {
                return entry.totalCount;
            }
        });
    }

    private void applyScaleSettings() {
        double scale = storageManager.screenScale.getValue();
        GRID_COLS = storageManager.screenGridCols.getValue().intValue();
        GRID_ROWS = storageManager.screenGridRows.getValue().intValue();

        SLOT_SIZE = (int) (18 * scale);
        GRID_X_OFFSET = (int) (9 * scale);
        GRID_Y_OFFSET = (int) (50 * scale);

        GUI_WIDTH = GRID_X_OFFSET + (GRID_COLS * SLOT_SIZE) + (int) (20 * scale);
        GUI_HEIGHT = GRID_Y_OFFSET + (GRID_ROWS * SLOT_SIZE) + (int) (32 * scale);
    }

    @Override
    protected void init() {
        super.init();
        this.keepModuleOn = false;

        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        // Search Box
        // UPDATE: Đặt Search Box nằm gọn giữa Title và Grid
        int padding = 20;
        int searchW = GUI_WIDTH - (padding * 2);
        int searchY = guiTop + 25;

        this.searchBox = new EditBox(this.font, guiLeft + padding, searchY, searchW, 12, Component.literal("Search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFFFF);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addWidget(this.searchBox);
        // Request Button
        // UPDATE: Đặt nút xuống đáy GUI
        int btnY = guiTop + GUI_HEIGHT - 28;

        this.requestButton = new ModernButton(guiLeft + GUI_WIDTH - 87, btnY, 80, 20, Component.literal("Request"),
                button -> {
                    if (!storageManager.isEnabled()) {
                        storageManager.setEnabled(true);
                    }
                    storageManager.startRetrieval();
                    this.keepModuleOn = true;
                    Minecraft.getInstance().getSoundManager()
                            .play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                                    .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                });
        this.addRenderableWidget(requestButton);

        // AutoStash Button
        this.autoStashButton = new ModernButton(guiLeft + 7, btnY, 80, 20, Component.literal("AutoStash"), button -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) {
                stash.setEnabled(true);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                        .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
        });
        this.addRenderableWidget(autoStashButton);

        int topButtonsY = guiTop - 20; // Tọa độ Y của nút X hiện tại
        int closeBtnX = guiLeft + GUI_WIDTH - 20; // Vị trí nút X
        int buttonSpacing = 2; // Khoảng cách giữa các nút
        int sideBtnWidth = 40; // Độ rộng nút Build/Clear

        // 1. Nút Close (X) - Đã có sẵn, giữ nguyên tọa độ
        this.addRenderableWidget(Button.builder(Component.literal("X"), b -> {
            storageManager.setEnabled(false);
            this.onClose();
        }).bounds(closeBtnX, topButtonsY, 20, 20).build());

        // 2. Nút Clear Cache (Nằm bên trái nút X)
        int clearBtnX = closeBtnX - sideBtnWidth - buttonSpacing;
        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            // Clear both active and global caches and wipe persistence
            AutoStash.clearCachesAndStorage(this.minecraft);

            // Refresh visible list immediately
            refreshItemList();

            ToastUtils.sendToast("§6Storage", "Cache cleared.");

        }).bounds(clearBtnX, topButtonsY, sideBtnWidth, 20).build());

        // 3. Nút Build Cache (Nằm bên trái nút Clear)
        int buildBtnX = clearBtnX - sideBtnWidth - buttonSpacing;
        this.addRenderableWidget(Button.builder(Component.literal("Build"), b -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) {
                // Tìm và bật setting Rebuild Cache
                stash.getSettings().stream()
                        .filter(s -> s.getName().equalsIgnoreCase("Rebuild Cache Next Run"))
                        .findFirst()
                        .ifPresent(s -> ((BooleanSetting) s).setValue(true));

                stash.setEnabled(true);
                ToastUtils.sendToast("§bStorage", "Rebuilding cache...");
            }
        }).bounds(buildBtnX, topButtonsY, sideBtnWidth, 20).build());
        refreshItemList();
    }

    @Override
    public void onClose() {
        if (!keepModuleOn) {
            storageManager.clearRequestQueue();
            //storageManager.setEnabled(false);
        }
        super.onClose();
    }

    // --- RENDER ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (AutoStash.cacheDirty) {
            refreshItemList();
            AutoStash.cacheDirty = false; // Đã xử lý xong
        }
        //this.renderBackground(graphics, mouseX, mouseY, partialTick);

        // 1. Main GUI Panel
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, COLOR_BG_MAIN);
        graphics.renderOutline(guiLeft, guiTop, GUI_WIDTH, GUI_HEIGHT, COLOR_BG_BORDER);

        // 2. Slot Grid Background
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int x = guiLeft + GRID_X_OFFSET + col * SLOT_SIZE;
                int y = guiTop + GRID_Y_OFFSET + row * SLOT_SIZE;
                graphics.fill(x, y, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, COLOR_SLOT_BG);
            }
        }

        // 3. Search Box Background
        int searchX = searchBox.getX() - 4;
        int searchY = searchBox.getY() - 2;
        graphics.fill(searchX, searchY, searchX + searchBox.getWidth() + 8, searchY + 16, 0xFF000000);
        graphics.renderOutline(searchX, searchY, searchBox.getWidth() + 8, 16, COLOR_BG_BORDER);
        this.searchBox.render(graphics, mouseX, mouseY, partialTick);
        // 4. Scrollbar
        renderScrollbar(graphics, mouseX, mouseY);

        // 5. Title
        // UPDATE: Title nằm cao hẳn lên trên
        graphics.drawString(this.font, this.title, guiLeft + 8, guiTop + 10, COLOR_TEXT_TITLE, false);

        // 6. Items
        float itemScale = storageManager.screenScale.getValue().floatValue();
        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        int startIndex = (int) (scrollPosition * Math.max(0, totalRows - GRID_ROWS)) * GRID_COLS;
        int endIndex = Math.min(startIndex + (GRID_ROWS * GRID_COLS), filteredItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            ItemEntry entry = filteredItems.get(i);
            int relIndex = i - startIndex;
            int col = relIndex % GRID_COLS;
            int row = relIndex / GRID_COLS;

            int x = guiLeft + GRID_X_OFFSET + col * SLOT_SIZE;
            int y = guiTop + GRID_Y_OFFSET + row * SLOT_SIZE;

            // Hover logic (remains using 'x' and 'y' because mouse coords are absolute)
            boolean isHovered = mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
            if (isHovered) {
                graphics.fill(x, y, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, COLOR_SLOT_HIGHLIGHT);
            }
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 1, y + 1);
            graphics.pose().scale(itemScale, itemScale);
            graphics.renderItem(entry.stack, 0, 0);
            graphics.renderItemDecorations(this.font, entry.stack, 0, 0, shortenedCount(entry.totalCount));
            graphics.pose().popMatrix();
            int queued = storageManager.getRequestQueue().getOrDefault(entry.id, 0);
            if (queued > 0) {
                graphics.renderOutline(x, y, SLOT_SIZE - 1, SLOT_SIZE - 1, COLOR_BTN_ACTIVE_BORDER);
            }

            if (isHovered) {
                List<Component> tooltip = getTooltipFromItem(this.minecraft, entry.stack);
                tooltip.add(Component.literal("§7Stored: §f" + entry.totalCount));
                if (queued > 0) {
                    tooltip.add(Component.literal("§eRequesting: " + queued));
                }
                tooltip.add(Component.literal("§8[L-Click: +64 | R-Click: +1 | Shift: Remove]"));
                graphics.setTooltipForNextFrame(this.font, tooltip, entry.stack.getTooltipImage(), mouseX, mouseY);            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int scrollBarX = guiLeft + GUI_WIDTH - 16;
        int scrollBarY = guiTop + GRID_Y_OFFSET;
        int scrollBarHeight = GRID_ROWS * SLOT_SIZE;

        graphics.fill(scrollBarX, scrollBarY, scrollBarX + 10, scrollBarY + scrollBarHeight, COLOR_BTN_NORMAL_BG);

        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        int visibleRows = GRID_ROWS;

        if (totalRows > visibleRows) {
            int thumbHeight = (int) ((float) (visibleRows * visibleRows) / totalRows * SLOT_SIZE);
            if (thumbHeight < 32)
                thumbHeight = 32;
            if (thumbHeight > scrollBarHeight)
                thumbHeight = scrollBarHeight;

            int thumbY = scrollBarY + (int) ((scrollBarHeight - thumbHeight) * scrollPosition);

            boolean isHovered = mouseX >= scrollBarX && mouseX <= scrollBarX + 10 && mouseY >= scrollBarY
                    && mouseY <= scrollBarY + scrollBarHeight;
            graphics.fill(scrollBarX + 1, thumbY, scrollBarX + 9, thumbY + thumbHeight,
                    isHovered ? COLOR_BTN_HOVER_BG : COLOR_BG_BORDER);
            graphics.renderOutline(scrollBarX + 1, thumbY, 8, thumbHeight,
                    isHovered ? COLOR_BTN_HOVER_BORDER : COLOR_BG_MAIN);
        }
    }

    // --- INPUT HANDLING ---

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        System.out.println(
                "[StorageScreen] mouseScrolled called: scrollX=" + scrollX + ", scrollY=" + scrollY + ", mouse=(" + mouseX + ", " + mouseY + ")");

        // PRIORITY 1: Try to handle as slot scroll (quantity adjustment)
        int gridX = guiLeft + GRID_X_OFFSET;
        int gridY = guiTop + GRID_Y_OFFSET;

        System.out.println("[StorageScreen] Grid coords: gridX=" + gridX + ", gridY=" + gridY);
        System.out.println("[StorageScreen] Filtered items count: " + filteredItems.size());

        boolean handledBySlot = scrollHandler.handleScroll(
                mouseX, mouseY, scrollX, scrollY,
                gridX, gridY,
                SLOT_SIZE, GRID_COLS, GRID_ROWS,
                scrollPosition, filteredItems);

        System.out.println("[StorageScreen] Slot handler result: " + handledBySlot);

        if (handledBySlot) {
            System.out.println("[StorageScreen] Event handled by slot scroll");
            return true; // Slot scroll handled, don't scroll the list
        }

        // PRIORITY 2: Fallback to normal list scrolling
        System.out.println("[StorageScreen] Falling back to list scroll");
        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        if (totalRows <= GRID_ROWS) {
            System.out.println("[StorageScreen] Not enough rows to scroll (" + totalRows + " <= " + GRID_ROWS + ")");
            return false;
        }

        float scrollStep = 1.0f / (totalRows - GRID_ROWS);
        if (scrollY > 0) {
            scrollPosition -= scrollStep;
        } else if (scrollY < 0) {
            scrollPosition += scrollStep;
        }
        scrollPosition = Mth.clamp(scrollPosition, 0.0f, 1.0f);
        System.out.println("[StorageScreen] List scrolled to position: " + scrollPosition);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
// 1. Trích xuất dữ liệu từ event record
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        // Check if clicking on scrollbar first
        int scrollBarX = guiLeft + GUI_WIDTH - 16;
        int scrollBarY = guiTop + GRID_Y_OFFSET;
        int scrollBarHeight = GRID_ROWS * SLOT_SIZE;

        if (button == 0 && mouseX >= scrollBarX && mouseX <= scrollBarX + 10 &&
            mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {
            int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
            if (totalRows > GRID_ROWS) {
                isDraggingScrollbar = true;
                scrollbarDragStartY = (int) mouseY;
                scrollPositionAtDragStart = scrollPosition;
                return true;
            }
        }

        if (super.mouseClicked(event, isFocused))
            return true;

        int startX = guiLeft + GRID_X_OFFSET;
        int startY = guiTop + GRID_Y_OFFSET;

        if (mouseX < startX || mouseX > startX + (GRID_COLS * SLOT_SIZE) ||
                mouseY < startY || mouseY > startY + (GRID_ROWS * SLOT_SIZE)) {
            return false;
        }

        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        int startRow = (int) (scrollPosition * Math.max(0, totalRows - GRID_ROWS));

        int clickedCol = (int) ((mouseX - startX) / SLOT_SIZE);
        int clickedRow = (int) ((mouseY - startY) / SLOT_SIZE);

        int index = (startRow + clickedRow) * GRID_COLS + clickedCol;

        if (index >= 0 && index < filteredItems.size()) {
            ItemEntry entry = filteredItems.get(index);
            handleClick(entry, button);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.button();

        if (isDraggingScrollbar && button == 0) {
            isDraggingScrollbar = false;
            return true;
        }
        // FIX: Gọi super với event object
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int button = event.button();
        double mouseY = event.y();
        if (isDraggingScrollbar && button == 0) {
            int scrollBarY = guiTop + GRID_Y_OFFSET;
            int scrollBarHeight = GRID_ROWS * SLOT_SIZE;
            int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);

            if (totalRows > GRID_ROWS) {
                int thumbHeight = (int) ((float) (GRID_ROWS * GRID_ROWS) / totalRows * SLOT_SIZE);
                if (thumbHeight < 32)
                    thumbHeight = 32;
                if (thumbHeight > scrollBarHeight)
                    thumbHeight = scrollBarHeight;

                int maxThumbTravel = scrollBarHeight - thumbHeight;
                if (maxThumbTravel > 0) {
                    int dragDelta = (int) (mouseY - scrollbarDragStartY);
                    float deltaScroll = (float) dragDelta / maxThumbTravel;
                    scrollPosition = Mth.clamp(scrollPositionAtDragStart + deltaScroll, 0.0f, 1.0f);
                }
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);    }

    private void handleClick(ItemEntry entry, int button) {
        int change = 0;
        boolean isShift = this.minecraft.hasShiftDown();        if (button == 0)
            change = 64; // Left
        if (button == 1)
            change = 1; // Right
        if (isShift)
            change = -change;

        int current = storageManager.getRequestQueue().getOrDefault(entry.id, 0);
        int target = current + change;

        if (target < 0)
            target = 0;
        if (target > entry.totalCount)
            target = entry.totalCount;

        if (target == 0) {
            storageManager.getRequestQueue().remove(entry.id);
        } else {
            storageManager.getRequestQueue().put(entry.id, target);
        }
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // --- UTILS ---

    private void refreshItemList() {
        allItems.clear();
        Map<String, Map<String, Integer>> cache = AutoStash.getChestCache();
        Map<String, Integer> totals = new HashMap<>();

        for (Map<String, Integer> contents : cache.values()) {
            for (Map.Entry<String, Integer> entry : contents.entrySet()) {
                totals.put(entry.getKey(), totals.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }
        }
        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            allItems.add(new ItemEntry(entry.getKey(), entry.getValue()));
        }
        allItems.sort((a, b) -> Integer.compare(b.totalCount, a.totalCount));
        filterItems();
    }

    private void filterItems() {
        String query = searchBox.getValue().toLowerCase();
        if (query.isEmpty()) {
            filteredItems = new ArrayList<>(allItems);
        } else {
            filteredItems = allItems.stream()
                    .filter(e -> e.stack.getHoverName().getString().toLowerCase().contains(query)
                            || e.id.contains(query))
                    .collect(Collectors.toList());
        }
        // [FIX] ADDED: Safety check.
        // If the new list is smaller than the old one, clamp the scroll position
        // so we don't end up looking at empty space.
        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        if (totalRows <= GRID_ROWS) {
            scrollPosition = 0.0f;
        } else {
            scrollPosition = Mth.clamp(scrollPosition, 0.0f, 1.0f);
        }
    }

    private void onSearchChanged(String text) {

        filterItems();
        scrollPosition = 0.0f;
    }

    private String shortenedCount(int count) {
        if (count >= 1000000)
            return String.format("%.1fM", count / 1000000.0);
        if (count >= 1000)
            return String.format("%.1fk", count / 1000.0);
        return String.valueOf(count);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // FIX: Sử dụng event.key() thay vì keyCode
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        // FIX: Gọi super với event object
        return super.keyPressed(event);
    }
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Để trống để tắt hoàn toàn background blur
    }
}
