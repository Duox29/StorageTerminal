package com.duox.advancedutilities.system.settings;
/*
 * Setting that stores a map of Item -> Boolean.
 * Used for item filtering/whitelisting.
 */
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;

public class ItemListSetting extends Setting<LinkedHashMap<Item, Boolean>> {
    public ItemListSetting(String name) {
        super(name, new LinkedHashMap<>());
    }

    public void add(Item item) {
        if (!value.containsKey(item)) {
            value.put(item, true);
        }
    }

    public void remove(Item item) {
        value.remove(item);
    }

    public void toggle(Item item) {
        if (value.containsKey(item)) {
            value.put(item, !value.get(item));
        }
    }

    public boolean contains(Item item) {
        return value.getOrDefault(item, false);
    }

    @Override
    public JsonElement save() {
        JsonObject map = new JsonObject();
        this.value.forEach((item, enabled) -> {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key != null) map.addProperty(key.toString(), enabled);
        });
        return map;
    }

    @Override
    public void load(JsonElement element) {
        if (!element.isJsonObject()) return;

        LinkedHashMap<Item, Boolean> newMap = new LinkedHashMap<>();
        JsonObject obj = element.getAsJsonObject();

        for (String key : obj.keySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                newMap.put(BuiltInRegistries.ITEM.get(rl), obj.get(key).getAsBoolean());
            }
        }
        this.value = newMap;
    }
}
