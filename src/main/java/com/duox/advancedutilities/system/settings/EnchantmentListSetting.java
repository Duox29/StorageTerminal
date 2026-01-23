package com.duox.advancedutilities.system.settings;
/*
 * Setting that stores a map of Enchantment ID -> Data (enabled, minLevel, maxPrice).
 * Used for advanced enchantment filtering (e.g., in VillagerRoller).
 */
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
// import net.minecraft.core.registries.BuiltInRegistries;
// import net.minecraft.resources.ResourceLocation;
// import net.minecraft.world.item.enchantment.Enchantment;

import java.util.LinkedHashMap;

public class EnchantmentListSetting extends Setting<LinkedHashMap<String, EnchantmentListSetting.EnchantmentData>> {
    public EnchantmentListSetting(String name) {
        super(name, new LinkedHashMap<>());
    }

    public void add(String enchantmentId) {
        if (!value.containsKey(enchantmentId)) {
            value.put(enchantmentId, new EnchantmentData(true, 1, 64));
        }
    }

    public void remove(String enchantmentId) {
        value.remove(enchantmentId);
    }

    public void toggle(String enchantmentId) {
        if (value.containsKey(enchantmentId)) {
            EnchantmentData data = value.get(enchantmentId);
            data.enabled = !data.enabled;
        }
    }
    
    public EnchantmentData getData(String enchantmentId) {
        return value.get(enchantmentId);
    }

    public boolean contains(String enchantmentId) {
        return value.containsKey(enchantmentId) && value.get(enchantmentId).enabled;
    }

    @Override
    public JsonElement save() {
        JsonObject map = new JsonObject();
        this.value.forEach((key, data) -> {
            JsonObject dataObj = new JsonObject();
            dataObj.addProperty("enabled", data.enabled);
            dataObj.addProperty("minLevel", data.minLevel);
            dataObj.addProperty("maxPrice", data.maxPrice);
            map.add(key, dataObj);
        });
        return map;
    }

    @Override
    public void load(JsonElement element) {
        if (!element.isJsonObject()) return;

        LinkedHashMap<String, EnchantmentData> newMap = new LinkedHashMap<>();
        JsonObject obj = element.getAsJsonObject();

        for (String key : obj.keySet()) {
            JsonElement dataElem = obj.get(key);
            if (dataElem.isJsonObject()) {
                JsonObject dataObj = dataElem.getAsJsonObject();
                boolean enabled = dataObj.has("enabled") ? dataObj.get("enabled").getAsBoolean() : true;
                int minLevel = dataObj.has("minLevel") ? dataObj.get("minLevel").getAsInt() : 1;
                int maxPrice = dataObj.has("maxPrice") ? dataObj.get("maxPrice").getAsInt() : 64;
                newMap.put(key, new EnchantmentData(enabled, minLevel, maxPrice));
            } else if (dataElem.isJsonPrimitive() && dataElem.getAsJsonPrimitive().isBoolean()) {
                // Legacy support for boolean
                newMap.put(key, new EnchantmentData(dataElem.getAsBoolean(), 1, 64));
            }
        }
        this.value = newMap;
    }
    
    public static class EnchantmentData {
        public boolean enabled;
        public int minLevel;
        public int maxPrice;
        
        public EnchantmentData(boolean enabled, int minLevel, int maxPrice) {
            this.enabled = enabled;
            this.minLevel = minLevel;
            this.maxPrice = maxPrice;
        }
    }
}
