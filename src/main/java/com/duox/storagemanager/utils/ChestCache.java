package com.duox.storagemanager.utils;

import com.duox.storagemanager.utils.CacheDatabase;
import com.duox.storagemanager.utils.CacheUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChestCache {
    private static final Map<String, Map<String, Integer>> GLOBAL_BUFFER = new HashMap<>();
    private static final Map<String, Map<String, Integer>> ACTIVE_CACHE = new HashMap<>();
    public static final AtomicBoolean DIRTY_FLAG = new AtomicBoolean(false);

    public static Map<String, Map<String, Integer>> getGlobalBuffer() {
        return GLOBAL_BUFFER;
    }

    public static Map<String, Map<String, Integer>> getActiveCache() {
        return ACTIVE_CACHE;
    }

    public static boolean isCacheEmpty() {
        return GLOBAL_BUFFER.isEmpty();
    }

    public static void loadFromDatabase(Minecraft mc) {
        CacheDatabase db = CacheDatabase.getInstance(mc);
        Map<String, Map<String, Integer>> loaded = db.loadAll();

        if (loaded != null && !loaded.isEmpty()) {
            GLOBAL_BUFFER.putAll(loaded);
            DIRTY_FLAG.set(true);
        }
    }

    public static void clearAll(Minecraft mc) {
        GLOBAL_BUFFER.clear();
        ACTIVE_CACHE.clear();
        DIRTY_FLAG.set(true);

        CacheDatabase db = CacheDatabase.getInstance(mc);
        db.clearServer();

        java.nio.file.Path cacheFile = com.duox.storagemanager.utils.CacheUtils.getCacheFilePath(mc, "autostash");
        try {
            java.nio.file.Files.deleteIfExists(cacheFile);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateAndSync(BlockPos pos, Map<String, Integer> newData, Minecraft mc) {
        String key = CacheUtils.posToString(pos);
        GLOBAL_BUFFER.put(key, newData);

        CacheDatabase db = CacheDatabase.getInstance(mc);
        db.upsertChest(key, newData);

        // FIXED: Removed extra 'pos' argument - only pass center and range
        BlockPos center = mc.player != null ? mc.player.blockPosition() : pos;
        refreshActiveCache(center, 64.0);
        DIRTY_FLAG.set(true);
    }

    public static void refreshActiveCache(BlockPos center, double range) {
        double rangeSq = range * range;
        Map<String, Map<String, Integer>> newActive = new HashMap<>();

        GLOBAL_BUFFER.forEach((posStr, contents) -> {
            BlockPos chestPos = CacheUtils.stringToPos(posStr);
            if (chestPos.distSqr(center) <= rangeSq) {
                newActive.put(posStr, contents);
            }
        });

        if (!newActive.equals(ACTIVE_CACHE)) {
            ACTIVE_CACHE.clear();
            ACTIVE_CACHE.putAll(newActive);
            DIRTY_FLAG.set(true);
        }
    }

    public static void updateFromContainerMenu(BlockPos pos, AbstractContainerMenu menu, Minecraft mc) {
        int containerSlots = menu.slots.size() - 36; // Player inventory is always last 36 slots

        if (containerSlots <= 0) return;

        Map<String, Integer> contents = new HashMap<>();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                String itemId = ItemSerializer.serialize(stack);
                contents.put(itemId, contents.getOrDefault(itemId, 0) + stack.getCount());
            }
        }

        String posKey = CacheUtils.posToString(pos);
        GLOBAL_BUFFER.put(posKey, contents);

        // Update active cache if in range
        if (mc.player != null) {
            double scanRange = 16.0; // Default
            com.duox.storagemanager.modules.StorageManager sm =
                    com.duox.storagemanager.system.ModuleManager.INSTANCE.getModule(
                            com.duox.storagemanager.modules.StorageManager.class);
            if (sm != null) {
                scanRange = sm.scanRange.getValue();
            }

            if (pos.distSqr(mc.player.blockPosition()) <= scanRange * scanRange) {
                ACTIVE_CACHE.put(posKey, contents);
            }
        }

        DIRTY_FLAG.set(true);
        CacheDatabase db = CacheDatabase.getInstance(mc);
        db.upsertChest(posKey, contents);
    }

    public static void markDirty() {
        DIRTY_FLAG.set(true);
    }

    public static void clearDirty() {
        DIRTY_FLAG.set(false);
    }
}