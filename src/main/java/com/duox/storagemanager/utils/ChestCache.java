package com.duox.storagemanager.utils;

import com.duox.storagemanager.modules.StorageManager;
import com.duox.storagemanager.system.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/* Global and in-range chest content caches, keyed by immutable chest position. */
public class ChestCache {
    /* Active-cache refresh radius used when no module setting is available yet. */
    private static final double DEFAULT_ACTIVE_RANGE = 16.0;

    private static final Map<BlockPos, Map<String, Integer>> GLOBAL_BUFFER = new HashMap<>();
    private static final Map<BlockPos, Map<String, Integer>> ACTIVE_CACHE = new HashMap<>();
    private static long version = 0;

    private static String currentLoadedServerId = "";
    private static String currentLoadedDimension = "";

    public static Map<BlockPos, Map<String, Integer>> getGlobalBuffer() {
        return GLOBAL_BUFFER;
    }

    public static Map<BlockPos, Map<String, Integer>> getActiveCache() {
        return ACTIVE_CACHE;
    }

    public static long getVersion() {
        return version;
    }

    private static void markChanged() {
        version++;
    }

    /* Rebuilds the in-memory cache when the world or dimension changes. */
    public static boolean ensureLoaded(Minecraft mc) {
        if (isLoadedFor(mc)) return false;
        loadFromDatabase(mc, CacheUtils.getDimensionId(mc));
        return true;
    }

    private static boolean isLoadedFor(Minecraft mc) {
        return currentLoadedServerId.equals(CacheUtils.getSafeServerIdentifier(mc))
                && currentLoadedDimension.equals(CacheUtils.getDimensionId(mc));
    }

    private static void loadFromDatabase(Minecraft mc, String dimensionId) {
        CacheDatabase db = CacheDatabase.getInstance(mc);
        Map<String, Map<String, Integer>> loaded = db.loadByDimension(dimensionId);

        GLOBAL_BUFFER.clear();
        ACTIVE_CACHE.clear();
        currentLoadedServerId = CacheUtils.getSafeServerIdentifier(mc);
        currentLoadedDimension = dimensionId;

        if (loaded != null) {
            // Rows are keyed by text in SQLite; parse each position once here.
            loaded.forEach((posStr, contents) -> {
                BlockPos pos = parsePosition(posStr);
                if (pos != null) GLOBAL_BUFFER.put(pos, contents);
            });
        }

        if (mc.player != null) {
            refreshActiveCache(mc.player.blockPosition(), scanRangeFromModule());
        }

        markChanged();
    }

    /* Ignores rows written by an older or corrupted build rather than failing the load. */
    private static BlockPos parsePosition(String posStr) {
        return CacheUtils.tryStringToPos(posStr);
    }

    public static void clearAll(Minecraft mc) {
        GLOBAL_BUFFER.clear();
        ACTIVE_CACHE.clear();
        markChanged();

        CacheDatabase db = CacheDatabase.getInstance(mc);
        db.clearServer();
    }

    public static void updateAndSync(BlockPos pos, Map<String, Integer> newData) {
        Minecraft mc = Minecraft.getInstance();
        // Snapshot so the caches never alias a map the caller still mutates.
        Map<String, Integer> contents = new HashMap<>(newData);
        BlockPos key = pos.immutable();
        GLOBAL_BUFFER.put(key, contents);

        BlockPos center = mc.player != null ? mc.player.blockPosition() : key;
        refreshActiveCache(center, scanRangeFromModule());
        persist(mc, key, contents);
    }

    /* Stores the chest in the database and bumps the version. */
    private static void persist(Minecraft mc, BlockPos pos, Map<String, Integer> contents) {
        CacheDatabase.getInstance(mc).upsertChest(CacheUtils.getDimensionId(mc),
                CacheUtils.posToString(pos), contents);
        markChanged();
    }

    public static void refreshActiveCache(BlockPos center, double range) {
        double rangeSq = range * range;
        Map<BlockPos, Map<String, Integer>> newActive = new HashMap<>();

        GLOBAL_BUFFER.forEach((chestPos, contents) -> {
            if (chestPos.distSqr(center) <= rangeSq) {
                newActive.put(chestPos, contents);
            }
        });

        if (!newActive.equals(ACTIVE_CACHE)) {
            ACTIVE_CACHE.clear();
            ACTIVE_CACHE.putAll(newActive);
            markChanged();
        }
    }

    /* Rebuilds the active cache around the player with the configured scan range. */
    public static void refreshActiveCache(Minecraft mc) {
        if (mc.player == null) return;
        refreshActiveCache(mc.player.blockPosition(), scanRangeFromModule());
    }

    private static double scanRangeFromModule() {
        StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
        return sm != null ? sm.scanRange.getValue() : DEFAULT_ACTIVE_RANGE;
    }

    public static void updateFromContainerMenu(BlockPos pos, AbstractContainerMenu menu, Minecraft mc) {
        int containerSlots = InventoryUtils.storageSlotCount(menu);

        if (containerSlots <= 0) return;

        Map<String, Integer> contents = new HashMap<>();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                String itemId = ItemSerializer.serialize(stack);
                contents.merge(itemId, stack.getCount(), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
            }
        }

        BlockPos key = pos.immutable();
        GLOBAL_BUFFER.put(key, contents);

        if (mc.player != null) {
            double scanRange = scanRangeFromModule();
            if (key.distSqr(mc.player.blockPosition()) <= scanRange * scanRange) {
                ACTIVE_CACHE.put(key, contents);
            }
        }

        persist(mc, key, contents);
    }
}
