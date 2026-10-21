package com.duox.storagemanager.system.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/* Numeric setting with bounds and a step increment. */
public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double increment;

    public NumberSetting(String name, double defaultValue, double min, double max, double increment) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getIncrement() { return increment; }

    public int getInt() { return value.intValue(); }

    @Override
    public void setValue(Double val) {
        // Clamp and round to the nearest increment.
        double precision = 1.0 / increment;
        super.setValue(Math.round(Math.max(min, Math.min(max, val)) * precision) / precision);
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(this.value);
    }

    @Override
    public void load(JsonElement element) {
        // Ignore non-numeric values.
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            this.setValue(element.getAsDouble());
        }
    }
}