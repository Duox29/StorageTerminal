package com.duox.storagemanager.system.settings;

import com.google.gson.JsonElement;

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

    public abstract JsonElement save();

    public abstract void load(JsonElement element);
}