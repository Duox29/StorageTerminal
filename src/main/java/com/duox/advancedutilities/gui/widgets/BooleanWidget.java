package com.duox.advancedutilities.gui.widgets;
/*
 * Widget for configuring a boolean setting.
 * Renders as a toggle button.
 */
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.BooleanSetting;
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
        Button btn = Button.builder(
                        Component.literal(setting.getName() + ": " + (setting.getValue() ? "ON" : "OFF")),
                        button -> {
                            setting.toggle();
                            ConfigManager.getInstance().save();
                            // Cập nhật text và màu sắc ngay lập tức
                            button.setMessage(Component.literal(setting.getName() + ": " + (setting.getValue() ? "ON" : "OFF")));
                            button.setFGColor(setting.getValue() ? 0x55FF55 : 0xAAAAAA);
                        })
                .bounds(x, y, width, height)
                .build();

        btn.setFGColor(setting.getValue() ? 0x55FF55 : 0xAAAAAA);
        widgetConsumer.accept(btn);
    }
}