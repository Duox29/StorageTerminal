package com.duox.advancedutilities.gui.widgets;
/*
 * Widget for configuring numeric settings.
 * Renders as a slider.
 */
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class NumberWidget extends SettingWidget {
    private final NumberSetting setting;

    public NumberWidget(NumberSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        double currentVal = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        
        AbstractSliderButton slider = new AbstractSliderButton(
                x, y, width, height,
                Component.literal(setting.getName() + ": " + String.format("%.1f", setting.getValue())),
                currentVal
        ) {
            @Override
            protected void updateMessage() {
                double val = this.value * (setting.getMax() - setting.getMin()) + setting.getMin();
                // Snap to increment
                double inc = setting.getIncrement();
                if (inc > 0) {
                    val = Math.round(val / inc) * inc;
                }
                this.setMessage(Component.literal(setting.getName() + ": " + String.format("%.1f", val)));
            }

            @Override
            protected void applyValue() {
                double val = this.value * (setting.getMax() - setting.getMin()) + setting.getMin();
                // Snap to increment
                double inc = setting.getIncrement();
                if (inc > 0) {
                    val = Math.round(val / inc) * inc;
                }
                setting.setValue(val);
                ConfigManager.getInstance().save();
            }
        };
        widgetConsumer.accept(slider);
    }
}