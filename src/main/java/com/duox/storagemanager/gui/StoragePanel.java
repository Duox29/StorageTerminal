package com.duox.storagemanager.gui;

import com.duox.storagemanager.gui.widgets.ItemGridView;
import com.duox.storagemanager.gui.widgets.ModernButton;
import com.duox.storagemanager.gui.widgets.StorageGridMetrics;
import com.duox.storagemanager.gui.widgets.StorageTheme;
import com.duox.storagemanager.gui.widgets.StorageUi;
import com.duox.storagemanager.gui.widgets.VanillaStorageGui;
import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.logic.StorageTransferCoordinator;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ConfigManager;
import com.duox.storagemanager.system.ItemFilterCategory;
import com.duox.storagemanager.system.ModuleManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class StoragePanel implements Renderable, GuiEventListener, NarratableEntry {

    private static final int HEADER_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TOP_BTN_WIDTH = 37;
    private static final int TOP_BTN_SPACING = 1;
    private static final int TOP_ROW_SPACING = 2;
    private static final int BOTTOM_PADDING = 10;
    private static final int BOTTOM_BTN_SPACING = 4;
    private static final int CATEGORY_WIDTH = 44;
    private static final int CATEGORY_SPACING = 2;
    private static final int CATEGORY_GAP = 4;

    public int x;
    public int y;

    private StorageGridMetrics metrics;

    private final StorageManager storageManager;
    private final Minecraft mc = Minecraft.getInstance();
    private final ItemGridView itemGrid;
    private StorageDragController dragController;
    private EditBox searchBox;

    private ModernButton requestButton;
    private ModernButton autoStashButton;
    private ModernButton recipeButton;
    private ModernButton hotbarButton;
    private ModernButton clearCacheButton;
    private ModernButton buildCacheButton;
    private ModernButton dumpButton;
    private final List<ModernButton> topButtons = new ArrayList<>();
    private final List<ModernButton> categoryButtons = new ArrayList<>();

    private boolean isDragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private boolean widgetFocused = false;

    public StoragePanel(StorageManager manager, int startX, int startY) {
        this.storageManager = manager;
        this.x = startX;
        this.y = startY;

        AutoStash autoStash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        if (autoStash != null && Minecraft.getInstance().player != null) {
            autoStash.forceRefreshActiveCache();
        }
        this.itemGrid = new ItemGridView(manager);
        applyScaleSettings();
        initComponents();
    }

    private void applyScaleSettings() {
        metrics = StorageGridMetrics.create(
                storageManager.panelScale.getValue(),
                storageManager.panelGridCols.getValue().intValue(),
                storageManager.panelGridRows.getValue().intValue(),
                10, 45, 16, 30);
    }

    private void initComponents() {
        int searchW = metrics.width() - (BOTTOM_PADDING * 2);
        this.searchBox = StorageUi.searchBox(0, 0, searchW, 18, itemGrid::setQuery);

        this.hotbarButton = StorageUi.hotbarButton(0, 0, TOP_BTN_WIDTH, BUTTON_HEIGHT);
        this.clearCacheButton = StorageUi.clearCacheButton(
                0, 0, TOP_BTN_WIDTH, BUTTON_HEIGHT, itemGrid::refreshFromCache);
        this.buildCacheButton = StorageUi.buildCacheButton(0, 0, TOP_BTN_WIDTH, BUTTON_HEIGHT);
        this.dumpButton = StorageUi.dumpButton(0, 0, TOP_BTN_WIDTH, BUTTON_HEIGHT);

        this.recipeButton = new ModernButton(0, 0, TOP_BTN_WIDTH, BUTTON_HEIGHT,
                Component.literal("Recipe"), b -> {
                    storageManager.autoRequestRecipe.setValue(!storageManager.autoRequestRecipe.getValue());
                    StorageUi.playClick();
                }).setActiveSupplier(() -> storageManager.autoRequestRecipe.getValue());

        topButtons.clear();
        topButtons.add(clearCacheButton);
        topButtons.add(buildCacheButton);
        topButtons.add(hotbarButton);
        topButtons.add(dumpButton);
        topButtons.add(recipeButton);

        int bottomBtnWidth = (metrics.width() - (BOTTOM_PADDING * 2) - BOTTOM_BTN_SPACING) / 2;
        this.requestButton = StorageUi.requestButton(0, 0, bottomBtnWidth, BUTTON_HEIGHT,
                storageManager::startRetrieval);
        this.autoStashButton = StorageUi.autoStashButton(0, 0, bottomBtnWidth, BUTTON_HEIGHT);

        initCategoryButtons();
        layoutWidgets();
        itemGrid.refreshFromCache();
    }

    private void initCategoryButtons() {
        categoryButtons.clear();
        for (ItemFilterCategory category : ItemFilterCategory.values()) {
            categoryButtons.add(StorageUi.categoryButton(
                    0, 0, CATEGORY_WIDTH, BUTTON_HEIGHT, category,
                    () -> itemGrid.setCategory(category),
                    () -> itemGrid.getCategory() == category));
        }
    }

    private void layoutWidgets() {
        searchBox.setX(x + BOTTOM_PADDING);
        searchBox.setY(y + 25);

        layoutTopButtons();

        int bottomBtnY = y + metrics.height() - 25;
        int bottomBtnWidth = (metrics.width() - (BOTTOM_PADDING * 2) - BOTTOM_BTN_SPACING) / 2;
        requestButton.setX(x + BOTTOM_PADDING);
        requestButton.setY(bottomBtnY);
        autoStashButton.setX(x + BOTTOM_PADDING + bottomBtnWidth + BOTTOM_BTN_SPACING);
        autoStashButton.setY(bottomBtnY);

        layoutCategoryButtons();
    }

    private int topButtonsPerRow() {
        int step = TOP_BTN_WIDTH + TOP_BTN_SPACING;
        return Math.max(1, (metrics.width() + TOP_BTN_SPACING) / step);
    }

    private int topRowCount() {
        int perRow = topButtonsPerRow();
        return (topButtons.size() + perRow - 1) / perRow;
    }

    private int topRowsHeight() {
        return topRowCount() * (BUTTON_HEIGHT + TOP_ROW_SPACING);
    }

    /** Wraps the top controls across as many rows as the panel width allows. */
    private void layoutTopButtons() {
        int perRow = topButtonsPerRow();
        int rows = topRowCount();
        int step = TOP_BTN_WIDTH + TOP_BTN_SPACING;
        int firstRowY = y - 2 - rows * BUTTON_HEIGHT - (rows - 1) * TOP_ROW_SPACING;

        for (int i = 0; i < topButtons.size(); i++) {
            int row = i / perRow;
            int offsetInRow = i % perRow;
            topButtons.get(i).setX(x + offsetInRow * step);
            topButtons.get(i).setY(firstRowY + row * (BUTTON_HEIGHT + TOP_ROW_SPACING));
        }
    }

    private void layoutCategoryButtons() {
        int columnX = prefersLeftSide()
                ? x - CATEGORY_WIDTH - CATEGORY_GAP
                : x + metrics.width() + CATEGORY_GAP;

        for (int i = 0; i < categoryButtons.size(); i++) {
            ModernButton button = categoryButtons.get(i);
            button.setX(columnX);
            button.setY(y + i * (BUTTON_HEIGHT + CATEGORY_SPACING));
        }
    }

    private boolean prefersLeftSide() {
        return x + metrics.width() / 2 < mc.getWindow().getGuiScaledWidth() / 2;
    }

    private boolean isOverCategoryStrip(double mouseX, double mouseY) {
        if (categoryButtons.isEmpty()) return false;
        ModernButton first = categoryButtons.getFirst();
        ModernButton last = categoryButtons.getLast();
        return mouseX >= first.getX() && mouseX <= first.getX() + CATEGORY_WIDTH
                && mouseY >= first.getY() && mouseY <= last.getY() + BUTTON_HEIGHT;
    }

    private boolean isOverHeader(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + metrics.width()
                && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
    }

    private ItemGridView.Layout layout() {
        return metrics.layoutAt(x, y, 13, 6);
    }

    public void setDragController(StorageDragController controller) {
        this.dragController = controller;
    }

    public boolean isStorageDropTarget(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + metrics.width()
                && mouseY >= y && mouseY < y + metrics.height();
    }

    public int getPanelWidth() {
        return metrics.width();
    }

    public int getPanelHeight() {
        return metrics.height();
    }

    /** Selects a stored item while a silent menu makes the visible screen stale. */
    public boolean beginPanelDragAt(MouseButtonEvent event) {
        if (dragController == null || mc.player == null
                || event.button() != InputConstants.MOUSE_BUTTON_LEFT
                || event.hasShiftDown() || event.hasControlDown() || event.hasAltDown()
                || (event.modifiers() & InputConstants.MOD_SUPER) != 0
                || (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                    && screen.getMenu() == mc.player.containerMenu
                    && !mc.player.containerMenu.getCarried().isEmpty())) return false;
        ItemGridView.ItemSelection selected = itemGrid.itemAt(event.x(), event.y(), layout());
        if (selected == null) return false;
        dragController.beginPanel(itemGrid, selected, event.x(), event.y());
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        itemGrid.refreshIfDirty();
        // Rows and the strip side depend on the panel position and width.
        layoutTopButtons();
        layoutCategoryButtons();

        VanillaStorageGui.panel(graphics, x, y, metrics.width(), metrics.height());

        if (isOverHeader(mouseX, mouseY) && metrics.width() > 8) {
            graphics.fill(x + 4, y + 4, x + metrics.width() - 4, y + HEADER_HEIGHT,
                    StorageTheme.HEADER_HOVER);
        }
        graphics.text(mc.font, "Storage Terminal", x + 8, y + 8, StorageTheme.TEXT_TITLE, false);
        int queued = StorageTransferCoordinator.queuedCount();
        if (queued > 0) graphics.text(mc.font, "Queued: " + queued,
                x + metrics.width() - 70, y + 8, StorageTheme.TEXT_TITLE, false);
        renderWidget(graphics, searchBox, mouseX, mouseY, partialTick);
        renderWidget(graphics, requestButton, mouseX, mouseY, partialTick);
        renderWidget(graphics, autoStashButton, mouseX, mouseY, partialTick);
        for (ModernButton button : topButtons) {
            renderWidget(graphics, button, mouseX, mouseY, partialTick);
        }
        for (ModernButton categoryButton : categoryButtons) {
            renderWidget(graphics, categoryButton, mouseX, mouseY, partialTick);
        }

        itemGrid.render(graphics, layout(), mouseX, mouseY);
    }

    private void renderWidget(GuiGraphicsExtractor graphics, AbstractWidget widget,
                              int mouseX, int mouseY, float partialTick) {
        // Vanilla clips widget labels separately; even an off-screen label can
        // otherwise produce a scissor outside the window and crash the renderer.
        int inset = 4;
        if (widget.getX() + widget.getWidth() - inset <= 0
                || widget.getX() + inset >= graphics.guiWidth()
                || widget.getY() + widget.getHeight() - inset <= 0
                || widget.getY() + inset >= graphics.guiHeight()) return;
        graphics.enableScissor(0, 0, graphics.guiWidth(), graphics.guiHeight());
        try {
            widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        // A cursor stack belongs to vanilla. Do not trigger panel actions until
        // it has been placed back into a real slot.
        if (mc.player != null && mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                && screen.getMenu() == mc.player.containerMenu && !mc.player.containerMenu.getCarried().isEmpty()) {
            if (dragController != null) dragController.clear();
            return true;
        }

        if (button == InputConstants.MOUSE_BUTTON_LEFT && isOverHeader(mouseX, mouseY)) {
            isDragging = true;
            widgetFocused = true;
            dragOffsetX = (int) (mouseX - x);
            dragOffsetY = (int) (mouseY - y);
            return true;
        }

        if (searchBox.mouseClicked(event, false)) {
            searchBox.setFocused(true);
            return true;
        }

        if (requestButton.mouseClicked(event, false)) return true;
        if (autoStashButton.mouseClicked(event, false)) return true;
        if (recipeButton.mouseClicked(event, false)) return true;
        if (hotbarButton.mouseClicked(event, false)) return true;
        if (clearCacheButton.mouseClicked(event, false)) return true;
        if (buildCacheButton.mouseClicked(event, false)) return true;
        if (dumpButton.mouseClicked(event, false)) return true;

        // Buttons sit outside the panel border, so test every category before
        // the panel surface starts swallowing clicks.
        for (ModernButton categoryButton : categoryButtons) {
            if (categoryButton.mouseClicked(event, false)) return true;
        }

        if (button == InputConstants.MOUSE_BUTTON_LEFT && event.hasControlDown() && requestStackAt(event)) {
            return true;
        }

        if (beginPanelDragAt(event)) {
            return true;
        }

        itemGrid.mouseClicked(mouseX, mouseY, button, layout());
        return true;
    }

    /** Ctrl-click requests one stack (or as much as is stored) into a free inventory slot. */
    private boolean requestStackAt(MouseButtonEvent event) {
        ItemGridView.ItemSelection selected = itemGrid.itemAt(event.x(), event.y(), layout());
        if (selected == null) return false;
        StorageTransferCoordinator.retrieveStack(selected.id(), selected.stack(), selected.totalCount());
        StorageUi.playClick();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.button();

        if (itemGrid.mouseReleased(button)) {
            widgetFocused = false;
            return true;
        }

        if (isDragging && button == InputConstants.MOUSE_BUTTON_LEFT) {
            isDragging = false;
            widgetFocused = false;
            storageManager.panelX.setValue((double) x);
            storageManager.panelY.setValue((double) y);
            ConfigManager.getInstance().save();
            return true;
        }

        return searchBox.mouseReleased(event)
                || requestButton.mouseReleased(event)
                || autoStashButton.mouseReleased(event)
                || recipeButton.mouseReleased(event)
                || hotbarButton.mouseReleased(event)
                || clearCacheButton.mouseReleased(event)
                || buildCacheButton.mouseReleased(event)
                || dumpButton.mouseReleased(event)
                || releaseCategoryButtons(event);
    }

    private boolean releaseCategoryButtons(MouseButtonEvent event) {
        boolean released = false;
        for (ModernButton categoryButton : categoryButtons) {
            released |= categoryButton.mouseReleased(event);
        }
        return released;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int button = event.button();
        double mouseX = event.x();
        double mouseY = event.y();

        if (itemGrid.mouseDragged(mouseY, button, layout())) {
            return true;
        }

        if (isDragging && button == InputConstants.MOUSE_BUTTON_LEFT) {
            x = (int) (mouseX - dragOffsetX);
            y = (int) (mouseY - dragOffsetY);
            layoutWidgets();
            return true;
        }

        return searchBox.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        itemGrid.mouseScrolled(mouseX, mouseY, scrollX, scrollY, layout());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBox.isFocused()) {
            return searchBox.keyPressed(event);
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchBox.isFocused()) {
            return searchBox.charTyped(event);
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Include the wrapped top buttons and the category strip beside the panel.
        boolean overPanel = mouseX >= x && mouseX <= x + metrics.width()
                && mouseY >= y - 2 - topRowsHeight() && mouseY <= y + metrics.height();
        return overPanel || isOverCategoryStrip(mouseX, mouseY);
    }

    @Override
    public void setFocused(boolean focused) {
        this.widgetFocused = focused;
    }

    @Override
    public boolean isFocused() {
        return this.widgetFocused || this.isDragging || itemGrid.isDraggingScrollbar();
    }

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {}

    public boolean isSearchFocused() {
        return this.searchBox != null && this.searchBox.isFocused();
    }

    public boolean isHandlingDrag() {
        return isDragging || itemGrid.isDraggingScrollbar();
    }
}
