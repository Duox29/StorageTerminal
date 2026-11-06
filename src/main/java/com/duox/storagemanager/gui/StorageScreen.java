package com.duox.storagemanager.gui;

import com.duox.storagemanager.gui.widgets.ItemGridView;
import com.duox.storagemanager.gui.widgets.ModernButton;
import com.duox.storagemanager.gui.widgets.StorageGridMetrics;
import com.duox.storagemanager.gui.widgets.StorageTheme;
import com.duox.storagemanager.gui.widgets.StorageUi;
import com.duox.storagemanager.gui.widgets.VanillaStorageGui;
import com.duox.storagemanager.modules.AutoStash;
import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ItemFilterCategory;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.utils.ChestCache;
import com.duox.storagemanager.utils.ToastUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class StorageScreen extends Screen {
    private int guiLeft;
    private int guiTop;
    private StorageGridMetrics metrics;

    private final StorageManager storageManager;
    private final ItemGridView itemGrid;
    private EditBox searchBox;

    public StorageScreen(StorageManager manager) {
        super(Component.literal("Storage Terminal"));
        this.storageManager = manager;
        this.storageManager.ensureCacheLoaded();
        AutoStash autoStash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        if (autoStash != null && Minecraft.getInstance().player != null) {
            autoStash.forceRefreshActiveCache();
        }
        this.itemGrid = new ItemGridView(manager);
        applyScaleSettings();
    }

    private void applyScaleSettings() {
        metrics = StorageGridMetrics.create(
                storageManager.screenScale.getValue(),
                storageManager.screenGridCols.getValue().intValue(),
                storageManager.screenGridRows.getValue().intValue(),
                9, 50, 20, 32);
    }

    @Override
    protected void init() {
        super.init();

        this.guiLeft = (this.width - metrics.width()) / 2;
        this.guiTop = (this.height - metrics.height()) / 2;

        int padding = 10;
        int searchW = metrics.width() - (padding * 2);
        this.searchBox = new EditBox(this.font, guiLeft + padding, guiTop + 25, searchW, 18, Component.literal("Search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setTextColor(0xFFFFFFFF);
        this.searchBox.setResponder(itemGrid::setQuery);
        this.addWidget(this.searchBox);
        itemGrid.setQuery("");

        int btnY = guiTop + metrics.height() - 28;
        int bottomPadding = 7;
        int bottomBtnSpacing = 2;
        int bottomBtnWidth = (metrics.width() - (bottomPadding * 2) - bottomBtnSpacing) / 2;

        ModernButton autoStashButton = StorageUi.autoStashButton(
                guiLeft + bottomPadding, btnY, bottomBtnWidth, 20);
        this.addRenderableWidget(autoStashButton);

        ModernButton requestButton = StorageUi.requestButton(
                guiLeft + bottomPadding + bottomBtnWidth + bottomBtnSpacing, btnY, bottomBtnWidth, 20,
                () -> {
                    if (!storageManager.isEnabled()) {
                        storageManager.setEnabled(true);
                    } else {
                        storageManager.startRetrieval();
                    }
                });
        this.addRenderableWidget(requestButton);

        int topButtonsY = guiTop - 20;
        int closeBtnX = guiLeft + metrics.width() - 20;
        int buttonSpacing = 2;
        int sideBtnWidth = 40;

        ModernButton closeBtn = new ModernButton(closeBtnX, topButtonsY, 20, 20, Component.literal("X"), b -> {
            if (!storageManager.isRetrieving()) {
                storageManager.setEnabled(false);
            }
            this.onClose();
        });
        this.addRenderableWidget(closeBtn);

        int clearBtnX = closeBtnX - sideBtnWidth - buttonSpacing;
        ModernButton clearCacheButton = new ModernButton(clearBtnX, topButtonsY, sideBtnWidth, 20,
                Component.literal("Clear"), b -> {
                    ChestCache.clearAll(this.minecraft);
                    itemGrid.refreshFromCache();
                    ToastUtils.sendToast("§6Storage", "Cache cleared.");
                });
        this.addRenderableWidget(clearCacheButton);

        int buildBtnX = clearBtnX - sideBtnWidth - buttonSpacing;
        ModernButton buildCacheButton = new ModernButton(buildBtnX, topButtonsY, sideBtnWidth, 20,
                Component.literal("Build"), b -> {
                    AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
                    if (stash != null && !stash.isEnabled() && !stash.isCancelling()) {
                        stash.requestCacheRebuild();
                        stash.setEnabled(true);
                        ToastUtils.sendToast("§bStorage", "Rebuilding cache...");
                    }
                    StorageUi.playClick();
                });
        this.addRenderableWidget(buildCacheButton);

        int dumpBtnX = buildBtnX - sideBtnWidth - buttonSpacing;
        ModernButton dumpAllButton = StorageUi.dumpButton(dumpBtnX, topButtonsY, sideBtnWidth, 20);
        this.addRenderableWidget(dumpAllButton);

        ModernButton hotbarButton = StorageUi.hotbarButton(
                dumpBtnX - sideBtnWidth - buttonSpacing, topButtonsY, sideBtnWidth, 20);
        this.addRenderableWidget(hotbarButton);

        int btnWidth = 55;
        int btnHeight = 24;
        int startY = guiTop;
        int startX = guiLeft - btnWidth - 4;

        ItemFilterCategory[] categories = ItemFilterCategory.values();
        for (int i = 0; i < categories.length; i++) {
            ItemFilterCategory cat = categories[i];
            ModernButton catBtn = new ModernButton(startX, startY + (i * (btnHeight + 2)), btnWidth, btnHeight,
                    Component.literal(cat.displayName), b -> {
                        itemGrid.setCategory(cat);
                        StorageUi.playClick();
                    }).setActiveSupplier(() -> itemGrid.getCategory() == cat);

            this.addRenderableWidget(catBtn);
        }

        itemGrid.refreshFromCache();
    }

    @Override
    public void onClose() {
        storageManager.clearPendingRequests();
        super.onClose();
    }

    @Override
    public void removed() {
        storageManager.clearPendingRequests();
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        itemGrid.refreshIfDirty();

        graphics.fill(0, 0, this.width, this.height, 0x66000000);
        VanillaStorageGui.panel(graphics, guiLeft, guiTop, metrics.width(), metrics.height());
        this.searchBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, this.title, guiLeft + 8, guiTop + 9, StorageTheme.TEXT_TITLE, false);

        itemGrid.render(graphics, layout(), mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private ItemGridView.Layout layout() {
        return metrics.layoutAt(guiLeft, guiTop, 16, 7);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return itemGrid.mouseScrolled(mouseX, mouseY, scrollX, scrollY, layout());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (super.mouseClicked(event, isFocused)) {
            return true;
        }
        return itemGrid.mouseClicked(event.x(), event.y(), event.button(), layout());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (itemGrid.mouseReleased(event.button())) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (itemGrid.mouseDragged(event.y(), event.button(), layout())) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // No background blur.
    }
}
