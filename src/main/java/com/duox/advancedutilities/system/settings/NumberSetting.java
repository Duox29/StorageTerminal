package com.duox.advancedutilities.system.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/*
 * A setting that holds a numeric value with min/max bounds and increment.
 */
public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double increment;

    /**
     * Creates a new number setting with the specified parameters.
     *
     * @param name The display name
     * @param defaultValue The default value
     * @param min The minimum allowed value
     * @param max The maximum allowed value
     * @param increment The increment step for the slider
     */
    public NumberSetting(String name, double defaultValue, double min, double max, double increment) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getIncrement() { return increment; }

    /**
     * Gets the value as an integer.
     *
     * @return The integer value
     */
    public int getInt() { return value.intValue(); }

    @Override
    public void setValue(Double val) {
        // Clamp value to min/max and round to nearest increment
        double precision = 1.0 / increment;
        super.setValue(Math.round(Math.max(min, Math.min(max, val)) * precision) / precision);
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(this.value);
    }

    @Override
    public void load(JsonElement element) {
        // Validation: Ensure it's a number to avoid ClassCastExceptions
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            this.setValue(element.getAsDouble());
        }
    }
}