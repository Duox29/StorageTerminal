package com.duox.advancedutilities.gui.factory;
/*
 * Factory for creating widgets from settings.
 */
import com.duox.advancedutilities.gui.widgets.*;
import com.duox.advancedutilities.system.settings.*;

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

        // --- FIX START ---
        // We cast the raw EnumSetting.class to Class<EnumSetting<?>>.
        // This satisfies the generic bound <T extends Setting<?>>.
        @SuppressWarnings("unchecked")
        Class<EnumSetting<?>> enumClass = (Class<EnumSetting<?>>) (Class<?>) EnumSetting.class;

        register(enumClass, (s, x, y, w, h) -> new EnumWidget(s, x, y, w, h));
        // --- FIX END ---

        register(BlockListSetting.class, (s, x, y, w, h) -> new BlockListWidget(s, x, y, w, 55));
        register(EntityListSetting.class, (s, x, y, w, h) -> new EntityListWidget(s, x, y, w, 55));
        register(ItemListSetting.class, (s, x, y, w, h) -> new ItemListWidget(s, x, y, w, 55));
        register(EnchantmentListSetting.class, (s, x, y, w, h) -> new EnchantmentListWidget(s, x, y, w, 55));
    }

    public static <T extends Setting<?>> void register(Class<T> settingClass, WidgetProvider<T> provider) {
        providers.put(settingClass, provider);
    }

    @SuppressWarnings("unchecked")
    public static SettingWidget create(Setting<?> setting, int x, int y, int w, int hDefault) {
        Class<? extends Setting> key = setting.getClass();

        // Handle EnumSetting special case (Since Enums are generic, their class is technically EnumSetting.class)
        if (EnumSetting.class.isAssignableFrom(key)) {
            key = EnumSetting.class;
        }

        WidgetProvider provider = providers.get(key);
        if (provider == null) {
            System.err.println("[WidgetFactory] No provider found for: " + setting.getClass().getSimpleName());
            return null;
        }
        return provider.create(setting, x, y, w, hDefault);
    }
}