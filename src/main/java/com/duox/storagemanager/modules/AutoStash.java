package com.duox.storagemanager.modules;

import com.duox.storagemanager.system.Category;
import com.duox.storagemanager.system.Module;
import com.duox.storagemanager.system.settings.BooleanSetting;
import com.duox.storagemanager.system.settings.NumberSetting;
import com.duox.storagemanager.utils.CacheDatabase;
import com.duox.storagemanager.utils.CacheUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

import com.google.gson.reflect.TypeToken;

import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * AutoStash Module
 * 
 * Scans chests to build a global cache of items and intelligently stashes items
 * from the player's inventory into the most appropriate chests based on
 * distance, item grouping, and available space.
 * 
 * Also handles manual cache updates when players interact with chests.
 */
public class AutoStash extends Module {
    // --- Settings ---
    private final NumberSetting range = new NumberSetting("Range", 5.0, 1.0, 10.0, 0.5);
    private final BooleanSetting includeHotbar = new BooleanSetting("Include Hotbar", false);
    private final NumberSetting itemsPerTick = new NumberSetting("Items/Tick", 4.0, 1.0, 27.0, 1.0);
    private final BooleanSetting rebuildCache = new BooleanSetting("Rebuild Cache Next Run", false);

    // --- Scoring Weight Settings ---
    private final NumberSetting distanceWeight = new NumberSetting("Distance Weight", 0.2, 0.0, 1.0, 0.1);
    private final NumberSetting itemConcentrationWeight = new NumberSetting("Item Concentration Weight", 0.4, 0.0, 1.0,
            0.1);
    private final NumberSetting spaceWeight = new NumberSetting("Space Weight", 0.4, 0.0, 1.0, 0.1);

    // --- Cache Data Structures ---
    // Global Buffer: Contains ALL chest data loaded from file
    private static Map<String, Map<String, Integer>> globalBuffer = new HashMap<>();

    // Active Cache: Contains only chests within scan range (used by UI and
    // operations)
    private static Map<String, Map<String, Integer>> activeCache = new HashMap<>();

    // Flag indicating cache has changed
    public static boolean cacheDirty = false;

    // Position tracking for event-driven cache refresh
    private BlockPos lastUpdatePos = null;
    private double lastRange = -1;

    public static Map<String, Map<String, Integer>> getChestCache() {
        return activeCache; // Return active cache for UI/operations
    }

    public static Map<String, Map<String, Integer>> getGlobalBuffer() {
        return globalBuffer;
    }

    public double getRange() {
        return range.getValue();
    }

    // --- Runtime Variables ---
    private State currentState = State.IDLE;
    private BlockPos currentTarget = null;
    private int waitTimer = 0;
    private int silentContainerId = -1;
    private boolean containerReady = false;

    // --- Manual Container Tracking ---
    private static BlockPos lastInteractedBlock = null;
    private static boolean isManualOpen = false;
    private static BlockPos pendingManualUpdate = null;
    private static int manualUpdateTimer = 0;

    private List<BlockPos> scanQueue = new ArrayList<>();
    private Map<BlockPos, List<Integer>> stashQueue = new LinkedHashMap<>();
    private Iterator<Map.Entry<BlockPos, List<Integer>>> stashIterator;
    private Map.Entry<BlockPos, List<Integer>> currentStashEntry;

    // Track chests đã được xử lý trong session hiện tại để tránh spam
    private Set<BlockPos> processedChests = new HashSet<>();

    private enum State {
        IDLE,
        // Rebuild Cache States
        SCANNING_WORLD, OPENING_FOR_SCAN, WAITING_FOR_SCAN_OPEN, SCANNING_CONTENTS, CLOSING_AFTER_SCAN,
        // Smart Stash States
        CALCULATING_STASH,
        OPENING_FOR_STASH,
        WAITING_FOR_STASH_OPEN,
        STASHING_ITEMS,
        WAITING_FOR_UPDATE, // Wait for server to sync items to chest
        CLOSING_AFTER_STASH
    }

