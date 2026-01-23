package com.duox.advancedutilities.system.settings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;

public class BlockListSetting extends Setting<LinkedHashMap<Block, Boolean>> {
    public BlockListSetting(String name) {
        super(name, new LinkedHashMap<>());
    }

    public void add(Block block) {
        if (!value.containsKey(block)) value.put(block, true);
    }

    public void remove(Block block) {
        value.remove(block);
    }

    public void toggle(Block block) {
        if (value.containsKey(block)) value.put(block, !value.get(block));
    }

    public boolean contains(Block block) {
        return value.getOrDefault(block, false);
    }
    // Add this method to allow the Finder module to retrieve the list of blocks to search for
    public java.util.List<Block> getBlocks() {
        java.util.List<Block> list = new java.util.ArrayList<>();
        // Iterate through the map and only add blocks that are enabled (true)
        this.value.forEach((block, enabled) -> {
            if (enabled) list.add(block);
        });
        return list;
    }

    @Override
    public JsonElement save() {
        JsonObject map = new JsonObject();
        this.value.forEach((block, enabled) -> {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (key != null) map.addProperty(key.toString(), enabled);
        });
        return map;
    }

    @Override
    public void load(JsonElement element) {
        if (!element.isJsonObject()) return;

        LinkedHashMap<Block, Boolean> newMap = new LinkedHashMap<>();
        JsonObject obj = element.getAsJsonObject();

        for (String key : obj.keySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
                newMap.put(BuiltInRegistries.BLOCK.get(rl), obj.get(key).getAsBoolean());
            }
        }
        this.value = newMap;
    }
}