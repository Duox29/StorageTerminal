package com.duox.storagemanager.gui;

import com.duox.storagemanager.gui.widgets.SlotScrollHandler;
import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.system.settings.BooleanSetting;
import com.duox.storagemanager.utils.ItemSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public class StoragePanel implements Renderable, GuiEventListener, NarratableEntry {

    // --- SETTINGS ---
    private int PANEL_WIDTH = 180;
    private int PANEL_HEIGHT = 200;
    private static final int HEADER_HEIGHT = 20;

    public int x;
    public int y;

    // --- COLORS ---
    private static final int COLOR_BG_MAIN = 0xFF212121;
    private static final int COLOR_BG_BORDER = 0xFF585858;
    private static final int COLOR_SLOT_BG = 0xFF353535;
    private static final int COLOR_SLOT_HIGHLIGHT = 0x80FFFFFF;
    private static final int COLOR_HEADER = 0xFF303030;
    private static final int COLOR_HEADER_HOVER = 0xFF404040;

    // Button Colors (AE2/Tech Style)
    private static final int COLOR_BTN_NORMAL_BG = 0xFF2A2A2A;
    private static final int COLOR_BTN_NORMAL_BORDER = 0xFF4A4A4A;
    private static final int COLOR_BTN_HOVER_BG = 0xFF3A3A3A;
    private static final int COLOR_BTN_HOVER_BORDER = 0xFF0099FF;
    private static final int COLOR_BTN_ACTIVE_BORDER = 0xFF00FF00;

    // Grid Settings
    private int SLOT_SIZE = 18;
    private int GRID_COLS = 8;
    private int GRID_ROWS = 7;
    private int GRID_X_OFFSET = 10;
    private int GRID_Y_OFFSET = 45;

    // Components
    private final StorageManager storageManager;
    private final Minecraft mc = Minecraft.getInstance();
    private EditBox searchBox;

    // Nút chức năng mới (ModernButton)
    private ModernButton requestButton;
    private ModernButton autoStashButton;
    private ModernButton recipeButton;
    private ModernButton hotbarButton;

    private SlotScrollHandler<ItemEntry> scrollHandler;
    private List<ItemEntry> allItems = new ArrayList<>();
    private List<ItemEntry> filteredItems = new ArrayList<>();
    private float scrollPosition = 0.0f;

    // Drag Support
    private boolean isDragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private boolean widgetFocused = false;
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
            this.stack = ItemSerializer.deserialize(id);
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

//        @Override
//        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
//            boolean hovered = isHoveredOrFocused();
//            boolean active = isActiveSupplier.getAsBoolean();
//
//            int bgColor = hovered ? COLOR_BTN_HOVER_BG : COLOR_BTN_NORMAL_BG;
//            int borderColor = active ? COLOR_BTN_ACTIVE_BORDER
//                    : (hovered ? COLOR_BTN_HOVER_BORDER : COLOR_BTN_NORMAL_BORDER);
//            int textColor = hovered || active ? 0xFFFFFFFF : 0xFFAAAAAA;
//
//            graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
//            graphics.renderOutline(getX(), getY(), width, height, borderColor);
//            graphics.drawCenteredString(mc.font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
//        }
    }

    public StoragePanel(StorageManager manager, int startX, int startY) {
        this.storageManager = manager;
        this.x = startX;
        this.y = startY;

        AutoStash autoStash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        if (autoStash != null && Minecraft.getInstance().player != null) {
            autoStash.forceRefreshActiveCache();
        }
        applyScaleSettings();

        this.scrollHandler = new SlotScrollHandler<>(manager, new SlotScrollHandler.ItemEntryAccessor<ItemEntry>() {
            @Override
            public String getId(ItemEntry entry) { return entry.id; }
            @Override
            public int getTotalCount(ItemEntry entry) { return entry.totalCount; }
        });

        initComponents();
    }

    private void applyScaleSettings() {
        double scale = storageManager.panelScale.getValue();
        GRID_COLS = storageManager.panelGridCols.getValue().intValue();
        GRID_ROWS = storageManager.panelGridRows.getValue().intValue();

        SLOT_SIZE = (int) (18 * scale);
        GRID_X_OFFSET = (int) (10 * scale);
        GRID_Y_OFFSET = (int) (45 * scale);

        PANEL_WIDTH = GRID_X_OFFSET + (GRID_COLS * SLOT_SIZE) + (int) (16 * scale);
        PANEL_HEIGHT = GRID_Y_OFFSET + (GRID_ROWS * SLOT_SIZE) + (int) (30 * scale);
    }

    private void initComponents() {
        int padding = 10;
        int searchW = PANEL_WIDTH - (padding * 2);
        this.searchBox = new EditBox(mc.font, x + padding, y + 25, searchW, 12, Component.literal("Search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFFFF);
        this.searchBox.setResponder(this::onSearchChanged);

        // --- TOP BUTTONS (Lơ lửng phía trên bên phải) ---
        int topBtnY = y - 22;
        int topBtnWidth = 48; // Chiều rộng vừa đủ cho chữ Hot:ON
        int topBtnSpacing = 2;

        // Nút Hotbar
        this.hotbarButton = new ModernButton(x + PANEL_WIDTH - topBtnWidth, topBtnY, topBtnWidth, 20, getHotbarLabel(), b -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) {
                stash.getSettings().stream()
                        .filter(s -> s.getName().equals("Include Hotbar"))
                        .findFirst()
                        .ifPresent(s -> {
                            BooleanSetting bs = (BooleanSetting) s;
                            bs.setValue(!bs.getValue());
                            b.setMessage(getHotbarLabel());
                        });
            }
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }).setActiveSupplier(() -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) {
                return stash.getSettings().stream()
                        .filter(s -> s.getName().equals("Include Hotbar"))
                        .map(s -> ((BooleanSetting) s).getValue())
                        .findFirst().orElse(false);
            }
            return false;
        });

        // Nút Recipe
        this.recipeButton = new ModernButton(x + PANEL_WIDTH - topBtnWidth * 2 - topBtnSpacing, topBtnY, topBtnWidth, 20, getRecipeLabel(), b -> {
            boolean current = storageManager.autoRequestRecipe.getValue();
            storageManager.autoRequestRecipe.setValue(!current);
            b.setMessage(getRecipeLabel());
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }).setActiveSupplier(() -> storageManager.autoRequestRecipe.getValue());


        // --- BOTTOM BUTTONS (Dàn đều bên dưới) ---
        int bottomBtnY = y + PANEL_HEIGHT - 25;
        int bottomBtnSpacing = 4;
        int bottomBtnWidth = (PANEL_WIDTH - (padding * 2) - bottomBtnSpacing) / 2;

        this.requestButton = new ModernButton(x + padding, bottomBtnY, bottomBtnWidth, 20, Component.literal("Request"), b -> {
            storageManager.startRetrieval();
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        });

        this.autoStashButton = new ModernButton(x + padding + bottomBtnWidth + bottomBtnSpacing, bottomBtnY, bottomBtnWidth, 20, Component.literal("AutoStash"), b -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) stash.setEnabled(true);
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        });

        refreshItemList();
    }

    private Component getRecipeLabel() {
        boolean on = storageManager.autoRequestRecipe.getValue();
        return Component.literal("Recipe:" + (on ? "ON" : "OFF")).withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED);
    }

    private Component getHotbarLabel() {
        AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        boolean on = false;
        if (stash != null) {
            on = stash.getSettings().stream()
                    .filter(s -> s.getName().equals("Include Hotbar"))
                    .map(s -> ((BooleanSetting) s).getValue())
                    .findFirst().orElse(false);
        }
        return Component.literal("Hotbar:" + (on ? "ON" : "OFF")).withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED);
    }

    private void repositionComponents() {
        int padding = 10;
        searchBox.setX(x + padding);
        searchBox.setY(y + 25);

        // Top Buttons
        int topBtnY = y - 22;
        int topBtnWidth = 48;
        int topBtnSpacing = 2;
        hotbarButton.setX(x + PANEL_WIDTH - topBtnWidth);
        hotbarButton.setY(topBtnY);
        recipeButton.setX(x + PANEL_WIDTH - topBtnWidth * 2 - topBtnSpacing);
        recipeButton.setY(topBtnY);

        // Bottom Buttons
        int bottomBtnY = y + PANEL_HEIGHT - 25;
        int bottomBtnSpacing = 4;
        int bottomBtnWidth = (PANEL_WIDTH - (padding * 2) - bottomBtnSpacing) / 2;
        requestButton.setX(x + padding);
        requestButton.setY(bottomBtnY);
        autoStashButton.setX(x + padding + bottomBtnWidth + bottomBtnSpacing);
        autoStashButton.setY(bottomBtnY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (AutoStash.cacheDirty) {
            refreshItemList();
            AutoStash.cacheDirty = false;
        }

        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, COLOR_BG_MAIN);
        graphics.renderOutline(x, y, PANEL_WIDTH, PANEL_HEIGHT, COLOR_BG_BORDER);

        boolean isHeaderHovered = mouseX >= x && mouseX <= x + PANEL_WIDTH && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
        int headerColor = isHeaderHovered ? COLOR_HEADER_HOVER : COLOR_HEADER;
        graphics.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, headerColor);
        graphics.drawString(mc.font, "Storage Terminal", x + 5, y + 6, 0xFFE0E0E0, false);
        graphics.hLine(x, x + PANEL_WIDTH - 1, y + HEADER_HEIGHT, COLOR_BG_BORDER);

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int sx = x + GRID_X_OFFSET + col * SLOT_SIZE;
                int sy = y + GRID_Y_OFFSET + row * SLOT_SIZE;
                graphics.fill(sx, sy, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, COLOR_SLOT_BG);
            }
        }

        graphics.fill(searchBox.getX() - 2, searchBox.getY() - 2, searchBox.getX() + searchBox.getWidth() + 2, searchBox.getY() + 14, 0xFF000000);

        searchBox.render(graphics, mouseX, mouseY, partialTick);

        // Render 4 Buttons
        requestButton.render(graphics, mouseX, mouseY, partialTick);
        autoStashButton.render(graphics, mouseX, mouseY, partialTick);
        recipeButton.render(graphics, mouseX, mouseY, partialTick);
        hotbarButton.render(graphics, mouseX, mouseY, partialTick);

        renderScrollbar(graphics, mouseX, mouseY);
        renderItems(graphics, mouseX, mouseY);
    }

    private void renderItems(GuiGraphics graphics, int mouseX, int mouseY) {
        float itemScale = storageManager.panelScale.getValue().floatValue();
        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        int startIndex = (int) (scrollPosition * Math.max(0, totalRows - GRID_ROWS)) * GRID_COLS;
        int endIndex = Math.min(startIndex + (GRID_ROWS * GRID_COLS), filteredItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            ItemEntry entry = filteredItems.get(i);
            int relIndex = i - startIndex;
            int col = relIndex % GRID_COLS;
            int row = relIndex / GRID_COLS;

            int sx = x + GRID_X_OFFSET + col * SLOT_SIZE;
            int sy = y + GRID_Y_OFFSET + row * SLOT_SIZE;

            boolean isHovered = mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE;
            if (isHovered) {
                graphics.fill(sx, sy, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, COLOR_SLOT_HIGHLIGHT);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(sx + 1, sy + 1, 0);
            graphics.pose().scale(itemScale, itemScale, 1.0f);
            graphics.renderItem(entry.stack, 0, 0);
            graphics.renderItemDecorations(mc.font, entry.stack, 0, 0, shortenedCount(entry.totalCount));

            graphics.pose().popPose();
            int queued = storageManager.getRequestQueue().getOrDefault(entry.id, 0);
            if (queued > 0) {
                // Sử dụng màu viền xanh tech (Tech Green) giống nút khi được chọn
                graphics.renderOutline(sx, sy, SLOT_SIZE - 1, SLOT_SIZE - 1, COLOR_BTN_ACTIVE_BORDER);
            }

            if (isHovered) {
                List<Component> tooltip = new ArrayList<>();
                Item.TooltipContext context = Item.TooltipContext.of(mc.level);
                tooltip.addAll(entry.stack.getTooltipLines(context, mc.player, TooltipFlag.NORMAL));
                tooltip.add(Component.literal("§7Stored: §f" + entry.totalCount));
                if (queued > 0) tooltip.add(Component.literal("§eRequesting: " + queued));
                graphics.renderTooltip(mc.font, tooltip, entry.stack.getTooltipImage(), mouseX, mouseY);
            }
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int scrollX = x + PANEL_WIDTH - 8;
        int scrollY = y + GRID_Y_OFFSET;
        int scrollH = GRID_ROWS * SLOT_SIZE;

        graphics.fill(scrollX, scrollY, scrollX + 6, scrollY + scrollH, 0xFF202020);

        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        if (totalRows > GRID_ROWS) {
            int thumbH = (int) ((float) (GRID_ROWS * GRID_ROWS) / totalRows * SLOT_SIZE);
            thumbH = Math.max(20, Math.min(scrollH, thumbH));
            int thumbY = scrollY + (int) ((scrollH - thumbH) * scrollPosition);
            graphics.fill(scrollX, thumbY, scrollX + 6, thumbY + thumbH, 0xFF606060);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (!isMouseOver(mouseX, mouseY)) return false;

        int scrollX = x + PANEL_WIDTH - 8;
        int scrollY = y + GRID_Y_OFFSET;
        int scrollH = GRID_ROWS * SLOT_SIZE;

        if (button == 0 && mouseX >= scrollX && mouseX <= scrollX + 6 && mouseY >= scrollY && mouseY <= scrollY + scrollH) {
            int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
            if (totalRows > GRID_ROWS) {
                isDraggingScrollbar = true;
                widgetFocused = true;
                scrollbarDragStartY = (int) mouseY;
                scrollPositionAtDragStart = scrollPosition;
                return true;
            }
        }

        int headerDragWidth = PANEL_WIDTH - 130;
        if (button == 0 && mouseX >= x && mouseX <= x + headerDragWidth && mouseY >= y && mouseY <= y + HEADER_HEIGHT) {
            isDragging = true;
            widgetFocused = true;
            dragOffsetX = (int) (mouseX - x);
            dragOffsetY = (int) (mouseY - y);
            return true;
        }

        if (searchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocusedListener(searchBox);
            return true;
        }

        // Cập nhật lại danh sách nút nhận ClickmouseX, mouseY, button
        if (requestButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (autoStashButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (recipeButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (hotbarButton.mouseClicked(mouseX, mouseY, button)) return true;

        handleGridClick(mouseX, mouseY, button);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar && button == 0) {
            isDraggingScrollbar = false;
            widgetFocused = false;
            return true;
        }

        if (isDragging && button == 0) {
            isDragging = false;
            widgetFocused = false;
            storageManager.panelX.setValue((double) x);
            storageManager.panelY.setValue((double) y);
            return true;
        }

        return searchBox.mouseReleased(mouseX, mouseY, button) ||
                requestButton.mouseReleased(mouseX, mouseY, button) ||
                autoStashButton.mouseReleased(mouseX, mouseY, button) ||
                recipeButton.mouseReleased(mouseX, mouseY, button) ||
                hotbarButton.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar && button == 0) {
            int scrollH = GRID_ROWS * SLOT_SIZE;
            int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);

            if (totalRows > GRID_ROWS) {
                int thumbH = (int) ((float) (GRID_ROWS * GRID_ROWS) / totalRows * SLOT_SIZE);
                thumbH = Math.max(20, Math.min(scrollH, thumbH));
                int dragDelta = (int) (mouseY - scrollbarDragStartY);
                int maxThumbTravel = scrollH - thumbH;

                if (maxThumbTravel > 0) {
                    float deltaScroll = (float) dragDelta / maxThumbTravel;
                    scrollPosition = Mth.clamp(scrollPositionAtDragStart + deltaScroll, 0.0f, 1.0f);
                }
            }
            return true;
        }

        if (isDragging && button == 0) {
            x = (int) (mouseX - dragOffsetX);
            y = (int) (mouseY - dragOffsetY);
            repositionComponents();
            return true;
        }

        return searchBox.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        int gridX = x + GRID_X_OFFSET;
        int gridY = y + GRID_Y_OFFSET;

        boolean handledBySlot = scrollHandler.handleScroll(mouseX, mouseY, scrollX, scrollY, gridX, gridY, SLOT_SIZE, GRID_COLS, GRID_ROWS, scrollPosition, filteredItems);

        if (handledBySlot) return true;

        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        if (totalRows <= GRID_ROWS) return true;

        float scrollStep = 1.0f / (totalRows - GRID_ROWS);
        scrollPosition = Mth.clamp(scrollPosition - (float) scrollY * scrollStep, 0.0f, 1.0f);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused()) return searchBox.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox.isFocused()) return searchBox.charTyped(codePoint, modifiers);
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + PANEL_WIDTH && mouseY >= y - 25 && mouseY <= y + PANEL_HEIGHT;
    }

    @Override
    public void setFocused(boolean focused) { this.widgetFocused = focused; }

    @Override
    public boolean isFocused() { return this.widgetFocused || this.isDragging || this.isDraggingScrollbar; }

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
    @Override
    public void updateNarration(NarrationElementOutput output) {}

    public void setFocusedListener(GuiEventListener listener) {
        searchBox.setFocused(listener == searchBox);
    }

    private void handleGridClick(double mouseX, double mouseY, int button) {
        int startX = x + GRID_X_OFFSET;
        int startY = y + GRID_Y_OFFSET;
        if (mouseX < startX || mouseX > startX + (GRID_COLS * SLOT_SIZE) || mouseY < startY || mouseY > startY + (GRID_ROWS * SLOT_SIZE)) return;

        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        int startRow = (int) (scrollPosition * Math.max(0, totalRows - GRID_ROWS));
        int clickedCol = (int) ((mouseX - startX) / SLOT_SIZE);
        int clickedRow = (int) ((mouseY - startY) / SLOT_SIZE);
        int index = (startRow + clickedRow) * GRID_COLS + clickedCol;

        if (index >= 0 && index < filteredItems.size()) {
            ItemEntry entry = filteredItems.get(index);
            int change = (button == 0) ? 64 : 1;
            if (Screen.hasShiftDown()) change = -change;

            int current = storageManager.getRequestQueue().getOrDefault(entry.id, 0);
            int target = Math.max(0, Math.min(entry.totalCount, current + change));

            if (target == 0) storageManager.getRequestQueue().remove(entry.id);
            else storageManager.getRequestQueue().put(entry.id, target);

            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void refreshItemList() {
        allItems.clear();
        // SỬA DÒNG NÀY TƯƠNG TỰ
        Map<String, Map<String, Integer>> cache = AutoStash.getGlobalBuffer();
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

    private void onSearchChanged(String text) {
        filterItems();
        scrollPosition = 0.0f;
    }

    private void filterItems() {
        String query = searchBox.getValue().toLowerCase();
        if (query.isEmpty()) filteredItems = new ArrayList<>(allItems);
        else filteredItems = allItems.stream().filter(e -> e.stack.getHoverName().getString().toLowerCase().contains(query)).collect(Collectors.toList());

        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        if (totalRows <= GRID_ROWS) scrollPosition = 0.0f;
        else scrollPosition = Mth.clamp(scrollPosition, 0.0f, 1.0f);
    }

    private String shortenedCount(int count) {
        if (count >= 1000000) return String.format("%.1fM", count / 1000000.0);
        if (count >= 1000) return String.format("%.1fk", count / 1000.0);
        return String.valueOf(count);
    }

    public boolean isSearchFocused() { return this.searchBox != null && this.searchBox.isFocused(); }
}