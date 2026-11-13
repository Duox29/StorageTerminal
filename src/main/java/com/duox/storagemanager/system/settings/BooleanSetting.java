package com.duox.storagemanager.system.settings;

import com.google.gson.JsonElement;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public void toggle() {
        this.value = !this.value;
    }

    @Override
    public void load(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            this.value = element.getAsBoolean();
        }
    }
}