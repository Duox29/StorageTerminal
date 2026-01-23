package com.duox.advancedutilities.gui;

import com.duox.advancedutilities.modules.AutoStash;
import com.duox.advancedutilities.modules.StorageManager;
import com.duox.advancedutilities.system.ModuleManager;
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

    // UPDATE: Tăng chiều cao GUI để chứa đủ các khoảng trống
    private static final int GUI_WIDTH = 196;
    private static final int GUI_HEIGHT = 250; // Tăng từ 222 lên 250

    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 9;
    private static final int GRID_X_OFFSET = 9;

    // UPDATE: Đẩy Grid xuống thấp hơn để nhường chỗ cho Title & Search Box
    private static final int GRID_Y_OFFSET = 50; // Tăng từ 36 lên 50

    // --- LOGIC ---
    private final StorageManager storageManager;
    private EditBox searchBox;
    private ModernButton requestButton;
    private ModernButton autoStashButton;

    private boolean keepModuleOn = false;
    private List<ItemEntry> allItems = new ArrayList<>();
    private List<ItemEntry> filteredItems = new ArrayList<>();

    // Scrolling
    private float scrollPosition = 0.0f;

    private static class ItemEntry {
        ItemStack stack;
        String id;
        int totalCount;
        ItemEntry(String id, int count) {
            this.id = id;
            this.totalCount = count;
            Item item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id));
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
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            boolean active = isActiveSupplier.getAsBoolean();

            int bgColor = hovered ? COLOR_BTN_HOVER_BG : COLOR_BTN_NORMAL_BG;
            int borderColor = active ? COLOR_BTN_ACTIVE_BORDER : (hovered ? COLOR_BTN_HOVER_BORDER : COLOR_BTN_NORMAL_BORDER);
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
    }

    @Override
    protected void init() {
        super.init();
        this.keepModuleOn = false;

        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        // Search Box
        // UPDATE: Đặt Search Box nằm gọn giữa Title và Grid
        int searchW = 150;
        int searchY = guiTop + 25; // Vị trí Y mới (dưới title)

        this.searchBox = new EditBox(this.font, guiLeft + GUI_WIDTH - searchW - 10, searchY, searchW, 12, Component.literal("Search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFFFF);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addWidget(this.searchBox);

        // Request Button
        // UPDATE: Đặt nút xuống đáy GUI
        int btnY = guiTop + GUI_HEIGHT - 28;

        this.requestButton = new ModernButton(guiLeft + GUI_WIDTH - 87, btnY, 80, 20, Component.literal("Request"), button -> {
            if (!storageManager.isEnabled()) {
                storageManager.setEnabled(true);
            }
            storageManager.startRetrieval();
            this.keepModuleOn = true;
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        });
        this.addRenderableWidget(requestButton);

        // AutoStash Button
        this.autoStashButton = new ModernButton(guiLeft + 7, btnY, 80, 20, Component.literal("AutoStash"), button -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) {
                stash.setEnabled(true);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
        });
        this.addRenderableWidget(autoStashButton);

        this.addRenderableWidget(Button.builder(Component.literal("X"), b -> {
            storageManager.setEnabled(false); // Tắt hẳn module
            this.onClose(); // Đóng GUI
        }).bounds(guiLeft + GUI_WIDTH - 20, guiTop - 20, 20, 20).build());

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
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

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

        // 4. Scrollbar
        renderScrollbar(graphics, mouseX, mouseY);

        // 5. Title
        // UPDATE: Title nằm cao hẳn lên trên
        graphics.drawString(this.font, this.title, guiLeft + 8, guiTop + 10, COLOR_TEXT_TITLE, false);

        // 6. Items
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

            boolean isHovered = mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
            if (isHovered) {
                graphics.fill(x, y, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, COLOR_SLOT_HIGHLIGHT);
            }

            graphics.renderItem(entry.stack, x + 1, y + 1);
            graphics.renderItemDecorations(this.font, entry.stack, x + 1, y + 1, shortenedCount(entry.totalCount));

            int queued = storageManager.getRequestQueue().getOrDefault(entry.id, 0);
            if (queued > 0) {
                graphics.renderOutline(x, y, SLOT_SIZE -1, SLOT_SIZE -1, COLOR_BTN_ACTIVE_BORDER);
            }

            if (isHovered) {
                List<Component> tooltip = getTooltipFromItem(this.minecraft, entry.stack);
                tooltip.add(Component.literal("§7Stored: §f" + entry.totalCount));
                if (queued > 0) {
                    tooltip.add(Component.literal("§eRequesting: " + queued));
                }
                tooltip.add(Component.literal("§8[L-Click: +64 | R-Click: +1 | Shift: Remove]"));
                graphics.renderTooltip(this.font, tooltip, entry.stack.getTooltipImage(), mouseX, mouseY);
            }
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
            if (thumbHeight < 32) thumbHeight = 32;
            if (thumbHeight > scrollBarHeight) thumbHeight = scrollBarHeight;

            int thumbY = scrollBarY + (int) ((scrollBarHeight - thumbHeight) * scrollPosition);

            boolean isHovered = mouseX >= scrollBarX && mouseX <= scrollBarX + 10 && mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight;
            graphics.fill(scrollBarX + 1, thumbY, scrollBarX + 9, thumbY + thumbHeight, isHovered ? COLOR_BTN_HOVER_BG : COLOR_BG_BORDER);
            graphics.renderOutline(scrollBarX + 1, thumbY, 8, thumbHeight, isHovered ? COLOR_BTN_HOVER_BORDER : COLOR_BG_MAIN);
        }
    }

    // --- INPUT HANDLING ---

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        if (totalRows <= GRID_ROWS) return false;

        float scrollStep = 1.0f / (totalRows - GRID_ROWS);
        if (delta > 0) {
            scrollPosition -= scrollStep;
        } else if (delta < 0) {
            scrollPosition += scrollStep;
        }
        scrollPosition = Mth.clamp(scrollPosition, 0.0f, 1.0f);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

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

    private void handleClick(ItemEntry entry, int button) {
        int change = 0;
        boolean isShift = Screen.hasShiftDown();
        if (button == 0) change = 64; // Left
        if (button == 1) change = 1;  // Right
        if (isShift) change = -change;

        int current = storageManager.getRequestQueue().getOrDefault(entry.id, 0);
        int target = current + change;

        if (target < 0) target = 0;
        if (target > entry.totalCount) target = entry.totalCount;

        if (target == 0) {
            storageManager.getRequestQueue().remove(entry.id);
        } else {
            storageManager.getRequestQueue().put(entry.id, target);
        }
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
                    .filter(e -> e.stack.getHoverName().getString().toLowerCase().contains(query) || e.id.contains(query))
                    .collect(Collectors.toList());
        }
        scrollPosition = 0.0f;
    }

    private void onSearchChanged(String text) {
        filterItems();
    }

    private String shortenedCount(int count) {
        if (count >= 1000000) return String.format("%.1fM", count / 1000000.0);
        if (count >= 1000) return String.format("%.1fk", count / 1000.0);
        return String.valueOf(count);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}