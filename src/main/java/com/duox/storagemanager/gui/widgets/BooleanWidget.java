package com.duox.storagemanager.gui.widgets;
/*
 * Widget for configuring a boolean setting.
 * Renders as a toggle button.
 */
import com.duox.storagemanager.system.ConfigManager;
import com.duox.storagemanager.system.settings.BooleanSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class BooleanWidget extends SettingWidget {
    private final BooleanSetting setting;

    public BooleanWidget(BooleanSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        // Tạo message có màu ngay từ đầu
        Component initialMessage = Component.literal(
                        setting.getName() + ": " + (setting.getValue() ? "ON" : "OFF"))
                .withStyle(setting.getValue() ? ChatFormatting.GREEN : ChatFormatting.GRAY);

        Button btn = Button.builder(initialMessage, button -> {
                    setting.toggle();
                    ConfigManager.getInstance().save();

                    // Cập nhật message + màu ngay lập tức
                    Component newMessage = Component.literal(
                                    setting.getName() + ": " + (setting.getValue() ? "ON" : "OFF"))
                            .withStyle(setting.getValue() ? ChatFormatting.GREEN : ChatFormatting.GRAY);

                    button.setMessage(newMessage);
                })
                .bounds(x, y, width, height)
                .build();

        widgetConsumer.accept(btn);
    }
}