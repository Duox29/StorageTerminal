package com.duox.advancedutilities.system.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/*
 * A setting that holds an enum value.
 *
 * @param <T> The enum type
 */
public class EnumSetting<T extends Enum<T>> extends Setting<T> {
    private final T[] modes;

    /**
     * Creates a new enum setting with the specified default value.
     *
     * @param name The display name
     * @param defaultValue The default enum value
     */
    public EnumSetting(String name, T defaultValue) {
        super(name, defaultValue);
        this.modes = defaultValue.getDeclaringClass().getEnumConstants();
    }

    /**
     * Cycles to the next enum value.
     */
    public void next() {
        int nextIndex = (value.ordinal() + 1) % modes.length;
        value = modes[nextIndex];
    }

    /**
     * Sets the value by enum name (case-insensitive).
     *
     * @param name The enum constant name
     */
    public void setValueByName(String name) {
        for (T constant : modes) {
            if (constant.name().equalsIgnoreCase(name)) {
                this.value = constant;
                return;
            }
        }
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(this.value.name());
    }

    @Override
    public void load(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            setValueByName(element.getAsString());
        }
    }
}