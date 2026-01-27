package com.duox.storagemanager.gui.widgets;

/*
 * Base class for all setting widgets.
 * Provides common methods for initialization and rendering.
 */
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent; // Thêm import này

import java.util.function.Consumer;

public abstract class SettingWidget {
    protected final int x, y, width, height;

    public SettingWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int getHeight() { return height; }

    // Hàm khởi tạo các nút bấm/slider
    public abstract void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh);

    // Hàm vẽ thêm (nếu cần)
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}

    // FIX: Cập nhật signature để khớp với hệ thống Input Event mới
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        return false;
    }
}