    public AutoStash() {
        super("AutoStash", "Scans chests to build a cache, then intelligently stashes items.", Category.UTILITY);

        this.getKeyMapping().setKey(InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_B));
        this.addSetting(range);
        this.addSetting(includeHotbar);
        this.addSetting(itemsPerTick);
        this.addSetting(rebuildCache);
        this.addSetting(distanceWeight);
        this.addSetting(itemConcentrationWeight);
        this.addSetting(spaceWeight);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null)
            return;
        resetState();
        if (rebuildCache.getValue()) {
            startRebuildCache();
        } else {
            startSmartStash();
        }
    }

    @Override
    public void onDisable() {
        if (silentContainerId != -1 && mc.player != null)
            sendClosePacket();
        resetState();
    }

    private void resetState() {
        currentState = State.IDLE;
        currentTarget = null;
        silentContainerId = -1;
        containerReady = false;
        scanQueue.clear();
        stashQueue.clear();
        stashIterator = null;
        currentStashEntry = null;
        processedChests.clear(); // Reset processed chests tracking
    }

    public boolean isSilentMode() {
        return this.isEnabled();
    }

    public void onSilentContainerOpen(int containerId, MenuType<?> menuType) {
        this.silentContainerId = containerId;
        this.containerReady = true;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            this.setEnabled(false);
            return;
        }

        // Process manual cache update (runs continuously)
        processManualCacheUpdate();

        switch (currentState) {
            case SCANNING_WORLD:
                processScanQueue();
                break;
            case OPENING_FOR_SCAN:
                openTargetSilent();
                break;
            case WAITING_FOR_SCAN_OPEN:
                waitForContainer(State.SCANNING_CONTENTS);
                break;
            case SCANNING_CONTENTS:
                scanContainerContents();
                break;
            case CLOSING_AFTER_SCAN:
                closeSilent(State.SCANNING_WORLD);
                break;

            case CALCULATING_STASH:
                break;
            case OPENING_FOR_STASH:
                openTargetSilent();
                break;
            case WAITING_FOR_STASH_OPEN:
                waitForContainer(State.STASHING_ITEMS);
                break;
            case STASHING_ITEMS:
                performStash();
                break;
            case WAITING_FOR_UPDATE:
                waitForUpdate();
                break; // Handle waiting state
            case CLOSING_AFTER_STASH:
                closeSilent(State.OPENING_FOR_STASH);
                break;
            case IDLE:
            default:
                break;
        }
    }

    // --- Rebuild Cache Logic ---
    private void startRebuildCache() {
        //globalBuffer.clear();
        //activeCache.clear();
        cacheDirty = true;
        scanQueue.clear();
        BlockPos playerPos = mc.player.blockPosition();
        int r = range.getInt();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (isValidContainer(be)) {
                        if (isDuplicateDoubleChest(be))
                            continue;
                        scanQueue.add(pos);
                    }
                }
            }
        }
        scanQueue.sort(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)));
        currentState = State.SCANNING_WORLD;
    }

    private void processScanQueue() {
        if (scanQueue.isEmpty()) {
            //CacheDatabase db = CacheDatabase.getInstance(mc);
            //db.replaceAll(globalBuffer);
            rebuildCache.setValue(false);

            // After rebuild, refresh active cache based on current position
            if (mc.player != null) {
                StorageManager sm = com.duox.storagemanager.system.ModuleManager.INSTANCE
                        .getModule(StorageManager.class);
                double range = sm != null ? sm.scanRange.getValue() : 16.0;
                refreshActiveCache(mc.player.blockPosition(), range);
            }

            sendMessage("Cache rebuild complete. Saved to disk.");
            this.setEnabled(false);
            return;
        }
        currentTarget = scanQueue.remove(0);
        currentState = State.OPENING_FOR_SCAN;
    }

    private void scanContainerContents() {
        updateCurrentContainerToCache();
        currentState = State.CLOSING_AFTER_SCAN;
    }

    // --- Smart Stash Logic ---
    private void startSmartStash() {
        CacheDatabase db = CacheDatabase.getInstance(mc);
        Type type = new TypeToken<Map<String, Map<String, Integer>>>() {
        }.getType();

        Map<String, Map<String, Integer>> loaded = db.loadAll();

        // Migration path: if DB empty but JSON exists, load JSON then persist to DB
        // if (loaded == null || loaded.isEmpty()) {
        // Path jsonPath = CacheUtils.getCacheFilePath(mc, "autostash");
        // Map<String, Map<String, Integer>> legacy = CacheUtils.loadFromJson(jsonPath,
        // type);
        // if (legacy != null && !legacy.isEmpty()) {
        // db.replaceAll(legacy);
        // loaded = legacy;
        // }
        // }

        if (loaded != null && !loaded.isEmpty() && globalBuffer.isEmpty()) {
            globalBuffer.putAll(loaded);
            cacheDirty = true;
        }

        if (globalBuffer.isEmpty()) {
            sendMessage("§cCache is empty. Please run Rebuild Cache first.");
            this.setEnabled(false);
            return;
        }

        // Refresh active cache before calculating stash plan
        if (mc.player != null) {
            StorageManager sm = com.duox.storagemanager.system.ModuleManager.INSTANCE.getModule(StorageManager.class);
            double range = sm != null ? sm.scanRange.getValue() : 16.0;
            refreshActiveCache(mc.player.blockPosition(), range);
        }

        calculateStashPlan();
        if (stashQueue.isEmpty()) {
            sendMessage("Nothing to stash.");
            this.setEnabled(false);
            return;
        }
        stashIterator = stashQueue.entrySet().iterator();
        moveToNextStashTarget();
    }

    private void calculateStashPlan() {
        sendMessage("§e[DEBUG] === Starting calculateStashPlan ===");
        stashQueue = new LinkedHashMap<>();
        if (mc.player == null) {
            sendMessage("§c[DEBUG] Player is null!");
            return;
        }

        LocalPlayer player = mc.player;
        int startInv = includeHotbar.getValue() ? 0 : 9;
        sendMessage("§e[DEBUG] Scanning inventory from slot " + startInv + " to 35");

        for (int i = startInv; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            int count = stack.getCount();

            sendMessage("§b[DEBUG] Slot " + i + ": " + itemId + " x" + count);

            // Kiểm tra active cache trước khi gọi findBestChest
            sendMessage("§7[DEBUG]   ActiveCache size: " + activeCache.size());
            boolean inAnyCache = false;
            for (Map.Entry<String, Map<String, Integer>> entry : activeCache.entrySet()) {
                if (entry.getValue().containsKey(itemId)) {
                    inAnyCache = true;
                    sendMessage("§7[DEBUG]   Found in cache at " + entry.getKey() + ": " + entry.getValue().get(itemId));
                    break;
                }
            }
            if (!inAnyCache) {
                sendMessage("§c[DEBUG]   Item NOT found in any active cache!");
            }

            BlockPos bestChest = findBestChest(itemId);

            if (bestChest != null) {
                sendMessage("§a[DEBUG]   -> Assigned to chest at " + bestChest);
                stashQueue.computeIfAbsent(bestChest, k -> new ArrayList<>()).add(i);
            } else {
                sendMessage("§c[DEBUG]   -> findBestChest returned NULL!");
            }
        }

        sendMessage("§e[DEBUG] === Stash Plan Summary ===");
        sendMessage("§e[DEBUG] Total chests in plan: " + stashQueue.size());
    }
    private BlockPos findBestChest(String itemId) {
        sendMessage("§6[DEBUG] findBestChest called for: " + itemId);
        BlockPos bestPos = null;
        double maxScore = -1;
        BlockPos playerPos = mc.player.blockPosition();
        double rangeSq = Math.pow(range.getValue(), 2);

        sendMessage("§6[DEBUG] Player pos: " + playerPos + ", Range: " + range.getValue() + " (sq: " + rangeSq + ")");
        sendMessage("§6[DEBUG] Checking " + activeCache.size() + " chests in active cache");

        int checked = 0;
        int skippedProcessed = 0;
        int skippedRange = 0;
        int skippedNoItem = 0;
        int skippedNegativeScore = 0;

        for (Map.Entry<String, Map<String, Integer>> entry : activeCache.entrySet()) {
            String posStr = entry.getKey();
            Map<String, Integer> contents = entry.getValue();
            BlockPos pos = CacheUtils.stringToPos(posStr);
            checked++;

            // Log mỗi 5 chest để tránh spam
            if (checked % 5 == 0) {
                sendMessage("§7[DEBUG]   Processed " + checked + "/" + activeCache.size() + " chests...");
            }

            // Skip if out of range
            double distSq = pos.distSqr(playerPos);
            if (distSq > rangeSq) {
                skippedRange++;
                continue;
            }

            // Skip if already processed in this session
            if (processedChests.contains(pos)) {
                skippedProcessed++;
                sendMessage("§7[DEBUG]   Chest " + posStr + " already processed this session");
                continue;
            }

            // Skip if doesn't contain item
            if (!contents.containsKey(itemId)) {
                skippedNoItem++;
                continue;
            }

            sendMessage("§a[DEBUG]   Chest " + posStr + " contains " + itemId + " (count: " + contents.get(itemId) + ")");
            sendMessage("§a[DEBUG]   Distance: " + Math.sqrt(distSq));

            // Calculate multi-factor score
            double score = calculateChestScore(pos, contents, itemId);
            sendMessage("§a[DEBUG]   Score: " + score);

            if (score > maxScore) {
                maxScore = score;
                bestPos = pos;
                sendMessage("§a[DEBUG]   -> New best chest!");
            } else if (score < 0) {
                skippedNegativeScore++;
                sendMessage("§c[DEBUG]   -> Negative score, excluded");
            }
        }

        sendMessage("§6[DEBUG] findBestChest Summary:");
        sendMessage("§6[DEBUG]   Total checked: " + checked);
        sendMessage("§6[DEBUG]   Skipped (range): " + skippedRange);
        sendMessage("§6[DEBUG]   Skipped (processed): " + skippedProcessed);
        sendMessage("§6[DEBUG]   Skipped (no item): " + skippedNoItem);
        sendMessage("§6[DEBUG]   Skipped (negative score): " + skippedNegativeScore);
        sendMessage("§6[DEBUG]   Best pos: " + bestPos + " (score: " + maxScore + ")");

        return bestPos;
    }

    /**
     * Calculate a multi-factor score for chest selection
     *
     * @param pos      Chest position
     * @param contents Chest contents (itemId -> count)
     * @param itemId   Item to stash
     * @return Score (higher = better fit)
     */
    private double calculateChestScore(BlockPos pos, Map<String, Integer> contents, String itemId) {
        // Factor 1: Distance (closer = better)
        double distance = Math.sqrt(pos.distSqr(mc.player.blockPosition()));
        double distScore = 1.0 / (1.0 + distance / 10.0);

        // Get max stack size for this specific item
        int maxStackSize = getMaxStackSize(itemId);
        sendMessage("§8[DEBUG-SCORE] " + pos + " maxStackSize for " + itemId + ": " + maxStackSize);

        // Factor 2: Item Concentration (more of this item = better fit)
        int itemCount = contents.getOrDefault(itemId, 0);
        double itemScore = Math.min(1.0, itemCount / 64.0);

        // Factor 3: Space Availability
        ChestSpaceInfo spaceInfo = calculateChestSpace(pos, contents);
        sendMessage("§8[DEBUG-SCORE] " + pos + " spaceInfo: total=" + spaceInfo.totalSlots
                + ", used=" + spaceInfo.usedSlots + ", available=" + spaceInfo.availableSlots
                + ", hasStackableSpace=" + spaceInfo.hasStackableSpace);

        double spaceScore;

        if (spaceInfo.availableSlots == 0) {
            sendMessage("§8[DEBUG-SCORE] " + pos + " No empty slots available");
            // Chest has no empty slots - check if THIS SPECIFIC ITEM can be stacked
            if (contents.containsKey(itemId)) {
                int currentItemCount = contents.get(itemId);
                sendMessage("§8[DEBUG-SCORE] " + pos + " Current item count in chest: " + currentItemCount);
                sendMessage("§8[DEBUG-SCORE] " + pos + " Calculating lastStackCount = " + currentItemCount + " % " + maxStackSize);

                // CRITICAL FIX: currentItemCount is TOTAL count, not per-slot
                // Check if the last stack has space
                int lastStackCount = currentItemCount % maxStackSize;
                sendMessage("§8[DEBUG-SCORE] " + pos + " lastStackCount: " + lastStackCount);

                if (lastStackCount == 0) {
                    // All stacks are full (e.g., 192 = 3 * 64)
                    sendMessage("§c[DEBUG-SCORE] " + pos + " All stacks FULL (lastStackCount=0), returning -1.0");
                    return -1.0; // Exclude this chest from consideration
                } else {
                    // Last stack has space (e.g., 130 = 2*64 + 2, last stack has 62 free)
                    spaceScore = 0.3;
                    sendMessage("§a[DEBUG-SCORE] " + pos + " Last stack has space, spaceScore=0.3");
                }
            } else {
                // Chest doesn't contain this item and has no empty slots
                sendMessage("§c[DEBUG-SCORE] " + pos + " Item not in chest and no empty slots, returning -1.0");
                return -1.0; // Exclude this chest from consideration
            }
        } else {
            // Normal case: calculate based on used capacity
            int totalCapacity = spaceInfo.totalSlots * 64;
            int usedCapacity = contents.values().stream().mapToInt(Integer::intValue).sum();
            spaceScore = Math.max(0, 1.0 - (double) usedCapacity / totalCapacity);
            sendMessage("§8[DEBUG-SCORE] " + pos + " Normal calculation: usedCapacity=" + usedCapacity
                    + ", totalCapacity=" + totalCapacity + ", spaceScore=" + spaceScore);
        }

        // Weighted Score
        double totalWeight = distanceWeight.getValue() + itemConcentrationWeight.getValue() + spaceWeight.getValue();
        if (totalWeight == 0) totalWeight = 1.0;

        double finalScore = (distScore * distanceWeight.getValue() +
                itemScore * itemConcentrationWeight.getValue() +
                spaceScore * spaceWeight.getValue()) / totalWeight;

        sendMessage("§8[DEBUG-SCORE] " + pos + " distScore=" + distScore
                + ", itemScore=" + itemScore + ", spaceScore=" + spaceScore
                + ", final=" + finalScore);

        return finalScore;
    }
    /**
     * Get maximum stack size for an item
     */
    private int getMaxStackSize(String itemId) {
        try {
            net.minecraft.resources.Identifier location = net.minecraft.resources.Identifier.parse(itemId);

            // FIX: Handle Optional<Holder<Item>> return type
            var optionalItem = BuiltInRegistries.ITEM.get(location);
            if (optionalItem.isPresent()) {
                return optionalItem.get().value().getDefaultMaxStackSize();
            }
        } catch (Exception e) {
            // If error, default to 64
        }
        return 64; // Default max stack size
    }

    /**
     * Calculate chest space information
     */
    /**
     * Calculate chest space information
     */
    /**
     * Refined calculation of chest space based on actual item counts.
     */
    private ChestSpaceInfo calculateChestSpace(BlockPos pos, Map<String, Integer> contents) {
        BlockEntity be = mc.level.getBlockEntity(pos);
        int totalSlots = getChestCapacity(be);

        int occupiedSlots = 0;
        boolean hasStackableSpace = false;

        for (Map.Entry<String, Integer> entry : contents.entrySet()) {
            String itemId = entry.getKey();
            int totalCount = entry.getValue();
            int maxStack = getMaxStackSize(itemId);

            // Calculate physical slots occupied: ceil(totalCount / maxStack)
            // Formula: (numerator + denominator - 1) / denominator
            int slotsTaken = (totalCount + maxStack - 1) / maxStack;
            occupiedSlots += slotsTaken;

            // Check if the last stack has space
            if (totalCount % maxStack != 0) {
                hasStackableSpace = true;
            }
        }

        // Clamp available slots to 0 to prevent negatives
        int availableSlots = Math.max(0, totalSlots - occupiedSlots);

        return new ChestSpaceInfo(totalSlots, occupiedSlots, availableSlots, hasStackableSpace);
    }    /**
     * Get chest capacity (number of slots)
     */
    private int getChestCapacity(BlockEntity be) {
        if (be instanceof ChestBlockEntity) {
            net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
            if (state.hasProperty(ChestBlock.TYPE)) {
                ChestType type = state.getValue(ChestBlock.TYPE);
                // If it's NOT a single chest, it's a Double Chest (Left or Right).
                // Minecraft combines them into one inventory view of 54 slots.
                if (type != ChestType.SINGLE) {
                    return 54;
                }
            }
            return 27;
        } else if (be instanceof BarrelBlockEntity) {
            return 27;
        } else if (be instanceof ShulkerBoxBlockEntity) {
            return 27;
        }
        return 27; // Default
    }
    /**
     * Helper class to store chest space information
     */
    private static class ChestSpaceInfo {
        final int totalSlots;
        final int usedSlots;
        final int availableSlots;
        final boolean hasStackableSpace;

        ChestSpaceInfo(int totalSlots, int usedSlots, int availableSlots, boolean hasStackableSpace) {
            this.totalSlots = totalSlots;
            this.usedSlots = usedSlots;
            this.availableSlots = availableSlots;
            this.hasStackableSpace = hasStackableSpace;
        }
    }

    private void moveToNextStashTarget() {
        if (stashIterator != null && stashIterator.hasNext()) {
            currentStashEntry = stashIterator.next();
            currentTarget = currentStashEntry.getKey();
            currentState = State.OPENING_FOR_STASH;
        } else {
            this.setEnabled(false);
        }
    }

    private void performStash() {
        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlots = menu.slots.size() - 36;
        if (containerSlots <= 0) {
            currentState = State.CLOSING_AFTER_STASH;
            return;
        }
        List<Integer> slotsToMove = currentStashEntry.getValue();
        int limit = itemsPerTick.getInt();
        int moves = 0;
        Iterator<Integer> it = slotsToMove.iterator();
        while (it.hasNext() && moves < limit) {
            int invSlotIndex = it.next();
            int menuSlotId = (invSlotIndex < 9) ? containerSlots + 27 + invSlotIndex
                    : containerSlots + (invSlotIndex - 9);
            Slot slot = menu.getSlot(menuSlotId);
            if (slot.hasItem()) {
                sendQuickMovePacket(menu, menuSlotId);
                moves++;
            }
            it.remove();
        }

        // When all transfer packets are sent, switch to waiting state for sync
        if (slotsToMove.isEmpty()) {
            currentState = State.WAITING_FOR_UPDATE;
            waitTimer = 10; // Wait 10 ticks (0.5s) for server to update chest
        }
    }

    // Wait for synchronization
    private void waitForUpdate() {
        waitTimer--;
        if (waitTimer <= 0) {
            // After waiting, rescan chest for most accurate data
            updateCurrentContainerToCache();

            // Mark current chest as processed to avoid re-selecting it
            if (currentTarget != null) {
                processedChests.add(currentTarget);
            }

            // DYNAMIC RE-PLANNING: Kiểm tra xem còn items cần stash không
            if (mc.player != null) {
                LocalPlayer player = mc.player;
                int startInv = includeHotbar.getValue() ? 0 : 9;
                boolean hasItemsToStash = false;

                // Kiểm tra xem còn items trong inventory không
                for (int i = startInv; i < 36; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty()) {
                        hasItemsToStash = true;
                        break;
                    }
                }

                if (hasItemsToStash) {
                    // Refresh active cache với dữ liệu mới nhất
                    StorageManager sm = com.duox.storagemanager.system.ModuleManager.INSTANCE
                            .getModule(StorageManager.class);
                    double range = sm != null ? sm.scanRange.getValue() : 16.0;
                    refreshActiveCache(player.blockPosition(), range);

                    // RE-CALCULATE PLAN cho các items còn lại
                    calculateStashPlan();

                    if (!stashQueue.isEmpty()) {
                        // Còn chest để stash, reset iterator và tiếp tục
                        stashIterator = stashQueue.entrySet().iterator();
                        currentState = State.CLOSING_AFTER_STASH;
                        return;
                    }
                }
            }

            // Không còn gì để stash hoặc không còn chest phù hợp
            currentState = State.CLOSING_AFTER_STASH;
        }
    }

    private void updateCurrentContainerToCache() {
        if (currentTarget == null || mc.player == null)
            return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlots = menu.slots.size() - 36;
        if (containerSlots > 0) {
            Map<String, Integer> contents = new HashMap<>();
            for (int i = 0; i < containerSlots; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (!stack.isEmpty()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    contents.put(itemId, contents.getOrDefault(itemId, 0) + stack.getCount());
                }
            }
            String posKey = CacheUtils.posToString(currentTarget);

            // Update both globalBuffer and activeCache
            globalBuffer.put(posKey, contents);

            // Only add to activeCache if within range
            if (mc.player != null) {
                StorageManager sm = com.duox.storagemanager.system.ModuleManager.INSTANCE
                        .getModule(StorageManager.class);
                double scanRange = sm != null ? sm.scanRange.getValue() : 16.0;
                if (currentTarget.distSqr(mc.player.blockPosition()) <= scanRange * scanRange) {
                    activeCache.put(posKey, contents);
                }
            }

            // Set dirty flag for GUI update
            cacheDirty = true;

            CacheDatabase db = CacheDatabase.getInstance(mc);
            db.upsertChest(posKey, contents);
        }
    }

    // --- Helper Methods ---
    private void openTargetSilent() {
        if (currentTarget == null)
            return;
        Vec3 center = Vec3.atCenterOf(currentTarget);
        BlockHitResult hitResult = new BlockHitResult(center, Direction.UP, currentTarget, false);
        containerReady = false;
        silentContainerId = -1;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(InteractionHand.MAIN_HAND);
        currentState = (currentState == State.OPENING_FOR_SCAN) ? State.WAITING_FOR_SCAN_OPEN
                : State.WAITING_FOR_STASH_OPEN;
        waitTimer = 20;
    }

    private void waitForContainer(State nextState) {
        if (containerReady && silentContainerId != -1) {
            currentState = nextState;
            return;
        }
        waitTimer--;
        if (waitTimer <= 0) {
            if (currentState == State.WAITING_FOR_SCAN_OPEN)
                currentState = State.SCANNING_WORLD;
            else
                currentState = State.CLOSING_AFTER_STASH;
        }
    }

    private void closeSilent(State nextState) {
        sendClosePacket();
        silentContainerId = -1;
        containerReady = false;
        if (nextState == State.OPENING_FOR_STASH)
            moveToNextStashTarget();
        else
            currentState = nextState;
    }

    private void sendMessage(String message) {
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("§b[AutoStash] §r" + message), false);
    }

    private boolean isValidContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    private boolean isDuplicateDoubleChest(BlockEntity be) {
        if (be instanceof ChestBlockEntity) {
            net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
            if (state.hasProperty(ChestBlock.TYPE))
                return state.getValue(ChestBlock.TYPE) == ChestType.LEFT;
        }
        return false;
    }

    private void sendQuickMovePacket(AbstractContainerMenu menu, int slotId) {
        // FIX: Use GameMode to handle the click.
        // This automatically handles the packet creation, state IDs, and the new
        // HashedStack logic.
        if (mc.gameMode != null) {
            mc.gameMode.handleInventoryMouseClick(
                    menu.containerId,
                    slotId,
                    0,
                    ClickType.QUICK_MOVE,
                    mc.player);
        }
    }

    private void sendClosePacket() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
    }

    // ========== MANUAL CONTAINER TRACKING ==========

    /**
     * Event-driven cache refresh logic
     * Called from ModuleManager on tick to check if activeCache needs updating
     */
    public void forceRefreshActiveCache() {
        if (mc.player == null)
            return;

        BlockPos currentPos = mc.player.blockPosition();

        // Get range directly from the module instance or settings
        StorageManager sm = com.duox.storagemanager.system.ModuleManager.INSTANCE.getModule(StorageManager.class);
        double currentRange = sm != null ? sm.scanRange.getValue() : 16.0;

        // Directly call internal refresh logic
        refreshActiveCache(currentPos, currentRange);

        // Update state tracking (optional, but good for debugging)
        lastUpdatePos = currentPos;
        lastRange = currentRange;
    }

    /**
     * Refresh active cache by filtering globalBuffer based on position and range
     */
    private void refreshActiveCache(BlockPos center, double range) {
        double rangeSq = range * range;
        Map<String, Map<String, Integer>> newActive = new HashMap<>();

        globalBuffer.forEach((posStr, contents) -> {
            BlockPos chestPos = CacheUtils.stringToPos(posStr);
            if (chestPos.distSqr(center) <= rangeSq) {
                newActive.put(posStr, contents);
            }
        });

        activeCache = newActive;
        cacheDirty = true; // Notify UI to update
    }

    /**
     * Update global buffer and sync to file, then force refresh activeCache
     * This method should be called after successful stash/withdraw operations
     */
    public static void updateGlobalAndSync(BlockPos pos, Map<String, Integer> newData) {
        String key = CacheUtils.posToString(pos);
        globalBuffer.put(key, newData);

        // Sync to SQLite immediately
        Minecraft mc = Minecraft.getInstance();
        CacheDatabase db = CacheDatabase.getInstance(mc);
        db.upsertChest(key, newData);

        // Force refresh active cache on next tick
        AutoStash instance = com.duox.storagemanager.system.ModuleManager.INSTANCE.getModule(AutoStash.class);
        if (instance != null) {
            instance.lastUpdatePos = null;
            AutoStash.cacheDirty = true;
        }
    }

    /**
     * Clear all in-memory caches and persisted storage.
     * Used by the StorageScreen clear button to ensure both active and global
     * caches reset.
     */
    public static void clearCachesAndStorage(Minecraft mc) {
        globalBuffer.clear();
        activeCache.clear();
        cacheDirty = true;

        if (mc != null) {
            CacheDatabase db = CacheDatabase.getInstance(mc);
            db.clearServer();

            Path cacheFile = CacheUtils.getCacheFilePath(mc, "autostash");
            try {
                Files.deleteIfExists(cacheFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        AutoStash instance = com.duox.storagemanager.system.ModuleManager.INSTANCE.getModule(AutoStash.class);
        if (instance != null) {
            instance.lastUpdatePos = null;
            instance.lastRange = -1;
        }
    }

    /**
     * Called from Mixin when player clicks a block
     */
    public static void setLastInteractedBlock(BlockPos pos) {
        lastInteractedBlock = pos;
        isManualOpen = true;
    }

    /**
     * Get the block position the player just clicked
     */
    public static BlockPos getLastInteractedBlock() {
        return lastInteractedBlock;
    }

    /**
     * Called from Mixin when container open packet is received
     * If manual open (not silent mode), update cache
     */
    public void onManualContainerOpen(int containerId, BlockPos containerPos) {
        if (mc == null || mc.player == null || mc.level == null)
            return;

        // Skip if not manual open
        if (!isManualOpen)
            return;

        // Check if container is valid
        BlockEntity be = mc.level.getBlockEntity(containerPos);
        if (!isValidContainer(be))
            return;

        // Save position to update cache after container fully opens
        // We will update in onTick after a few ticks
        scheduleManualCacheUpdate(containerPos);
    }

    private void scheduleManualCacheUpdate(BlockPos pos) {
        // Handle double chest: skip if LEFT chest
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (isDuplicateDoubleChest(be)) {
            // Find RIGHT chest and use its position
            if (be instanceof ChestBlockEntity) {
                net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
                if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) == ChestType.LEFT) {
                    Direction facing = state.getValue(ChestBlock.FACING);
                    pos = pos.relative(facing.getClockWise());
                }
            }
        }

        pendingManualUpdate = pos;
        manualUpdateTimer = 5; // Wait 5 ticks for container menu to sync
    }

    /**
     * Called in onTick to process manual cache update
     */
    private void processManualCacheUpdate() {
        tickManualCacheUpdate();
    }

    /**
     * Static method to handle manual cache update, measurable from anywhere
     */
    public static void tickManualCacheUpdate() {
        if (pendingManualUpdate == null)
            return;

        Minecraft minecraft = Minecraft.getInstance();
        manualUpdateTimer--;
        if (manualUpdateTimer <= 0) {
            // Check if container is currently open
            if (minecraft.player != null && minecraft.player.containerMenu != null
                    && minecraft.player.containerMenu != minecraft.player.inventoryMenu) {
                updateManualContainerCache(pendingManualUpdate);
            }
            pendingManualUpdate = null;
            isManualOpen = false;
        }
    }

    /**
     * Update cache for manually opened container
     */
    private static void updateManualContainerCache(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null)
            return;

        AbstractContainerMenu menu = minecraft.player.containerMenu;
        int containerSlots = menu.slots.size() - 36;

        if (containerSlots > 0) {
            Map<String, Integer> contents = new HashMap<>();
            for (int i = 0; i < containerSlots; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (!stack.isEmpty()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    contents.put(itemId, contents.getOrDefault(itemId, 0) + stack.getCount());
                }
            }

            String posKey = CacheUtils.posToString(pos);

            // Update both globalBuffer and activeCache
            globalBuffer.put(posKey, contents);

            // Only add to activeCache if within range
            StorageManager sm = com.duox.storagemanager.system.ModuleManager.INSTANCE.getModule(StorageManager.class);
            double scanRange = sm != null ? sm.scanRange.getValue() : 16.0;
            if (minecraft.player != null && pos.distSqr(minecraft.player.blockPosition()) <= scanRange * scanRange) {
                activeCache.put(posKey, contents);
            }

            cacheDirty = true;

            // Save to DB
            CacheDatabase db = CacheDatabase.getInstance(minecraft);
            db.upsertChest(posKey, contents);

            // Notify player (optional)
            // sendMessage("§aCache updated for chest at " + posKey);
        }
    }
}
