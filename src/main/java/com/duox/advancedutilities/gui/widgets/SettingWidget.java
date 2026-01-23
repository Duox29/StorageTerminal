package com.duox.advancedutilities.gui.widgets;
/*
 * Base class for all setting widgets.
 * Provides common methods for initialization and rendering.
 */
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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

    // Hàm xử lý click chuột (nếu cần)
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}