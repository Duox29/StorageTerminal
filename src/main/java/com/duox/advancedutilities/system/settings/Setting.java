package com.duox.advancedutilities.system.settings;

import com.google.gson.JsonElement;

/*
 * Base class for all settings.
 * Provides serialization/deserialization logic so ConfigManager doesn't need to know specific types.
 *
 * @param <T> The type of value this setting holds
 */
public abstract class Setting<T> {
    private final String name;
    protected T value;

    /**
     * Creates a new setting with the specified name and default value.
     *
     * @param name The display name of the setting
     * @param defaultValue The default value
     */
    public Setting(String name, T defaultValue) {
        this.name = name;
        this.value = defaultValue;
    }

    public String getName() { return name; }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }

    /**
     * Serializes the current value to a JsonElement.
     *
     * @return A JsonElement representing the current value
     */
    public abstract JsonElement save();

    /**
     * Loads the value from a JsonElement.
     * Implementation should handle validation and error cases.
     *
     * @param element The JsonElement to load from
     */
    public abstract void load(JsonElement element);
}