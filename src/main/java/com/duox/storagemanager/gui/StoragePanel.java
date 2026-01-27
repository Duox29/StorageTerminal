package com.duox.storagemanager.gui;

import com.duox.storagemanager.gui.widgets.SlotScrollHandler;
import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StoragePanel implements Renderable, GuiEventListener, NarratableEntry {

    // --- SETTINGS ---
    private int PANEL_WIDTH = 180;
    private int PANEL_HEIGHT = 200;
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
    private int SLOT_SIZE = 18;
    private int GRID_COLS = 8;
    private int GRID_ROWS = 7;
    private int GRID_X_OFFSET = 10;
    private int GRID_Y_OFFSET = 45;

    // Components
    private final StorageManager storageManager;
    private final Minecraft mc = Minecraft.getInstance();
    private EditBox searchBox;
    private Button requestButton;
    private Button autoStashButton;
    private Button recipeButton;

    // Custom scroll handler
    private SlotScrollHandler<ItemEntry> scrollHandler;

    // Data
    private List<ItemEntry> allItems = new ArrayList<>();
    private List<ItemEntry> filteredItems = new ArrayList<>();
    private float scrollPosition = 0.0f;

    // Drag Support
    private boolean isDragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private boolean widgetFocused = false;

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

    public StoragePanel(StorageManager manager, int startX, int startY) {
        this.storageManager = manager;
        this.x = startX;
        this.y = startY;
        // This ensures the overlay shows correct items for the current location
        AutoStash autoStash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        if (autoStash != null && Minecraft.getInstance().player != null) {
            autoStash.forceRefreshActiveCache();
        }
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

        int btnY = y + PANEL_HEIGHT - 25;
        this.requestButton = Button.builder(Component.literal("Req"), b -> {
            storageManager.startRetrieval();
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                    .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }).bounds(x + 10, btnY, 40, 18).build();

        this.autoStashButton = Button.builder(Component.literal("Stash"), b -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null)
                stash.setEnabled(true);
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                    .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }).bounds(x + PANEL_WIDTH - 50, btnY, 40, 18).build();

        this.recipeButton = Button.builder(getRecipeLabel(), b -> {
            boolean current = storageManager.autoRequestRecipe.getValue();
            storageManager.autoRequestRecipe.setValue(!current);
            b.setMessage(getRecipeLabel());
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                    .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }).bounds(x + 55, btnY, 70, 18).build();

        refreshItemList();
    }

    private void repositionComponents() {
        int padding = 10;
        int searchW = PANEL_WIDTH - (padding * 2);
        searchBox.setWidth(searchW); // Ensure width is updated if panel resizes (optional)
        searchBox.setX(x + padding);
        searchBox.setY(y + 25);

        int btnY = y + PANEL_HEIGHT - 25;
        requestButton.setX(x + 10);
        requestButton.setY(btnY);

        autoStashButton.setX(x + PANEL_WIDTH - 50);
        autoStashButton.setY(btnY);

        recipeButton.setX(x + 55);
        recipeButton.setY(btnY);
    }

    private Component getRecipeLabel() {
        boolean on = storageManager.autoRequestRecipe.getValue();
        return Component.literal("Recipe: " + (on ? "ON" : "OFF"))
                .withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED);
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

        // 2. Header - with hover effect to indicate draggable
        boolean isHeaderHovered = mouseX >= x && mouseX <= x + PANEL_WIDTH &&
                                   mouseY >= y && mouseY <= y + HEADER_HEIGHT;
        int headerColor = isHeaderHovered ? COLOR_HEADER_HOVER : COLOR_HEADER;
        graphics.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, headerColor);
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
        graphics.fill(searchBox.getX() - 2, searchBox.getY() - 2, searchBox.getX() + searchBox.getWidth() + 2,
                searchBox.getY() + 14, 0xFF000000);

        // 5. Render Components
        searchBox.render(graphics, mouseX, mouseY, partialTick);
        requestButton.render(graphics, mouseX, mouseY, partialTick);
        autoStashButton.render(graphics, mouseX, mouseY, partialTick);
        recipeButton.render(graphics, mouseX, mouseY, partialTick);

        // 6. Scrollbar
        renderScrollbar(graphics, mouseX, mouseY);

        // 7. Items
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
            graphics.pose().pushMatrix();
            // Translate to slot + padding
            graphics.pose().translate(sx + 1, sy + 1);            // Scale
            graphics.pose().scale(itemScale, itemScale);
            graphics.renderItem(entry.stack, 0, 0);
            graphics.renderItemDecorations(mc.font, entry.stack, 0, 0, shortenedCount(entry.totalCount));

            graphics.pose().popMatrix();
            // Queue overlay
            int queued = storageManager.getRequestQueue().getOrDefault(entry.id, 0);
            if (queued > 0) {
                graphics.renderOutline(sx, sy, SLOT_SIZE - 1, SLOT_SIZE - 1, 0xFF00FF00);
            }

            if (isHovered) {
                List<Component> tooltip = new ArrayList<>();
                Item.TooltipContext context = Item.TooltipContext.of(mc.level);
                tooltip.addAll(entry.stack.getTooltipLines(context, mc.player, TooltipFlag.NORMAL));
                tooltip.add(Component.literal("§7Stored: §f" + entry.totalCount));
                if (queued > 0)
                    tooltip.add(Component.literal("§eRequesting: " + queued));
                graphics.setTooltipForNextFrame(mc.font, tooltip, entry.stack.getTooltipImage(), mouseX, mouseY);            }
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
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        // 1. Extract the raw values from the event object
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (!isMouseOver(mouseX, mouseY))
            return false;

        // --- Original Logic Starts Here ---

        // PRIORITY 1: Check if clicking on scrollbar
        int scrollX = x + PANEL_WIDTH - 8;
        int scrollY = y + GRID_Y_OFFSET;
        int scrollH = GRID_ROWS * SLOT_SIZE;

        if (button == 0 && mouseX >= scrollX && mouseX <= scrollX + 6 &&
                mouseY >= scrollY && mouseY <= scrollY + scrollH) {
            int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
            if (totalRows > GRID_ROWS) {
                isDraggingScrollbar = true;
                widgetFocused = true;
                scrollbarDragStartY = (int) mouseY;
                scrollPositionAtDragStart = scrollPosition;
                return true;
            }
        }

        // PRIORITY 2: Check if clicking on header for dragging
        int headerDragWidth = PANEL_WIDTH - 130;
        if (button == 0 && mouseX >= x && mouseX <= x + headerDragWidth &&
                mouseY >= y && mouseY <= y + HEADER_HEIGHT) {
            isDragging = true;
            widgetFocused = true;
            dragOffsetX = (int) (mouseX - x);
            dragOffsetY = (int) (mouseY - y);
            System.out.println("[StoragePanel] Started dragging at offset: " + dragOffsetX + ", " + dragOffsetY);
            return true;
        }

        // PRIORITY 3: Components
        // Note: You may need to update these components too if they expect the Event object now!
        // For standard vanilla components (EditBox, Button), they likely handle the new system internally
        // or you might need to pass the 'event' directly if their signature changed too.
        if (searchBox.mouseClicked(event, false)) {
            setFocusedListener(searchBox);
            return true;
        }
        if (requestButton.mouseClicked(event, false))
            return true;
        if (autoStashButton.mouseClicked(event, false))
            return true;
        if (recipeButton.mouseClicked(event, false))
            return true;

        // PRIORITY 4: Grid
        handleGridClick(mouseX, mouseY, button);

        return true;
    }
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // Extract data from the record
        int button = event.button();
        double mouseX = event.x();
        double mouseY = event.y();

        // Logic from your original code
        if (isDraggingScrollbar && button == 0) {
            isDraggingScrollbar = false;
            widgetFocused = false;
            return true;
        }

        if (isDragging && button == 0) {
            isDragging = false;
            widgetFocused = false; // Release focus
            // Save position to config
            storageManager.panelX.setValue((double) x);
            storageManager.panelY.setValue((double) y);
            System.out.println("[StoragePanel] Drag ended. Saved position: " + x + ", " + y);
            return true;
        }

        // Pass through to components
        // NOTE: You will need to check if these components (EditBox/Button) also expect the event object.
        // Standard Minecraft widgets usually handle the new system, but if they are custom wrappers,
        // you might need to verify their signatures too.
        return searchBox.mouseReleased(event) ||
                requestButton.mouseReleased(event) ||
                autoStashButton.mouseReleased(event) ||
                recipeButton.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        // Extract data from the record
        int button = event.button();
        double mouseX = event.x();
        double mouseY = event.y();

        if (isDraggingScrollbar && button == 0) {
            // Calculate new scroll position based on drag
            int scrollY = y + GRID_Y_OFFSET;
            int scrollH = GRID_ROWS * SLOT_SIZE;
            int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);

            if (totalRows > GRID_ROWS) {
                int thumbH = (int) ((float) (GRID_ROWS * GRID_ROWS) / totalRows * SLOT_SIZE);
                thumbH = Math.max(20, Math.min(scrollH, thumbH));

                // Note: scrollbarDragStartY was an int, but mouseY is double. explicit cast is fine.
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
            // Update panel position
            x = (int) (mouseX - dragOffsetX);
            y = (int) (mouseY - dragOffsetY);

            System.out.println("[StoragePanel] Dragging to position: " + x + ", " + y);

            // Reposition all components to follow the panel
            repositionComponents();
            return true;
        }

        return searchBox.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        System.out.println(
                "[StoragePanel] mouseScrolled called: scrollX=" + scrollX + ", scrollY=" + scrollY + ", mouse=(" + mouseX + ", " + mouseY + ")");

        if (!isMouseOver(mouseX, mouseY)) {
            System.out.println("[StoragePanel] Mouse not over panel, ignoring scroll");
            return false;
        }

        System.out.println("[StoragePanel] Mouse is over panel");

        // PRIORITY 1: Try to handle as slot scroll (quantity adjustment)
        int gridX = x + GRID_X_OFFSET;
        int gridY = y + GRID_Y_OFFSET;

        System.out.println("[StoragePanel] Grid coords: gridX=" + gridX + ", gridY=" + gridY);
        System.out.println("[StoragePanel] Filtered items count: " + filteredItems.size());

        boolean handledBySlot = scrollHandler.handleScroll(
                mouseX, mouseY, scrollX, scrollY,
                gridX, gridY,
                SLOT_SIZE, GRID_COLS, GRID_ROWS,
                scrollPosition, filteredItems);

        System.out.println("[StoragePanel] Slot handler result: " + handledBySlot);

        if (handledBySlot) {
            System.out.println("[StoragePanel] Event handled by slot scroll");
            return true; // Slot scroll handled, don't scroll the list
        }

        // PRIORITY 2: Fallback to normal list scrolling
        System.out.println("[StoragePanel] Falling back to list scroll");
        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        if (totalRows <= GRID_ROWS) {
            System.out.println("[StoragePanel] Not enough rows to scroll");
            return true;
        }
        float scrollStep = 1.0f / (totalRows - GRID_ROWS);
        scrollPosition = Mth.clamp(scrollPosition - (float) scrollY * scrollStep, 0.0f, 1.0f);
        System.out.println("[StoragePanel] List scrolled to position: " + scrollPosition);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Check if searchBox is focused
        if (searchBox.isFocused()) {
            // The EditBox component also expects the 'KeyEvent' object now
            return searchBox.keyPressed(event);
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchBox.isFocused()) {
            // Delegate the event object directly to the EditBox
            return searchBox.charTyped(event);
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + PANEL_WIDTH &&
                mouseY >= y && mouseY <= y + PANEL_HEIGHT;
    }

    @Override
    public void setFocused(boolean focused) {
        this.widgetFocused = focused;
    }

    @Override
    public boolean isFocused() {
        return this.widgetFocused || this.isDragging || this.isDraggingScrollbar;
    }

    // --- Narration / Focus Boilerplate ---
    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
    }

    public void setFocusedListener(GuiEventListener listener) {
        if (listener == searchBox)
            searchBox.setFocused(true);
        else
            searchBox.setFocused(false);
    }

    // --- UTILS ---
    private void handleGridClick(double mouseX, double mouseY, int button) {
        int startX = x + GRID_X_OFFSET;
        int startY = y + GRID_Y_OFFSET;
        if (mouseX < startX || mouseX > startX + (GRID_COLS * SLOT_SIZE) ||
                mouseY < startY || mouseY > startY + (GRID_ROWS * SLOT_SIZE))
            return;

        int totalRows = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        int startRow = (int) (scrollPosition * Math.max(0, totalRows - GRID_ROWS));
        int clickedCol = (int) ((mouseX - startX) / SLOT_SIZE);
        int clickedRow = (int) ((mouseY - startY) / SLOT_SIZE);
        int index = (startRow + clickedRow) * GRID_COLS + clickedCol;

        if (index >= 0 && index < filteredItems.size()) {
            ItemEntry entry = filteredItems.get(index);
            int change = (button == 0) ? 64 : 1;

            // FIX: Use the Minecraft instance (mc) to check input state
            if (mc.hasShiftDown())
                change = -change;

            int current = storageManager.getRequestQueue().getOrDefault(entry.id, 0);
            int target = Math.max(0, Math.min(entry.totalCount, current + change));

            if (target == 0)
                storageManager.getRequestQueue().remove(entry.id);
            else
                storageManager.getRequestQueue().put(entry.id, target);

            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                    .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
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

    private void onSearchChanged(String text) {
        filterItems();
    }

    private void filterItems() {
        String query = searchBox.getValue().toLowerCase();
        if (query.isEmpty())
            filteredItems = new ArrayList<>(allItems);
        else
            filteredItems = allItems.stream()
                    .filter(e -> e.stack.getHoverName().getString().toLowerCase().contains(query))
                    .collect(Collectors.toList());
        scrollPosition = 0.0f;
    }

    private String shortenedCount(int count) {
        if (count >= 1000000)
            return String.format("%.1fM", count / 1000000.0);
        if (count >= 1000)
            return String.format("%.1fk", count / 1000.0);
        return String.valueOf(count);
    }
}