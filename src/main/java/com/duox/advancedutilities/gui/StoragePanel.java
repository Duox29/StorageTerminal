package com.duox.advancedutilities.gui;

import com.duox.advancedutilities.modules.AutoStash;
import com.duox.advancedutilities.modules.StorageManager;
import com.duox.advancedutilities.system.ModuleManager;
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
import java.util.stream.Collectors;

public class StoragePanel implements Renderable, GuiEventListener, NarratableEntry {

    // --- SETTINGS ---
    private static final int PANEL_WIDTH = 180;
    private static final int PANEL_HEIGHT = 200;
    private static final int HEADER_HEIGHT = 20; // Chiều cao thanh tiêu đề

    // Position
    public int x;
    public int y;

    // --- COLORS ---
    private static final int COLOR_BG_MAIN = 0xFF212121;
    private static final int COLOR_BG_BORDER = 0xFF585858;
    private static final int COLOR_SLOT_BG = 0xFF353535;
    private static final int COLOR_SLOT_HIGHLIGHT = 0x80FFFFFF;

    // Màu Header: Bình thường / Khi hover chuột (để biết là kéo được)
    private static final int COLOR_HEADER = 0xFF303030;
    private static final int COLOR_HEADER_HOVER = 0xFF404040;

    // Grid Settings
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 7;
    private static final int GRID_X_OFFSET = 10;
    private static final int GRID_Y_OFFSET = 45;

    // Components
    private final StorageManager storageManager;
    private final Minecraft mc = Minecraft.getInstance();
    private EditBox searchBox;
    private Button requestButton;
    private Button autoStashButton;

    // Data
    private List<ItemEntry> allItems = new ArrayList<>();
    private List<ItemEntry> filteredItems = new ArrayList<>();
    private float scrollPosition = 0.0f;
    private boolean isFocused = false;

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

    public StoragePanel(StorageManager manager, int startX, int startY) {
        this.storageManager = manager;
        this.x = startX;
        this.y = startY;
        initComponents();
    }

    private void initComponents() {
        int searchW = 120;
        this.searchBox = new EditBox(mc.font, x + PANEL_WIDTH - searchW - 10, y + 25, searchW, 12, Component.literal("Search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFFFF);
        this.searchBox.setResponder(this::onSearchChanged);

        int btnY = y + PANEL_HEIGHT - 25;
        this.requestButton = Button.builder(Component.literal("Req"), b -> {
            storageManager.startRetrieval();
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }).bounds(x + 10, btnY, 40, 18).build();

        this.autoStashButton = Button.builder(Component.literal("Stash"), b -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) stash.setEnabled(true);
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }).bounds(x + PANEL_WIDTH - 50, btnY, 40, 18).build();

        refreshItemList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (AutoStash.cacheDirty) {
            refreshItemList();
            AutoStash.cacheDirty = false;
        }
        // 1. Background
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, COLOR_BG_MAIN);
        graphics.renderOutline(x, y, PANEL_WIDTH, PANEL_HEIGHT, COLOR_BG_BORDER);

        // 2. Header
        graphics.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, COLOR_HEADER);
        graphics.drawString(mc.font, "Storage Terminal", x + 5, y + 6, 0xFFE0E0E0, false);

        // Vẽ thêm một cái viền nhỏ dưới header để tách biệt
        graphics.hLine(x, x + PANEL_WIDTH - 1, y + HEADER_HEIGHT, COLOR_BG_BORDER);

        // 3. Grid Background
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int sx = x + GRID_X_OFFSET + col * SLOT_SIZE;
                int sy = y + GRID_Y_OFFSET + row * SLOT_SIZE;
                graphics.fill(sx, sy, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, COLOR_SLOT_BG);
            }
        }

        // 4. Search Box BG
        graphics.fill(searchBox.getX() - 2, searchBox.getY() - 2, searchBox.getX() + searchBox.getWidth() + 2, searchBox.getY() + 14, 0xFF000000);

        // 5. Render Components
        searchBox.render(graphics, mouseX, mouseY, partialTick);
        requestButton.render(graphics, mouseX, mouseY, partialTick);
        autoStashButton.render(graphics, mouseX, mouseY, partialTick);

        // 6. Scrollbar
        renderScrollbar(graphics, mouseX, mouseY);

        // 7. Items
        renderItems(graphics, mouseX, mouseY);
    }

    private void renderItems(GuiGraphics graphics, int mouseX, int mouseY) {
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

            graphics.renderItem(entry.stack, sx + 1, sy + 1);
            graphics.renderItemDecorations(mc.font, entry.stack, sx + 1, sy + 1, shortenedCount(entry.totalCount));

            // Queue overlay
            int queued = storageManager.getRequestQueue().getOrDefault(entry.id, 0);
            if (queued > 0) {
                graphics.renderOutline(sx, sy, SLOT_SIZE -1, SLOT_SIZE -1, 0xFF00FF00);
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

    // --- INPUT HANDLING ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        // 1. Components
        if (searchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocusedListener(searchBox);
            return true;
        }
        if (requestButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (autoStashButton.mouseClicked(mouseX, mouseY, button)) return true;

        // 3. Grid
        handleGridClick(mouseX, mouseY, button);

        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return searchBox.mouseReleased(mouseX, mouseY, button) ||
                requestButton.mouseReleased(mouseX, mouseY, button) ||
                autoStashButton.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return searchBox.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseOver(mouseX, mouseY)) {
            int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
            if (totalRows <= GRID_ROWS) return true;
            float scrollStep = 1.0f / (totalRows - GRID_ROWS);
            scrollPosition = Mth.clamp(scrollPosition - (float)delta * scrollStep, 0.0f, 1.0f);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused()) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + PANEL_WIDTH &&
                mouseY >= y && mouseY <= y + PANEL_HEIGHT;
    }

    // --- Narration / Focus Boilerplate ---
    @Override
    public void setFocused(boolean focused) { this.isFocused = focused; }
    @Override
    public boolean isFocused() { return isFocused; }
    @Override
    public NarratableEntry.NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {}

    public void setFocusedListener(GuiEventListener listener) {
        if (listener == searchBox) searchBox.setFocused(true);
        else searchBox.setFocused(false);
    }

    // --- UTILS ---
    private void handleGridClick(double mouseX, double mouseY, int button) {
        int startX = x + GRID_X_OFFSET;
        int startY = y + GRID_Y_OFFSET;
        if (mouseX < startX || mouseX > startX + (GRID_COLS * SLOT_SIZE) ||
                mouseY < startY || mouseY > startY + (GRID_ROWS * SLOT_SIZE)) return;

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

    private void onSearchChanged(String text) { filterItems(); }

    private void filterItems() {
        String query = searchBox.getValue().toLowerCase();
        if (query.isEmpty()) filteredItems = new ArrayList<>(allItems);
        else filteredItems = allItems.stream().filter(e -> e.stack.getHoverName().getString().toLowerCase().contains(query)).collect(Collectors.toList());
        scrollPosition = 0.0f;
    }

    private String shortenedCount(int count) {
        if (count >= 1000000) return String.format("%.1fM", count / 1000000.0);
        if (count >= 1000) return String.format("%.1fk", count / 1000.0);
        return String.valueOf(count);
    }
}