package com.duox.storagemanager.system.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/* Base type for serializable settings. */
public abstract class Setting<T> {
    private final String name;
    protected T value;

    public Setting(String name, T defaultValue) {
        this.name = name;
        this.value = defaultValue;
    }

    public String getName() { return name; }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }

    /* Every concrete setting stores a plain JSON primitive. */
    public JsonElement save() {
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        return new JsonPrimitive((Number) value);
    }

    public abstract void load(JsonElement element);
}