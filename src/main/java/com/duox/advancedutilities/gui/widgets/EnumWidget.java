package com.duox.advancedutilities.gui.widgets;
/*
 * Widget for configuring an enum setting.
 * Renders as a button that cycles through enum values.
 */
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.EnumSetting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class EnumWidget extends SettingWidget {
    private final EnumSetting<?> setting;

    public EnumWidget(EnumSetting<?> setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        Button btn = Button.builder(
                        Component.literal(setting.getName() + ": " + setting.getValue().name()),
                        button -> {
                            setting.next();
                            ConfigManager.getInstance().save();
                            button.setMessage(Component.literal(setting.getName() + ": " + setting.getValue().name()));
                        })
                .bounds(x, y, width, height)
                .build();
        widgetConsumer.accept(btn);
    }
}