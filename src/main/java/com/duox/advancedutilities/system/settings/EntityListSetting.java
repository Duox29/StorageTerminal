package com.duox.advancedutilities.system.settings;
/*
 * Setting that stores a map of EntityType -> Boolean.
 * Used for entity filtering/whitelisting.
 */
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.LinkedHashMap;

public class EntityListSetting extends Setting<LinkedHashMap<EntityType<?>, Boolean>> {
    public EntityListSetting(String name) {
        super(name, new LinkedHashMap<>());
    }

    public void add(EntityType<?> entity) {
        if (!value.containsKey(entity)) {
            value.put(entity, true);
        }
    }

    public void remove(EntityType<?> entity) {
        value.remove(entity);
    }

    public void toggle(EntityType<?> entity) {
        if (value.containsKey(entity)) {
            value.put(entity, !value.get(entity));
        }
    }

    public boolean contains(EntityType<?> entity) {
        return value.getOrDefault(entity, false);
    }

    // --- Polymorphic Serialization ---

    @Override
    public JsonElement save() {
        JsonObject map = new JsonObject();
        this.value.forEach((type, enabled) -> {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key != null) map.addProperty(key.toString(), enabled);
        });
        return map;
    }

    @Override
    public void load(JsonElement element) {
        if (!element.isJsonObject()) return;

        LinkedHashMap<EntityType<?>, Boolean> newMap = new LinkedHashMap<>();
        JsonObject obj = element.getAsJsonObject();

        for (String key : obj.keySet()) {
            // Safe parsing to avoid crashes if config contains invalid IDs
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                newMap.put(BuiltInRegistries.ENTITY_TYPE.get(rl), obj.get(key).getAsBoolean());
            }
        }
        this.value = newMap;
    }
}