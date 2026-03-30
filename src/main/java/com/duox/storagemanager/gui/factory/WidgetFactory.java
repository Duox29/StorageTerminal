package com.duox.storagemanager.gui.factory;
/*
 * Factory for creating widgets from settings.
 */
import com.duox.storagemanager.gui.widgets.*;
import com.duox.storagemanager.system.settings.*;

import java.util.HashMap;
import java.util.Map;

public class WidgetFactory {

    @FunctionalInterface
    public interface WidgetProvider<T extends Setting<?>> {
        SettingWidget create(T setting, int x, int y, int w, int h);
    }

    @SuppressWarnings("rawtypes")
    private static final Map<Class<? extends Setting>, WidgetProvider> providers = new HashMap<>();

    static {
        register(BooleanSetting.class, (s, x, y, w, h) -> new BooleanWidget(s, x, y, w, h));
        register(NumberSetting.class, (s, x, y, w, h) -> new NumberWidget(s, x, y, w, h));
    }

    public static <T extends Setting<?>> void register(Class<T> settingClass, WidgetProvider<T> provider) {
        providers.put(settingClass, provider);
    }

    @SuppressWarnings("unchecked")
    public static SettingWidget create(Setting<?> setting, int x, int y, int w, int hDefault) {
        Class<? extends Setting> key = setting.getClass();

        WidgetProvider provider = providers.get(key);
        if (provider == null) {
            System.err.println("[WidgetFactory] No provider found for: " + setting.getClass().getSimpleName());
            return null;
        }
        return provider.create(setting, x, y, w, hDefault);
    }
}