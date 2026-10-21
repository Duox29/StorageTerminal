package com.duox.storagemanager.gui;

import com.duox.storagemanager.gui.widgets.ItemGridView;
import com.duox.storagemanager.gui.widgets.ModernButton;
import com.duox.storagemanager.gui.widgets.StorageGridMetrics;
import com.duox.storagemanager.gui.widgets.StorageTheme;
import com.duox.storagemanager.gui.widgets.StorageUi;
import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class StoragePanel implements Renderable, GuiEventListener, NarratableEntry {

    private static final int HEADER_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TOP_BTN_WIDTH = 48;
    private static final int TOP_BTN_SPACING = 2;
    private static final int BOTTOM_PADDING = 10;
    private static final int BOTTOM_BTN_SPACING = 4;

    public int x;
    public int y;

    private StorageGridMetrics metrics;

    private final StorageManager storageManager;
    private final Minecraft mc = Minecraft.getInstance();
    private final ItemGridView itemGrid;
    private EditBox searchBox;

    private ModernButton requestButton;
    private ModernButton autoStashButton;
    private ModernButton recipeButton;
    private ModernButton hotbarButton;

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
        this.searchBox = new EditBox(mc.font, 0, 0, searchW, 12, Component.literal("Search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFFFF);
        this.searchBox.setResponder(itemGrid::setQuery);

        this.hotbarButton = StorageUi.hotbarButton(0, 0, TOP_BTN_WIDTH, BUTTON_HEIGHT);

        this.recipeButton = new ModernButton(0, 0, TOP_BTN_WIDTH, BUTTON_HEIGHT,
                getRecipeLabel(), b -> {
                    storageManager.autoRequestRecipe.setValue(!storageManager.autoRequestRecipe.getValue());
                    b.setMessage(getRecipeLabel());
                    StorageUi.playClick();
                }).setActiveSupplier(() -> storageManager.autoRequestRecipe.getValue());

        int bottomBtnWidth = (metrics.width() - (BOTTOM_PADDING * 2) - BOTTOM_BTN_SPACING) / 2;
        this.requestButton = StorageUi.requestButton(0, 0, bottomBtnWidth, BUTTON_HEIGHT,
                storageManager::startRetrieval);
        this.autoStashButton = StorageUi.autoStashButton(0, 0, bottomBtnWidth, BUTTON_HEIGHT);

        layoutWidgets();
        itemGrid.refreshFromCache();
    }

    private Component getRecipeLabel() {
        return StorageUi.toggleLabel("Recipe", storageManager.autoRequestRecipe.getValue());
    }

    private void layoutWidgets() {
        searchBox.setX(x + BOTTOM_PADDING);
        searchBox.setY(y + 25);

        int topBtnY = y - 22;
        hotbarButton.setX(x + metrics.width() - TOP_BTN_WIDTH);
        hotbarButton.setY(topBtnY);
        recipeButton.setX(x + metrics.width() - TOP_BTN_WIDTH * 2 - TOP_BTN_SPACING);
        recipeButton.setY(topBtnY);

        int bottomBtnY = y + metrics.height() - 25;
        int bottomBtnWidth = (metrics.width() - (BOTTOM_PADDING * 2) - BOTTOM_BTN_SPACING) / 2;
        requestButton.setX(x + BOTTOM_PADDING);
        requestButton.setY(bottomBtnY);
        autoStashButton.setX(x + BOTTOM_PADDING + bottomBtnWidth + BOTTOM_BTN_SPACING);
        autoStashButton.setY(bottomBtnY);
    }

    private ItemGridView.Layout layout() {
        return metrics.layoutAt(x, y, 8, 6);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        itemGrid.refreshIfDirty();

        graphics.fill(x, y, x + metrics.width(), y + metrics.height(), StorageTheme.BG_MAIN);
        graphics.outline(x, y, metrics.width(), metrics.height(), StorageTheme.BG_BORDER);

        boolean isHeaderHovered = mouseX >= x && mouseX <= x + metrics.width()
                && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
        graphics.fill(x, y, x + metrics.width(), y + HEADER_HEIGHT,
                isHeaderHovered ? StorageTheme.HEADER_HOVER : StorageTheme.HEADER);
        graphics.text(mc.font, "Storage Terminal", x + 5, y + 6, StorageTheme.TEXT_TITLE, false);
        graphics.horizontalLine(x, x + metrics.width() - 1, y + HEADER_HEIGHT, StorageTheme.BG_BORDER);

        graphics.fill(searchBox.getX() - 2, searchBox.getY() - 2,
                searchBox.getX() + searchBox.getWidth() + 2, searchBox.getY() + 14, 0xFF000000);
        searchBox.extractRenderState(graphics, mouseX, mouseY, partialTick);

        requestButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        autoStashButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        recipeButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        hotbarButton.extractRenderState(graphics, mouseX, mouseY, partialTick);

        itemGrid.render(graphics, layout(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        int headerDragWidth = metrics.width() - 130;
        if (button == 0 && mouseX >= x && mouseX <= x + headerDragWidth
                && mouseY >= y && mouseY <= y + HEADER_HEIGHT) {
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

        itemGrid.mouseClicked(mouseX, mouseY, button, layout());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.button();

        if (itemGrid.mouseReleased(button)) {
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

        return searchBox.mouseReleased(event)
                || requestButton.mouseReleased(event)
                || autoStashButton.mouseReleased(event)
                || recipeButton.mouseReleased(event)
                || hotbarButton.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int button = event.button();
        double mouseX = event.x();
        double mouseY = event.y();

        if (itemGrid.mouseDragged(mouseY, button, layout())) {
            return true;
        }

        if (isDragging && button == 0) {
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
        // Include the floating top buttons above the panel.
        return mouseX >= x && mouseX <= x + metrics.width()
                && mouseY >= y - 25 && mouseY <= y + metrics.height();
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
}
