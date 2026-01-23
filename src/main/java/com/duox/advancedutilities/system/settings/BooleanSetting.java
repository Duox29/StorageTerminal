package com.duox.advancedutilities.system.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/*
 * A setting that holds a boolean value.
 */
public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    /**
     * Toggles the boolean value.
     */
    public void toggle() {
        this.value = !this.value;
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(this.value);
    }

    @Override
    public void load(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            this.value = element.getAsBoolean();
        }
    }
}